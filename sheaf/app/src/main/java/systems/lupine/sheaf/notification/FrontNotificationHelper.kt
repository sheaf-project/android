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

/**
 * How the fronting notification should look, from the user's settings.
 *
 * Every default here is what the notification did before any of this was
 * configurable, so an upgrade changes nobody's shade.
 */
data class FrontNotificationStyle(
    /** The Sheaf mark rather than the generic people glyph. */
    val useLogo: Boolean = false,
    /** Names on the collapsed notification, rather than behind an expand. */
    val showNames: Boolean = true,
    /** Re-post a few minutes after the user swipes it away. */
    val respawn: Boolean = false,
)

@Singleton
class FrontNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID  = "sheaf_fronting"
        const val NOTIFICATION_ID = 1001

        /**
         * What the collapsed notification says when names are hidden.
         *
         * Deliberately says nothing about fronting: the point of the setting is
         * that somebody reading the shade over your shoulder learns nothing,
         * and "someone is fronting" is already more than nothing.
         */
        const val DISCREET_TEXT = "Tap to expand"

        /** Who is fronting, as a sentence. */
        fun frontingText(memberNames: List<String>): String = when {
            memberNames.isEmpty() -> "No one is fronting"
            memberNames.size == 1 -> "${memberNames[0]} is fronting"
            else -> "${memberNames.dropLast(1).joinToString(", ")} and ${memberNames.last()} are fronting"
        }
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Currently fronting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing silent notification showing who is currently fronting"
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun post(memberNames: List<String>, style: FrontNotificationStyle = FrontNotificationStyle()) {
        val names = frontingText(memberNames)
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (style.useLogo) R.drawable.ic_notification_sheaf else R.drawable.ic_notification
            )
            .setContentTitle("Currently Fronting")
            // Collapsed line. With names hidden this is the whole of what the
            // shade shows; the names live in the expanded style below, which
            // the user opens deliberately.
            .setContentText(if (style.showNames) names else DISCREET_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(contentIntent)
            // Still set, and still worth setting: pre-14 it resists a swipe,
            // and it keeps the notification out of "clear all". Android 14
            // onwards lets the user dismiss it regardless, which is why the
            // respawn option below exists rather than a promise we cannot keep.
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        // What a locked screen shows. Without this the system substitutes its
        // own "contents hidden" line for a VISIBILITY_PRIVATE notification;
        // with it we choose the wording, and with names hidden it matches what
        // the unlocked shade says instead of being a louder placeholder.
        if (!style.showNames) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(
                        if (style.useLogo) R.drawable.ic_notification_sheaf else R.drawable.ic_notification
                    )
                    .setContentTitle("Currently Fronting")
                    .setContentText(DISCREET_TEXT)
                    .setSilent(true)
                    .build()
            )
        }

        if (style.respawn) {
            builder.setDeleteIntent(FrontNotificationRespawn.deleteIntent(context))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() {
        FrontNotificationRespawn.cancel(context)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
