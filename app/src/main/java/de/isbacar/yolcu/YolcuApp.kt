package de.isbacar.yolcu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class YolcuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ShizukuManager.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Yolcu", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        const val CHANNEL_ID = "yolcu"
    }
}
