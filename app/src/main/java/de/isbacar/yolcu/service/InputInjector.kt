package de.isbacar.yolcu.service

import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method

/** Speist Touch- und Tasten-Ereignisse in einen bestimmten Bildschirm ein (nur im Shell-Prozess erlaubt). */
object InputInjector {
    private const val MODE_ASYNC = 0

    private val manager: Any? by lazy {
        runCatching {
            Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)
        }.getOrNull() ?: runCatching {
            InputManager::class.java.getMethod("getInstance").invoke(null)
        }.getOrNull()
    }

    private val inject: Method? by lazy {
        runCatching {
            manager!!.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    private val setDisplayId: Method? by lazy {
        runCatching { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }.getOrNull()
    }

    fun motion(displayId: Int, action: Int, x: Float, y: Float, downTime: Long): Boolean {
        val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        return try {
            send(ev, displayId)
        } finally {
            ev.recycle()
        }
    }

    fun key(displayId: Int, keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
        )
        val up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP)
        if (!send(down, displayId) || !send(up, displayId)) {
            exec("input", "-d", displayId.toString(), "keyevent", keyCode.toString())
        }
    }

    /** Ersatzweg über das input-Kommando, falls die Einspeisung per Reflection scheitert. */
    fun tapFallback(displayId: Int, x: Float, y: Float) {
        exec("input", "-d", displayId.toString(), "tap", x.toInt().toString(), y.toInt().toString())
    }

    private fun send(ev: InputEvent, displayId: Int): Boolean = try {
        setDisplayId!!.invoke(ev, displayId)
        inject!!.invoke(manager, ev, MODE_ASYNC) as Boolean
    } catch (_: Throwable) {
        false
    }

    fun exec(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Throwable) {
        "Error: $e"
    }
}
