package com.feiche.qqspeed.game

import com.feiche.qqspeed.gl.DynMesh
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// 通用粒子池（移植 effects.js Particles）— 顶点布局 pos3+color4+size1
class Particles(private val max: Int, val additive: Boolean) {
    val pos = FloatArray(max * 3)
    val col = FloatArray(max * 4)
    val size = FloatArray(max)
    val vel = FloatArray(max * 3)
    val life = FloatArray(max)
    val maxLife = FloatArray(max)
    val s0 = FloatArray(max)
    val s1 = FloatArray(max)
    val a0 = FloatArray(max)
    val grav = FloatArray(max)
    val drag = FloatArray(max)
    val rgb = FloatArray(max * 3)
    var cursor = 0
    private var mesh: DynMesh? = null
    private var idxArr = ShortArray(max)
    private var idxDirty = true

    fun ensureMesh(): DynMesh {
        if (mesh == null) {
            mesh = DynMesh(max, 8, max)
            for (i in 0 until max) idxArr[i] = i.toShort()
        }
        return mesh!!
    }

    fun emit(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float,
             lifeT: Float, s0v: Float, s1v: Float, r: Float, g: Float, b: Float, a: Float, gravV: Float = 0f, dragV: Float = 0f) {
        val i = cursor
        cursor = (cursor + 1) % max
        pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z
        vel[i * 3] = vx; vel[i * 3 + 1] = vy; vel[i * 3 + 2] = vz
        life[i] = lifeT; maxLife[i] = lifeT
        s0[i] = s0v; s1[i] = s1v; a0[i] = a
        rgb[i * 3] = r * r; rgb[i * 3 + 1] = g * g; rgb[i * 3 + 2] = b * b // 近似 sRGB→线性
        grav[i] = gravV; drag[i] = dragV
    }

    fun update(dt: Float) {
        for (i in 0 until max) {
            if (life[i] <= 0) {
                if (size[i] != 0f) { size[i] = 0f; col[i * 4 + 3] = 0f }
                continue
            }
            life[i] -= dt
            val k = 1f - max(0f, life[i]) / maxLife[i]
            val dr = exp(-drag[i] * dt)
            vel[i * 3] *= dr
            vel[i * 3 + 1] = vel[i * 3 + 1] * dr - grav[i] * dt
            vel[i * 3 + 2] *= dr
            pos[i * 3] += vel[i * 3] * dt
            pos[i * 3 + 1] += vel[i * 3 + 1] * dt
            pos[i * 3 + 2] += vel[i * 3 + 2] * dt
            size[i] = s0[i] + (s1[i] - s0[i]) * k
            col[i * 4] = rgb[i * 3]
            col[i * 4 + 1] = rgb[i * 3 + 1]
            col[i * 4 + 2] = rgb[i * 3 + 2]
            col[i * 4 + 3] = a0[i] * (1f - k) * min(1f, k * 8f + 0.2f)
        }
    }

    // 打包并上传
    fun upload(): Pair<Int, Int> {
        val m = ensureMesh()
        val vbuf = FloatArray(max * 8)
        for (i in 0 until max) {
            var o = i * 8
            vbuf[o] = pos[i * 3]; vbuf[o + 1] = pos[i * 3 + 1]; vbuf[o + 2] = pos[i * 3 + 2]
            vbuf[o + 3] = col[i * 4]; vbuf[o + 4] = col[i * 4 + 1]; vbuf[o + 5] = col[i * 4 + 2]; vbuf[o + 6] = col[i * 4 + 3]
            vbuf[o + 7] = size[i]
        }
        if (idxDirty) { for (i in 0 until max) idxArr[i] = i.toShort(); idxDirty = false }
        m.upload(vbuf, max, idxArr, max)
        return Pair(max, max)
    }

    fun dispose() { mesh?.dispose(); mesh = null }
}

// 轮胎印：环形缓冲的带状网格 — 顶点 pos3+alpha1
class SkidMarks(private val maxQuads: Int = 2400) {
    val pos = FloatArray(maxQuads * 4 * 4)
    val idx = ShortArray(maxQuads * 6)
    private var mesh: DynMesh? = null
    var cursor = 0
    private val last = HashMap<String, FloatArray>()
    var dirty = false
    private val vbuf = FloatArray(maxQuads * 4 * 4)

    init {
        for (q in 0 until maxQuads) {
            val a = q * 4
            idx[q * 6] = a.toShort(); idx[q * 6 + 1] = (a + 1).toShort(); idx[q * 6 + 2] = (a + 2).toShort()
            idx[q * 6 + 3] = (a + 1).toShort(); idx[q * 6 + 4] = (a + 3).toShort(); idx[q * 6 + 5] = (a + 2).toShort()
        }
    }

    fun ensureMesh(): DynMesh {
        if (mesh == null) mesh = DynMesh(maxQuads * 4, 4, maxQuads * 6)
        return mesh!!
    }

    fun add(key: String, x: Float, y: Float, z: Float, rx: Float, rz: Float, w: Float, strength: Float) {
        val prev = last[key]
        val curY = y + 0.06f
        if (prev != null && (x - prev[0]) * (x - prev[0]) + (z - prev[2]) * (z - prev[2]) < 25f) {
            val q = cursor
            cursor = (cursor + 1) % maxQuads
            val o = q * 16
            val p = pos
            // v0 = prev 左
            p[o] = prev[0] - prev[3] * w; p[o + 1] = prev[1]; p[o + 2] = prev[2] - prev[4] * w; p[o + 3] = prev[5]
            // v1 = prev 右
            p[o + 4] = prev[0] + prev[3] * w; p[o + 5] = prev[1]; p[o + 6] = prev[2] + prev[4] * w; p[o + 7] = prev[5]
            // v2 = 当前左
            p[o + 8] = x - rx * w; p[o + 9] = curY; p[o + 10] = z - rz * w; p[o + 11] = strength
            // v3 = 当前右
            p[o + 12] = x + rx * w; p[o + 13] = curY; p[o + 14] = z + rz * w; p[o + 15] = strength
            dirty = true
        }
        last[key] = floatArrayOf(x, curY, z, rx, rz, strength)
    }

    fun cut(key: String) { last.remove(key) }

    fun update(): Pair<Int, Int> {
        if (!dirty) return Pair(0, 0)
        dirty = false
        val m = ensureMesh()
        m.upload(pos, maxQuads * 4, idx, maxQuads * 6)
        return Pair(maxQuads * 4, maxQuads * 6)
    }

    fun clear() {
        pos.fill(0f)
        last.clear()
        dirty = true
        update()
    }

    fun dispose() { mesh?.dispose(); mesh = null }
}
