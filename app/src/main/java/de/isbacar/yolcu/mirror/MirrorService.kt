package de.isbacar.yolcu.mirror

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.isbacar.yolcu.R
import de.isbacar.yolcu.YolcuApp

/** Vordergrunddienst, der die Bildschirmaufnahme hält (von Android so vorgeschrieben). */
class MirrorService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, YolcuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mirror)
            .setContentTitle("Yolcu zeigt dein Handy im Auto")
            .setContentText("Zum Beenden im Auto auf ✕ tippen.")
            .setOngoing(true)
            .build()
        startForeground(MirrorController.NOTIFICATION_RUNNING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val req = MirrorController.request
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (req == null || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val mp = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)
                ?: throw IllegalStateException("Bildschirmaufnahme nicht verfügbar")
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    release()
                    req.listener.onMirrorStopped(null)
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))
            display = mp.createVirtualDisplay(
                "Yolcu-Spiegel", req.width, req.height, req.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, req.surface, null, null,
            )
            req.listener.onMirrorStarted()
        } catch (e: Throwable) {
            release()
            req.listener.onMirrorStopped(e.message)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun release() {
        display?.release()
        display = null
        val p = projection
        projection = null
        runCatching { p?.stop() }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }
}
