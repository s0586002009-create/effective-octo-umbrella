package com.screenprank

import android.content.Context
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
        if (cracked) {
            canvas.drawPath(crackPath, crackDarkPaint)
            canvas.save()
            canvas.translate(-0.6f, -0.6f)
            canvas.drawPath(crackPath, crackLightPaint)
            canvas.restore()
        }
    }

    private fun drawCritter(canvas: Canvas, bug: Critter) {
        canvas.save()
        canvas.translate(bug.x, bug.y)
        canvas.rotate(bug.rotation)
        canvas.scale(bug.scale, bug.scale)
        canvas.drawOval(5f, 42f, 96f, 62f, shadowPaint)

        if (bug.type == 9 || bug.type == 13) {
            drawWorm(canvas, bug)
            canvas.restore()
            return
        }

        val legs = when (bug.type) {
            1, 7 -> 8
            8, 10, 11, 12 -> 4
            9, 13 -> 0
            else -> 6
        }
        for (i in 0 until legs / 2) {
            val x = 29f + i * (38f / (legs / 2).coerceAtLeast(1))
            val phase = sin(bug.phase * 8f + i * 1.6f) * 3f
            path.reset()
            path.moveTo(x, 38f)
            path.quadTo(x - 8f, 42f + phase, x - 17f, 55f + phase)
            path.moveTo(x + 5f, 38f)
            path.quadTo(x + 14f, 43f - phase, x + 20f, 55f - phase)
            canvas.drawPath(path, legPaint)
            if (realistic) {
                path.reset()
                path.moveTo(x - 9f, 46f + phase)
                path.lineTo(x - 12f, 43f + phase)
                path.moveTo(x + 12f, 47f - phase)
                path.lineTo(x + 15f, 44f - phase)
                canvas.drawPath(path, veinPaint)
            }
        }

        if (bug.type == 0 || bug.type == 5 || bug.type == 6) {
            canvas.drawOval(34f, 4f, 75f, 36f, wingPaint)
            canvas.drawOval(44f, 1f, 84f, 31f, wingPaint)
            path.reset()
            path.moveTo(44f, 8f); path.quadTo(58f, 19f, 69f, 32f)
            path.moveTo(52f, 5f); path.quadTo(66f, 18f, 79f, 27f)
            canvas.drawPath(path, veinPaint)
        }

        bodyPaint.shader = shaders[bug.type]
        when (bug.type) {
            10 -> {
                canvas.drawCircle(31f, 24f, 13f, bodyPaint)
                canvas.drawCircle(74f, 28f, 20f, bodyPaint)
                canvas.drawOval(78f, 6f, 96f, 26f, bodyPaint)
                canvas.drawOval(80f, 9f, 92f, 23f, shinePaint)
                canvas.drawOval(48f, 25f, 90f, 51f, bodyPaint)
                path.reset(); path.moveTo(18f, 32f); path.cubicTo(0f, 42f, 7f, 59f, 0f, 62f)
                canvas.drawPath(path, legPaint)
            }
            11 -> {
                canvas.drawOval(16f, 24f, 88f, 53f, bodyPaint)
                canvas.drawCircle(60f, 22f, 21f, bodyPaint)
                canvas.drawCircle(60f, 22f, 14f, shinePaint)
                path.reset(); path.moveTo(60f, 22f); path.cubicTo(35f, -2f, 83f, 0f, 67f, 28f)
                canvas.drawPath(path, legPaint)
                canvas.drawOval(78f, 15f, 83f, 20f, eyeWhitePaint)
            }
            12 -> {
                canvas.drawOval(16f, 18f, 84f, 55f, bodyPaint)
                canvas.drawCircle(35f, 14f, 10f, bodyPaint)
                canvas.drawCircle(69f, 14f, 10f, bodyPaint)
            }
            8 -> {
                canvas.drawOval(16f, 23f, 82f, 48f, bodyPaint)
                canvas.drawCircle(87f, 25f, 13f, bodyPaint)
                canvas.drawCircle(88f, 22f, 3.5f, eyeWhitePaint)
                canvas.drawCircle(89f, 22f, 1.7f, pupilPaint)
                path.reset(); path.moveTo(20f, 32f); path.quadTo(5f, 20f, 0f, 9f)
                canvas.drawPath(path, legPaint)
            }
            7 -> {
                canvas.drawOval(26f, 20f, 73f, 49f, bodyPaint)
                path.reset(); path.moveTo(66f, 24f); path.cubicTo(95f, -3f, 96f, -12f, 85f, -17f)
                canvas.drawPath(path, legPaint)
                canvas.drawCircle(85f, -17f, 4.5f, bodyPaint)
            }
            3 -> {
                canvas.drawOval(18f, 24f, 48f, 43f, bodyPaint)
                canvas.drawOval(46f, 17f, 64f, 43f, bodyPaint)
                canvas.drawOval(61f, 20f, 91f, 46f, bodyPaint)
            }
            4 -> {
                canvas.drawOval(19f, 15f, 87f, 54f, bodyPaint)
                bodyPaint.shader = null
                canvas.drawLine(52f, 16f, 52f, 53f, legPaint)
                for (x in listOf(36f, 68f)) for (y in listOf(26f, 41f)) canvas.drawCircle(x, y, 4f, darkPaint)
            }
            else -> {
                canvas.drawOval(18f, 25f, 72f, 49f, bodyPaint)
                canvas.drawOval(57f, 19f, 89f, 43f, bodyPaint)
                if (bug.type == 2) {
                    bodyPaint.shader = null
                    canvas.drawOval(25f, 28f, 66f, 39f, shinePaint)
                }
            }
        }

        bodyPaint.shader = null
        if (bug.type != 10 && bug.type != 11 && bug.type != 12) {
            canvas.drawCircle(83f, 24f, 5.4f, eyeWhitePaint)
            canvas.drawCircle(85f, 24f, 2.4f, pupilPaint)
            canvas.drawCircle(86f, 22.8f, 1f, shinePaint)
        } else if (bug.type == 12) {
            canvas.drawCircle(34f, 10f, 5f, eyeWhitePaint)
            canvas.drawCircle(68f, 10f, 5f, eyeWhitePaint)
            canvas.drawCircle(35f, 10f, 2.2f, pupilPaint)
            canvas.drawCircle(69f, 10f, 2.2f, pupilPaint)
        }

        path.reset()
        path.moveTo(79f, 15f); path.quadTo(94f, 0f, 91f, -9f)
        path.moveTo(73f, 15f); path.quadTo(82f, -1f, 78f, -10f)
        canvas.drawPath(path, legPaint)

        if (realistic) {
            canvas.drawOval(28f, 27f, 63f, 32f, shinePaint)
            for (n in 0..5) {
                val hx = 29f + n * 8f
                canvas.drawLine(hx, 40f, hx - 3f, 44f, veinPaint)
            }
        }
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
        for (spot in spots) {
            dirtPaint.color = when (spot.kind) {
                0 -> Color.argb(72, 88, 64, 48)
                1 -> Color.argb(48, 43, 47, 54)
                else -> Color.argb(60, 110, 82, 52)
            }
            canvas.drawCircle(spot.x, spot.y, spot.radius, dirtPaint)
            if (spot.kind == 0) {
                dirtPaint.color = Color.argb(55, 255, 255, 255)
                canvas.drawCircle(spot.x - spot.radius * 0.28f, spot.y - spot.radius * 0.22f, spot.radius * 0.25f, dirtPaint)
            }
        }
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

    fun clearEffects() {
        cracked = false
        dirty = false
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
                type = creatureType
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
