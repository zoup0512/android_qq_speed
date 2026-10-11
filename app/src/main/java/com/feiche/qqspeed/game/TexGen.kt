package com.feiche.qqspeed.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 程序化纹理生成（移植 textures.js，Canvas2D → android.graphics）
object TexGen {

    private fun mk(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private class G(val bmp: Bitmap) {
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: Int) { p.shader = null; p.color = color; c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), p) }
        fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
            p.shader = null; p.color = color; c.drawRect(x, y, x + w, y + h, p)
        }
        fun fillRect(x: Float, y: Float, w: Float, h: Float, shader: Shader) { p.color = Color.WHITE; p.shader = shader; c.drawRect(x, y, x + w, y + h, p) }
    }

    private fun rgba(r: Int, g: Int, b: Int, a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, b)
    private fun white(a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
    private fun black(a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)

    private fun rectNoise(g: G, w: Int, h: Int, base: Int, amount: Float, rnd: Rnd, size: Float, count: Int) {
        g.fill(base)
        val n = if (count != 0) count else (w * h / 6)
        for (i in 0 until n) {
            val v = (rnd.next() - 0.5f) * amount
            g.p.shader = null
            g.p.color = if (v > 0) white(v) else black(-v)
            val s = size * (0.5f + rnd.next())
            val x = rnd.next() * w; val y = rnd.next() * h
            g.c.drawRect(x, y, x + s, y + s, g.p)
        }
    }

    // ---------- 路面（u 横跨路宽，v 一张 24 米）----------
    fun roadTexture(style: String): Bitmap {
        val W = 512; val H = 1024
        val g = G(mk(W, H))
        val rnd = Rnd(7)
        when (style) {
            "asphalt" -> {
                rectNoise(g, W, H, Color.rgb(0x47, 0x4b, 0x55), 0.16f, rnd, 2f, 0)
                for (x in listOf(0.3f, 0.7f)) {
                    val grd = LinearGradient(W * x - 40, 0f, W * x + 40, 0f,
                        intArrayOf(black(0f), black(0.12f), black(0f)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    g.fillRect(W * x - 40, 0f, 80f, H.toFloat(), grd)
                }
                g.fillRect(14f, 0f, 8f, H.toFloat(), Color.rgb(0xf2, 0xf2, 0xf2))
                g.fillRect((W - 22).toFloat(), 0f, 8f, H.toFloat(), Color.rgb(0xf2, 0xf2, 0xf2))
                g.fillRect(W / 2f - 5, 0f, 4f, H.toFloat(), Color.rgb(0xe8, 0xc3, 0x3a))
                g.fillRect(W / 2f + 1, 0f, 4f, H.toFloat(), Color.rgb(0xe8, 0xc3, 0x3a))
                g.p.shader = null
                for (y in 0 until H step 256) {
                    g.fillRect(W * 0.25f - 3, y.toFloat(), 6f, 128f, white(0.85f))
                    g.fillRect(W * 0.75f - 3, y.toFloat(), 6f, 128f, white(0.85f))
                }
                g.fillRect(0f, 0f, 8f, H.toFloat(), Color.rgb(0x2a, 0xa8, 0xff))
                g.fillRect((W - 8).toFloat(), 0f, 8f, H.toFloat(), Color.rgb(0x2a, 0xa8, 0xff))
            }
            "flagstone" -> {
                g.fill(Color.rgb(0x9d, 0x95, 0x87))
                val cw = 64f; val ch = 56f
                var y = -ch
                var row = 0
                while (y < H + ch) {
                    val off = (row and 1) * cw * 0.5f
                    var x = -cw
                    while (x < W + cw) {
                        val x0 = x + off + 3; val y0 = y + 3
                        val x1 = x + off + cw - 3; val y1 = y + ch - 3
                        val l = 168 + rnd.next() * 32
                        g.p.shader = null
                        g.p.color = Color.rgb(l.toInt(), (l - 10 - rnd.next() * 8).toInt(), (l - 26 - rnd.next() * 10).toInt())
                        val path = android.graphics.Path()
                        path.moveTo(x0 + j(), y0 + j() * 0.5f)
                        path.lineTo(x1 + j(), y0 + j() * 0.5f)
                        path.lineTo(x1 + j() * 0.5f, y1 + j())
                        path.lineTo(x0 + j() * 0.5f, y1 + j())
                        path.close()
                        g.c.drawPath(path, g.p)
                        g.fillRect(x0 + 6, y0 + 4, cw * 0.4f, 3f, white(0.08f))
                        x += cw
                    }
                    y += ch; row++
                }
                for (i in 0 until 4000) g.c.drawRect(rnd.next() * W, rnd.next() * H, rnd.next() * W + 2, rnd.next() * H + 2, g.p.also { it.shader = null; it.color = black(rnd.next() * 0.08f) })
                g.fillRect(0f, 0f, 14f, H.toFloat(), Color.rgb(0x2f, 0x7f, 0xd8))
                g.fillRect((W - 14).toFloat(), 0f, 14f, H.toFloat(), Color.rgb(0x2f, 0x7f, 0xd8))
                g.fillRect(14f, 0f, 6f, H.toFloat(), Color.rgb(0xf5, 0xf3, 0xee))
                g.fillRect((W - 20).toFloat(), 0f, 6f, H.toFloat(), Color.rgb(0xf5, 0xf3, 0xee))
            }
            "sandstone" -> {
                g.fill(Color.rgb(0xc9, 0xa3, 0x6a))
                val bw = 128f; val bh = 96f
                var y = 0
                while (y < H) {
                    val off = ((y / bh.toInt()) and 1) * bw * 0.5f
                    var x = -bw
                    while (x < W + bw) {
                        val l = rnd.next() * 22
                        g.p.shader = null
                        g.p.color = Color.rgb((222 - l).toInt(), (188 - l).toInt(), (128 - l).toInt())
                        g.c.drawRect(x + off + 2, y + 2f, x + off + bw - 2, y + bh - 2, g.p)
                        g.p.color = rgba(120, 80, 40, 0.25f)
                        g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 2f
                        var cx = x + off + rnd.next() * bw; var cy = y + rnd.next() * bh
                        val path = android.graphics.Path(); path.moveTo(cx, cy)
                        for (k in 0 until 4) { cx += (rnd.next() - 0.5f) * 30; cy += (rnd.next() - 0.5f) * 30; path.lineTo(cx, cy) }
                        g.c.drawPath(path, g.p)
                        g.p.style = Paint.Style.FILL
                        x += bw
                    }
                    y += bh.toInt()
                }
                for (i in 0 until 6000) {
                    g.p.shader = null
                    g.p.color = if (rnd.next() > 0.5f) rgba(255, 240, 200, rnd.next() * 0.12f) else rgba(90, 60, 30, rnd.next() * 0.12f)
                    g.c.drawRect(rnd.next() * W, rnd.next() * H, rnd.next() * W + 2, rnd.next() * H + 2, g.p)
                }
                g.fillRect(0f, 0f, 18f, H.toFloat(), Color.rgb(0x5b, 0x3f, 0x9e))
                g.fillRect((W - 18).toFloat(), 0f, 18f, H.toFloat(), Color.rgb(0x5b, 0x3f, 0x9e))
                g.fillRect(18f, 0f, 5f, H.toFloat(), Color.rgb(0xf3, 0xe3, 0xb5))
                g.fillRect((W - 23).toFloat(), 0f, 5f, H.toFloat(), Color.rgb(0xf3, 0xe3, 0xb5))
            }
            "plate" -> {
                g.fill(Color.rgb(0x3c, 0x4a, 0x66))
                var y = 0
                while (y < H) {
                    var x = 0
                    while (x < W) {
                        val ox = ((y / 24) and 1) * 12
                        g.c.save()
                        g.c.rotate(45f, x + ox + 0f, y + 0f)
                        g.fillRect(x + ox - 6f, y - 1.5f, 12f, 3f, rgba(160, 190, 230, 0.18f))
                        g.c.restore()
                        x += 24
                    }
                    y += 24
                }
                g.p.shader = null; g.p.color = rgba(20, 28, 45, 0.8f); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 3f
                y = 0
                while (y <= H) { g.c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), g.p); y += 256 }
                for (x in listOf(W / 3f, 2f * W / 3f)) g.c.drawLine(x, 0f, x, H.toFloat(), g.p)
                g.p.style = Paint.Style.FILL
                for (i in 0 until 3000) {
                    g.p.color = white(rnd.next() * 0.08f)
                    g.c.drawRect(rnd.next() * W, rnd.next() * H, rnd.next() * W + 2, rnd.next() * H + 2, g.p)
                }
                val grd = LinearGradient(0f, 0f, 40f, 0f, intArrayOf(rgba(240, 248, 255, 0.95f), rgba(240, 248, 255, 0f)), null, Shader.TileMode.CLAMP)
                g.fillRect(0f, 0f, 40f, H.toFloat(), grd)
                g.c.save()
                g.c.scale(-1f, 1f, W / 2f, H / 2f)
                g.fillRect(0f, 0f, 40f, H.toFloat(), grd)
                g.c.restore()
                g.fillRect(22f, 0f, 6f, H.toFloat(), Color.rgb(0x39, 0xc5, 0xff))
                g.fillRect((W - 28).toFloat(), 0f, 6f, H.toFloat(), Color.rgb(0x39, 0xc5, 0xff))
            }
        }
        return g.bmp
    }

    private fun j(): Float = (Math.random().toFloat() - 0.5f) * 14f

    fun curbTexture(a: Int, b: Int): Bitmap {
        val g = G(mk(64, 256))
        for (i in 0 until 4) {
            g.fillRect(0f, (i * 64).toFloat(), 64f, 64f, if (i % 2 == 1) b else a)
        }
        g.fillRect(0f, 0f, 6f, 256f, black(0.15f))
        return g.bmp
    }

    fun checkerTexture(a: Int, b: Int, nx: Int = 8, ny: Int = 2): Bitmap {
        val g = G(mk(nx * 32, ny * 32))
        for (y in 0 until ny) for (x in 0 until nx) {
            g.fillRect((x * 32).toFloat(), (y * 32).toFloat(), 32f, 32f, if ((x + y) % 2 == 1) a else b)
        }
        return g.bmp
    }

    // 护栏（一张 16 米）
    fun wallTexture(style: String): Bitmap {
        val W = 1024; val H = 128
        val g = G(mk(W, H))
        val rnd = Rnd(3)
        when (style) {
            "city" -> {
                val cols = listOf(Color.rgb(0x15, 0x65, 0xc0), Color.rgb(0xfa, 0xfa, 0xfa), Color.rgb(0xe5, 0x39, 0x35), Color.rgb(0xfa, 0xfa, 0xfa))
                for (i in 0 until 4) g.fillRect((i * 256).toFloat(), 0f, 256f, H.toFloat(), cols[i])
                val txt = listOf("SPEED", "SPEEDQQ.COM", "NITRO", "SPEEDQQ.COM")
                val tc = listOf(white(1f), Color.rgb(0x15, 0x65, 0xc0), white(1f), Color.rgb(0xe5, 0x39, 0x35))
                for (i in 0 until 4) {
                    g.p.shader = null; g.p.color = tc[i]
                    g.p.textAlign = Paint.Align.CENTER
                    if (i % 2 == 1) { g.p.textSize = 34f; g.p.typeface = TypefaceHelper.bold() }
                    else { g.p.textSize = 60f; g.p.typeface = TypefaceHelper.blackItalic() }
                    g.c.drawText(txt[i], i * 256f + 128f, H / 2f + 2f, g.p)
                }
                g.fillRect(0f, (H - 10).toFloat(), W.toFloat(), 10f, black(0.35f))
                g.fillRect(0f, 0f, W.toFloat(), 5f, white(0.5f))
            }
            "aegean" -> {
                g.fill(Color.rgb(0xf4, 0xf1, 0xea))
                for (i in 0 until 3000) {
                    g.p.shader = null; g.p.color = rgba(150, 140, 120, rnd.next() * 0.1f)
                    g.c.drawRect(rnd.next() * W, rnd.next() * H, rnd.next() * W + 3, rnd.next() * H + 3, g.p)
                }
                g.fillRect(0f, 0f, W.toFloat(), 18f, Color.rgb(0x2d, 0x6f, 0xc4))
                g.p.color = Color.rgb(0x3a, 0x82, 0xd6); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 5f
                var x = 0
                while (x < W) {
                    g.c.drawRect(x + 8f, 30f, x + 56f, 90f, g.p)
                    g.c.drawLine(x + 8f, 30f, x + 56f, 90f, g.p)
                    g.c.drawLine(x + 56f, 30f, x + 8f, 90f, g.p)
                    x += 64
                }
                g.p.style = Paint.Style.FILL
                g.fillRect(0f, (H - 22).toFloat(), W.toFloat(), 22f, Color.rgb(0xd8, 0xd2, 0xc4))
            }
            "egypt" -> {
                g.fill(Color.rgb(0xd2, 0xad, 0x6f))
                g.p.shader = null; g.p.color = rgba(110, 70, 30, 0.45f); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 2f
                var y = 20
                while (y < H) {
                    var x = 0
                    while (x < W) {
                        val o = if (((y / 36) % 2) == 1) 48f else 0f
                        g.c.drawRect(x + o, y.toFloat(), x + o + 96, (y + 36).toFloat(), g.p)
                        x += 96
                    }
                    y += 36
                }
                g.p.style = Paint.Style.FILL
                g.fillRect(0f, 0f, W.toFloat(), 20f, Color.rgb(0x4f, 0x3a, 0x96))
                g.fillRect(0f, 20f, W.toFloat(), 4f, Color.rgb(0xe8, 0xc5, 0x47))
                g.p.color = rgba(80, 45, 20, 0.55f)
                var x = 30
                while (x < W) {
                    g.c.drawCircle(x.toFloat(), 70f, 12f, g.p)
                    g.fillRect((x + 22).toFloat(), 56f, 6f, 30f, g.p.color)
                    val path = android.graphics.Path()
                    path.moveTo((x + 40).toFloat(), 88f); path.lineTo((x + 52).toFloat(), 56f); path.lineTo((x + 64).toFloat(), 88f); path.close()
                    g.c.drawPath(path, g.p)
                    g.fillRect((x + 72).toFloat(), 66f, 22f, 5f, g.p.color)
                    x += 120
                }
            }
            "snow" -> {
                val cols = listOf(Color.rgb(0xc6, 0x28, 0x28), Color.rgb(0x1e, 0x4f, 0xb0))
                val txt = listOf("SPEED", "Victory", "SPEED", "Victory")
                for (i in 0 until 4) {
                    val grd = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(cols[i % 2], Color.rgb(0x0b, 0x16, 0x36)), null, Shader.TileMode.CLAMP)
                    g.fillRect((i * 256).toFloat(), 0f, 256f, H.toFloat(), grd)
                    g.p.shader = null; g.p.color = white(0.9f)
                    g.p.textSize = 54f; g.p.typeface = TypefaceHelper.blackItalic()
                    g.p.textAlign = Paint.Align.CENTER
                    g.c.drawText(txt[i], i * 256f + 128f, H / 2f + 6f, g.p)
                }
                g.fillRect(0f, 0f, W.toFloat(), 14f, Color.rgb(0xf5, 0xfb, 0xff))
            }
        }
        return g.bmp
    }

    fun groundTexture(type: String): Bitmap {
        val S = 512
        val g = G(mk(S, S))
        val rnd = Rnd(11)
        when (type) {
            "grass" -> {
                rectNoise(g, S, S, Color.rgb(0x5f, 0xae, 0x4a), 0.18f, rnd, 3f, 0)
                for (i in 0 until 9000) {
                    g.p.shader = null
                    g.p.color = if (rnd.next() > 0.5f) rgba(120, 190, 80, 0.35f) else rgba(40, 110, 40, 0.3f)
                    val x = rnd.next() * S; val y = rnd.next() * S
                    g.c.drawRect(x, y, x + 1.5f, y + 4f, g.p)
                }
            }
            "sand" -> {
                rectNoise(g, S, S, Color.rgb(0xe2, 0xbf, 0x82), 0.12f, rnd, 3f, 0)
                g.p.shader = null; g.p.color = rgba(170, 120, 60, 0.18f); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 3f
                for (i in 0 until 26) {
                    val path = android.graphics.Path()
                    val y0 = rnd.next() * S
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= S) { path.lineTo(x, y0 + sin(x * 0.03 + i).toFloat() * 8f); x += 32f }
                    g.c.drawPath(path, g.p)
                }
                g.p.style = Paint.Style.FILL
            }
            "snow" -> {
                rectNoise(g, S, S, Color.rgb(0xdd, 0xe8, 0xf4), 0.1f, rnd, 4f, 0)
                for (i in 0 until 800) {
                    g.p.shader = null; g.p.color = rgba(170, 200, 235, rnd.next() * 0.25f)
                    g.c.drawCircle(rnd.next() * S, rnd.next() * S, 2 + rnd.next() * 10, g.p)
                }
            }
            "aegean" -> {
                rectNoise(g, S, S, Color.rgb(0xb7, 0xb2, 0x7e), 0.2f, rnd, 3f, 0)
                for (i in 0 until 400) {
                    g.p.shader = null
                    g.p.color = if (rnd.next() > 0.5f) rgba(120, 150, 70, 0.3f + rnd.next() * 0.3f) else rgba(200, 190, 160, 0.3f + rnd.next() * 0.3f)
                    g.c.drawCircle(rnd.next() * S, rnd.next() * S, 2 + rnd.next() * 7, g.p)
                }
            }
            "plaza" -> {
                g.fill(Color.rgb(0xc9, 0xc4, 0xba))
                var y = 0
                while (y < S) {
                    var x = 0
                    while (x < S) {
                        val l = 190 + rnd.next() * 30
                        g.p.shader = null
                        g.p.color = Color.rgb(l.toInt(), (l - 4).toInt(), (l - 12).toInt())
                        g.c.drawRect(x + 1f, y + 1f, x + 31f, y + 31f, g.p)
                        x += 32
                    }
                    y += 32
                }
            }
        }
        return g.bmp
    }

    // 建筑立面
    fun facadeTexture(kind: String): Bitmap {
        val W = 256; val H = 512
        val g = G(mk(W, H))
        val rnd = Rnd(kind.length * 17 + 5)
        when (kind) {
            "glass" -> {
                val grd = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                    intArrayOf(Color.rgb(0x6f, 0xb6, 0xec), Color.rgb(0x2d, 0x6a, 0xa8)), null, Shader.TileMode.CLAMP)
                g.fillRect(0f, 0f, W.toFloat(), H.toFloat(), grd)
                g.p.shader = null
                var y = 0
                while (y < H) { g.fillRect(0f, y.toFloat(), W.toFloat(), 2f, white(0.25f)); y += 24 }
                var x = 0
                while (x < W) { g.fillRect(x.toFloat(), 0f, 3f, H.toFloat(), rgba(20, 50, 90, 0.5f)); x += 32 }
                for (i in 0 until 40) {
                    g.p.color = white(rnd.next() * 0.25f)
                    val wx = (rnd.next() * 8).toInt() * 32 + 3; val wy = (rnd.next() * 21).toInt() * 24 + 2
                    g.c.drawRect(wx.toFloat(), wy.toFloat(), (wx + 29).toFloat(), (wy + 22).toFloat(), g.p)
                }
            }
            "office" -> {
                g.fill(Color.rgb(0xe9, 0xe3, 0xd6))
                var y = 10
                while (y < H) {
                    var x = 10
                    while (x < W) {
                        g.p.shader = null
                        g.p.color = when { rnd.next() > 0.85f -> Color.rgb(0xff, 0xf4, 0xc2); rnd.next() > 0.5f -> Color.rgb(0x5b, 0x87, 0xb8); else -> Color.rgb(0x47, 0x6f, 0x9c) }
                        g.c.drawRect(x.toFloat(), y.toFloat(), (x + 20).toFloat(), (y + 20).toFloat(), g.p)
                        x += 30
                    }
                    y += 32
                }
            }
            "modern" -> {
                g.fill(Color.rgb(0xd7, 0xdd, 0xe6))
                var y = 0
                while (y < H) {
                    g.fillRect(0f, (y + 8).toFloat(), W.toFloat(), 26f, Color.rgb(0x3f, 0x6f, 0xa3))
                    g.fillRect(0f, (y + 10).toFloat(), W.toFloat(), 4f, white(0.3f))
                    y += 40
                }
            }
            "aegean" -> {
                g.fill(Color.rgb(0xf7, 0xf5, 0xf0))
                for (i in 0 until 2000) {
                    g.p.shader = null; g.p.color = rgba(160, 150, 130, rnd.next() * 0.08f)
                    g.c.drawRect(rnd.next() * W, rnd.next() * H, rnd.next() * W + 3, rnd.next() * H + 3, g.p)
                }
                var y = 40
                while (y < H) {
                    var x = 30
                    while (x < W) {
                        g.p.shader = null; g.p.color = Color.rgb(0x24, 0x66, 0xb8)
                        val path = android.graphics.Path()
                        path.moveTo(x.toFloat(), (y + 70).toFloat())
                        path.lineTo(x.toFloat(), (y + 20).toFloat())
                        path.arcTo(android.graphics.RectF(x.toFloat(), (y - 5).toFloat(), (x + 50).toFloat(), (y + 45).toFloat()), 180f, 180f)
                        path.lineTo((x + 50).toFloat(), (y + 70).toFloat())
                        path.close()
                        g.c.drawPath(path, g.p)
                        g.fillRect((x + 8).toFloat(), (y + 22).toFloat(), 34f, 40f, Color.rgb(0x9e, 0xc8, 0xf0))
                        g.fillRect((x + 23).toFloat(), (y + 18).toFloat(), 4f, 46f, Color.rgb(0x24, 0x66, 0xb8))
                        x += 110
                    }
                    y += 128
                }
            }
            "egypt" -> {
                g.fill(Color.rgb(0xd9, 0xb7, 0x7a))
                g.p.shader = null; g.p.color = rgba(120, 80, 40, 0.4f); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 2f
                var y = 0
                while (y < H) {
                    var x = 0
                    while (x < W) {
                        val o = (((y / 32) % 2) * 32).toFloat()
                        g.c.drawRect(x + o, y.toFloat(), x + o + 64, (y + 32).toFloat(), g.p)
                        x += 64
                    }
                    y += 32
                }
                g.p.style = Paint.Style.FILL
            }
            "lodge" -> {
                g.fill(Color.rgb(0x7a, 0x4a, 0x2a))
                var y = 0
                while (y < H) {
                    g.fillRect(0f, y.toFloat(), W.toFloat(), 18f, if (y % 40 != 0) Color.rgb(0x8b, 0x5a, 0x36) else Color.rgb(0x6d, 0x3f, 0x22))
                    y += 20
                }
                y = 60
                while (y < H) {
                    var x = 40
                    while (x < W) {
                        g.p.shader = null
                        g.fillRect(x.toFloat(), y.toFloat(), 44f, 50f, Color.rgb(0xff, 0xd3, 0x6b))
                        g.p.color = Color.rgb(0x3a, 0x22, 0x10); g.p.strokeWidth = 5f; g.p.style = Paint.Style.STROKE
                        g.c.drawRect(x.toFloat(), y.toFloat(), (x + 44).toFloat(), (y + 50).toFloat(), g.p)
                        g.p.style = Paint.Style.FILL
                        x += 110
                    }
                    y += 140
                }
            }
        }
        return g.bmp
    }

    fun billboardTexture(title: String, sub: String?, c1: Int, c2: Int, fg: Int = Color.WHITE): Bitmap {
        val g = G(mk(512, 256))
        val grd = LinearGradient(0f, 0f, 512f, 256f, intArrayOf(c1, c2), null, Shader.TileMode.CLAMP)
        g.fillRect(0f, 0f, 512f, 256f, grd)
        g.p.shader = null
        for (i in 0 until 6) g.fillRect((-40 + i * 110).toFloat(), 0f, 40f, 256f, white(0.15f))
        g.p.color = white(0.9f); g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 10f
        g.c.drawRect(5f, 5f, 507f, 251f, g.p)
        g.p.style = Paint.Style.FILL
        g.p.color = fg
        g.p.textAlign = Paint.Align.CENTER
        g.p.textSize = 92f; g.p.typeface = TypefaceHelper.blackItalic()
        g.c.drawText(title, 256f, if (sub != null) 108f else 128f, g.p)
        if (sub != null) {
            g.p.textSize = 40f; g.p.typeface = TypefaceHelper.bold()
            g.c.drawText(sub, 256f, 196f, g.p)
        }
        return g.bmp
    }

    fun chevronTexture(bg: Int, fg: Int): Bitmap {
        val g = G(mk(256, 128))
        g.fill(bg)
        g.p.shader = null; g.p.color = fg
        for (i in 0 until 4) {
            val x = 20 + i * 60
            val path = android.graphics.Path()
            path.moveTo(x.toFloat(), 16f); path.lineTo((x + 26).toFloat(), 16f); path.lineTo((x + 56).toFloat(), 64f)
            path.lineTo((x + 26).toFloat(), 112f); path.lineTo(x.toFloat(), 112f); path.lineTo((x + 30).toFloat(), 64f)
            path.close()
            g.c.drawPath(path, g.p)
        }
        return g.bmp
    }

    // 加速带箭头（半透明背景 + 发光箭头）
    fun boostPadTexture(color: Int): Bitmap {
        val g = G(mk(256, 512))
        g.fillRect(0f, 0f, 256f, 512f, rgba(10, 30, 60, 0.55f))
        val cr = Color.red(color); val cg = Color.green(color); val cb = Color.blue(color)
        // 光晕（用多层描边模拟 shadowBlur）
        for (pass in 0 until 3) {
            g.p.shader = null
            g.p.color = rgba(cr, cg, cb, if (pass == 0) 0.10f else if (pass == 1) 0.18f else 0.3f)
            g.p.strokeWidth = 18f + pass * 10f
            g.p.style = Paint.Style.STROKE
            for (i in 0 until 4) {
                val y = 480f - i * 120f
                val path = android.graphics.Path()
                path.moveTo(28f, y); path.lineTo(128f, y - 80f); path.lineTo(228f, y)
                path.lineTo(228f, y - 36f); path.lineTo(128f, y - 116f); path.lineTo(28f, y - 36f)
                path.close()
                g.c.drawPath(path, g.p)
            }
        }
        g.p.style = Paint.Style.FILL
        g.p.color = color
        for (i in 0 until 4) {
            val y = 480f - i * 120f
            val path = android.graphics.Path()
            path.moveTo(28f, y); path.lineTo(128f, y - 80f); path.lineTo(228f, y)
            path.lineTo(228f, y - 36f); path.lineTo(128f, y - 116f); path.lineTo(28f, y - 36f)
            path.close()
            g.c.drawPath(path, g.p)
        }
        g.p.strokeWidth = 8f; g.p.style = Paint.Style.STROKE
        g.c.drawRect(4f, 4f, 252f, 508f, g.p)
        g.p.style = Paint.Style.FILL
        return g.bmp
    }

    fun softDotTexture(): Bitmap {
        val g = G(mk(64, 64))
        val grd = RadialGradient(32f, 32f, 32f,
            intArrayOf(white(1f), white(0.7f), white(0f)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        g.fillRect(0f, 0f, 64f, 64f, grd)
        return g.bmp
    }

    fun cloudTexture(): Bitmap {
        val g = G(mk(256, 128))
        val rnd = Rnd(99)
        for (i in 0 until 26) {
            val x = 40 + rnd.next() * 176; val y = 50 + rnd.next() * 40; val r = 18 + rnd.next() * 30
            val grd = RadialGradient(x, y, r,
                intArrayOf(white(0.95f), white(0.6f), white(0f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            g.fillRect(x - r, y - r, r * 2, r * 2, grd)
        }
        return g.bmp
    }

    fun textTexture(text: String, w: Int = 512, h: Int = 128, bg: Int? = null, fg: Int = Color.WHITE,
                    sizePx: Float = 80f, italic: Boolean = true, bold: Boolean = true, stroke: Int? = null): Bitmap {
        val g = G(mk(w, h))
        if (bg != null) g.fill(bg)
        g.p.shader = null
        g.p.color = fg
        g.p.textSize = sizePx
        g.p.typeface = if (italic) TypefaceHelper.blackItalic() else if (bold) TypefaceHelper.bold() else Typeface.DEFAULT
        g.p.textAlign = Paint.Align.CENTER
        if (stroke != null) {
            g.p.style = Paint.Style.STROKE
            g.p.color = stroke
            g.p.strokeWidth = 10f
            g.c.drawText(text, w / 2f, h / 2f, g.p)
            g.p.style = Paint.Style.FILL
            g.p.color = fg
        }
        g.c.drawText(text, w / 2f, h / 2f, g.p)
        return g.bmp
    }

    fun rockTexture(base: Int): Bitmap {
        val S = 256
        val g = G(mk(S, S))
        rectNoise(g, S, S, base, 0.25f, Rnd(5), 4f, 0)
        val rnd = Rnd(6)
        var y = 0
        while (y < S) {
            g.p.shader = null; g.p.color = black(0.05f + rnd.next() * 0.1f)
            g.c.drawRect(0f, y.toFloat(), S.toFloat(), y + 3f + rnd.next() * 4f, g.p)
            y += 14
        }
        return g.bmp
    }

    fun tunnelTexture(): Bitmap {
        val g = G(mk(256, 256))
        g.fill(Color.rgb(0x3b, 0x3f, 0x4a))
        var y = 0
        while (y < 256) {
            g.fillRect(0f, y.toFloat(), 256f, 2f, Color.rgb(0x34, 0x38, 0x42))
            y += 32
        }
        // 顶灯 + 光晕
        g.p.shader = null
        for (pass in 0 until 3) {
            g.p.color = when (pass) { 0 -> rgba(255, 242, 168, 0.10f); 1 -> rgba(255, 242, 168, 0.2f); else -> Color.rgb(0xff, 0xf7, 0xd0) }
            g.c.drawRect((118 - (2 - pass) * 6).toFloat(), (20 - (2 - pass) * 4).toFloat(), (138 + (2 - pass) * 6).toFloat(), (110 + (2 - pass) * 4).toFloat(), g.p)
        }
        g.fillRect(0f, 230f, 256f, 8f, Color.rgb(0x2a, 0xa8, 0xff))
        return g.bmp
    }

    fun crowdTexture(): Bitmap {
        val g = G(mk(512, 256))
        g.fill(Color.rgb(0x3b, 0x3f, 0x52))
        val cols = listOf(0xff5252, 0xffd740, 0x40c4ff, 0x69f0ae, 0xffffff, 0xff80ab, 0xb388ff, 0xffab40).map { it.toInt() }
        val rnd = Rnd(42)
        var y = 8
        while (y < 256) {
            var x = 4
            while (x < 512) {
                g.p.shader = null
                g.p.color = cols[rnd.int(cols.size)]
                g.c.drawRect(x + rnd.next() * 3, (y + 7).toFloat(), x + rnd.next() * 3 + 9, (y + 7 + 11).toFloat(), g.p)
                g.p.color = Color.rgb(0xf1, 0xc7, 0xa2)
                g.c.drawCircle(x + 5 + rnd.next() * 2, (y + 4).toFloat(), 4f, g.p)
                if (rnd.next() < 0.15f) {
                    g.p.color = cols[rnd.int(cols.size)]
                    g.c.drawRect((x + 2).toFloat(), (y - 8).toFloat(), (x + 2 + 3).toFloat(), (y - 8 + 10).toFloat(), g.p)
                }
                x += 12
            }
            y += 21
        }
        return g.bmp
    }
}

// 字体帮助
object TypefaceHelper {
    private var blackItalicPaint: android.graphics.Typeface? = null
    private var boldPaint: android.graphics.Typeface? = null
    fun blackItalic(): android.graphics.Typeface {
        if (blackItalicPaint == null) {
            val base = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            blackItalicPaint = android.graphics.Typeface.create(base, android.graphics.Typeface.ITALIC)
        }
        return blackItalicPaint!!
    }
    fun bold(): android.graphics.Typeface {
        if (boldPaint == null) boldPaint = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.NORMAL)
        return boldPaint!!
    }
}
