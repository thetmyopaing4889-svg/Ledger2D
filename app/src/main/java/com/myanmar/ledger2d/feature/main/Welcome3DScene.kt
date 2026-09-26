package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun Welcome3DScene(modifier: Modifier = Modifier, transitioningOut: Boolean) {
    AndroidView(
        modifier = modifier,
        factory = { context -> WelcomeGlSurface(context).also { it.setTransitioningOut(transitioningOut) } },
        update = { it.setTransitioningOut(transitioningOut) }
    )
}

private class WelcomeGlSurface(context: Context) : GLSurfaceView(context) {
    private val renderer = WelcomeGlRenderer()
    init {
        setEGLContextClientVersion(2)
        setZOrderOnTop(false)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    fun setTransitioningOut(value: Boolean) { renderer.transitioningOut = value }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { renderer.lastX = event.x; renderer.lastY = event.y; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - renderer.lastX
                val dy = event.y - renderer.lastY
                renderer.cameraYaw = (renderer.cameraYaw + dx * 0.18f).coerceIn(-18f, 18f)
                renderer.cameraPitch = (renderer.cameraPitch + dy * 0.12f).coerceIn(-12f, 12f)
                renderer.lastX = event.x; renderer.lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                renderer.cameraYaw *= 0.35f
                renderer.cameraPitch *= 0.35f
                return true
            }
        }
        return true
    }
}

private class WelcomeGlRenderer : GLSurfaceView.Renderer {
    var transitioningOut = false
    var lastX = 0f
    var lastY = 0f
    var cameraYaw = 0f
    var cameraPitch = 0f
    private var startNanos = 0L
    private var program = 0
    private var digitProgram = 0
    private lateinit var sphere: Mesh
    private lateinit var quad: Mesh
    private var surfaceAspect = 1f
    private val digitValues = arrayOf("27", "12", "53", "84", "42", "19", "61", "35", "96", "70", "00", "07")
    private val digitTextures = IntArray(12)
    private val digitBase = arrayOf(
        floatArrayOf(-1.55f, 1.35f, -0.35f), floatArrayOf(-0.65f, 1.60f, -0.15f), floatArrayOf(0.60f, 1.45f, -0.20f), floatArrayOf(1.45f, 1.15f, -0.40f),
        floatArrayOf(-1.70f, 0.35f, -0.10f), floatArrayOf(1.65f, 0.25f, -0.15f), floatArrayOf(-1.20f, -0.55f, -0.25f), floatArrayOf(1.12f, -0.62f, -0.20f),
        floatArrayOf(-0.60f, -1.10f, -0.30f), floatArrayOf(0.50f, -1.22f, -0.20f), floatArrayOf(-1.52f, -1.48f, -0.38f), floatArrayOf(1.48f, -1.38f, -0.32f)
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0.055f, 0.008f, 0.025f, 1f)
        program = linkProgram(VERTEX, FRAGMENT)
        digitProgram = linkProgram(DIGIT_VERTEX, DIGIT_FRAGMENT)
        sphere = Mesh.sphere(0.58f, 28, 20)
        quad = Mesh.quad()
        startNanos = System.nanoTime()
        for (i in digitValues.indices) digitTextures[i] = makeDigitTexture(digitValues[i])
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, max(1, height))
        surfaceAspect = width.toFloat() / max(1, height).toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val t = (System.nanoTime() - startNanos) / 1_000_000_000f
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val projection = perspective(48f, surfaceAspect, 0.1f, 20f)
        val view = lookAt(0f, 0f, 6.4f, 0f, 0f, 0f, 0f, 1f, 0f)
        val camera = multiply(rotateX(cameraPitch), rotateY(cameraYaw))
        val vp = multiply(projection, multiply(view, camera))
        drawDigits(vp, t)
        drawCherry(vp, t, -0.30f, 0.02f, 0.02f)
        drawCherry(vp, t, 0.30f, 0.06f, -0.02f)
    }

    private fun drawCherry(vp: FloatArray, time: Float, x: Float, y: Float, z: Float) {
        val breathing = 1f + sin(time * 1.35f) * 0.018f
        val model = multiply(translate(x, y + sin(time * 1.1f) * 0.025f, z), scale(breathing, breathing, breathing))
        val mvp = multiply(vp, model)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uColor"), 0.82f, 0.025f, 0.16f, 1f)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uLight"), -2.4f, 3.3f, 4.6f)
        sphere.draw(program)
    }

    private fun drawDigits(vp: FloatArray, time: Float) {
        GLES20.glUseProgram(digitProgram)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        for (i in digitValues.indices) {
            val cycle = (time * 0.18f + i * 0.19f) % 1f
            val attracting = cycle > 0.68f
            val p = if (attracting) ((cycle - 0.68f) / 0.32f).coerceIn(0f, 1f) else 0f
            val ease = p * p * (3f - 2f * p)
            val base = digitBase[i]
            val orbit = ease * 1.8f
            val targetX = sin(i * 1.7f + orbit * 4f) * (0.32f * (1f - ease))
            val targetY = cos(i * 1.3f + orbit * 3f) * (0.20f * (1f - ease))
            val x = base[0] * (1f - ease) + targetX
            val y = base[1] * (1f - ease) + targetY
            val z = base[2] * (1f - ease) + 0.35f * ease
            val depth = 0.74f + (i % 4) * 0.10f
            val size = depth * (1f + 0.22f * sin(time * 0.8f + i)) * (1f - 0.55f * ease)
            val model = multiply(translate(x, y, z), scale(size * 0.46f, size * 0.25f, size))
            val alpha = if (attracting) 0.42f * (1f - ease) else 0.25f + (i % 3) * 0.07f
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(digitProgram, "uMvp"), 1, false, multiply(vp, model), 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(digitProgram, "uAlpha"), alpha)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, digitTextures[i])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(digitProgram, "uTexture"), 0)
            quad.draw(digitProgram)
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun makeDigitTexture(text: String): Int {
        val bitmap = Bitmap.createBitmap(180, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 58f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); textAlign = Paint.Align.CENTER; alpha = 220 }
        canvas.drawText(text, 90f, 68f, paint)
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return texture[0]
    }
}

private class Mesh(private val vertices: FloatBuffer, private val normals: FloatBuffer? = null, private val indices: ShortBuffer? = null, private val uvs: FloatBuffer? = null, private val count: Int) {
    fun draw(program: Int) {
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(pos); vertices.position(0); GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, vertices)
        normals?.let { val n = GLES20.glGetAttribLocation(program, "aNormal"); GLES20.glEnableVertexAttribArray(n); it.position(0); GLES20.glVertexAttribPointer(n, 3, GLES20.GL_FLOAT, false, 0, it) }
        uvs?.let { val u = GLES20.glGetAttribLocation(program, "aUv"); GLES20.glEnableVertexAttribArray(u); it.position(0); GLES20.glVertexAttribPointer(u, 2, GLES20.GL_FLOAT, false, 0, it) }
        if (indices != null) { indices.position(0); GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices) } else GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(pos)
    }
    companion object {
        fun sphere(r: Float, slices: Int, stacks: Int): Mesh {
            val v = ArrayList<Float>(); val n = ArrayList<Float>(); val idx = ArrayList<Short>()
            for (stack in 0..stacks) { val phi = Math.PI * stack / stacks; val y = cos(phi).toFloat(); val rr = sin(phi).toFloat(); for (slice in 0..slices) { val th = 2 * Math.PI * slice / slices; val x = (rr * cos(th)).toFloat(); val z = (rr * sin(th)).toFloat(); v += x*r; v += y*r; v += z*r; n += x; n += y; n += z } }
            for (stack in 0 until stacks) for (slice in 0 until slices) { val a = (stack*(slices+1)+slice).toShort(); val b = (a+1).toShort(); val c = (a+slices+1).toShort(); val d = (c+1).toShort(); idx += a; idx += c; idx += b; idx += b; idx += c; idx += d }
            return Mesh(floatBuffer(v.toFloatArray()), floatBuffer(n.toFloatArray()), shortBuffer(idx.toShortArray()), count = idx.size)
        }
        fun quad(): Mesh = Mesh(floatBuffer(floatArrayOf(-1f,-1f,0f, 1f,-1f,0f, 1f,1f,0f, -1f,-1f,0f, 1f,1f,0f, -1f,1f,0f)), uvs=floatBuffer(floatArrayOf(0f,1f, 1f,1f, 1f,0f, 0f,1f, 1f,0f, 0f,0f)), count=6)
    }
}

private fun floatBuffer(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
private fun shortBuffer(a: ShortArray): ShortBuffer = ByteBuffer.allocateDirect(a.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(a); position(0) }
private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, source); GLES20.glCompileShader(it) }
private fun linkProgram(vertex: String, fragment: String): Int = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertex)); GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragment)); GLES20.glLinkProgram(it) }
private fun translate(x: Float,y: Float,z: Float)=floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, x,y,z,1f)
private fun scale(x: Float,y: Float,z: Float)=floatArrayOf(x,0f,0f,0f, 0f,y,0f,0f, 0f,0f,z,0f, 0f,0f,0f,1f)
private fun rotateX(a: Float): FloatArray { val r=Math.toRadians(a.toDouble()).toFloat(); val c=cos(r); val s=sin(r); return floatArrayOf(1f,0f,0f,0f,0f,c,s,0f,0f,-s,c,0f,0f,0f,0f,1f) }
private fun rotateY(a: Float): FloatArray { val r=Math.toRadians(a.toDouble()).toFloat(); val c=cos(r); val s=sin(r); return floatArrayOf(c,0f,-s,0f,0f,1f,0f,0f,s,0f,c,0f,0f,0f,0f,1f) }
// OpenGL ES matrices are column-major: r = a * b with column/row indexing.
private fun multiply(a: FloatArray,b: FloatArray): FloatArray { val r=FloatArray(16); for (col in 0..3) for (row in 0..3) for (k in 0..3) r[col * 4 + row] += a[k * 4 + row] * b[col * 4 + k]; return r }
private fun perspective(fov: Float, aspect: Float, near: Float, far: Float): FloatArray { val f=1f/ kotlin.math.tan(Math.toRadians((fov/2).toDouble())).toFloat(); return floatArrayOf(f/aspect,0f,0f,0f,0f,f,0f,0f,0f,0f,(far+near)/(near-far),-1f,0f,0f,(2f*far*near)/(near-far),0f) }
private fun lookAt(ex:Float,ey:Float,ez:Float,cx:Float,cy:Float,cz:Float,ux:Float,uy:Float,uz:Float):FloatArray = translate(-ex,-ey,-ez)

private const val VERTEX = "attribute vec3 aPosition; attribute vec3 aNormal; uniform mat4 uMvp; uniform mat4 uModel; varying vec3 vNormal; varying vec3 vPosition; void main(){vNormal=mat3(uModel)*aNormal;vPosition=(uModel*vec4(aPosition,1.0)).xyz;gl_Position=uMvp*vec4(aPosition,1.0);}"
private const val FRAGMENT = "precision mediump float; uniform vec4 uColor; uniform vec3 uLight; varying vec3 vNormal; varying vec3 vPosition; void main(){vec3 n=normalize(vNormal);vec3 l=normalize(uLight-vPosition);float d=max(dot(n,l),0.0);float rim=pow(1.0-max(dot(n,normalize(-vPosition)),0.0),2.0);vec3 c=uColor.rgb*(0.20+0.78*d)+vec3(1.0,0.16,0.32)*rim*0.34;gl_FragColor=vec4(c,uColor.a);}"
private const val DIGIT_VERTEX = "attribute vec3 aPosition; attribute vec2 aUv; uniform mat4 uMvp; varying vec2 vUv; void main(){vUv=aUv;gl_Position=uMvp*vec4(aPosition,1.0);}"
private const val DIGIT_FRAGMENT = "precision mediump float; uniform sampler2D uTexture; uniform float uAlpha; varying vec2 vUv; void main(){vec4 c=texture2D(uTexture,vUv);gl_FragColor=vec4(c.rgb,c.a*uAlpha);}"
