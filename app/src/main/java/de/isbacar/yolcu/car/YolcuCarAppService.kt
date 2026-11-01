package de.isbacar.yolcu.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import de.isbacar.yolcu.BuildConfig
import de.isbacar.yolcu.Prefs

class YolcuCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG || Prefs.allowAllHosts.value) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = YolcuSession()
}

class YolcuSession : Session() {
    val hub = SurfaceHub()

    override fun onCreateScreen(intent: Intent): Screen {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(hub)
        DriveMonitor.attach(carContext, lifecycle)
        return AppGridScreen(carContext, hub, onPick = null)
    }
}

/**
 * Android Auto vergibt die Zeichenfläche pro Sitzung. Der Hub merkt sie sich
 * und reicht sie samt Touch-Ereignissen an den gerade aktiven Bildschirm weiter.
 */
class SurfaceHub : SurfaceCallback {
    interface Listener {
        fun onSurfaceAvailable(sc: SurfaceContainer)
        fun onSurfaceDestroyed(sc: SurfaceContainer)
        fun onClick(x: Float, y: Float)
        fun onScroll(dx: Float, dy: Float)
    }

    var container: SurfaceContainer? = null
        private set
    var listener: Listener? = null

    override fun onSurfaceAvailable(sc: SurfaceContainer) {
        if (sc.surface == null) return
        container = sc
        listener?.onSurfaceAvailable(sc)
    }

    override fun onSurfaceDestroyed(sc: SurfaceContainer) {
        container = null
        listener?.onSurfaceDestroyed(sc)
    }

    override fun onClick(x: Float, y: Float) {
        listener?.onClick(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        listener?.onScroll(distanceX, distanceY)
    }
}
