package dev.valkhes.mealplanner.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.valkhes.mealplanner.MainActivity
import dev.valkhes.mealplanner.MealPlannerApplication
import dev.valkhes.mealplanner.R
import dev.valkhes.mealplanner.application.shared.today
import dev.valkhes.mealplanner.domain.journal.MealSlot
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * One notification at 21:00, only when no dinner was logged that evening, answerable without opening
 * the app (README, "the evening reminder"). Scheduled on the phone: no push service.
 */
object EveningReminder {
    private const val WORK = "evening-reminder"
    private const val CHANNEL = "evening"
    const val NOTIFICATION_ID: Int = 2100
    private val TIME: LocalTime = LocalTime.of(21, 0)

    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.with(TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request =
            PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class Worker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val container = (applicationContext as MealPlannerApplication).container
            val server = container.server()
            val today = container.clock.today()
            if (server.mealsOn(today).any { it.meal.slot == MealSlot.DINNER }) return Result.success()
            val dishes = server.suggestions(2).map { it.dish.id.value to it.dish.name.text }
            show(applicationContext, today.toString(), dishes)
            return Result.success()
        }
    }

    fun show(
        context: Context,
        date: String,
        dishes: List<Pair<String, String>>,
    ) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, manager)
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_text))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
        for ((index, dish) in dishes.withIndex()) {
            val intent = ReminderActionReceiver.logDish(context, date, dish.first, dish.second)
            builder.addAction(Notification.Action.Builder(null, dish.second, broadcast(context, index + 1, intent)).build())
        }
        val reply = RemoteInput.Builder(ReminderActionReceiver.KEY_TEXT).setLabel(context.getString(R.string.reminder_reply_hint)).build()
        builder.addAction(
            Notification.Action
                .Builder(null, context.getString(R.string.reminder_other), broadcast(context, 10, ReminderActionReceiver.other(context, date), mutable = true))
                .addRemoteInput(reply)
                .build(),
        )
        builder.addAction(
            Notification.Action
                .Builder(null, context.getString(R.string.reminder_not_home), broadcast(context, 11, ReminderActionReceiver.notHome(context)))
                .build(),
        )
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    /** A short-lived confirmation in place of the question. */
    fun confirm(
        context: Context,
        text: String,
        openOnTap: Boolean,
    ) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentText(text)
                .setAutoCancel(true)
                .setTimeoutAfter(if (openOnTap) 60_000 else 4_000)
        if (openOnTap) builder.setContentIntent(openApp(context))
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.reminder_channel_description)
        manager.createNotificationChannel(channel)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun broadcast(
        context: Context,
        code: Int,
        intent: Intent,
        mutable: Boolean = false,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
}
