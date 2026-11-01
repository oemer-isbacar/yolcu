package de.isbacar.yolcu.service

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Gibt sich als com.android.shell aus. Das System prüft beim Anlegen von Bildschirmen,
 * ob Paketname und uid zusammenpassen – im Shell-Prozess muss es daher "com.android.shell" sein.
 * (Gleiches Vorgehen wie scrcpy.)
 */
class FakeContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = SHELL_PACKAGE

    override fun getOpPackageName(): String = SHELL_PACKAGE

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun getDeviceId(): Int = 0

    override fun getApplicationContext(): Context = this

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val SHELL_UID = 2000
    }
}
