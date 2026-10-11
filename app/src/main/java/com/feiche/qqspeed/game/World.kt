package com.feiche.qqspeed.game

import kotlin.math.*

// 地形基准与颜色（移植 main.js baseFor / tintFor）
class WorldCtx(val track: Track, val map: MapDef, val noise: Noise2D, val oases: Array<FloatArray>) {
    val cx = track.cx
    val cz = track.cz

    fun base(x: Float, z: Float): Float {
        return when (map.id) {
            "city" -> {
                val r = abs(z)
                val river = if (r < 44) -6f else if (r < 64) U.lerp(-6f, 0f, U.smoothstep(44f, 64f, r)) else 0f
                river + max(0f, sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - 950f) * 0.12f
            }
            "aegean" -> {
                val land = U.clamp((z + 195) / 420, 0f, 1f).pow(0.8f) * 16f + noise.fbm(x * 0.008f, z * 0.008f) * 5f + max(0f, sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - 900f) * 0.1f
                U.lerp(land, -14f, U.smoothstep(-200f, -262f, z))
            }
            "egypt" -> {
                var h = 2f + noise.fbm(x * 0.0045f, z * 0.0045f, 4) * 18f + max(0f, sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - 900f) * 0.1f
                for (o in oases) {
                    val d = sqrt((x - o[0]) * (x - o[0]) + (z - o[1]) * (z - o[1]))
                    if (d < o[2] + 25) h = U.lerp(-2.6f, h, U.smoothstep(o[2] - 6, o[2] + 25, d))
                }
                h
            }
            else -> 3f + noise.fbm(x * 0.004f, z * 0.004f, 4) * 26f + max(0f, sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - 650f) * 0.18f
        }
    }

    fun tint(x: Float, z: Float, h: Float): FloatArray {
        val n = noise.noise(x * 0.02f, z * 0.02f)
        return when (map.id) {
            "city" -> if (h < -0.8f) floatArrayOf(1.25f, 1.1f, 0.8f)
            else floatArrayOf(0.88f + n * 0.2f, 0.97f + n * 0.08f, 0.86f)
            "aegean" -> if (h < -0.5f) floatArrayOf(1.35f, 1.25f, 1f)
            else {
                val r = U.clamp(0.5f + n * 1.2f, 0f, 1f)
                floatArrayOf(U.lerp(0.85f, 1.2f, r), U.lerp(1f, 1.05f, r), U.lerp(0.75f, 1f, r))
            }
            "egypt" -> if (h < -1f) floatArrayOf(0.6f, 0.85f, 0.45f)
            else floatArrayOf(1f + n * 0.1f, 0.97f + n * 0.08f, 0.92f)
            else -> floatArrayOf(0.92f + n * 0.06f, 0.96f + n * 0.04f, 1f)
        }
    }
}

class GroundField {
    lateinit var h: FloatArray
    var n = 0; var seg = 0; var cell = 1f
    var x0 = 0f; var z0 = 0f

    fun heightAt(x: Float, z: Float): Float {
        val fx = U.clamp((x - x0) / cell, 0f, seg - 0.001f)
        val fz = U.clamp((z - z0) / cell, 0f, seg - 0.001f)
        val i = floor(fx).toInt(); val j = floor(fz).toInt()
        val tx = fx - i; val tz = fz - j
        val a = h[j * n + i]; val b = h[j * n + i + 1]; val c = h[(j + 1) * n + i]; val d = h[(j + 1) * n + i + 1]
        return U.lerp(U.lerp(a, b, tx), U.lerp(c, d, tx), tz)
    }
}

class World(val ctx: WorldCtx) {
    val track = ctx.track
    val map = ctx.map

    lateinit var ground: GroundField
    val clouds = ArrayList<FloatArray>() // [x,y,z,size]

    fun buildGround(uintOk: Boolean): List<MeshTemplate> {
        val size = 3200f
        val seg = if (uintOk) 320 else 250
        val n = seg + 1
        val cell = size / seg
        val cx = track.cx; val cz = track.cz
        val x0 = cx - size / 2; val z0 = cz - size / 2
        val flatR = track.halfW + (if (map.theme.shoulderWidth > 0) map.theme.shoulderWidth + 2.5f else 3.5f)
        val H = FloatArray(n * n)
        val lo = FloatArray(n * n) { -1e9f }
        val hi = FloatArray(n * n) { 1e9f }
        for (j in 0 until n) for (i in 0 until n) H[j * n + i] = ctx.base(x0 + i * cell, z0 + j * cell)
        val R = flatR + cell + 48
        val slope = 0.55f
        val flat = flatR + cell * 0.7f
        for (k in 0 until track.N) {
            val px = track.px[k]; val pz = track.pz[k]; val ty = track.py[k] - 0.6f
            val fill = !track.bridge[k]
            val i0 = max(0, floor((px - R - x0) / cell).toInt()); val i1 = min(seg, ceil((px + R - x0) / cell).toInt())
            val j0 = max(0, floor((pz - R - z0) / cell).toInt()); val j1 = min(seg, ceil((pz + R - z0) / cell).toInt())
            for (j in j0..j1) for (i in i0..i1) {
                val dx = x0 + i * cell - px; val dz = z0 + j * cell - pz
                val dist = sqrt(dx * dx + dz * dz)
                if (dist > R) continue
                val kk = max(0f, dist - flat) * slope
                val id = j * n + i
                if (ty + kk < hi[id]) hi[id] = ty + kk
                if (fill && ty - kk * 1.2f > lo[id]) lo[id] = ty - kk * 1.2f
            }
        }
        for (id in 0 until n * n) {
            var h = H[id]
            if (h > hi[id]) h = hi[id]
            if (h < lo[id]) h = min(lo[id], hi[id])
            H[id] = h
        }
        ground = GroundField()
        ground.h = H; ground.n = n; ground.seg = seg; ground.cell = cell; ground.x0 = x0; ground.z0 = z0

        // 分块生成网格（每块 ≤ 80×80 → 顶点数 < 65535）
        val chunks = ArrayList<MeshTemplate>()
        val CH = 80
        var c0 = 0
        while (c0 < seg) {
            var r0 = 0
            while (r0 < seg) {
                val i1 = min(c0 + CH, seg); val j1 = min(r0 + CH, seg)
                val w = VertWriter()
                for (j in r0..j1) {
                    for (i in c0..i1) {
                        val x = x0 + i * cell; val z = z0 + j * cell
                        val h = H[j * n + i]
                        val col = ctx.tint(x, z, h)
                        val u = x / 22f; val v = z / 22f
                        w.vert(x, h, z, 0f, 1f, 0f, u, v, col[0], col[1], col[2], 1f)
                    }
                }
                val wCount = i1 - c0 + 1
                for (j in 0 until (j1 - r0)) {
                    for (i in 0 until (i1 - c0)) {
                        val a = j * wCount + i
                        w.ib.push(a); w.ib.push(a + wCount); w.ib.push(a + 1); w.ib.push(a + 1); w.ib.push(a + wCount); w.ib.push(a + wCount + 1)
                    }
                }
                chunks.add(w.finish())
                r0 += CH
            }
            c0 += CH
        }
        return chunks
    }

    // 水面网格（裸 pos3 布局，供专用着色器使用）
    fun waterMeshRaw(size: Float): com.feiche.qqspeed.gl.GlMesh {
        val verts = floatArrayOf(
            -size / 2, 0f, size / 2,
            size / 2, 0f, size / 2,
            -size / 2, 0f, -size / 2,
            size / 2, 0f, -size / 2,
        )
        return com.feiche.qqspeed.gl.GlMesh(verts, shortArrayOf(0, 1, 2, 1, 3, 2), 6)
    }

    // 天空球（裸 pos3 布局）
    fun skyMeshRaw(): com.feiche.qqspeed.gl.GlMesh {
        val wSeg = 32; val hSeg = 16
        val verts = FloatArray((wSeg + 1) * (hSeg + 1) * 3)
        val idx = IntBuf(wSeg * hSeg * 6)
        var o = 0
        for (iy in 0..hSeg) {
            val v = iy.toFloat() / hSeg
            val phi = v * PI.toFloat()
            for (ix in 0..wSeg) {
                val u = ix.toFloat() / wSeg
                val theta = u * PI.toFloat() * 2f
                val nx = -cos(theta) * sin(phi); val ny = cos(phi); val nz = sin(theta) * sin(phi)
                verts[o] = nx * 4000f; verts[o + 1] = ny * 4000f; verts[o + 2] = nz * 4000f
                o += 3
            }
        }
        for (iy in 0 until hSeg) for (ix in 0 until wSeg) {
            val a = iy * (wSeg + 1) + ix; val b = a + wSeg + 1
            idx.push(a); idx.push(b); idx.push(a + 1); idx.push(a + 1); idx.push(b); idx.push(b + 1)
        }
        val shorts = ShortArray(idx.n)
        for (i in 0 until idx.n) shorts[i] = idx.get(i).toShort()
        return com.feiche.qqspeed.gl.GlMesh(verts, shorts, idx.n)
    }

    // 远景山脉（两层山环）
    fun mountainMeshes(rnd: Rnd, cfg: MountainCfg): List<MeshTemplate> {
        val out = ArrayList<MeshTemplate>()
        val baseC = cfg.color
        val capC = cfg.cap
        val noise = Noise2D(rnd.int(1000))
        val layers = listOf(
            floatArrayOf(cfg.r0 + cfg.r1 * 0.9f, (cfg.h0 + cfg.h1) * 1.25f, 0.85f),
            floatArrayOf(cfg.r0, cfg.h0 + cfg.h1 * 0.5f, 1f),
        )
        val M = 360; val V = 7
        val br = ((baseC shr 16) and 0xff) / 255f; val bg = ((baseC shr 8) and 0xff) / 255f; val bb = (baseC and 0xff) / 255f
        val cr = if (capC != null) ((capC shr 16) and 0xff) / 255f else 0f
        val cg = if (capC != null) ((capC shr 8) and 0xff) / 255f else 0f
        val cb = if (capC != null) (capC and 0xff) / 255f else 0f
        for (L in layers) {
            val w = VertWriter()
            val R = L[0]; val h = L[1]; val k = L[2]
            for (i in 0..M) {
                val a = i.toFloat() / M * PI.toFloat() * 2f
                val nx = cos(a) * 3f; val nz = sin(a) * 3f
                val top = h * (0.35f + 0.65f * max(0f, 0.5f + noise.fbm(nx + R * 0.001f, nz, 5) * 1.3f))
                for (j in 0..V) {
                    val t = j.toFloat() / V
                    val y = -40f + (top + 40f) * t.pow(0.9f)
                    val r = R + (1f - t) * top * 1.4f
                    val x = track.cx + cos(a) * r
                    val z = track.cz + sin(a) * r
                    var mR = br * (0.62f + 0.38f * t) * k
                    var mG = bg * (0.62f + 0.38f * t) * k
                    var mB = bb * (0.62f + 0.38f * t) * k
                    if (capC != null && t > 0.62f && top > h * 0.45f) { mR = cr; mG = cg; mB = cb }
                    w.vert(x, y, z, 0f, 1f, 0f, 0f, 0f, mR, mG, mB, 1f)
                }
            }
            for (i in 0 until M) for (j in 0 until V) {
                val a = i * (V + 1) + j; val b = a + V + 1
                w.ib.push(a); w.ib.push(a + 1); w.ib.push(b); w.ib.push(a + 1); w.ib.push(b + 1); w.ib.push(b)
            }
            out.add(w.finish())
        }
        return out
    }

    fun buildClouds(rnd: Rnd, count: Int = 26) {
        for (i in 0 until count) {
            val a = rnd.next() * PI.toFloat() * 2f
            val r = 900 + rnd.next() * 1500
            val s = 220 + rnd.next() * 380
            clouds.add(floatArrayOf(track.cx + cos(a) * r, 180 + rnd.next() * 380, track.cz + sin(a) * r, s))
        }
    }
}
