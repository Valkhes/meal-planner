package dev.valkhes.mealplanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.RemoveAbsence
import dev.valkhes.mealplanner.application.server.SetAbsence
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.domain.planning.Absence
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.ui.AppDependencies
import dev.valkhes.mealplanner.ui.ReminderControls
import dev.valkhes.mealplanner.ui.ReminderSetting
import dev.valkhes.mealplanner.ui.common.dateText
import dev.valkhes.mealplanner.ui.common.errorText
import dev.valkhes.mealplanner.ui.common.timeText
import dev.valkhes.mealplanner.ui.generated.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

private const val STEP_MINUTES = 15
private const val DEFAULT_ABSENCE_DAYS = 7

/** The evening reminder of this phone, and the household's absences. */
@Composable
fun SettingsScreen(dependencies: AppDependencies) {
    val server = dependencies.server
    val scope = rememberCoroutineScope()
    val revision by server.revision.collectAsState()
    var absences by remember { mutableStateOf<List<Absence>>(emptyList()) }
    var error by remember { mutableStateOf<DomainError?>(null) }
    LaunchedEffect(revision) { absences = server.absences() }
    val today = dependencies.clock.today()

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(Res.string.tab_settings), style = MaterialTheme.typography.headlineSmall) }
        dependencies.reminder?.let { controls -> item { ReminderSection(controls) } }
        item { Text(stringResource(Res.string.absences_title), style = MaterialTheme.typography.titleMedium) }
        if (absences.isEmpty()) item { Text(stringResource(Res.string.absences_empty)) }
        items(absences, key = { it.id.value }) { absence ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.absence_range, dateText(absence.days.start, today), dateText(absence.days.endInclusive, today)),
                    Modifier.weight(1f),
                )
                TextButton(onClick = { scope.launch { server.send(RemoveAbsence(absence.id)) } }) {
                    Text(stringResource(Res.string.action_remove))
                }
            }
        }
        item {
            AddAbsence(today, error) { from, to ->
                scope.launch { error = (server.send(SetAbsence(from, to)) as? CommandResult.Refused)?.error }
            }
        }
        item { Text(stringResource(Res.string.absences_hint), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ReminderSection(controls: ReminderControls) {
    var setting by remember { mutableStateOf(controls.current()) }

    fun change(next: ReminderSetting) {
        setting = next
        controls.update(next)
    }

    fun shift(minutes: Int) {
        val total = ((setting.hour * 60 + setting.minute + minutes) % (24 * 60) + 24 * 60) % (24 * 60)
        change(setting.copy(hour = total / 60, minute = total % 60))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.settings_reminder_title), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.settings_reminder_enabled), Modifier.weight(1f))
            Switch(checked = setting.enabled, onCheckedChange = { change(setting.copy(enabled = it)) })
        }
        if (setting.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { shift(-STEP_MINUTES) }) { Text(stringResource(Res.string.time_earlier)) }
                Text(stringResource(Res.string.settings_reminder_time, timeText(setting.hour, setting.minute)), Modifier.weight(1f))
                TextButton(onClick = { shift(STEP_MINUTES) }) { Text(stringResource(Res.string.time_later)) }
            }
        }
    }
}

@Composable
private fun AddAbsence(
    today: LocalDate,
    error: DomainError?,
    onAdd: (LocalDate, LocalDate) -> Unit,
) {
    var from by remember { mutableStateOf(today) }
    var to by remember { mutableStateOf(today.plus(DatePeriod(days = DEFAULT_ABSENCE_DAYS))) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.absence_add), style = MaterialTheme.typography.labelLarge)
        DayStepper(stringResource(Res.string.absence_from), from, today) { from = it }
        DayStepper(stringResource(Res.string.absence_to), to, today) { to = it }
        error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onAdd(from, to) }) { Text(stringResource(Res.string.action_save)) }
    }
}

@Composable
private fun DayStepper(
    label: String,
    date: LocalDate,
    today: LocalDate,
    onChange: (LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(date.plus(DatePeriod(days = -1))) }) { Text("‹") }
        Text(dateText(date, today))
        TextButton(onClick = { onChange(date.plus(DatePeriod(days = 1))) }) { Text("›") }
    }
}
