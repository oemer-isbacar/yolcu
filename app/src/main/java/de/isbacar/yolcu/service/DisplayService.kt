package de.isbacar.yolcu.service

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import de.isbacar.yolcu.IDisplayService
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * Shizuku-UserService. Läuft NICHT im App-Prozess, sondern als Shell (uid 2000).
 * Legt den unsichtbaren zweiten Bildschirm an, dessen Bild direkt ins Auto geht.
 */
class DisplayService() : IDisplayService.Stub() {
    private var base: Context? = null

    // Shizuku ruft ab Version 13 diesen Konstruktor mit Context auf.
    constructor(context: Context) : this() {
        base = context
    }

    private val displays = ConcurrentHashMap<Int, VirtualDisplay>()

    @Volatile
    private var lastError = ""

    override fun destroy() {
        displays.values.forEach { runCatching { it.release() } }
        exitProcess(0)
    }

    override fun myUid(): Int = Process.myUid()

    override fun lastError(): String = lastError

    override fun createDisplay(surface: Surface, width: Int, height: Int, dpi: Int, decorations: Boolean): Int {
        val dm = try {
            displayManager()
        } catch (e: Throwable) {
            lastError = "DisplayManager nicht verfügbar: $e"
            return -1
        }
        for (flags in flagVariants(decorations)) {
            try {
                val vd = dm.createVirtualDisplay("Yolcu", width, height, dpi, surface, flags) ?: continue
                val id = vd.display.displayId
                displays[id] = vd
                return id
            } catch (e: Throwable) {
                lastError = e.toString()
            }
        }
        return -1
    }

    override fun releaseDisplay(displayId: Int) {
        displays.remove(displayId)?.let { runCatching { it.release() } }
    }

    override fun launch(displayId: Int, component: String): Boolean {
        // 0x10000000 = FLAG_ACTIVITY_NEW_TASK; ein bestehender Task wird auf den Auto-Bildschirm verschoben.
        val out = InputInjector.exec(
            "am", "start", "--display", displayId.toString(), "-f", "0x10000000", "-n", component,
        )
        if (out.contains("Error")) {
            lastError = out.trim()
            return false
        }
        return true
    }

    override fun tap(displayId: Int, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        if (!InputInjector.motion(displayId, MotionEvent.ACTION_DOWN, x, y, down)) {
            InputInjector.tapFallback(displayId, x, y)
            return
        }
        SystemClock.sleep(40)
        InputInjector.motion(displayId, MotionEvent.ACTION_UP, x, y, down)
    }

    override fun touch(displayId: Int, action: Int, x: Float, y: Float, downTime: Long) {
        InputInjector.motion(displayId, action, x, y, downTime)
    }

    override fun key(displayId: Int, keyCode: Int) {
        InputInjector.key(displayId, keyCode)
    }

    override fun setInstaller(packageName: String, apkPath: String): String {
        fun installer() = InputInjector.exec("pm", "list", "packages", "-i", packageName)
            .lineSequence().firstOrNull { it.startsWith("package:$packageName ") || it.trim() == "package:$packageName" }
            ?.substringAfter("installer=", "")?.trim().orEmpty()

        if (installer() == PLAY_STORE) return "OK"
        val out = InputInjector.exec("pm", "set-installer", packageName, PLAY_STORE)
        if (installer() == PLAY_STORE) return "OK"

        // Ältere Systeme kennen set-installer nicht: dann über sich selbst neu installieren.
        // Die App wird dabei kurz beendet, Einstellungen bleiben erhalten.
        val tmp = "/data/local/tmp/yolcu-reinstall.apk"
        val copy = InputInjector.exec("cp", apkPath, tmp)
        if (copy.isNotBlank()) return "Kopieren fehlgeschlagen: ${copy.trim()} / ${out.trim()}"
        InputInjector.exec(
            "sh", "-c",
            "nohup sh -c 'sleep 1; pm install -r -i $PLAY_STORE $tmp; rm -f $tmp' >/dev/null 2>&1 &",
        )
        return "REINSTALL"
    }

    private fun displayManager(): DisplayManager {
        val ctx = FakeContext(base ?: systemContext())
        val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(ctx)
    }

    @SuppressLint("PrivateApi")
    private fun systemContext(): Context {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("currentActivityThread").invoke(null)
            ?: at.getMethod("systemMain").invoke(null)
        return at.getMethod("getSystemContext").invoke(thread) as Context
    }

    /** Von „alles“ bis „Minimum“ – manche Hersteller lassen nicht jedes Flag zu. */
    private fun flagVariants(decorations: Boolean): List<Int> {
        val base = PUBLIC or PRESENTATION or OWN_CONTENT_ONLY or SUPPORTS_TOUCH or DESTROY_CONTENT_ON_REMOVAL
        var full = base or TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED or TOUCH_FEEDBACK_DISABLED
        if (decorations) full = full or SHOULD_SHOW_SYSTEM_DECORATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) full = full or OWN_FOCUS
        val reduced = full and (OWN_FOCUS or ALWAYS_UNLOCKED).inv()
        return listOf(full, reduced, base).distinct()
    }

    private companion object {
        const val PLAY_STORE = "com.android.vending"

        // Werte aus android.hardware.display.DisplayManager (teils versteckt)
        const val PUBLIC = 1
        const val PRESENTATION = 1 shl 1
        const val OWN_CONTENT_ONLY = 1 shl 3
        const val SUPPORTS_TOUCH = 1 shl 6
        const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        const val SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
        const val TRUSTED = 1 shl 10
        const val OWN_DISPLAY_GROUP = 1 shl 11
        const val ALWAYS_UNLOCKED = 1 shl 12
        const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
        const val OWN_FOCUS = 1 shl 14
    }
}
