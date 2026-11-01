package de.isbacar.yolcu

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.isbacar.yolcu.service.DisplayService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Verbindung zu Shizuku und zum DisplayService, der im Shell-Prozess läuft. */
object ShizukuManager {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 42

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state

    @Volatile
    private var service: IDisplayService? = null
    private val waiting = mutableListOf<CancellableContinuation<IDisplayService?>>()

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(app.packageName, DisplayService::class.java.name))
            .daemon(false)
            .processNameSuffix("display")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("display")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = if (binder != null && binder.pingBinder()) IDisplayService.Stub.asInterface(binder) else null
            service = svc
            resumeAll(svc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !Shizuku.pingBinder() -> if (isInstalled()) State.NOT_RUNNING else State.NOT_INSTALLED
            Shizuku.isPreV11() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.NO_PERMISSION
        }
    }

    fun isInstalled(): Boolean = try {
        app.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Fragt die Shizuku-Berechtigung an. false = nicht möglich (z. B. dauerhaft abgelehnt). */
    fun requestPermission(): Boolean {
        if (!Shizuku.pingBinder() || Shizuku.shouldShowRequestPermissionRationale()) return false
        Shizuku.requestPermission(REQUEST_CODE)
        return true
    }

    /** Liefert den DisplayService oder null, wenn Shizuku nicht bereit ist. */
    suspend fun service(): IDisplayService? {
        service?.let { if (it.asBinder().pingBinder()) return it }
        refresh()
        if (_state.value != State.READY) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                synchronized(waiting) { waiting += cont }
                main.post {
                    val current = service
                    if (current != null) {
                        resumeAll(current)
                    } else {
                        try {
                            Shizuku.bindUserService(args, connection)
                        } catch (_: Throwable) {
                            resumeAll(null)
                        }
                    }
                }
            }
        }
    }

    private fun resumeAll(svc: IDisplayService?) {
        val list = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
        list.forEach { if (it.isActive) it.resume(svc) }
    }
}
