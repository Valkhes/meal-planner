package dev.valkhes.mealplanner.application.server

import dev.valkhes.mealplanner.application.events.EventEnvelope
import dev.valkhes.mealplanner.application.events.EventSchema
import dev.valkhes.mealplanner.application.events.EventStore
import dev.valkhes.mealplanner.application.events.ReadEvent
import dev.valkhes.mealplanner.application.habits.DishDetail
import dev.valkhes.mealplanner.application.habits.DishSummary
import dev.valkhes.mealplanner.application.habits.HabitViews
import dev.valkhes.mealplanner.application.habits.MealEntry
import dev.valkhes.mealplanner.application.habits.Tonight
import dev.valkhes.mealplanner.application.habits.TonightView
import dev.valkhes.mealplanner.application.shared.Clock
import dev.valkhes.mealplanner.application.shared.Device
import dev.valkhes.mealplanner.application.shared.IdGenerator
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.application.sync.SyncChannel
import dev.valkhes.mealplanner.application.sync.SyncCodec
import dev.valkhes.mealplanner.application.sync.SyncMessage
import dev.valkhes.mealplanner.application.sync.SyncProtocolMismatch
import dev.valkhes.mealplanner.application.sync.SyncReport
import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.journal.MealLogged
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.planning.Absence
import dev.valkhes.mealplanner.domain.planning.AbsenceId
import dev.valkhes.mealplanner.domain.shared.DateRange
import dev.valkhes.mealplanner.domain.shared.Decision
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.domain.shared.DomainEvent
import dev.valkhes.mealplanner.domain.shared.MealId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** The in-process server: the phone's own event store, the read state in memory. */
class LocalMealServer private constructor(
    private val store: EventStore,
    private val schema: EventSchema,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val device: Device,
    private var state: MealState,
) : MealServer {
    private val mutex = Mutex()
    private val revisionFlow = MutableStateFlow(0L)

    override val revision: StateFlow<Long> = revisionFlow.asStateFlow()

    override suspend fun send(command: Command): CommandResult =
        mutex.withLock {
            when (val decision = decide(command)) {
                is Outcome.Refused -> CommandResult.Refused(decision.error)
                is Outcome.Events -> {
                    if (decision.events.isNotEmpty()) record(decision.events)
                    decision.result
                }
            }
        }

    override suspend fun dishes(): List<DishSummary> = read { HabitViews.summaries(it.cookbook, it.journal, it.planning.ranges) }

    override suspend fun dish(id: DishId): DishDetail? = read { HabitViews.detail(it.cookbook, it.journal, it.planning.ranges, id) }

    override suspend fun recentMeals(limit: Int): List<MealEntry> = read { HabitViews.meals(it.cookbook, it.journal).take(limit) }

    override suspend fun mealsOn(date: LocalDate): List<MealEntry> = read { s -> HabitViews.meals(s.cookbook, s.journal).filter { it.meal.date == date } }

    override suspend fun tonight(availableMinutes: Int?): Tonight =
        read { TonightView.of(it.cookbook, it.journal, it.planning.ranges, clock.today(), availableMinutes) }

    override suspend fun absences(): List<Absence> = read { s -> s.planning.all.filter { it.days.endInclusive >= clock.today() } }

    override suspend fun isAway(date: LocalDate): Boolean = read { it.planning.isAway(date) }

    /**
     * Exchanges events with the other phone over [channel] (README, "Sync"): both sides end with the
     * union of both logs, replayed in recorded order. Events from a newer app are kept and passed on.
     */
    suspend fun sync(channel: SyncChannel): SyncReport {
        val mine = mutex.withLock { store.all() }
        channel.send(SyncMessage.Hello(SyncCodec.PROTOCOL, device.deviceId, mine.mapTo(HashSet()) { it.id }))
        val hello = channel.receive() as? SyncMessage.Hello ?: throw IllegalStateException("expected the other phone's hello")
        if (hello.protocol != SyncCodec.PROTOCOL) throw SyncProtocolMismatch(hello.protocol)
        val missing = mine.filter { it.id !in hello.ids }
        channel.send(SyncMessage.Events(missing))
        val theirs = channel.receive() as? SyncMessage.Events ?: throw IllegalStateException("expected the other phone's events")
        return SyncReport(sent = missing.size, received = receive(theirs.envelopes))
    }

    /** Stores the events not seen yet and replays the whole log: they may sit anywhere in recorded order. */
    private suspend fun receive(envelopes: List<EventEnvelope>): Int =
        mutex.withLock {
            val known = store.all().mapTo(HashSet()) { it.id }
            val fresh = envelopes.filter { it.id !in known }.distinctBy { it.id }
            if (fresh.isNotEmpty()) {
                store.append(fresh)
                state = replay(store.all(), schema)
                revisionFlow.value += 1
            }
            fresh.size
        }

    private suspend fun <T> read(view: (MealState) -> T): T = mutex.withLock { view(state) }

    private fun decide(command: Command): Outcome =
        when (command) {
            is LogMeal -> logMeal(command)
            is RetractMeal -> Outcome.of(state.journal.retract(command.mealId), CommandResult.Done(mealId = command.mealId))
            is CreateDish -> createDish(command)
            is RenameDish ->
                withName(command.name) { name ->
                    Outcome.of(state.cookbook.rename(command.dishId, name), CommandResult.Done(dishId = command.dishId))
                }
            is EditRecipe -> Outcome.of(state.cookbook.editRecipe(command.dishId, command.recipe), CommandResult.Done(dishId = command.dishId))
            is SetAbsence -> {
                val id = AbsenceId(ids.newId())
                Outcome.of(state.planning.setAbsence(id, DateRange(command.from, command.to)), CommandResult.Done(absenceId = id))
            }
            is RemoveAbsence -> Outcome.of(state.planning.remove(command.absenceId), CommandResult.Done(absenceId = command.absenceId))
        }

    private fun logMeal(command: LogMeal): Outcome {
        val mealId = MealId(ids.newId())
        val created = mutableListOf<DomainEvent>()
        val dishId =
            when (val ref = command.dish) {
                is DishRef.Existing ->
                    if (state.cookbook[ref.id] == null) return Outcome.Refused(DomainError.DISH_UNKNOWN) else ref.id
                is DishRef.Named -> {
                    val (name, error) = DishName.parse(ref.name)
                    if (name == null) return Outcome.Refused(error!!)
                    state.cookbook.findByName(name)?.id ?: DishId(ids.newId()).also { id ->
                        created += (state.cookbook.create(id, name) as Decision.Accepted).events
                    }
                }
            }
        val logged = state.journal.log(mealId, dishId, command.date, command.slot) as Decision.Accepted
        return Outcome.Events(created + logged.events, CommandResult.Done(dishId = dishId, mealId = mealId))
    }

    private fun createDish(command: CreateDish): Outcome =
        withName(command.name) { name ->
            val id = DishId(ids.newId())
            when (val created = state.cookbook.create(id, name)) {
                is Decision.Rejected -> Outcome.Refused(created.error)
                is Decision.Accepted -> {
                    if (command.recipe.isEmpty) {
                        Outcome.Events(created.events, CommandResult.Done(dishId = id))
                    } else {
                        val withDish = created.events.fold(state.cookbook) { book, e -> book.apply(e) }
                        when (val edited = withDish.editRecipe(id, command.recipe)) {
                            is Decision.Rejected -> Outcome.Refused(edited.error)
                            is Decision.Accepted -> Outcome.Events(created.events + edited.events, CommandResult.Done(dishId = id))
                        }
                    }
                }
            }
        }

    private fun withName(
        raw: String,
        then: (DishName) -> Outcome,
    ): Outcome {
        val (name, error) = DishName.parse(raw)
        return if (name == null) Outcome.Refused(error!!) else then(name)
    }

    private suspend fun record(events: List<DomainEvent>) {
        val now = clock.now()
        val zone = clock.timeZone()
        val envelopes =
            events.map { event ->
                val (type, payload) = schema.encode(event)
                EventEnvelope(
                    id = ids.newId(),
                    type = type.name,
                    version = type.version,
                    householdId = device.householdId,
                    deviceId = device.deviceId,
                    occurredAt = occurredAt(event, now, zone),
                    timeZone = zone.id,
                    recordedAt = now,
                    payload = payload,
                )
            }
        store.append(envelopes)
        state = events.fold(state) { s, e -> s.apply(e) }
        revisionFlow.value += 1
    }

    /** A meal happened on its day, at its slot's usual hour; every other fact happens when recorded. */
    private fun occurredAt(
        event: DomainEvent,
        now: Instant,
        zone: TimeZone,
    ): Instant =
        if (event is MealLogged) {
            val time =
                when (event.slot) {
                    MealSlot.LUNCH -> LocalTime(12, 30)
                    MealSlot.DINNER -> LocalTime(20, 0)
                    MealSlot.OTHER -> LocalTime(16, 0)
                }
            LocalDateTime(event.date, time).toInstant(zone)
        } else {
            now
        }

    private sealed interface Outcome {
        data class Events(
            val events: List<DomainEvent>,
            val result: CommandResult,
        ) : Outcome

        data class Refused(
            val error: DomainError,
        ) : Outcome

        companion object {
            fun of(
                decision: Decision<DomainEvent>,
                done: CommandResult.Done,
            ): Outcome =
                when (decision) {
                    is Decision.Accepted -> Events(decision.events, done)
                    is Decision.Rejected -> Refused(decision.error)
                }
        }
    }

    companion object {
        /** Replays the whole log into the read state. Unknown events (from a newer app) are kept but skipped. */
        suspend fun open(
            store: EventStore,
            clock: Clock,
            ids: IdGenerator,
            device: Device,
            schema: EventSchema = EventSchema.CURRENT,
        ): LocalMealServer = LocalMealServer(store, schema, clock, ids, device, replay(store.all(), schema))

        private fun replay(
            envelopes: List<EventEnvelope>,
            schema: EventSchema,
        ): MealState =
            envelopes.fold(MealState.EMPTY) { s, envelope ->
                when (val read = schema.read(envelope)) {
                    is ReadEvent.Known -> s.apply(read.event)
                    is ReadEvent.Unknown -> s
                }
            }
    }
}
