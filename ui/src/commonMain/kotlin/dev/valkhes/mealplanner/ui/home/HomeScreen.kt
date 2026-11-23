package dev.valkhes.mealplanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valkhes.mealplanner.application.assistant.InterpretContext
import dev.valkhes.mealplanner.application.assistant.Interpretation
import dev.valkhes.mealplanner.application.habits.MealEntry
import dev.valkhes.mealplanner.application.habits.Tonight
import dev.valkhes.mealplanner.application.habits.TonightSuggestion
import dev.valkhes.mealplanner.application.habits.TonightView
import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.CreateDish
import dev.valkhes.mealplanner.application.server.EditRecipe
import dev.valkhes.mealplanner.application.server.RetractMeal
import dev.valkhes.mealplanner.application.shared.hour
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.domain.journal.MealSlot
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.ui.AppDependencies
import dev.valkhes.mealplanner.ui.common.dateText
import dev.valkhes.mealplanner.ui.common.errorText
import dev.valkhes.mealplanner.ui.common.reasonText
import dev.valkhes.mealplanner.ui.common.slotText
import dev.valkhes.mealplanner.ui.generated.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val RECENT_MEALS = 20

/** The input box, what it understood, and the latest meals: the whole daily use in one screen. */
@Composable
fun HomeScreen(
    dependencies: AppDependencies,
    openDish: (DishId) -> Unit,
) {
    val server = dependencies.server
    val scope = rememberCoroutineScope()
    val revision by server.revision.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var proposal by remember { mutableStateOf<Interpretation?>(null) }
    var error by remember { mutableStateOf<DomainError?>(null) }
    var recent by remember { mutableStateOf<List<MealEntry>>(emptyList()) }
    var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var tonight by remember { mutableStateOf<Tonight?>(null) }
    var away by remember { mutableStateOf(false) }
    val today = dependencies.clock.today()

    LaunchedEffect(revision) {
        recent = server.recentMeals(RECENT_MEALS)
        away = server.isAway(today)
    }
    LaunchedEffect(revision, minutes) { tonight = server.tonight(minutes) }

    fun submit() {
        if (text.isBlank()) return
        scope.launch {
            val context = InterpretContext(server.dishes().map { it.dish }, dependencies.clock.today(), dependencies.clock.hour())
            proposal = dependencies.interpreter.interpret(text, context)
            error = null
        }
    }

    fun done(
        result: CommandResult,
        then: (CommandResult.Done) -> Unit = {},
    ) {
        when (result) {
            is CommandResult.Refused -> error = result.error
            is CommandResult.Done -> {
                text = ""
                proposal = null
                error = null
                then(result)
            }
        }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            TonightCard(tonight, minutes, away, onMinutes = { minutes = it }) { suggestion ->
                proposal = Interpretation.Meal(suggestion.dish.name.text, suggestion.dish.id, today, MealSlot.DINNER)
                error = null
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.input_placeholder)) },
                    maxLines = 8,
                )
                Button(onClick = ::submit, enabled = text.isNotBlank()) { Text(stringResource(Res.string.input_go)) }
            }
        }
        when (val current = proposal) {
            is Interpretation.Meal ->
                item {
                    MealProposal(current, today, error, onCancel = { proposal = null }) { edited ->
                        scope.launch { done(server.send(edited.toCommand())) }
                    }
                }
            is Interpretation.NewRecipe ->
                item {
                    RecipeProposal(current, error, onCancel = { proposal = null }) { name ->
                        scope.launch {
                            val command = current.dishId?.let { EditRecipe(it, current.recipe) } ?: CreateDish(name, current.recipe)
                            done(server.send(command)) { it.dishId?.let(openDish) }
                        }
                    }
                }
            is Interpretation.Absence ->
                item {
                    AbsenceProposal(current, today, error, onCancel = { proposal = null }) {
                        scope.launch { done(server.send(current.toCommand())) }
                    }
                }
            Interpretation.NotUnderstood ->
                item { Text(stringResource(Res.string.not_understood), color = MaterialTheme.colorScheme.error) }
            null -> Unit
        }
        item { Text(stringResource(Res.string.recent_title), style = MaterialTheme.typography.titleMedium) }
        if (recent.isEmpty()) item { Text(stringResource(Res.string.recent_empty)) }
        items(recent, key = { it.meal.id.value }) { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { openDish(entry.dish.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.dish.name.text, style = MaterialTheme.typography.bodyLarge)
                    Text("${dateText(entry.meal.date, today)} · ${slotText(entry.meal.slot)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { scope.launch { server.send(RetractMeal(entry.meal.id)) } }) {
                    Text(stringResource(Res.string.action_undo))
                }
            }
        }
    }
}

private val TIME_CHOICES: List<Int> = listOf(15, 30, 45)

/** "What do we eat tonight?": how much time, then three dishes and why. A tap prepares the log. */
@Composable
private fun TonightCard(
    tonight: Tonight?,
    minutes: Int?,
    away: Boolean,
    onMinutes: (Int?) -> Unit,
    onPick: (TonightSuggestion) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.tonight_title), style = MaterialTheme.typography.titleMedium)
            if (away) Text(stringResource(Res.string.tonight_away))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (choice in TIME_CHOICES) {
                    FilterChip(
                        selected = minutes == choice,
                        onClick = { onMinutes(choice) },
                        label = { Text(stringResource(Res.string.minutes_value, choice)) },
                    )
                }
                FilterChip(selected = minutes == null, onClick = { onMinutes(null) }, label = { Text(stringResource(Res.string.tonight_any_time)) })
            }
            val current = tonight ?: return@Column
            when {
                current.mealsLogged == 0 -> Text(stringResource(Res.string.tonight_empty))
                current.suggestions.isEmpty() -> Text(stringResource(Res.string.tonight_none_fit))
                current.learning ->
                    Text(
                        stringResource(Res.string.tonight_learning, current.mealsLogged, TonightView.BREAK_IN_MEALS),
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            for (suggestion in current.suggestions) {
                Column(Modifier.fillMaxWidth().clickable { onPick(suggestion) }.padding(vertical = 4.dp)) {
                    Text(suggestion.dish.name.text, style = MaterialTheme.typography.bodyLarge)
                    val time =
                        suggestion.dish.recipe.minutes
                            ?.let { " · " + stringResource(Res.string.minutes_value, it) }
                            .orEmpty()
                    Text(reasonText(suggestion.reason) + time, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AbsenceProposal(
    proposal: Interpretation.Absence,
    today: LocalDate,
    error: DomainError?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.absence_proposal_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.absence_range, dateText(proposal.from, today), dateText(proposal.to, today)))
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
                Button(onClick = onSave) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }
}

@Composable
private fun MealProposal(
    proposal: Interpretation.Meal,
    today: LocalDate,
    error: DomainError?,
    onCancel: () -> Unit,
    onSave: (Interpretation.Meal) -> Unit,
) {
    var name by remember(proposal) { mutableStateOf(proposal.dishName) }
    var date by remember(proposal) { mutableStateOf(proposal.date) }
    var slot by remember(proposal) { mutableStateOf(proposal.slot) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.proposal_meal_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(Res.string.field_dish)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { date = date.plus(DatePeriod(days = -1)) }) { Text("‹ " + stringResource(Res.string.day_previous)) }
                Text(dateText(date, today), Modifier.weight(1f))
                TextButton(onClick = { date = date.plus(DatePeriod(days = 1)) }, enabled = date < today) {
                    Text(stringResource(Res.string.day_next) + " ›")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in MealSlot.entries) {
                    FilterChip(selected = slot == option, onClick = { slot = option }, label = { Text(slotText(option)) })
                }
            }
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
                Button(onClick = {
                    // A renamed proposal is a different dish: match it by name again.
                    val dishId = proposal.dishId.takeIf { name == proposal.dishName }
                    onSave(proposal.copy(dishName = name, dishId = dishId, date = date, slot = slot))
                }) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }
}

@Composable
private fun RecipeProposal(
    proposal: Interpretation.NewRecipe,
    error: DomainError?,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(proposal) { mutableStateOf(proposal.name.orEmpty()) }
    val recipe = proposal.recipe
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.proposal_recipe_title), style = MaterialTheme.typography.titleMedium)
            if (proposal.dishId != null) {
                Text(stringResource(Res.string.recipe_replaces, name))
            } else {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(Res.string.recipe_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val summary =
                listOfNotNull(
                    pluralStringResource(Res.plurals.ingredients_count, recipe.ingredients.size, recipe.ingredients.size),
                    pluralStringResource(Res.plurals.steps_count, recipe.steps.size, recipe.steps.size),
                    recipe.minutes?.let { stringResource(Res.string.minutes_value, it) },
                )
            Text(summary.joinToString(" · "))
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
                Button(onClick = { onSave(name) }) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }
}
