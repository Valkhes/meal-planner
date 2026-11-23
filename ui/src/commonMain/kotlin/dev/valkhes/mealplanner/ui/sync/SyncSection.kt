package dev.valkhes.mealplanner.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valkhes.mealplanner.ui.generated.*
import org.jetbrains.compose.resources.stringResource

/** Pair once with a typed code, then one button to sync, both phones on the same Wi-Fi with the app open. */
@Composable
fun SyncSection(controls: SyncControls) {
    val state by controls.state.collectAsState()
    val paired by controls.paired.collectAsState()
    var code by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sync_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.sync_explain), style = MaterialTheme.typography.bodySmall)
        if (paired) {
            Button(onClick = controls::syncNow, enabled = state != SyncState.Syncing) { Text(stringResource(Res.string.sync_now)) }
        }
        when (val current = state) {
            is SyncState.Inviting -> {
                Text(stringResource(Res.string.sync_invite_code))
                Text(current.code, style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = controls::cancel) { Text(stringResource(Res.string.action_cancel)) }
            }
            SyncState.Joining, SyncState.Syncing -> Text(stringResource(Res.string.sync_working))
            SyncState.Paired -> Text(stringResource(Res.string.sync_paired))
            is SyncState.Synced -> Text(stringResource(Res.string.sync_done, current.sent, current.received))
            is SyncState.Failed -> Text(failureText(current.reason), color = MaterialTheme.colorScheme.error)
            SyncState.Idle -> Unit
        }
        if (state !is SyncState.Inviting) {
            Text(stringResource(if (paired) Res.string.sync_pair_again else Res.string.sync_not_paired), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = controls::invite) { Text(stringResource(Res.string.sync_invite)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    code,
                    { code = it.take(CODE_FIELD_LENGTH) },
                    label = { Text(stringResource(Res.string.sync_join_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { controls.join(code) }, enabled = code.count { it.isLetterOrDigit() } >= CODE_LETTERS) {
                    Text(stringResource(Res.string.sync_join))
                }
            }
        }
    }
}

@Composable
private fun failureText(reason: SyncFailure): String =
    stringResource(
        when (reason) {
            SyncFailure.NO_PHONE_FOUND -> Res.string.sync_failed_no_phone
            SyncFailure.NOT_AUTHENTICATED -> Res.string.sync_failed_not_authenticated
            SyncFailure.OTHER_VERSION -> Res.string.sync_failed_other_version
            SyncFailure.CONNECTION_LOST -> Res.string.sync_failed_connection
        },
    )

private const val CODE_LETTERS = 10
private const val CODE_FIELD_LENGTH = 16
