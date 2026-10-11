package com.feiche.qqspeed.game

import kotlin.math.*

// 可增长的 float/int 缓冲
class FloatBuf(init: Int = 256) {
    private var a = FloatArray(init)
    var n = 0; private set
    fun push(v: Float) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun push(a: Float, b: Float) { push(a); push(b) }
    fun push(a: Float, b: Float, c: Float) { push(a); push(b); push(c) }
    fun push3(x: Float, y: Float, z: Float) { push(x); push(y); push(z) }
    fun push4(a1: Float, b: Float, c: Float, d: Float) { push(a1); push(b); push(c); push(d) }
    fun pushAll(arr: FloatArray) { ensure(n + arr.size); System.arraycopy(arr, 0, a, n, arr.size); n += arr.size }
    fun ensure(cap: Int) { if (cap > a.size) a = a.copyOf(maxOf(a.size * 2, cap)) }
    fun trim(): FloatArray = a.copyOf(n)
    fun get(i: Int): Float = a[i]
    fun set(i: Int, v: Float) { a[i] = v }
    fun clear() { n = 0 }
}

class IntBuf(init: Int = 256) {
    private var a = IntArray(init)
    var n = 0; private set
    fun push(v: Int) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun push(a: Int, b: Int) { push(a); push(b) }
    fun push(a: Int, b: Int, c: Int) { push(a); push(b); push(c) }
    fun push(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) { push(a, b, c); push(d, e, f) }
    fun pushAll(arr: IntArray) { ensure(n + arr.size); System.arraycopy(arr, 0, a, n, arr.size); n += arr.size }
    fun ensure(cap: Int) { if (cap > a.size) a = a.copyOf(maxOf(a.size * 2, cap)) }
    fun trim(): IntArray = a.copyOf(n)
    fun get(i: Int): Int = a[i]
    fun set(i: Int, v: Int) { a[i] = v }
    fun clear() { n = 0 }
}

// 列主序 4x4 矩阵（与 three.js 一致）
class M4 {
    val e = FloatArray(16)

    init { identity() }

    fun identity(): M4 {
        java.util.Arrays.fill(e, 0f)
        e[0] = 1f; e[5] = 1f; e[10] = 1f; e[15] = 1f
        return this
    }

    fun set(o: M4): M4 { System.arraycopy(o.e, 0, e, 0, 16); return this }

    fun multiply(b: M4): M4 { // this = this * b
        val a = e.copyOf()
        for (c in 0 until 4) for (r in 0 until 4) {
            e[c * 4 + r] = a[r] * b.e[c * 4] + a[4 + r] * b.e[c * 4 + 1] + a[8 + r] * b.e[c * 4 + 2] + a[12 + r] * b.e[c * 4 + 3]
        }
        return this
    }

    fun compose(px: Float, py: Float, pz: Float, rx: Float, ry: Float, rz: Float, sx: Float, sy: Float, sz: Float): M4 {
        // Euler 'YXZ' → 四元数
        val c1 = cos(rx / 2).toFloat(); val c2 = cos(ry / 2).toFloat(); val c3 = cos(rz / 2).toFloat()
        val s1 = sin(rx / 2).toFloat(); val s2 = sin(ry / 2).toFloat(); val s3 = sin(rz / 2).toFloat()
        val qx = s1 * c2 * c3 - c1 * s2 * s3
        val qy = c1 * s2 * c3 + s1 * c2 * s3
        val qz = c1 * c2 * s3 - s1 * s2 * c3
        val qw = c1 * c2 * c3 + s1 * s2 * s3
        val x2 = qx + qx; val y2 = qy + qy; val z2 = qz + qz
        val xx = qx * x2; val xy = qx * y2; val xz = qx * z2
        val yy = qy * y2; val yz = qy * z2; val zz = qz * z2
        val wx = qw * x2; val wy = qw * y2; val wz = qw * z2
        e[0] = (1 - (yy + zz)) * sx; e[1] = (xy + wz) * sx; e[2] = (xz - wy) * sx; e[3] = 0f
        e[4] = (xy - wz) * sy; e[5] = (1 - (xx + zz)) * sy; e[6] = (yz + wx) * sy; e[7] = 0f
        e[8] = (xz + wy) * sz; e[9] = (yz - wx) * sz; e[10] = (1 - (xx + yy)) * sz; e[11] = 0f
        e[12] = px; e[13] = py; e[14] = pz; e[15] = 1f
        return this
    }

    fun apply(vx: Float, vy: Float, vz: Float, out: FloatArray, oi: Int) {
        out[oi] = e[0] * vx + e[4] * vy + e[8] * vz + e[12]
        out[oi + 1] = e[1] * vx + e[5] * vy + e[9] * vz + e[13]
        out[oi + 2] = e[2] * vx + e[6] * vy + e[10] * vz + e[14]
    }

    // 法线用逆转置 3x3
    fun applyNormal(vx: Float, vy: Float, vz: Float, out: FloatArray, oi: Int) {
        // 上 3x3 求伴随（逆转置 = 伴随转置/行列式，归一化后只差符号/缩放）
        val a00 = e[0]; val a01 = e[4]; val a02 = e[8]
        val a10 = e[1]; val a11 = e[5]; val a12 = e[9]
        val a20 = e[2]; val a21 = e[6]; val a22 = e[10]
        val b01 = a22 * a11 - a12 * a21
        val b11 = -a22 * a10 + a12 * a20
        val b21 = a21 * a10 - a11 * a20
        var det = a00 * b01 + a01 * b11 + a02 * b21
        if (abs(det) < 1e-12f) det = 1e-12f
        val id = 1f / det
        // inv 转置的行 = inv 的列
        val i0x = b01 * id; val i0y = (a02 * a21 - a01 * a22) * id; val i0z = (a01 * a12 - a02 * a11) * id
        val i1x = b11 * id; val i1y = (a00 * a22 - a02 * a20) * id; val i1z = (a02 * a10 - a00 * a12) * id
        val i2x = b21 * id; val i2y = (a01 * a20 - a00 * a21) * id; val i2z = (a00 * a11 - a01 * a10) * id
        // out = (v·inv行0, v·inv行1, v·inv行2) 即 inv^T * v
        val nx = vx * i0x + vy * i1x + vz * i2x
        val ny = vx * i0y + vy * i1y + vz * i2y
        val nz = vx * i0z + vy * i1z + vz * i2z
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l > 1e-10f) { out[oi] = nx / l; out[oi + 1] = ny / l; out[oi + 2] = nz / l }
        else { out[oi] = 0f; out[oi + 1] = 1f; out[oi + 2] = 0f }
    }
}

fun matCompose(x: Float, y: Float, z: Float, ry: Float = 0f, sx: Float = 1f, sy: Float = sx, sz: Float = sx, rx: Float = 0f, rz: Float = 0f): M4 =
    M4().compose(x, y, z, rx, ry, rz, sx, sy, sz)

// ---------- 网格模板 ----------
// 顶点布局：pos(3) normal(3) uv(2) color(4) = 12 floats
class MeshTemplate(val verts: FloatArray, val idx: IntArray, val vertCount: Int = verts.size / 12) {
    fun transformed(m: M4): MeshTemplate {
        val out = FloatArray(verts.size)
        val p = FloatArray(3)
        for (v in 0 until vertCount) {
            val o = v * 12
            m.apply(verts[o], verts[o + 1], verts[o + 2], out, o)
            m.applyNormal(verts[o + 3], verts[o + 4], verts[o + 5], out, o + 3)
            out[o + 6] = verts[o + 6]; out[o + 7] = verts[o + 7]
            out[o + 8] = verts[o + 8]; out[o + 9] = verts[o + 9]; out[o + 10] = verts[o + 10]; out[o + 11] = verts[o + 11]
        }
        return MeshTemplate(out, idx, vertCount)
    }
}

private fun pushQuad(idx: IntBuf, a: Int, b: Int, c: Int, d: Int) { idx.push(a); idx.push(b); idx.push(c); idx.push(b); idx.push(d); idx.push(c) }

object Geom {
    // 平面（XY，面向 +Z）
    fun plane(w: Float, h: Float): MeshTemplate {
        val v = floatArrayOf(
            -w / 2, -h / 2, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
            w / 2, -h / 2, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            -w / 2, h / 2, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f,
            w / 2, h / 2, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f,
        )
        return MeshTemplate(v, intArrayOf(0, 1, 2, 1, 3, 2))
    }

    fun box(w: Float, h: Float, d: Float): MeshTemplate {
        val vb = FloatBuf(256); val ib = IntBuf(64)
        val x0 = -w / 2; val x1 = w / 2; val y0 = -h / 2; val y1 = h / 2; val z0 = -d / 2; val z1 = d / 2
        data class Face(val n: FloatArray, val corners: FloatArray)
        fun face(n: FloatArray, a: FloatArray, b2: FloatArray, c: FloatArray, d2: FloatArray) {
            val base = vb.n / 12
            for (p in listOf(a, b2, c, d2)) {
                vb.push3(p[0], p[1], p[2]); vb.push3(n[0], n[1], n[2])
                // 简单 uv：按面尺寸归一
                val uv = when (p) { a -> floatArrayOf(0f, 1f); b2 -> floatArrayOf(1f, 1f); c -> floatArrayOf(0f, 0f); else -> floatArrayOf(1f, 0f) }
                vb.push(uv[0]); vb.push(uv[1]); vb.push4(1f, 1f, 1f, 1f)
            }
            pushQuad(ib, base, base + 1, base + 2, base + 3)
        }
        // +Z
        face(floatArrayOf(0f, 0f, 1f), floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1))
        // -Z
        face(floatArrayOf(0f, 0f, -1f), floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0))
        // +X
        face(floatArrayOf(1f, 0f, 0f), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0))
        // -X
        face(floatArrayOf(-1f, 0f, 0f), floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z0), floatArrayOf(x0, y1, z1))
        // +Y
        face(floatArrayOf(0f, 1f, 0f), floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0))
        // -Y
        face(floatArrayOf(0f, -1f, 0f), floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1))
        return MeshTemplate(vb.trim(), ib.trim())
    }

    // 圆柱（沿 Y 轴，中心在原点）
    fun cylinder(rTop: Float, rBottom: Float, h: Float, seg: Int): MeshTemplate {
        val vb = FloatBuf(seg * 12 * 6); val ib = IntBuf(seg * 12)
        val hy = h / 2
        fun ring(y: Float, r: Float, ny: Float, out: (Int) -> Unit) {
            for (i in 0..seg) {
                val a = i.toFloat() / seg * PI.toFloat() * 2f
                val nx = cos(a); val nz = sin(a)
                vb.push3(nx * r, y, nz * r); vb.push3(nx, ny, nz); vb.push(i.toFloat() / seg, 0f); vb.push4(1f, 1f, 1f, 1f)
            }
            out(0)
        }
        ring(hy, rTop, 0f, {})
        ring(-hy, rBottom, 0f, {})
        val baseSide = 0
        for (i in 0 until seg) {
            val a = baseSide + i; val b = baseSide + seg + 1 + i
            ib.push(a); ib.push(b); ib.push(a + 1); ib.push(a + 1); ib.push(b); ib.push(b + 1)
        }
        // 顶盖 / 底盖
        val topC = vb.n / 12
        vb.push3(0f, hy, 0f); vb.push3(0f, 1f, 0f); vb.push(0.5f); vb.push(0.5f); vb.push4(1f, 1f, 1f, 1f)
        val botC = vb.n / 12
        vb.push3(0f, -hy, 0f); vb.push3(0f, -1f, 0f); vb.push(0.5f); vb.push(0.5f); vb.push4(1f, 1f, 1f, 1f)
        for (i in 0 until seg) {
            val a = i; val b = i + 1
            ib.push(topC); ib.push(b); ib.push(a) // 顶
            ib.push(botC); ib.push(seg + 1 + a); ib.push(seg + 1 + b) // 底
        }
        return MeshTemplate(vb.trim(), ib.trim())
    }

    // 圆锥（底在 -h/2，尖在 +h/2）
    fun cone(r: Float, h: Float, seg: Int): MeshTemplate {
        val vb = FloatBuf(seg * 12 * 3); val ib = IntBuf(seg * 6)
        val hy = h / 2
        for (i in 0..seg) {
            val a = i.toFloat() / seg * PI.toFloat() * 2f
            val nx = cos(a); val nz = sin(a)
            val slope = atan2(r, h)
            val ny = cos(slope); val nr = sin(slope)
            vb.push3(nx * r, -hy, nz * r); vb.push3(nx * nr, ny, nz * nr); vb.push(i.toFloat() / seg, 0f); vb.push4(1f, 1f, 1f, 1f)
        }
        for (i in 0 until seg) {
            val ai = vb.n / 12
            vb.push3(0f, hy, 0f); vb.push3(0f, 1f, 0f); vb.push(0.5f); vb.push(1f); vb.push4(1f, 1f, 1f, 1f)
            ib.push(ai); ib.push(i + 1); ib.push(i)
        }
        val bc = vb.n / 12
        vb.push3(0f, -hy, 0f); vb.push3(0f, -1f, 0f); vb.push(0.5f); vb.push(0.5f); vb.push4(1f, 1f, 1f, 1f)
        for (i in 0 until seg) { ib.push(bc); ib.push(i); ib.push(i + 1) }
        return MeshTemplate(vb.trim(), ib.trim())
    }

    fun sphere(r: Float, wSeg: Int, hSeg: Int): MeshTemplate {
        val vb = FloatBuf((wSeg + 1) * (hSeg + 1) * 12); val ib = IntBuf(wSeg * hSeg * 6)
        for (iy in 0..hSeg) {
            val v = iy.toFloat() / hSeg
            val phi = v * PI.toFloat()
            for (ix in 0..wSeg) {
                val u = ix.toFloat() / wSeg
                val theta = u * PI.toFloat() * 2f
                val nx = -cos(theta) * sin(phi); val ny = cos(phi); val nz = sin(theta) * sin(phi)
                vb.push3(nx * r, ny * r, nz * r); vb.push3(nx, ny, nz); vb.push(u, 1f - v); vb.push4(1f, 1f, 1f, 1f)
            }
        }
        for (iy in 0 until hSeg) for (ix in 0 until wSeg) {
            val a = iy * (wSeg + 1) + ix; val b = a + wSeg + 1
            ib.push(a); ib.push(b); ib.push(a + 1); ib.push(a + 1); ib.push(b); ib.push(b + 1)
        }
        return MeshTemplate(vb.trim(), ib.trim())
    }

    // 圆环（XY 平面主环，绕 Z）
    fun torus(R: Float, r: Float, radSeg: Int, tubSeg: Int, arc: Float = PI.toFloat() * 2f): MeshTemplate {
        val vb = FloatBuf((radSeg + 1) * (tubSeg + 1) * 12); val ib = IntBuf(radSeg * tubSeg * 6)
        for (j in 0..radSeg) {
            val u = j.toFloat() / radSeg * arc
            val cu = cos(u); val su = sin(u)
            for (i in 0..tubSeg) {
                val v = i.toFloat() / tubSeg * PI.toFloat() * 2f
                val cv = cos(v); val sv = sin(v)
                val nx = cu * cv; val ny = su * cv; val nz = sv
                vb.push3((R + r * cv) * cu, (R + r * cv) * su, r * sv)
                vb.push3(nx, ny, nz); vb.push(j.toFloat() / radSeg, i.toFloat() / tubSeg); vb.push4(1f, 1f, 1f, 1f)
            }
        }
        for (j in 0 until radSeg) for (i in 0 until tubSeg) {
            val a = j * (tubSeg + 1) + i; val b = a + tubSeg + 1
            ib.push(a); ib.push(a + 1); ib.push(b); ib.push(a + 1); ib.push(b + 1); ib.push(b)
        }
        return MeshTemplate(vb.trim(), ib.trim())
    }

    // 水平面（XZ，法线 +Y）
    fun ground(w: Float, d: Float): MeshTemplate {
        val v = floatArrayOf(
            -w / 2, 0f, d / 2, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            w / 2, 0f, d / 2, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f,
            -w / 2, 0f, -d / 2, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f,
            w / 2, 0f, -d / 2, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
        )
        return MeshTemplate(v, intArrayOf(0, 1, 2, 1, 3, 2))
    }
}

// ---------- 耳切三角化 ----------
fun triangulate(pts: FloatArray): IntArray {
    // pts: [x0,y0,x1,y1,...] 简单多边形（不区分洞）
    val n = pts.size / 2
    if (n < 3) return IntArray(0)
    var idx = IntArray(n) { it }
    val out = IntBuf(n * 3)
    var guard = 0
    while (idx.size > 3 && guard++ < n * 4) {
        val len = idx.size
        var clipped = false
        var k = 0
        while (k < len) {
            val a = idx[(k + len - 1) % len]; val b = idx[k]; val c = idx[(k + 1) % len]
            val ax = pts[a * 2]; val ay = pts[a * 2 + 1]
            val bx = pts[b * 2]; val by = pts[b * 2 + 1]
            val cx = pts[c * 2]; val cy = pts[c * 2 + 1]
            val cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
            if (cross <= 1e-9f) { k++; continue } // 凹角或共线
            var ok = true
            for (j in 0 until len) {
                val p = idx[j]
                if (p == a || p == b || p == c) continue
                val px = pts[p * 2]; val py = pts[p * 2 + 1]
                if (pointInTri(px, py, ax, ay, bx, by, cx, cy)) { ok = false; break }
            }
            if (!ok) { k++; continue }
            out.push(a); out.push(b); out.push(c)
            val newIdx = IntArray(len - 1)
            var wi = 0
            for (j in 0 until len) if (j != k) newIdx[wi++] = idx[j]
            idx = newIdx
            clipped = true
            break
        }
        if (!clipped) break
    }
    if (idx.size == 3) { out.push(idx[0]); out.push(idx[1]); out.push(idx[2]) }
    return out.trim()
}

private fun pointInTri(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
    val d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
    val d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
    val d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
    val hasNeg = (d1 < 0f) || (d2 < 0f) || (d3 < 0f)
    val hasPos = (d1 > 0f) || (d2 > 0f) || (d3 > 0f)
    return !(hasNeg && hasPos)
}

// ---------- 轮廓挤出（带斜面倒角）----------
// pts: [x,y,...] 简单多边形；沿 Z 轴挤出 width，bevel 为倒角宽度
fun extrudeProfile(pts: FloatArray, width: Float, bevel: Float): MeshTemplate {
    val n = pts.size / 2
    require(n >= 3) { "profile too small" }
    // 展开轮廓
    val expand = FloatArray(pts.size)
    if (bevel <= 0.001f) System.arraycopy(pts, 0, expand, 0, pts.size)
    else {
        // 面积符号判断朝向
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
        }
        val cw = area < 0f
        for (i in 0 until n) {
            val p = (i + n - 1) % n; val q = (i + 1) % n
            val e0x = pts[i * 2] - pts[p * 2]; val e0y = pts[i * 2 + 1] - pts[p * 2 + 1]
            val e1x = pts[q * 2] - pts[i * 2]; val e1y = pts[q * 2 + 1] - pts[i * 2 + 1]
            fun norm(e: FloatArray): FloatArray { val l = sqrt(e[0] * e[0] + e[1] * e[1]).coerceAtLeast(1e-6f); return floatArrayOf(e[0] / l, e[1] / l) }
            val n0 = norm(floatArrayOf(e0x, e0y)); val n1 = norm(floatArrayOf(e1x, e1y))
            // 左法线
            fun left(e: FloatArray) = floatArrayOf(-e[1], e[0])
            val l0 = left(n0); val l1 = left(n1)
            var mx = l0[0] + l1[0]; var my = l0[1] + l1[1]
            val ml = sqrt(mx * mx + my * my).coerceAtLeast(1e-6f)
            mx /= ml; my /= ml
            val sinHalf = sqrt((1f + (l0[0] * l1[0] + l0[1] * l1[1])) * 0.5f).coerceAtLeast(0.35f)
            val off = bevel / sinHalf
            val sgn = if (cw) -1f else 1f
            expand[i * 2] = pts[i * 2] + mx * off * sgn
            expand[i * 2 + 1] = pts[i * 2 + 1] + my * off * sgn
        }
    }
    val zIn = width / 2 - bevel; val zOut = width / 2
    val vb = FloatBuf(4096); val ib = IntBuf(4096)

    var ccwArea = 0f
    for (i in 0 until n) {
        val j = (i + 1) % n
        ccwArea += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1]
    }

    fun ringAt(z: Float, arr: FloatArray) {
        for (i in 0 until n) {
            val x = arr[i * 2]; val y = arr[i * 2 + 1]
            val p = (i + n - 1) % n; val q = (i + 1) % n
            var ex = arr[q * 2] - arr[p * 2]; var ey = arr[q * 2 + 1] - arr[p * 2 + 1]
            val el = sqrt(ex * ex + ey * ey).coerceAtLeast(1e-6f)
            ex /= el; ey /= el
            var nx = ey; var ny = -ex
            var cx = 0f; var cy = 0f
            for (k in 0 until n) { cx += arr[k * 2]; cy += arr[k * 2 + 1] }
            cx /= n; cy /= n
            if ((x - cx) * nx + (y - cy) * ny < 0f) { nx = -nx; ny = -ny }
            vb.push3(x, y, z); vb.push3(nx, ny, 0f)
            vb.push(x / width + 0.5f, y / width + 0.5f)
            vb.push4(1f, 1f, 1f, 1f)
        }
    }

    // 侧面（外扩轮廓，z 从 -zIn 到 +zIn）
    ringAt(-zIn, expand)
    ringAt(zIn, expand)
    for (k in 0 until n) {
        val a = k; val b = k + n
        val q = (k + 1) % n
        val b2 = q + n
        if (ccwArea > 0f) { ib.push(a); ib.push(b); ib.push(q); ib.push(q); ib.push(b); ib.push(b2) }
        else { ib.push(q); ib.push(b); ib.push(a); ib.push(b2); ib.push(b); ib.push(q) }
    }
    // 前后盖（原轮廓）
    val capTri = triangulate(pts)
    val front = vb.n / 12
    ringAt(zOut, pts)
    val back = vb.n / 12
    ringAt(-zOut, pts)
    for (t in capTri.indices step 3) {
        if (ccwArea > 0f) { ib.push(front + capTri[t]); ib.push(front + capTri[t + 1]); ib.push(front + capTri[t + 2]) }
        else { ib.push(front + capTri[t + 2]); ib.push(front + capTri[t + 1]); ib.push(front + capTri[t]) }
        if (ccwArea > 0f) { ib.push(back + capTri[t + 2]); ib.push(back + capTri[t + 1]); ib.push(back + capTri[t]) }
        else { ib.push(back + capTri[t]); ib.push(back + capTri[t + 1]); ib.push(back + capTri[t + 2]) }
    }
    // 倒角环：expand@±zIn → pts@±zOut
    if (bevel > 0.001f) {
        val fz = vb.n / 12
        val bz = fz + n
        for (side in 0 until 2) {
            val z = if (side == 0) zIn else -zIn
            val innerZ = if (side == 0) zOut else -zOut
            val sgn = if (side == 0) 1f else -1f
            for (i in 0 until n) {
                val x = expand[i * 2]; val y = expand[i * 2 + 1]
                val p = (i + n - 1) % n; val q = (i + 1) % n
                var ex = pts[q * 2] - pts[p * 2]; var ey = pts[q * 2 + 1] - pts[p * 2 + 1]
                val el = sqrt(ex * ex + ey * ey).coerceAtLeast(1e-6f)
                ex /= el; ey /= el
                var nx = ey; var ny = -ex
                var cx = 0f; var cy = 0f
                for (k in 0 until n) { cx += pts[k * 2]; cy += pts[k * 2 + 1] }
                cx /= n; cy /= n
                if ((pts[i * 2] - cx) * nx + (pts[i * 2 + 1] - cy) * ny < 0f) { nx = -nx; ny = -ny }
                val l = sqrt(nx * nx + ny * ny + bevel * bevel)
                vb.push3(x, y, z); vb.push3(nx / l, ny / l, bevel / l * sgn)
                vb.push(0f, 0f); vb.push4(1f, 1f, 1f, 1f)
            }
        }
        for (k in 0 until n) {
            val q = (k + 1) % n
            ib.push(fz + k); ib.push(front + q); ib.push(front + k)
            ib.push(fz + k); ib.push(fz + q); ib.push(front + q)
            ib.push(bz + k); ib.push(back + k); ib.push(back + q)
            ib.push(bz + k); ib.push(back + q); ib.push(bz + q)
        }
    }
    return MeshTemplate(vb.trim(), ib.trim())
}

// ---------- 合批构建器 ----------
class BatchedChunk(val verts: FloatArray, val idx: IntArray, val texKey: String? = null) {
    var cx = 0f; var cy = 0f; var cz = 0f; var radius = 0f
    init {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < verts.size) {
            minX = min(minX, verts[i]); maxX = max(maxX, verts[i])
            minY = min(minY, verts[i + 1]); maxY = max(maxY, verts[i + 1])
            minZ = min(minZ, verts[i + 2]); maxZ = max(maxZ, verts[i + 2])
            i += 12
        }
        cx = (minX + maxX) / 2; cy = (minY + maxY) / 2; cz = (minZ + maxZ) / 2
        val dx = maxX - minX; val dy = maxY - minY; val dz = maxZ - minZ
        radius = sqrt(dx * dx + dy * dy + dz * dz) / 2f
    }
}

class Batch(private val tile: Float = 260f) {
    private val groups = HashMap<String, Pair<FloatBuf, IntBuf>>()
    private val groupTex = HashMap<String, String?>()

    private val IDENTITY = M4()
    fun add(tpl: MeshTemplate, color: Int, texKey: String? = null) = add(tpl, IDENTITY, color, texKey)
    fun add(tpl: MeshTemplate, colorR: Float = 1f, colorG: Float = 1f, colorB: Float = 1f, texKey: String? = null) =
        add(tpl, IDENTITY, colorR, colorG, colorB, texKey)
    fun add(tpl: MeshTemplate, m: M4, color: Int, texKey: String? = null) {
        add(tpl, m,
            ((color shr 16) and 0xff) / 255f, ((color shr 8) and 0xff) / 255f, (color and 0xff) / 255f, texKey)
    }

    fun add(tpl: MeshTemplate, m: M4, colorR: Float = 1f, colorG: Float = 1f, colorB: Float = 1f, texKey: String? = null) {
        val e = m.e
        val key = "$texKey|${floor(e[12] / tile).toInt()}|${floor(e[14] / tile).toInt()}"
        var grp = groups[key]
        if (grp == null) { grp = Pair(FloatBuf(4096), IntBuf(4096)); groups[key] = grp; groupTex[key] = texKey }
        val (vb, ib) = grp
        val base = vb.n / 12
        val src = tpl.verts
        val p = FloatArray(3)
        for (v in 0 until tpl.vertCount) {
            val o = v * 12
            m.apply(src[o], src[o + 1], src[o + 2], p, 0)
            vb.push3(p[0], p[1], p[2])
            m.applyNormal(src[o + 3], src[o + 4], src[o + 5], p, 0)
            vb.push3(p[0], p[1], p[2])
            vb.push(src[o + 6], src[o + 7])
            vb.push4(src[o + 8] * colorR, src[o + 9] * colorG, src[o + 10] * colorB, src[o + 11])
        }
        for (k in tpl.idx.indices) ib.push(base + tpl.idx[k])
    }

    fun build(): List<BatchedChunk> {
        val out = ArrayList<BatchedChunk>(groups.size)
        for ((key, grp) in groups) {
            if (grp.first.n == 0) continue
            out.add(BatchedChunk(grp.first.trim(), grp.second.trim(), groupTex[key]))
        }
        groups.clear()
        return out
    }
}

// ---------- 合并模板 ----------
fun mergeTemplates(list: List<MeshTemplate>): MeshTemplate {
    if (list.size == 1) return list[0]
    var vCount = 0; var iCount = 0
    for (t in list) { vCount += t.vertCount; iCount += t.idx.size }
    val verts = FloatArray(vCount * 12)
    val idx = IntArray(iCount)
    var vo = 0; var io = 0; var base = 0
    for (t in list) {
        System.arraycopy(t.verts, 0, verts, vo * 12, t.verts.size)
        for (k in t.idx.indices) idx[io + k] = t.idx[k] + base
        base += t.vertCount; vo += t.vertCount; io += t.idx.size
    }
    return MeshTemplate(verts, idx)
}

// 包围球
fun boundingSphere(verts: FloatArray, count: Int): FloatArray {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (v in 0 until count) {
        val o = v * 12
        minX = min(minX, verts[o]); maxX = max(maxX, verts[o])
        minY = min(minY, verts[o + 1]); maxY = max(maxY, verts[o + 1])
        minZ = min(minZ, verts[o + 2]); maxZ = max(maxZ, verts[o + 2])
    }
    val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
    val r = sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ)) / 2f
    return floatArrayOf(cx, cy, cz, r)
}
