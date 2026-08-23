package com.example.officedaystracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.text.SimpleDateFormat
import java.util.*

class OfficeGeofenceReceiver: BroadcastReceiver() {
    companion object {
        private const val MIN_STAY_MS = 2 * 60 * 60 * 1000L
        private const val PREF = "attendance"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                prefs.edit().putLong("entry_$today", System.currentTimeMillis()).apply()
                notify(context, "Office entry detected", "Your 2-hour office timer has started.")
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val entry = prefs.getLong("entry_$today", 0L)
                if (entry > 0L) {
                    val stay = System.currentTimeMillis() - entry
                    if (stay >= MIN_STAY_MS) {
                        prefs.edit().putBoolean(today, true).apply()
                        notify(context, "Office day recorded",
                            "You stayed in the office area for at least 2 hours today.")
                    } else {
                        notify(context, "Office day not counted",
                            "You were inside the office area for less than 2 hours.")
                    }
                    prefs.edit().remove("entry_$today").apply()
                }
            }
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        val channelId = "office"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId, "Office attendance",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n
            )
        }
    }
}
