package com.feiche.qqspeed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.feiche.qqspeed.game.Car
import com.feiche.qqspeed.game.Game
import com.feiche.qqspeed.game.Track
import com.feiche.qqspeed.game.U
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// HUD 叠加层：小地图/速度表/集气/排名/消息/倒计时/触控按钮（Canvas 绘制，移植 hud.js）
class HudView(context: Context, val game: Game) : View(context), com.feiche.qqspeed.game.HudSink {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 10, 20, 48) }
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(90, 120, 190, 255)
        strokeWidth = 1f * density
    }
    private var W = 0f; var H = 0f

    // 触控按钮
    class TBtn(val id: String, var x: Float = 0f, var y: Float = 0f, var r: Float = 0f, var on: Boolean = false, val label: String)

    private val btns = listOf(
        TBtn("left", label = "◀"), TBtn("right", label = "▶"), TBtn("brake", label = "刹"),
        TBtn("drift", label = "漂移"), TBtn("nitro", label = "氮气"), TBtn("boost", label = "小喷"), TBtn("swap", label = "换位"),
    )
    private val activePointers = HashMap<Int, String>()

    // 消息
    private class Msg(val text: String, val color: Int, val small: Boolean, val t0: Long)
    private val msgs = ArrayList<Msg>()
    private var countdownText = ""
    private var countdownT = 0L

    // 小地图
    private var mmBase: android.graphics.Bitmap? = null
    private var mmScale = 1f
    private var mmCx = 0f; private var mmCy = 0f
    private var mmSize = 0f
    private var startAngle = 0f
    private var startX = 0f; private var startY = 0f

    // 速度线
    private val speedLines = ArrayList<FloatArray>()

    var onPauseRequested: (() -> Unit)? = null
    private var showHud = false
    private var running = true

    init {
        val c = Choreographer.getInstance()
        c.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                invalidate()
                c.postFrameCallback(this)
            }
        })
    }

    fun shutdown() { running = false }

    // ---------- HudSink ----------
    override fun setupMinimap(track: Track) {
        post {
            try {
            val pad = 26f * density
            val size = 200f * density
            mmSize = size
            val b = floatArrayOf(track.minX, track.maxX, track.minZ, track.maxZ, track.cx, track.cz)
            boundsCache = b
            val sc = (size - pad * 2) / maxOf(b[1] - b[0], b[3] - b[2])
            mmScale = sc
            mmCx = size / 2f; mmCy = size / 2f
            val bmp = android.graphics.Bitmap.createBitmap(size.toInt(), size.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            val g = Canvas(bmp)
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
            fun path(): Path {
                val pp = Path()
                for (i in 0..track.N step 2) {
                    val k = i % track.N
                    val x = size / 2 + (track.px[k] - b[4]) * sc
                    val y = size / 2 - (track.pz[k] - b[5]) * sc
                    if (i == 0) pp.moveTo(x, y) else pp.lineTo(x, y)
                }
                pp.close()
                return pp
            }
            gp.color = Color.argb(140, 0, 0, 0); gp.strokeWidth = 16f * density
            g.drawPath(path(), gp)
            gp.color = Color.argb(235, 255, 255, 255); gp.strokeWidth = 9f * density
            g.drawPath(path(), gp)
            gp.color = Color.argb(230, 39, 199, 255); gp.strokeWidth = 5f * density
            for (i in 0 until track.N) {
                if (!track.bridge[i]) continue
                val x1 = size / 2 + (track.px[i] - b[4]) * sc
                val y1 = size / 2 - (track.pz[i] - b[5]) * sc
                val j = (i + 1) % track.N
                val x2 = size / 2 + (track.px[j] - b[4]) * sc
                val y2 = size / 2 - (track.pz[j] - b[5]) * sc
                g.drawLine(x1, y1, x2, y2, gp)
            }
            val s = track.sample(0f, com.feiche.qqspeed.game.SampleOut())
            val sx = size / 2 + (s.x - b[4]) * sc
            val sy = size / 2 - (s.z - b[5]) * sc
            g.save()
            g.translate(sx, sy)
            g.rotate(Math.toDegrees((s.hd - Math.PI / 2).toDouble()).toFloat())
            g.drawRect(-3f * density, -12f * density, 3f * density, 12f * density, Paint().apply { color = Color.rgb(17, 17, 17) })
            val chk = Paint().apply { color = Color.WHITE }
            for (k in 0 until 4) g.drawRect(if (k % 2 == 1) 0f else -3f * density, (-12 + k * 6) * density, if (k % 2 == 1) 3f * density else 0f, (-12 + k * 6 + 6) * density, chk)
            g.restore()
            startAngle = s.hd
            startX = sx; startY = sy
            mmBase = bmp
            } catch (_: Throwable) {
            }
        }
    }

    private fun mmT(x: Float, z: Float, b: FloatArray): FloatArray {
        return floatArrayOf(mmCx + (x - b[4]) * mmScale, mmCy - (z - b[5]) * mmScale)
    }

    override fun showResults(title: String, win: Boolean, stats: Car.RaceStats, rows: List<com.feiche.qqspeed.game.ResultRow>) {
        onResultsCallback?.invoke(title, win, stats, rows)
    }

    override fun onTogglePause() {
        onPauseUICallback?.invoke()
    }

    var onResultsCallback: ((String, Boolean, Car.RaceStats, List<com.feiche.qqspeed.game.ResultRow>) -> Unit)? = null
    var onPauseUICallback: (() -> Unit)? = null    // ---------- 触控 ----------
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val racing = game.state == "race" || game.state == "countdown"
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = ev.actionIndex
                handleTouch(ev.getPointerId(idx), ev.getX(idx), ev.getY(idx), racing)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    val holding = activePointers[id]
                    val hit = hitTest(ev.getX(i), ev.getY(i))
                    if (holding != null && hit != holding) {
                        release(holding)
                        handleTouch(id, ev.getX(i), ev.getY(i), racing)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (ev.actionMasked == MotionEvent.ACTION_UP) -1 else ev.actionIndex
                if (idx >= 0) {
                    val id = ev.getPointerId(idx)
                    activePointers.remove(id)?.let { release(it) }
                } else {
                    for ((id, b) in activePointers) release(b)
                    activePointers.clear()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                for ((_, b) in activePointers) release(b)
                activePointers.clear()
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): String? {
        for (b in btns) {
            val dx = x - b.x; val dy = y - b.y
            if (dx * dx + dy * dy <= b.r * b.r) return b.id
        }
        return null
    }

    private fun handleTouch(id: Int, x: Float, y: Float, racing: Boolean) {
        if (pauseHit(x, y)) {
            onPauseRequested?.invoke()
            return
        }
        val hit = hitTest(x, y) ?: return
        activePointers[id] = hit
        val inp = game.input
        when (hit) {
            "left" -> inp.left = true
            "right" -> inp.right = true
            "brake" -> inp.down = true
            "drift" -> inp.shift = true
            "nitro" -> inp.nitroPressed = true
            "boost" -> inp.upPressed = true
            "swap" -> inp.swapPressed = true
        }
        for (b in btns) if (b.id == hit) b.on = true
    }

    private fun release(id: String) {
        val inp = game.input
        when (id) {
            "left" -> inp.left = false
            "right" -> inp.right = false
            "brake" -> inp.down = false
            "drift" -> inp.shift = false
        }
        for (b in btns) if (b.id == id) b.on = false
    }

    // ---------- 布局 ----------
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        W = w.toFloat(); H = h.toFloat()
        layoutButtons()
    }

    private fun layoutButtons() {
        val r = 37f * density
        fun set(id: String, cx: Float, cy: Float, rr: Float = r) {
            for (b in btns) if (b.id == id) { b.x = cx; b.y = cy; b.r = rr }
        }
        set("left", 24f * density + r, H - 34f * density - r)
        set("right", 24f * density + r * 3.2f, H - 34f * density - r)
        set("brake", 24f * density + r * 2.1f, H - 34f * density - r * 3.1f, r * 0.8f)
        set("drift", W - 28f * density - r, H - 120f * density - r)
        set("nitro", W - 124f * density - r, H - 40f * density - r)
        set("boost", W - 124f * density - r, H - 132f * density - r)
        set("swap", W - 220f * density - r, H - 40f * density - r, r * 0.8f)
    }

    // ---------- 绘制 ----------
    override fun onDraw(canvas: Canvas) {
        W = width.toFloat(); H = height.toFloat()
        val hud = game.pollHud()
        showHud = hud.show && (game.state == "race" || game.state == "countdown" || game.state == "finish" || game.state == "paused")

        // 速度线
        drawSpeedLines(canvas)

        if (showHud) {
            drawStandings(canvas, hud)
            drawTopCenter(canvas, hud)
            drawMinimap(canvas, hud)
            drawBigRank(canvas, hud)
            drawSpeedo(canvas, hud)
            drawGauge(canvas, hud)
        }
        drawMessages(canvas)
        drawCountdown(canvas)
        if (showHud) {
            hud.warn?.let { drawWarn(canvas, it) }
            hud.finalCount?.let { drawFinalCount(canvas, it) }
        }
        // 受击闪红
        if (game.flash > 0) {
            p.color = Color.argb((game.flash * 90).toInt().coerceIn(0, 255), 255, 60, 40)
            canvas.drawRect(0f, 0f, W, H, p)
        }
        // 氮气渐晕
        if (showHud && (hud.nitroOn || hud.smallOn)) {
            val grad = RadialGradient(W / 2, H / 2, W * 0.35f,
                intArrayOf(Color.TRANSPARENT, if (hud.nitroOn) Color.argb(150, 60, 190, 255) else Color.argb(120, 255, 190, 60)),
                null, Shader.TileMode.CLAMP)
            p.shader = grad
            canvas.drawRect(0f, 0f, W, H, p)
            p.shader = null
        }
        // 触控按钮
        if (game.state == "race" || game.state == "countdown") drawButtons(canvas, hud)
        // 暂停按钮
        if (game.state == "race" || game.state == "countdown") drawPauseBtn(canvas)
    }

    private fun drawSpeedLines(canvas: Canvas) {
        val dt = 1f / 60f
        val k = game.speedLineIntensity()
        val cx = W / 2; val cy = H * 0.45f
        val spawn = k * 90f * dt
        if (k > 0.01f) {
            var n = (spawn * 1.2f).toInt()
            if (Math.random() < spawn * 1.2f % 1f) n++
            for (i in 0 until n) speedLines.add(floatArrayOf(
                (Math.random() * Math.PI * 2).toFloat(),
                0.35f + Math.random().toFloat() * 0.2f,
                1.8f + Math.random().toFloat() * 1.6f,
                0.08f + Math.random().toFloat() * 0.14f,
                1f + Math.random().toFloat() * 2.5f))
        }
        if (speedLines.isEmpty()) return
        val D = kotlin.math.hypot(W.toDouble(), H.toDouble()).toFloat() / 2f
        p.style = Paint.Style.STROKE
        p.color = if (game.player?.nitroTime ?: 0f > 0) Color.rgb(191, 233, 255) else Color.WHITE
        for (i in speedLines.indices.reversed()) {
            val L = speedLines[i]
            L[1] += L[2] * dt
            if (L[1] > 1.2f) { speedLines.removeAt(i); continue }
            val r1 = L[1] * D; val r2 = (L[1] + L[3]) * D
            p.alpha = (min(1f, k * 1.2f) * min(1f, (L[1] - 0.3f) * 3f) * 0.55f * 255).toInt().coerceIn(0, 255)
            p.strokeWidth = L[4] * (W / 1400f)
            canvas.drawLine(cx + cos(L[0]) * r1, cy + sin(L[0]) * r1 * 0.75f, cx + cos(L[0]) * r2, cy + sin(L[0]) * r2 * 0.75f, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    private fun drawStandings(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        val w = 190f * density
        val x0 = 16f * density; val y0 = 16f * density
        val rowH = 24f * density
        val hgt = rowH * hud.standings.size + 16f * density
        p.color = panel.color
        canvas.drawRoundRect(x0, y0, x0 + w, y0 + hgt, 12f * density, 12f * density, p)
        canvas.drawRoundRect(x0, y0, x0 + w, y0 + hgt, 12f * density, 12f * density, line)
        p.textSize = 14f * density
        for ((i, r) in hud.standings.withIndex()) {
            val y = y0 + 8f * density + i * rowH + rowH / 2f
            if (r.me) {
                p.color = Color.argb(140, 255, 138, 0)
                canvas.drawRect(x0, y - rowH / 2f, x0 + w, y + rowH / 2f, p)
            }
            p.color = Color.rgb(255, 210, 58)
            p.typeface = numFont
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("${i + 1}", x0 + 14f * density, y + 5f * density, p)
            p.typeface = uiFont
            p.color = Color.rgb(244, 248, 255)
            p.textAlign = Paint.Align.LEFT
            var name = r.name
            while (p.measureText(name) > w - 62f * density && name.length > 2) name = name.dropLast(2) + "…"
            canvas.drawText(name, x0 + 30f * density, y + 5f * density, p)
            p.color = r.color
            canvas.drawCircle(x0 + w - 18f * density, y - 1f * density, 5f * density, p)
            if (r.fin) {
                p.color = Color.rgb(109, 255, 158)
                canvas.drawText("✔", x0 + w - 18f * density, y + 5f * density, p)
            }
        }
    }

    private fun drawTopCenter(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        val cy0 = 14f * density
        val boxW = 150f * density; val boxH = 52f * density
        val cx = W / 2f
        // 圈数
        p.color = panel.color
        canvas.drawRoundRect(cx - boxW - 6f * density, cy0, cx - 6f * density, cy0 + boxH, 12f * density, 12f * density, p)
        canvas.drawRoundRect(cx - boxW - 6f * density, cy0, cx - 6f * density, cy0 + boxH, 12f * density, 12f * density, line)
        // 时间
        canvas.drawRoundRect(cx + 6f * density, cy0, cx + 6f * density + boxW * 1.5f, cy0 + boxH, 12f * density, 12f * density, p)
        canvas.drawRoundRect(cx + 6f * density, cy0, cx + 6f * density + boxW * 1.5f, cy0 + boxH, 12f * density, 12f * density, line)
        p.textAlign = Paint.Align.CENTER
        p.typeface = numFont
        p.color = Color.rgb(255, 210, 58)
        p.textSize = 24f * density
        canvas.drawText(hud.lap, cx - boxW / 2f, cy0 + 30f * density, p)
        p.color = Color.WHITE
        canvas.drawText(U.formatTime(hud.time), cx + 6f * density + boxW * 0.75f, cy0 + 30f * density, p)
        p.typeface = uiFont
        p.color = Color.argb(255, 169, 189, 219)
        p.textSize = 11f * density
        canvas.drawText("圈数 LAP", cx - boxW / 2f, cy0 + 44f * density, p)
        canvas.drawText("最佳 ${U.formatTime(hud.best)}", cx + 6f * density + boxW * 0.75f, cy0 + 44f * density, p)
        // 道具
        if (hud.itemMode) {
            val slotW = 46f * density
            for (k in 0 until 2) {
                val x = cx + 6f * density + boxW * 1.5f + 12f * density + k * (slotW + 8f * density)
                p.color = panel.color
                canvas.drawRoundRect(x, cy0, x + slotW, cy0 + slotW, 10f * density, 10f * density, p)
                canvas.drawRoundRect(x, cy0, x + slotW, cy0 + slotW, 10f * density, 10f * density, line)
                val it = if (k == 0) hud.item0 else hud.item1
                if (it.isNotEmpty()) {
                    p.color = Color.WHITE
                    p.textSize = 22f * density
                    canvas.drawText(itemIcon(it), x + slotW / 2f, cy0 + slotW / 2f + 8f * density, p)
                }
            }
        }
    }

    private fun itemIcon(it: String): String = when (it) {
        "nitro" -> "🔥"; "missile" -> "🚀"; "banana" -> "🍌"; "shield" -> "😇"; "magnet" -> "🧲"; else -> "?"
    }

    private fun drawMinimap(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        val base = mmBase ?: return
        val size = mmSize
        val x = W - size - 16f * density
        val y = 16f * density
        p.color = panel.color
        canvas.drawRoundRect(x, y, x + size, y + size, 12f * density, 12f * density, p)
        canvas.save()
        val clip = Path()
        clip.addRoundRect(x, y, x + size, y + size, 12f * density, 12f * density, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawBitmap(base, x, y, null)
        val b = boundsCache ?: run { canvas.restore(); return }
        for (r in game.racers) {
            if (r.isPlayer) continue
            val pt = mmT(r.x, r.z, b)
            p.color = r.skin.body
            canvas.drawCircle(x + pt[0], y + pt[1], 5f * density, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * density; p.color = Color.WHITE
            canvas.drawCircle(x + pt[0], y + pt[1], 5f * density, p)
            p.style = Paint.Style.FILL
        }
        val pl = game.player
        if (pl != null) {
            val pt = mmT(pl.x, pl.z, b)
            canvas.save()
            canvas.translate(x + pt[0], y + pt[1])
            canvas.rotate(Math.toDegrees((pl.h + Math.PI).toDouble()).toFloat())
            val path = Path()
            path.moveTo(0f, 10f * density); path.lineTo(7f * density, -7f * density)
            path.lineTo(0f, -3f * density); path.lineTo(-7f * density, -7f * density); path.close()
            p.color = Color.rgb(255, 210, 58)
            canvas.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * density; p.color = Color.BLACK
            canvas.drawPath(path, p)
            p.style = Paint.Style.FILL
            canvas.restore()
        }
        canvas.restore()
    }

    private var boundsCache: FloatArray? = null

    private fun drawBigRank(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        val x = 22f * density
        val y = H - 100f * density
        p.typeface = numFont
        p.textAlign = Paint.Align.LEFT
        p.textSize = 72f * density
        p.color = Color.WHITE
        canvas.drawText("${hud.rank}", x, y, p)
        p.textSize = 22f * density
        canvas.drawText("/${hud.total}", x + 76f * density, y, p)
    }

    private fun drawSpeedo(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        val cx = W - 18f * density - 75f * density
        val cy = H - 18f * density - 75f * density
        val R = 75f * density
        val a0 = (Math.PI * 0.75).toFloat()
        val sweep = (Math.PI * 1.5).toFloat()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 13f * density
        p.color = Color.argb(180, 8, 20, 50)
        canvas.drawArc(cx - R, cy - R, cx + R, cy + R, deg(a0), deg(sweep), false, p)
        val f = min(1f, hud.kmh / 300f)
        p.strokeWidth = 9f * density
        p.shader = LinearGradient(cx - R, cy + R, cx + R, cy - R,
            intArrayOf(if (hud.nitroOn) Color.rgb(39, 199, 255) else Color.rgb(39, 199, 255),
                if (hud.nitroOn) Color.WHITE else Color.rgb(255, 90, 31)),
            null, Shader.TileMode.CLAMP)
        canvas.drawArc(cx - R, cy - R, cx + R, cy + R, deg(a0), deg(sweep * f), false, p)
        p.shader = null
        // 刻度
        p.strokeWidth = 2f * density
        p.color = Color.argb(180, 255, 255, 255)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 9f * density
        for (k in 0..10) {
            val a = a0 + sweep * k / 10f
            val r1 = R - 13f * density
            val r2 = R - (if (k % 2 == 1) 17f else 21f) * density
            canvas.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, p)
            if (k % 2 == 0) {
                p.color = Color.argb(190, 255, 255, 255)
                canvas.drawText("${k * 30}", cx + cos(a) * (R - 31f * density), cy + sin(a) * (R - 31f * density) + 3f * density, p)
                p.color = Color.argb(180, 255, 255, 255)
            }
        }
        // 数字
        p.typeface = numFont
        p.textSize = 30f * density
        p.color = Color.WHITE
        canvas.drawText("${hud.kmh.toInt()}", cx, cy + 2f * density, p)
        p.typeface = uiFont
        p.textSize = 9f * density
        p.color = Color.argb(255, 169, 189, 219)
        canvas.drawText("KM/H", cx, cy + 20f * density, p)
        p.style = Paint.Style.FILL
    }

    private fun deg(rad: Float): Float = Math.toDegrees(rad.toDouble()).toFloat()

    private fun drawGauge(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        if (hud.itemMode) return
        val gw = 240f * density
        val gh = 14f * density
        val x = W / 2f - gw / 2f
        val y = H - 44f * density
        // N₂O 圆点
        p.textAlign = Paint.Align.CENTER
        p.textSize = 10f * density
        p.typeface = uiFont
        p.color = Color.argb(255, 169, 189, 219)
        canvas.drawText("集气", x - 34f * density, y + gh / 2f + 4f * density, p)
        p.color = panel.color
        canvas.drawRoundRect(x, y, x + gw + 110f * density, y + gh, 7f * density, 7f * density, p)
        canvas.drawRoundRect(x, y, x + gw + 110f * density, y + gh, 7f * density, 7f * density, line)
        val fillW = gw * hud.gauge
        p.shader = if (hud.gauge >= 0.999f)
            LinearGradient(x, y, x + fillW, y, intArrayOf(Color.rgb(255, 210, 58), Color.rgb(255, 243, 168)), null, Shader.TileMode.CLAMP)
        else
            LinearGradient(x, y, x + gw, y, intArrayOf(Color.rgb(39, 199, 255), Color.rgb(122, 240, 255), Color.WHITE), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(x + 2f * density, y + 2f * density, x + 2f * density + fillW, y + gh - 2f * density, 5f * density, 5f * density, p)
        p.shader = null
        for (k in 0 until 2) {
            val cx = x + gw + 40f * density + k * 36f * density
            val cy = y + gh / 2f
            val on = hud.nitro > k
            if (on) {
                p.shader = RadialGradient(cx, cy, 16f * density, intArrayOf(Color.rgb(39, 199, 255), Color.rgb(29, 111, 242)), null, Shader.TileMode.CLAMP)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * density
            p.color = if (on) Color.WHITE else Color.argb(90, 255, 255, 255)
            canvas.drawCircle(cx, cy, 13f * density, p)
            p.style = Paint.Style.FILL
            if (on) { canvas.drawCircle(cx, cy, 12f * density, p); p.shader = null }
            p.color = if (on) Color.WHITE else Color.argb(90, 255, 255, 255)
            p.textSize = 9f * density
            canvas.drawText("N₂O", cx, cy + 3f * density, p)
        }
    }

    private fun drawMessages(canvas: Canvas) {
        for (m in game.pollMessages()) {
            if (m.first.startsWith("\u0000CD\u0000")) {
                countdownText = m.first.removePrefix("\u0000CD\u0000")
                countdownT = System.currentTimeMillis()
            } else {
                msgs.add(Msg(m.first, m.second, m.third, System.currentTimeMillis()))
                if (msgs.size > 3) msgs.removeAt(0)
            }
        }
        val now = System.currentTimeMillis()
        for (i in msgs.indices.reversed()) {
            val m = msgs[i]
            val t = (now - m.t0) / 1150f
            if (t > 1f) { msgs.removeAt(i); continue }
            var scale = 1f
            var alpha = 1f
            when {
                t < 0.12f -> { val k = t / 0.12f; scale = 2.2f - 1.2f * k; alpha = k }
                t < 0.2f -> { val k = (t - 0.12f) / 0.08f; scale = 0.95f + 0.05f * k }
                t > 0.8f -> { alpha = 1f - (t - 0.8f) / 0.2f }
            }
            p.textAlign = Paint.Align.CENTER
            p.typeface = numFont
            p.textSize = (if (m.small) 26f else 40f) * density * scale
            p.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            val y = H * 0.30f + i * 44f * density - (if (t > 0.8f) (t - 0.8f) * 60f * density else 0f)
            p.color = m.color
            canvas.drawText(m.text, W / 2f, y, p)
            p.alpha = 255
        }
    }

    private fun drawCountdown(canvas: Canvas) {
        if (countdownText.isEmpty()) return
        val t = (System.currentTimeMillis() - countdownT) / 900f
        if (t > 1f) { countdownText = ""; return }
        var scale = 1f
        var alpha = 1f
        when {
            t < 0.25f -> { val k = t / 0.25f; scale = 2.6f - 1.6f * k; alpha = k }
            t > 0.8f -> { val k = (t - 0.8f) / 0.2f; scale = 1f - 0.3f * k; alpha = 1f - k }
        }
        p.textAlign = Paint.Align.CENTER
        p.typeface = numFont
        p.textSize = 150f * density * scale
        p.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        p.color = Color.WHITE
        canvas.drawText(countdownText, W / 2f, H * 0.48f, p)
        p.alpha = 255
    }

    private fun drawWarn(canvas: Canvas, text: String) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = uiFont
        p.textSize = 24f * density
        p.alpha = ((if ((System.currentTimeMillis() / 250) % 2 == 0L) 1f else 0.35f) * 255).toInt()
        p.color = Color.rgb(255, 75, 75)
        canvas.drawText(text, W / 2f, H * 0.22f, p)
        p.alpha = 255
    }

    private fun drawFinalCount(canvas: Canvas, text: String) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = uiFont
        p.textSize = 18f * density
        p.color = Color.rgb(255, 210, 58)
        canvas.drawText(text, W / 2f, 100f * density, p)
    }

    private fun drawButtons(canvas: Canvas, hud: com.feiche.qqspeed.game.HudData) {
        p.textAlign = Paint.Align.CENTER
        for (b in btns) {
            if (b.id == "swap" && !hud.itemMode) continue
            p.color = if (b.on) Color.argb(128, 39, 199, 255) else Color.argb(50, 255, 255, 255)
            canvas.drawCircle(b.x, b.y, b.r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * density
            p.color = Color.argb(90, 255, 255, 255)
            canvas.drawCircle(b.x, b.y, b.r, p)
            p.style = Paint.Style.FILL
            p.typeface = uiFont
            p.textSize = (if (b.id == "drift") 15f else 14f) * density
            p.color = Color.WHITE
            canvas.drawText(b.label, b.x, b.y + 5f * density, p)
        }
    }

    private var pauseRect = RectF()
    private fun drawPauseBtn(canvas: Canvas) {
        val size = 36f * density
        val x = W - size - 16f * density
        val y = H / 2f - size / 2f
        pauseRect.set(x, y, x + size, y + size)
        p.color = Color.argb(120, 10, 20, 48)
        canvas.drawRoundRect(pauseRect, 8f * density, 8f * density, p)
        p.color = Color.WHITE
        canvas.drawRect(x + size * 0.3f, y + size * 0.25f, x + size * 0.42f, y + size * 0.75f, p)
        canvas.drawRect(x + size * 0.58f, y + size * 0.25f, x + size * 0.7f, y + size * 0.75f, p)
    }

    fun pauseHit(x: Float, y: Float): Boolean =
        (game.state == "race" || game.state == "countdown") && pauseRect.contains(x, y)

    private val numFont: Typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
    private val uiFont: Typeface = Typeface.DEFAULT_BOLD

    override fun performClick(): Boolean { return super.performClick() }
}
