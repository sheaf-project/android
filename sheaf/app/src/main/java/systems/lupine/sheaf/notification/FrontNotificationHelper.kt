package systems.lupine.sheaf.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import systems.lupine.sheaf.MainActivity
import systems.lupine.sheaf.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID  = "sheaf_fronting"
        const val NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Currently fronting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification showing who is currently fronting"
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun post(memberNames: List<String>) {
        val text = when {
            memberNames.isEmpty()  -> "No one is fronting"
            memberNames.size == 1  -> "${memberNames[0]} is fronting"
            else                   -> "${memberNames.dropLast(1).joinToString(", ")} and ${memberNames.last()} are fronting"
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP + CLEAR_TOP brings the existing task to the front
            // instead of pushing a duplicate MainActivity onto the back stack.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Currently Fronting")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
