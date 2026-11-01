package de.isbacar.yolcu

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Prefs {
    private lateinit var sp: SharedPreferences

    private val _passengerMode = MutableStateFlow(false)
    private val _speedThreshold = MutableStateFlow(8)
    private val _dpiScale = MutableStateFlow(1f)
    private val _systemDecorations = MutableStateFlow(true)
    private val _allowAllHosts = MutableStateFlow(false)
    private val _carApps = MutableStateFlow<Set<String>>(emptySet())

    /** Bild bleibt während der Fahrt an (nur mit festem Blickschutz für den Fahrer). */
    val passengerMode: StateFlow<Boolean> = _passengerMode

    /** Ab dieser Geschwindigkeit gilt das Auto als fahrend (km/h). */
    val speedThreshold: StateFlow<Int> = _speedThreshold

    /** Skalierung der Inhalte auf dem Auto-Display (1.0 = Dichte des Autos). */
    val dpiScale: StateFlow<Float> = _dpiScale

    /** Systemleiste und Tastatur auf dem Auto-Bildschirm anzeigen. */
    val systemDecorations: StateFlow<Boolean> = _systemDecorations

    /** Jeden Android-Auto-Host zulassen statt nur die bekannten von Google. */
    val allowAllHosts: StateFlow<Boolean> = _allowAllHosts

    /** Paketnamen der Apps, die im Auto erscheinen. */
    val carApps: StateFlow<Set<String>> = _carApps

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("yolcu", Context.MODE_PRIVATE)
        _passengerMode.value = sp.getBoolean("passenger", false)
        _speedThreshold.value = sp.getInt("speed", 8)
        _dpiScale.value = sp.getFloat("dpi_scale", 1f)
        _systemDecorations.value = sp.getBoolean("decorations", true)
        _allowAllHosts.value = sp.getBoolean("all_hosts", false)
        _carApps.value = sp.getStringSet("apps", emptySet())!!.toSet()
    }

    fun setPassengerMode(v: Boolean) {
        _passengerMode.value = v
        sp.edit { putBoolean("passenger", v) }
    }

    fun setSpeedThreshold(v: Int) {
        _speedThreshold.value = v
        sp.edit { putInt("speed", v) }
    }

    fun setDpiScale(v: Float) {
        _dpiScale.value = v
        sp.edit { putFloat("dpi_scale", v) }
    }

    fun setSystemDecorations(v: Boolean) {
        _systemDecorations.value = v
        sp.edit { putBoolean("decorations", v) }
    }

    fun setAllowAllHosts(v: Boolean) {
        _allowAllHosts.value = v
        sp.edit { putBoolean("all_hosts", v) }
    }

    fun setCarApp(pkg: String, enabled: Boolean) {
        val apps = _carApps.value.toMutableSet()
        if (enabled) apps += pkg else apps -= pkg
        _carApps.value = apps
        sp.edit { putStringSet("apps", apps) }
    }
}
