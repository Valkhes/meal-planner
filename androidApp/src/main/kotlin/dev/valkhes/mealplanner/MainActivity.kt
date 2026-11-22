package dev.valkhes.mealplanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.valkhes.mealplanner.ui.MealPlannerApp
import dev.valkhes.mealplanner.ui.Navigator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { if (!navigator.back()) finish() }
        val container = (application as MealPlannerApplication).container
        lifecycleScope.launch {
            val dependencies = container.dependencies()
            setContent { MealPlannerApp(dependencies, navigator) }
        }
        askForNotificationsOnce()
    }

    /** The evening reminder needs it from Android 13. Asked once; the user can change it in settings. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false)) return
        prefs.edit().putBoolean(ASKED, true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val ASKED = "asked_notifications"
    }
}
