package dev.valkhes.mealplanner.ui.dishes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valkhes.mealplanner.application.habits.DishSummary
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.ui.AppDependencies
import dev.valkhes.mealplanner.ui.common.habitText
import dev.valkhes.mealplanner.ui.generated.*
import org.jetbrains.compose.resources.stringResource

/** Every dish, the most eaten first, with its rhythm. */
@Composable
fun DishesScreen(
    dependencies: AppDependencies,
    openDish: (DishId) -> Unit,
) {
    val revision by dependencies.server.revision.collectAsState()
    var dishes by remember { mutableStateOf<List<DishSummary>?>(null) }
    LaunchedEffect(revision) { dishes = dependencies.server.dishes() }
    val today = dependencies.clock.today()

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(stringResource(Res.string.tab_dishes), style = MaterialTheme.typography.headlineSmall) }
        val list = dishes ?: return@LazyColumn
        if (list.isEmpty()) item { Text(stringResource(Res.string.dishes_empty)) }
        items(list, key = { it.dish.id.value }) { summary ->
            Column(Modifier.fillMaxWidth().clickable { openDish(summary.dish.id) }.padding(vertical = 8.dp)) {
                Text(summary.dish.name.text, style = MaterialTheme.typography.bodyLarge)
                Text(habitText(summary.habit, today), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}
