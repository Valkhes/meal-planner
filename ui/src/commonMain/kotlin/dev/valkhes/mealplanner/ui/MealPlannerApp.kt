package dev.valkhes.mealplanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.valkhes.mealplanner.application.assistant.Interpreter
import dev.valkhes.mealplanner.application.server.MealServer
import dev.valkhes.mealplanner.application.shared.Clock
import dev.valkhes.mealplanner.domain.shared.DishId
import dev.valkhes.mealplanner.ui.dish.DishScreen
import dev.valkhes.mealplanner.ui.dishes.DishesScreen
import dev.valkhes.mealplanner.ui.generated.*
import dev.valkhes.mealplanner.ui.home.HomeScreen
import org.jetbrains.compose.resources.stringResource

/** What the screens need. The UI reads and sends through [server] only (README, "Architecture"). */
class AppDependencies(
    val server: MealServer,
    val interpreter: Interpreter,
    val clock: Clock,
)

sealed interface Screen {
    data object Home : Screen

    data object Dishes : Screen

    data class Dish(
        val id: DishId,
        val from: Screen,
    ) : Screen
}

/** Where the user is. Kept outside composition so the platform's back button can drive it. */
class Navigator {
    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    fun open(next: Screen) {
        screen = next
    }

    /** Goes one screen back; false when there is nowhere to go, so the platform closes the app. */
    fun back(): Boolean {
        val current = screen
        screen =
            when (current) {
                is Screen.Dish -> current.from
                Screen.Dishes -> Screen.Home
                Screen.Home -> return false
            }
        return true
    }
}

@Composable
fun MealPlannerApp(
    dependencies: AppDependencies,
    navigator: Navigator,
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = navigator.screen == Screen.Home,
                        onClick = { navigator.open(Screen.Home) },
                        icon = { Text("⌂") },
                        label = { Text(stringResource(Res.string.tab_home)) },
                    )
                    NavigationBarItem(
                        selected = navigator.screen != Screen.Home,
                        onClick = { navigator.open(Screen.Dishes) },
                        icon = { Text("☰") },
                        label = { Text(stringResource(Res.string.tab_dishes)) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val screen = navigator.screen) {
                    Screen.Home -> HomeScreen(dependencies) { navigator.open(Screen.Dish(it, Screen.Home)) }
                    Screen.Dishes -> DishesScreen(dependencies) { navigator.open(Screen.Dish(it, Screen.Dishes)) }
                    is Screen.Dish -> DishScreen(dependencies, screen.id, onBack = { navigator.back() })
                }
            }
        }
    }
}
