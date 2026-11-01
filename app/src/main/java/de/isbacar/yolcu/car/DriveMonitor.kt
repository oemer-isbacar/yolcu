package de.isbacar.yolcu.car

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import de.isbacar.yolcu.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Erkennt, ob das Auto fährt. Bevorzugt die Geschwindigkeit vom Auto selbst,
 * sonst GPS vom Handy. Mit Hysterese, damit das Bild an der Ampel nicht flackert.
 */
object DriveMonitor {
    enum class Source { NONE, CAR, GPS }

    data class Status(val moving: Boolean = false, val speedKmh: Float? = null, val source: Source = Source.NONE)

    private const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    private const val STOP_KMH = 3f
    private const val STOP_DELAY_MS = 5_000L

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var lastFast = 0L
    private var lastCar = 0L

    fun attach(ctx: CarContext, lifecycle: Lifecycle) {
        val carListener = OnCarDataAvailableListener<Speed> { speed ->
            val v = speed.rawSpeedMetersPerSecond.successValue() ?: speed.displaySpeedMetersPerSecond.successValue()
            if (v != null) {
                lastCar = SystemClock.elapsedRealtime()
                update(abs(v) * 3.6f, Source.CAR)
            }
        }
        val gpsListener = LocationListener { loc ->
            val carRecent = SystemClock.elapsedRealtime() - lastCar < 5_000
            if (loc.hasSpeed() && !carRecent) update(loc.speed * 3.6f, Source.GPS)
        }
        var carInfo: CarInfo? = null

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carInfo = startCar(ctx, carListener)
                startGps(ctx, gpsListener)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                runCatching { carInfo?.removeSpeedListener(carListener) }
                runCatching { ctx.getSystemService(LocationManager::class.java).removeUpdates(gpsListener) }
                _status.value = Status()
            }
        })
    }

    private fun startCar(ctx: CarContext, listener: OnCarDataAvailableListener<Speed>): CarInfo? = try {
        val info = ctx.getCarService(CarHardwareManager::class.java).carInfo
        if (ctx.checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) {
            info.addSpeedListener(ctx.mainExecutor, listener)
        } else {
            ctx.requestPermissions(listOf(CAR_SPEED)) { granted, _ ->
                if (CAR_SPEED in granted) runCatching { info.addSpeedListener(ctx.mainExecutor, listener) }
            }
        }
        info
    } catch (_: Throwable) {
        null
    }

    private fun startGps(ctx: CarContext, listener: LocationListener) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            ctx.getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        }
    }

    private fun update(kmh: Float, source: Source) {
        val now = SystemClock.elapsedRealtime()
        val prev = _status.value
        val moving = when {
            kmh >= Prefs.speedThreshold.value -> {
                lastFast = now
                true
            }
            prev.moving && (kmh > STOP_KMH || now - lastFast < STOP_DELAY_MS) -> true
            else -> false
        }
        _status.value = Status(moving, kmh, source)
    }

    private fun CarValue<Float>.successValue(): Float? = if (status == CarValue.STATUS_SUCCESS) value else null
}
