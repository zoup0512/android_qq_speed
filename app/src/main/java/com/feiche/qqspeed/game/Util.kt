package com.feiche.qqspeed.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

object U {
    fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun damp(a: Float, b: Float, lambda: Float, dt: Float): Float = lerp(a, b, 1f - exp(-lambda * dt))
    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = clamp((x - a) / (b - a), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun wrapAngle(a: Float): Float {
        var x = a
        while (x > Math.PI) x -= (Math.PI * 2.0).toFloat()
        while (x < -Math.PI) x += (Math.PI * 2.0).toFloat()
        return x.toFloat()
    }

    fun dampAngle(a: Float, b: Float, lambda: Float, dt: Float): Float =
        a + wrapAngle(b - a) * (1f - exp(-lambda * dt))

    fun formatTime(t: Float): String {
        if (t.isNaN() || t.isInfinite() || t <= 0f) return "--:--.--"
        val m = floor(t / 60).toInt()
        val s = t - m * 60
        return String.format("%02d:%05.2f", m, s)
    }
}

// mulberry32 伪随机（移植 util.js）
class Rnd(seed: Int) {
    private var s: Int = seed
    fun next(): Float {
        s = s + 0x6d2b79f5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        val u = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return (u / 4294967296.0).toFloat()
    }
    fun range(a: Float, b: Float): Float = a + next() * (b - a)
    fun int(n: Int): Int = (next() * n).toInt().coerceAtMost(n - 1)
}

// 二维 value noise（移植 util.js makeNoise2D）
class Noise2D(seed: Int = 1) {
    private val perm = IntArray(512)
    private val grad = FloatArray(256)

    init {
        val rnd = Rnd(seed)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = (rnd.next() * (i + 1)).toInt()
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
        for (i in 0 until 256) grad[i] = rnd.next() * 2f - 1f
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    fun noise(x: Float, y: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val X = xi and 255; val Y = yi and 255
        val a = grad[perm[X + perm[Y]]]
        val b = grad[perm[X + 1 + perm[Y]]]
        val c = grad[perm[X + perm[Y + 1]]]
        val d = grad[perm[X + 1 + perm[Y + 1]]]
        val u = fade(xf); val v = fade(yf)
        return U.lerp(U.lerp(a, b, u), U.lerp(c, d, u), v)
    }

    fun fbm(x: Float, y: Float, oct: Int = 4): Float {
        var s = 0f; var amp = 0.5f; var f = 1f
        for (i in 0 until oct) {
            s += amp * noise(x * f, y * f)
            f *= 2.03f
            amp *= 0.5f
        }
        return s
    }
}

// 简易向量
class V3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): V3 { this.x = x; this.y = y; this.z = z; return this }
    fun copy(o: V3): V3 { x = o.x; y = o.y; z = o.z; return this }
    fun add(o: V3): V3 { x += o.x; y += o.y; z += o.z; return this }
    fun addScaled(o: V3, k: Float): V3 { x += o.x * k; y += o.y * k; z += o.z * k; return this }
    fun sub(o: V3): V3 { x -= o.x; y -= o.y; z -= o.z; return this }
    fun mul(k: Float): V3 { x *= k; y *= k; z *= k; return this }
    fun len(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
    fun norm(): V3 { val l = len(); if (l > 1e-8f) mul(1f / l); return this }
    fun distanceTo(o: V3): Float {
        val dx = x - o.x; val dy = y - o.y; val dz = z - o.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    fun clone(): V3 = V3(x, y, z)
}

fun absMax(a: Float, b: Float) = if (abs(a) > abs(b)) a else b
