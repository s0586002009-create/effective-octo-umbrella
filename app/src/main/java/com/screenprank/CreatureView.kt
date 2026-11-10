package com.screenprank

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class CreatureView(context: Context) : View(context), Choreographer.FrameCallback {
    private data class Critter(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var rotation: Float,
        var scale: Float,
        val type: Int,
        var pause: Float = 0f,
        var phase: Float = Random.nextFloat() * 6.28f
    )

    private data class Spot(val x: Float, val y: Float, val radius: Float, val kind: Int)

    private val prefs = context.getSharedPreferences("screen_prank", Context.MODE_PRIVATE)
    private val random = Random(System.nanoTime())
    private val bugs = ArrayList<Critter>()
    private val spots = ArrayList<Spot>()
    private val handler = Handler(Looper.getMainLooper())
    private val bodyRect = RectF()
    private val creatureFrames by lazy {
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.creatures)
        Array(14) { index -> Bitmap.createBitmap(sheet, (index % 7) * 64, (index / 7) * 64, 64, 64) }
    }
    private val creatureImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val effectFrames by lazy {
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.effects)
        Array(7) { index -> Bitmap.createBitmap(sheet, index * 64, 0, 64, 64) }
    }
    private val effectImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val crackPath = Path()
    private var widthPx = 0f
    private var heightPx = 0f
    private var count = 6
    private var sizeDp = 74
    private var speedSetting = 4
    private var creatureType = 0
    private var selectedTypes: List<Int> = listOf(0)
    private var pathMode = 0
    private var extraEffect = 0
    private var realistic = true
    private var randomSize = true
    private var touchEnabled = false
    private var cracked = false
    private var dirty = false
    private var paused = false
    private var lastFrame = 0L
    private var lastConfig = ""

    private val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 29, 27)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 26, 28) }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255) }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 244, 232) }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 14, 17) }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(84, 224, 244, 250)
        style = Paint.Style.FILL
    }
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 220, 239, 245)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, 0, 0, 0)
    }
    private val crackDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 24, 30, 39)
        style = Paint.Style.STROKE
        strokeWidth = 3.1f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val crackLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 237, 248, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.15f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(62, 74, 52, 34) }
    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shaders = Array(14) { index ->
        val colors = when (index) {
            4 -> intArrayOf(Color.rgb(255, 220, 76), Color.rgb(208, 42, 44), Color.rgb(67, 20, 24))
            6 -> intArrayOf(Color.rgb(255, 238, 112), Color.rgb(210, 142, 27), Color.rgb(64, 43, 20))
            8, 12 -> intArrayOf(Color.rgb(183, 226, 115), Color.rgb(62, 137, 63), Color.rgb(24, 59, 39))
            9, 13 -> intArrayOf(Color.rgb(211, 190, 130), Color.rgb(106, 113, 72), Color.rgb(39, 48, 37))
            10 -> intArrayOf(Color.rgb(199, 150, 111), Color.rgb(119, 72, 49), Color.rgb(53, 39, 34))
            11 -> intArrayOf(Color.rgb(229, 208, 160), Color.rgb(135, 106, 65), Color.rgb(61, 49, 40))
            else -> intArrayOf(Color.rgb(171, 190, 132), Color.rgb(64, 83, 54), Color.rgb(23, 31, 26))
        }
        RadialGradient(30f, 18f, 70f, colors, floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
    }

    private val frameCallback = this

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        updateSettings()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrame != 0L && !paused) {
            val dt = ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            updatePositions(dt)
            invalidate()
        }
        lastFrame = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (!paused) invalidate()
    }

    fun updateSettings() {
        count = prefs.getInt("count", 6).coerceIn(1, 30)
        sizeDp = prefs.getInt("size", 74).coerceIn(16, 200)
        speedSetting = prefs.getInt("speed", 4).coerceIn(1, 10)
        creatureType = prefs.getInt("creature_type", 0).coerceIn(0, 13)
        selectedTypes = prefs.getString("selected_creatures", "")?.split(",")
            ?.mapNotNull { it.toIntOrNull() }?.filter { it in 0..13 }?.distinct()
            ?.ifEmpty { listOf(creatureType) } ?: listOf(creatureType)
        pathMode = prefs.getInt("path_mode", 0).coerceIn(0, 3)
        realistic = prefs.getBoolean("realistic", true)
        randomSize = prefs.getBoolean("random_size", true)
        touchEnabled = prefs.getBoolean("touch", false)
        val signature = "$count:$sizeDp:$speedSetting:$creatureType:$randomSize:$selectedTypes:$pathMode"
        if (signature != lastConfig && widthPx > 0f) rebuildCritters()
        lastConfig = signature
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w.toFloat()
        heightPx = h.toFloat()
        rebuildCritters()
    }

    private fun rebuildCritters() {
        bugs.clear()
        if (widthPx <= 0f || heightPx <= 0f) return
        val density = resources.displayMetrics.density
        repeat(count) {
            val pxSize = sizeDp * density * if (randomSize) random.nextFloat().let { 0.68f + it * 0.68f } else 1f
            val angle = random.nextFloat() * (2f * PI.toFloat())
            val speed = (18f + speedSetting * 10f) * density
            bugs.add(
                Critter(
                    x = random.nextFloat() * widthPx,
                    y = random.nextFloat() * heightPx,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed * 0.45f,
                    rotation = angle * 180f / PI.toFloat(),
                    scale = pxSize / 90f,
                    type = selectedTypes.random(random)
                )
            )
        }
    }

    private fun updatePositions(dt: Float) {
        if (paused || bugs.isEmpty()) return
        val bounds = 64f * resources.displayMetrics.density
        val velocity = (18f + speedSetting * 10f) * resources.displayMetrics.density
        bugs.forEach { bug ->
            bug.phase += dt * (1.1f + speedSetting * 0.16f)
            if (bug.pause > 0f) { bug.pause -= dt; return@forEach }
            when (pathMode) {
                1 -> {
                    bug.vx = if (bug.vx >= 0f) velocity else -velocity
                    bug.vy = sin(bug.phase * 1.7f) * velocity * 0.72f
                    bug.x += bug.vx * dt
                    bug.y = (bug.y + bug.vy * dt).coerceIn(bounds, (heightPx - bounds).coerceAtLeast(bounds))
                    if (bug.x < -bounds) bug.x = widthPx + bounds
                    if (bug.x > widthPx + bounds) bug.x = -bounds
                }
                2 -> {
                    bug.x += bug.vx * dt; bug.y += bug.vy * dt
                    if (bug.x < bounds || bug.x > widthPx - bounds) bug.vx = -bug.vx
                    if (bug.y < bounds || bug.y > heightPx - bounds) bug.vy = -bug.vy
                    bug.x = bug.x.coerceIn(bounds, (widthPx - bounds).coerceAtLeast(bounds))
                    bug.y = bug.y.coerceIn(bounds, (heightPx - bounds).coerceAtLeast(bounds))
                }
                3 -> {
                    bug.vy = if (bug.vy >= 0f) velocity * 0.7f else -velocity * 0.7f
                    bug.vx = cos(bug.phase) * velocity * 0.55f
                    bug.x += bug.vx * dt; bug.y += bug.vy * dt
                    if (bug.y < -bounds) bug.y = heightPx + bounds
                    if (bug.y > heightPx + bounds) bug.y = -bounds
                    if (bug.x < -bounds) bug.x = widthPx + bounds
                    if (bug.x > widthPx + bounds) bug.x = -bounds
                }
                else -> {
                    if (random.nextFloat() < dt * 0.42f) {
                        val angle = atan2(bug.vy, bug.vx) + (random.nextFloat() - 0.5f) * 1.4f
                        bug.vx = cos(angle) * velocity
                        bug.vy = sin(angle) * velocity * 0.45f
                        if (random.nextFloat() < 0.12f) bug.pause = 0.5f + random.nextFloat() * 1.2f
                    }
                    bug.x += bug.vx * dt
                    bug.y += bug.vy * dt + sin(bug.phase * 2f) * 9f * dt
                    if (bug.x < -bounds) bug.x = widthPx + bounds
                    if (bug.x > widthPx + bounds) bug.x = -bounds
                    if (bug.y < -bounds) bug.y = heightPx + bounds
                    if (bug.y > heightPx + bounds) bug.y = -bounds
                }
            }
            bug.rotation = atan2(bug.vy, bug.vx) * 180f / PI.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (bug in bugs) drawCritter(canvas, bug)
        if (dirty) drawDirt(canvas)
        if (extraEffect != 0) drawExtraEffect(canvas)
        if (cracked) drawCrackTexture(canvas)
    }

    private fun drawCritter(canvas: Canvas, bug: Critter) {
        canvas.save()
        canvas.translate(bug.x, bug.y)
        canvas.rotate(bug.rotation)
        canvas.scale(bug.scale, bug.scale)
        canvas.drawOval(5f, 76f, 96f, 94f, shadowPaint)
        val bitmap = creatureFrames[bug.type.coerceIn(0, 13)]
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 100f, 100f), creatureImagePaint)
        canvas.restore()
    }

    private fun drawWorm(canvas: Canvas, bug: Critter) {
        path.reset()
        val amplitude = if (bug.type == 9) 15f else 8f
        path.moveTo(6f, 34f)
        for (i in 1..9) {
            val x = 6f + i * 9f
            val y = 34f + sin(bug.phase + i * 0.75f) * amplitude
            path.lineTo(x, y)
        }
        val p = Paint(bodyPaint).apply {
            shader = shaders[bug.type]
            style = Paint.Style.STROKE
            strokeWidth = if (bug.type == 9) 13f else 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, p)
        canvas.drawPath(path, veinPaint)
        canvas.drawCircle(88f, 34f + sin(bug.phase + 7f) * amplitude, 7f, bodyPaint)
        bodyPaint.shader = null
    }

    private fun drawDirt(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val p = Paint(effectImagePaint).apply { alpha = 175 }
        repeat(12) { i ->
            val size = (62f + (i % 4) * 18f) * d
            val x = randomSpotX(i + 11)
            val y = randomSpotY(i + 31)
            canvas.drawBitmap(effectFrames[1], null, RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f), p)
        }
    }

    private fun drawCrackTexture(canvas: Canvas) {
        val p = Paint(effectImagePaint).apply { alpha = 245 }
        canvas.drawBitmap(effectFrames[0], null, RectF(-widthPx * 0.02f, -heightPx * 0.02f, widthPx * 1.02f, heightPx * 1.02f), p)
    }

    fun showCrack() {
        if (widthPx <= 0f || heightPx <= 0f) return
        cracked = true
        crackPath.reset()
        val cx = random.nextFloat() * widthPx
        val cy = random.nextFloat() * heightPx
        val rays = 9 + random.nextInt(6)
        for (i in 0 until rays) {
            val angle = (2.0 * PI * i / rays).toFloat() + (random.nextFloat() - 0.5f) * 0.2f
            val length = (90f + random.nextFloat() * 240f) * resources.displayMetrics.density
            val ex = cx + cos(angle) * length
            val ey = cy + sin(angle) * length
            crackPath.moveTo(cx, cy)
            val mx = cx + cos(angle + 0.12f) * length * 0.48f
            val my = cy + sin(angle - 0.10f) * length * 0.48f
            crackPath.quadTo(mx, my, ex, ey)
            if (i % 2 == 0) {
                crackPath.moveTo(mx, my)
                crackPath.lineTo(mx + sin(angle) * 30f, my - cos(angle) * 38f)
                crackPath.moveTo(ex, ey)
                crackPath.lineTo(ex + cos(angle + 1f) * 18f, ey + sin(angle + 1f) * 25f)
            }
        }
        invalidate()
    }

    fun showDirt() {
        dirty = true
        spots.clear()
        if (widthPx > 0f && heightPx > 0f) {
            repeat(55) {
                spots.add(
                    Spot(
                        random.nextFloat() * widthPx,
                        random.nextFloat() * heightPx,
                        (3f + random.nextFloat() * 23f) * resources.displayMetrics.density,
                        random.nextInt(3)
                    )
                )
            }
        }
        invalidate()
    }

    fun showExtraEffect(kind: Int) {
        extraEffect = kind.coerceIn(0, 5)
        invalidate()
    }

    private fun drawExtraEffect(canvas: Canvas) {
        val frameIndex = (extraEffect + 1).coerceIn(2, 6)
        val count = when (extraEffect) { 1 -> 5; 2 -> 3; 3 -> 4; 4 -> 5; else -> 5 }
        val alpha = when (extraEffect) { 1 -> 135; 2 -> 205; 3 -> 220; 4 -> 205; else -> 230 }
        val p = Paint(effectImagePaint).apply { this.alpha = alpha }
        repeat(count) { i ->
            val size = minOf(widthPx, heightPx) * when (extraEffect) { 1 -> 0.28f; 2 -> 0.23f; 3 -> 0.24f; 4 -> 0.25f; else -> 0.38f }
            val x = widthPx * (((i * 0.237f) + 0.08f) % 0.86f)
            val y = heightPx * (((i * 0.317f) + 0.10f) % 0.82f)
            canvas.drawBitmap(effectFrames[frameIndex], null, RectF(x, y, x + size, y + size), p)
        }
    }

    private fun randomSpotX(seed: Int): Float = ((seed * 73L % 997L).toFloat() / 997f) * widthPx
    private fun randomSpotY(seed: Int): Float = ((seed * 137L % 991L).toFloat() / 991f) * heightPx

    fun clearEffects() {
        cracked = false
        dirty = false
        extraEffect = 0
        crackPath.reset()
        spots.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchEnabled) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val hitRadius = (sizeDp * resources.displayMetrics.density).coerceAtLeast(44f)
            val index = bugs.indexOfLast {
                val dx = it.x - event.x
                val dy = it.y - event.y
                sqrt(dx * dx + dy * dy) < hitRadius * it.scale
            }
            if (index >= 0) {
                bugs.removeAt(index)
                vibrate()
                handler.postDelayed({
                    if (widthPx > 0f && bugs.size < count) addOneCritter()
                }, 1400L)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun addOneCritter() {
        val density = resources.displayMetrics.density
        val pxSize = sizeDp * density * if (randomSize) 0.8f + random.nextFloat() * 0.4f else 1f
        val angle = random.nextFloat() * 6.28f
        val velocity = (18f + speedSetting * 10f) * density
        bugs.add(
            Critter(
                x = random.nextFloat() * widthPx,
                y = random.nextFloat() * heightPx,
                vx = cos(angle) * velocity,
                vy = sin(angle) * velocity * 0.45f,
                rotation = angle * 180f / PI.toFloat(),
                scale = pxSize / 90f,
                type = selectedTypes.random(random)
            )
        )
    }

    private fun vibrate() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(45, 90))
            else @Suppress("DEPRECATION") vibrator.vibrate(45)
        } catch (_: Exception) { }
    }
}
