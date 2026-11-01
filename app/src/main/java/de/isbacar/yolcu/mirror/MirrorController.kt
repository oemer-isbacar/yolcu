package de.isbacar.yolcu.mirror

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.Surface
import de.isbacar.yolcu.R
import de.isbacar.yolcu.YolcuApp

/** Spiegelt das Handy-Display (MediaProjection) in die Fläche des Auto-Bildschirms. */
object MirrorController {
    const val NOTIFICATION_REQUEST = 10
    const val NOTIFICATION_RUNNING = 11

    interface Listener {
        fun onMirrorStarted()
        fun onMirrorStopped(reason: String?)
    }

    class Request(val surface: Surface, val width: Int, val height: Int, val dpi: Int, val listener: Listener)

    @Volatile
    var request: Request? = null
        private set

    fun start(ctx: Context, req: Request) {
        request = req
        val app = ctx.applicationContext
        val intent = Intent(app, MirrorPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Direkt öffnen klappt nicht immer aus dem Hintergrund – die Benachrichtigung ist der sichere Weg.
        runCatching { app.startActivity(intent) }
        val pi = PendingIntent.getActivity(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(app, YolcuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mirror)
            .setContentTitle("Handy im Auto anzeigen?")
            .setContentText("Tippe hier und erlaube „Bildschirm teilen“.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        app.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_REQUEST, n)
    }

    fun stop(ctx: Context) {
        request = null
        val app = ctx.applicationContext
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_REQUEST)
        app.stopService(Intent(app, MirrorService::class.java))
    }
}
