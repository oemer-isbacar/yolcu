package de.isbacar.yolcu.car

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.isbacar.yolcu.IDisplayService
import de.isbacar.yolcu.LaunchableApp
import de.isbacar.yolcu.Prefs
import de.isbacar.yolcu.R
import de.isbacar.yolcu.ShizukuManager
import de.isbacar.yolcu.car.SurfaceRenderer.Overlay
import de.isbacar.yolcu.mirror.MirrorController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Vollbild im Auto. Zeigt entweder einen eigenen virtuellen Bildschirm (App läuft unabhängig vom Handy)
 * oder die Spiegelung des Handys. Touch vom Auto-Display wird an die App weitergegeben.
 */
class DisplayScreen(
    ctx: CarContext,
    private val hub: SurfaceHub,
    private var target: Target,
) : Screen(ctx), SurfaceHub.Listener, MirrorController.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var renderer: SurfaceRenderer? = null
    private var service: IDisplayService? = null
    private var displayId = -1
    private var dpi = 160
    private var started = false
    private var blanked = false
    private var mirroring = false
    private var touchHintShown = false

    private var outW = 0
    private var outH = 0

    // Wischgesten kommen vom Auto nur als Abstände – daraus bauen wir einen durchgehenden Fingerzug.
    private var scrolling = false
    private var scrollX = 0f
    private var scrollY = 0f
    private var scrollDown = 0L
    private var lastScroll = 0L
    private val scrollEnd = object : Runnable {
        override fun run() {
            val idle = SystemClock.uptimeMillis() - lastScroll
            if (idle < SCROLL_IDLE_MS) {
                main.postDelayed(this, SCROLL_IDLE_MS - idle)
                return
            }
            finishScroll()
        }
    }

    init {
        hub.listener = this
        hub.container?.let { onSurfaceAvailable(it) }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = cleanup()
        })
        lifecycleScope.launch {
            combine(DriveMonitor.status, Prefs.passengerMode) { s, passenger -> s.moving && !passenger }
                .distinctUntilChanged()
                .collect { blank ->
                    blanked = blank
                    if (blank && scrolling) finishScroll()
                    renderer?.setBlank(if (blank) BLANK else null)
                }
        }
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_back) { sendKey(KeyEvent.KEYCODE_BACK) })
            .addAction(action(R.drawable.ic_apps) { openPicker() })
            .addAction(action(R.drawable.ic_close) { screenManager.popToRoot() })
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .build()
    }

    // ---- Zeichenfläche ----

    override fun onSurfaceAvailable(sc: SurfaceContainer) {
        val surface = sc.surface ?: return
        outW = sc.width
        outH = sc.height
        val r = renderer ?: SurfaceRenderer(sc.width, sc.height).also {
            renderer = it
            it.setBlank(if (blanked) BLANK else null)
        }
        r.setOutput(surface, sc.width, sc.height)
        if (!started) {
            started = true
            dpi = (sc.dpi * Prefs.dpiScale.value).roundToInt().coerceIn(80, 640)
            start(target)
        }
    }

    override fun onSurfaceDestroyed(sc: SurfaceContainer) {
        renderer?.setOutput(null, 0, 0)
    }

    // ---- Inhalte starten ----

    private fun start(t: Target) {
        target = t
        when (t) {
            is Target.App -> startApp(t.app)
            Target.Mirror -> startMirror()
        }
    }

    private fun switchTo(t: Target) {
        if (t is Target.App && target is Target.App && displayId >= 0) {
            target = t
            startApp(t.app)
            return
        }
        stopContent()
        start(t)
    }

    private fun startApp(app: LaunchableApp) {
        val r = renderer ?: return
        r.setStatus(Overlay("${app.label} wird gestartet …", clearOnFrame = true))
        lifecycleScope.launch {
            val svc = ShizukuManager.service()
            if (svc == null) {
                r.setStatus(
                    Overlay(
                        "Shizuku ist nicht aktiv",
                        "Starte Shizuku auf dem Handy – oder tippe oben auf das Raster und wähle „Handy spiegeln“.",
                    )
                )
                return@launch
            }
            service = svc
            val id = if (displayId >= 0) displayId else withContext(Dispatchers.IO) {
                runCatching {
                    svc.createDisplay(r.inputSurface, r.width, r.height, dpi, Prefs.systemDecorations.value)
                }.getOrDefault(-1)
            }
            if (id < 0) {
                r.setStatus(Overlay("Zweiter Bildschirm nicht möglich", lastError(svc)))
                return@launch
            }
            displayId = id
            val ok = withContext(Dispatchers.IO) { runCatching { svc.launch(id, app.component) }.getOrDefault(false) }
            if (!ok) r.setStatus(Overlay("${app.label} ließ sich nicht starten", lastError(svc)))
        }
    }

    private fun startMirror() {
        val r = renderer ?: return
        r.setStatus(Overlay("Bitte am Handy bestätigen", "Erlaube „Bildschirm teilen“ – danach erscheint dein Handy hier."))
        lifecycleScope.launch { service = ShizukuManager.service() }
        MirrorController.start(carContext, MirrorController.Request(r.inputSurface, r.width, r.height, dpi, this))
    }

    private fun stopContent() {
        if (mirroring || target == Target.Mirror) {
            mirroring = false
            MirrorController.stop(carContext)
        }
        val svc = service
        val id = displayId
        displayId = -1
        if (svc != null && id >= 0) io.execute { runCatching { svc.releaseDisplay(id) } }
    }

    override fun onMirrorStarted() {
        main.post {
            mirroring = true
            renderer?.setStatus(Overlay("Spiegelung startet …", clearOnFrame = true))
        }
    }

    override fun onMirrorStopped(reason: String?) {
        main.post {
            if (!mirroring && reason == null) return@post
            mirroring = false
            renderer?.setStatus(Overlay("Spiegelung beendet", reason))
        }
    }

    // ---- Bedienung ----

    override fun onClick(x: Float, y: Float) {
        if (blanked) return
        if (scrolling) finishScroll()
        val p = map(x, y) ?: return
        send { s, id -> s.tap(id, p.x, p.y) }
    }

    override fun onScroll(dx: Float, dy: Float) {
        if (blanked) return
        val now = SystemClock.uptimeMillis()
        lastScroll = now
        val scale = scale() ?: return
        if (!scrolling) {
            val start = map(outW / 2f, outH / 2f) ?: return
            scrolling = true
            scrollX = start.x
            scrollY = start.y
            scrollDown = now
            val (x, y, d) = Triple(scrollX, scrollY, scrollDown)
            send { s, id -> s.touch(id, MotionEvent.ACTION_DOWN, x, y, d) }
            main.postDelayed(scrollEnd, SCROLL_IDLE_MS)
        }
        scrollX -= dx * scale
        scrollY -= dy * scale
        val (x, y, d) = Triple(scrollX, scrollY, scrollDown)
        send { s, id -> s.touch(id, MotionEvent.ACTION_MOVE, x, y, d) }
    }

    private fun finishScroll() {
        main.removeCallbacks(scrollEnd)
        if (!scrolling) return
        scrolling = false
        val (x, y, d) = Triple(scrollX, scrollY, scrollDown)
        send { s, id -> s.touch(id, MotionEvent.ACTION_UP, x, y, d) }
    }

    private fun sendKey(code: Int) {
        if (blanked) return
        send { s, id -> s.key(id, code) }
    }

    private fun openPicker() {
        screenManager.push(AppGridScreen(carContext, hub) { picked ->
            screenManager.pop()
            switchTo(picked)
        })
    }

    /** Ziel-Bildschirm für Eingaben: eigener Bildschirm oder (beim Spiegeln) das Handy-Display 0. */
    private fun inputDisplay(): Int = if (target == Target.Mirror) 0 else displayId

    private fun send(block: (IDisplayService, Int) -> Unit) {
        val svc = service
        val id = inputDisplay()
        if (svc == null || id < 0) {
            if (target == Target.Mirror && !touchHintShown) {
                touchHintShown = true
                CarToast.makeText(carContext, "Bedienen im Auto braucht Shizuku – sonst direkt am Handy.", CarToast.LENGTH_LONG).show()
            }
            return
        }
        io.execute { runCatching { block(svc, id) } }
    }

    /** Auto-Koordinaten → Koordinaten auf dem Ziel-Bildschirm. */
    private fun map(x: Float, y: Float): PointF? {
        val r = renderer ?: return null
        if (outW <= 0 || outH <= 0) return null
        val vx = x * r.width / outW
        val vy = y * r.height / outH
        if (target != Target.Mirror) return PointF(vx, vy)
        // Beim Spiegeln skaliert Android das Handybild mit schwarzen Rändern in unsere Fläche.
        val phone = phoneSize() ?: return null
        val s = min(r.width / phone.x, r.height / phone.y)
        val ox = (r.width - phone.x * s) / 2f
        val oy = (r.height - phone.y * s) / 2f
        val px = (vx - ox) / s
        val py = (vy - oy) / s
        if (px < 0 || py < 0 || px > phone.x || py > phone.y) return null
        return PointF(px, py)
    }

    private fun scale(): Float? {
        val r = renderer ?: return null
        if (outW <= 0) return null
        val base = r.width.toFloat() / outW
        if (target != Target.Mirror) return base
        val phone = phoneSize() ?: return null
        return base / min(r.width / phone.x, r.height / phone.y)
    }

    private fun phoneSize(): PointF? = runCatching {
        val b = carContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        PointF(b.width().toFloat(), b.height().toFloat())
    }.getOrNull()

    private fun lastError(svc: IDisplayService): String? =
        runCatching { svc.lastError() }.getOrNull()?.takeIf { it.isNotBlank() }?.take(300)

    private fun action(icon: Int, onClick: () -> Unit) = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
        .setOnClickListener(onClick)
        .build()

    private fun cleanup() {
        main.removeCallbacksAndMessages(null)
        if (hub.listener === this) hub.listener = null
        stopContent()
        val r = renderer
        renderer = null
        // Erst den Bildschirm freigeben, dann die Fläche, in die er zeichnet.
        io.execute { r?.release() }
        io.shutdown()
    }

    private companion object {
        const val SCROLL_IDLE_MS = 250L
        val BLANK = Overlay(
            "Bild pausiert während der Fahrt",
            "Der Ton läuft weiter. Das Bild kommt zurück, sobald das Auto steht.",
            clock = true,
        )
    }
}
