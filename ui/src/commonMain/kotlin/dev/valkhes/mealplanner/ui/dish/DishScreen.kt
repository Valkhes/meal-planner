package dev.valkhes.mealplanner.ui.dish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.valkhes.mealplanner.application.habits.DishDetail
import dev.valkhes.mealplanner.application.server.CommandResult
import dev.valkhes.mealplanner.application.server.EditRecipe
import dev.valkhes.mealplanner.application.server.RenameDish
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.domain.cookbook.IngredientLine
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.domain.shared.DomainError
import dev.valkhes.mealplanner.ui.AppDependencies
import dev.valkhes.mealplanner.ui.common.dateText
import dev.valkhes.mealplanner.ui.common.errorText
import dev.valkhes.mealplanner.ui.common.habitText
import dev.valkhes.mealplanner.ui.common.slotText
import dev.valkhes.mealplanner.ui.generated.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One dish: its name, its rhythm, the household's recipe and when it was eaten. */
@Composable
fun DishScreen(
    dependencies: AppDependencies,
    id: DishId,
    onBack: () -> Unit,
) {
    val server = dependencies.server
    val scope = rememberCoroutineScope()
    val revision by server.revision.collectAsState()
    var detail by remember { mutableStateOf<DishDetail?>(null) }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<DomainError?>(null) }
    LaunchedEffect(revision, id) { detail = server.dish(id) }
    val today = dependencies.clock.today()

    fun send(result: CommandResult) {
        error = (result as? CommandResult.Refused)?.error
        if (error == null) editing = false
    }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TextButton(onClick = onBack) { Text("‹ " + stringResource(Res.string.action_back)) } }
        val current = detail ?: return@LazyColumn
        item {
            var name by remember(current.dish.name) { mutableStateOf(current.dish.name.text) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(Res.string.rename_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (name != current.dish.name.text) {
                    Button(onClick = { scope.launch { send(server.send(RenameDish(id, name))) } }) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
        item { Text(habitText(current.habit, today), style = MaterialTheme.typography.bodyMedium) }
        error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
        item { Text(stringResource(Res.string.recipe_title), style = MaterialTheme.typography.titleMedium) }
        if (editing) {
            item {
                RecipeEditor(current.dish.recipe, onCancel = { editing = false }) { recipe ->
                    scope.launch { send(server.send(EditRecipe(id, recipe))) }
                }
            }
        } else {
            item { RecipeView(current.dish.recipe) }
            item { TextButton(onClick = { editing = true }) { Text(stringResource(Res.string.action_edit)) } }
        }
        item { Text(stringResource(Res.string.history_title), style = MaterialTheme.typography.titleMedium) }
        items(current.meals, key = { it.id.value }) { meal ->
            Text("${dateText(meal.date, today)} · ${slotText(meal.slot)}")
        }
    }
}

@Composable
private fun RecipeView(recipe: Recipe) {
    if (recipe.isEmpty) {
        Text(stringResource(Res.string.recipe_none))
        return
    }
    recipe.minutes?.let { Text(stringResource(Res.string.minutes_value, it)) }
    if (recipe.ingredients.isNotEmpty()) {
        Text(stringResource(Res.string.ingredients_title), style = MaterialTheme.typography.labelLarge)
        recipe.ingredients.forEach { Text("• " + IngredientLine.format(it)) }
    }
    if (recipe.steps.isNotEmpty()) {
        Text(stringResource(Res.string.steps_title), style = MaterialTheme.typography.labelLarge)
        recipe.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
    }
    recipe.notes?.takeIf { it.isNotBlank() }?.let {
        Text(stringResource(Res.string.notes_title), style = MaterialTheme.typography.labelLarge)
        Text(it)
    }
}

@Composable
private fun RecipeEditor(
    recipe: Recipe,
    onCancel: () -> Unit,
    onSave: (Recipe) -> Unit,
) {
    var ingredients by remember { mutableStateOf(recipe.ingredients.joinToString("\n") { IngredientLine.format(it) }) }
    var steps by remember { mutableStateOf(recipe.steps.joinToString("\n")) }
    var minutes by remember { mutableStateOf(recipe.minutes?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(recipe.notes.orEmpty()) }
    OutlinedTextField(
        ingredients,
        { ingredients = it },
        label = { Text(stringResource(Res.string.ingredients_title)) },
        placeholder = { Text(stringResource(Res.string.ingredients_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        steps,
        { steps = it },
        label = { Text(stringResource(Res.string.steps_title)) },
        placeholder = { Text(stringResource(Res.string.steps_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        minutes,
        { value -> minutes = value.filter { it.isDigit() }.take(4) },
        label = { Text(stringResource(Res.string.minutes_label)) },
        singleLine = true,
    )
    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(Res.string.notes_title)) }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
        Button(onClick = {
            onSave(
                Recipe(
                    ingredients = ingredients.lines().mapNotNull { IngredientLine.parse(it) },
                    steps = steps.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    minutes = minutes.toIntOrNull(),
                    notes = notes.trim().ifEmpty { null },
                ),
            )
        }) { Text(stringResource(Res.string.action_save)) }
    }
}
