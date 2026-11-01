package de.isbacar.yolcu.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch

/**
 * Zeichnet den Inhalt eines (virtuellen) Bildschirms auf die Fläche, die Android Auto bereitstellt.
 * Zwischengeschaltet über OpenGL, damit wir jederzeit eine eigene Ansicht (Pause, Hinweis) zeigen können,
 * ohne den Bildschirm dahinter anzuhalten – der Ton läuft so einfach weiter.
 */
class SurfaceRenderer(val width: Int, val height: Int) : SurfaceTexture.OnFrameAvailableListener {

    data class Overlay(
        val title: String,
        val subtitle: String? = null,
        val clock: Boolean = false,
        /** Verschwindet automatisch, sobald das erste Bild vom Bildschirm kommt. */
        val clearOnFrame: Boolean = false,
    )

    private val thread = HandlerThread("yolcu-gl").also { it.start() }
    private val handler = Handler(thread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var outW = width
    private var outH = height

    private var oesTex = 0
    private var bmpTex = 0
    private var oesProgram = 0
    private var bmpProgram = 0
    private lateinit var surfaceTexture: SurfaceTexture

    /** Hierhin rendert der virtuelle Bildschirm bzw. die Spiegelung. */
    lateinit var inputSurface: Surface
        private set

    private val texMatrix = FloatArray(16)
    private var hasFrame = false
    private var status: Overlay? = null
    private var blank: Overlay? = null
    private var bitmapKey: String? = null
    private var released = false

    private val clockTick = Runnable { draw() }

    init {
        val latch = CountDownLatch(1)
        handler.post {
            setup()
            latch.countDown()
        }
        latch.await()
    }

    /** Neue oder keine Ausgabefläche (Android Auto kann sie jederzeit entziehen). */
    fun setOutput(surface: Surface?, w: Int, h: Int) = handler.post {
        if (released) return@post
        destroyWindow()
        if (surface != null && surface.isValid) {
            window = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
            outW = w
            outH = h
            bitmapKey = null
        }
        draw()
    }

    fun setStatus(overlay: Overlay?) = handler.post {
        status = overlay
        draw()
    }

    fun setBlank(overlay: Overlay?) = handler.post {
        blank = overlay
        draw()
    }

    fun release() = handler.post {
        if (released) return@post
        released = true
        handler.removeCallbacks(clockTick)
        destroyWindow()
        runCatching { surfaceTexture.release() }
        runCatching { inputSurface.release() }
        GLES20.glDeleteTextures(2, intArrayOf(oesTex, bmpTex), 0)
        GLES20.glDeleteProgram(oesProgram)
        GLES20.glDeleteProgram(bmpProgram)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, pbuffer)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglReleaseThread()
        thread.quitSafely()
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        if (released) return
        hasFrame = true
        if (status?.clearOnFrame == true) status = null
        draw()
    }

    private fun setup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0)
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            eglDisplay, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)

        oesProgram = program(VERTEX_OES, FRAGMENT_OES)
        bmpProgram = program(VERTEX_BMP, FRAGMENT_BMP)

        val tex = IntArray(2)
        GLES20.glGenTextures(2, tex, 0)
        oesTex = tex[0]
        bmpTex = tex[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        texParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bmpTex)
        texParams(GLES20.GL_TEXTURE_2D)

        surfaceTexture = SurfaceTexture(oesTex)
        surfaceTexture.setDefaultBufferSize(width, height)
        surfaceTexture.setOnFrameAvailableListener(this, handler)
        inputSurface = Surface(surfaceTexture)
    }

    private fun draw() {
        if (released) return
        handler.removeCallbacks(clockTick)
        if (window == EGL14.EGL_NO_SURFACE) {
            // Ohne Ausgabe trotzdem Bilder abholen, damit der Bildschirm nicht hängen bleibt.
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
            runCatching { surfaceTexture.updateTexImage() }
            return
        }
        EGL14.eglMakeCurrent(eglDisplay, window, window, eglContext)
        runCatching { surfaceTexture.updateTexImage() }
        GLES20.glViewport(0, 0, outW, outH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val overlay = blank ?: status
        when {
            overlay != null -> {
                drawOverlay(overlay)
                if (overlay.clock) handler.postDelayed(clockTick, 15_000)
            }
            hasFrame -> drawContent()
        }
        EGL14.eglSwapBuffers(eglDisplay, window)
    }

    private fun drawContent() {
        surfaceTexture.getTransformMatrix(texMatrix)
        GLES20.glUseProgram(oesProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(oesProgram, "uTexMatrix"), 1, false, texMatrix, 0)
        drawQuad(oesProgram, TEX_COORDS)
    }

    private fun drawOverlay(overlay: Overlay) {
        val time = if (overlay.clock) clockFormat.format(Date()) else ""
        val key = "${overlay.title}|${overlay.subtitle}|$time|$outW|$outH"
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bmpTex)
        if (key != bitmapKey) {
            val bmp = overlayBitmap(overlay, time)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            bitmapKey = key
        }
        GLES20.glUseProgram(bmpProgram)
        drawQuad(bmpProgram, TEX_COORDS_FLIPPED)
    }

    private fun overlayBitmap(overlay: Overlay, time: String): Bitmap {
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(13, 17, 23))
        val cx = outW / 2f
        val textWidth = (outW * 0.8f).toInt()

        var y = outH * if (overlay.clock) 0.30f else 0.40f
        if (overlay.clock) {
            val clock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = outH * 0.18f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(time, cx, y, clock)
            y += outH * 0.14f
        }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = outH * 0.065f
            typeface = Typeface.DEFAULT_BOLD
        }
        y = drawCentered(c, overlay.title, title, textWidth, y)
        overlay.subtitle?.let {
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(160, 170, 184)
                textSize = outH * 0.042f
            }
            drawCentered(c, it, sub, textWidth, y + outH * 0.03f)
        }
        return bmp
    }

    private fun drawCentered(c: Canvas, text: String, paint: TextPaint, width: Int, top: Float): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save()
        c.translate((outW - width) / 2f, top)
        layout.draw(c)
        c.restore()
        return top + layout.height
    }

    private fun drawQuad(program: Int, tex: FloatBuffer) {
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val coord = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, VERTICES)
        GLES20.glEnableVertexAttribArray(coord)
        GLES20.glVertexAttribPointer(coord, 2, GLES20.GL_FLOAT, false, 0, tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(coord)
    }

    private fun destroyWindow() {
        if (window != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
            EGL14.eglDestroySurface(eglDisplay, window)
            window = EGL14.EGL_NO_SURFACE
        }
    }

    private fun texParams(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
        }
    }

    private companion object {
        val clockFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)

        fun buffer(vararg v: Float): FloatBuffer = ByteBuffer.allocateDirect(v.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v)
                position(0)
            }

        val VERTICES = buffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val TEX_COORDS = buffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        val TEX_COORDS_FLIPPED = buffer(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

        const val VERTEX_OES = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """
        const val FRAGMENT_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
        const val VERTEX_BMP = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord.xy;
            }
        """
        const val FRAGMENT_BMP = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
