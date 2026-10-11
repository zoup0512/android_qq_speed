package com.feiche.qqspeed.game

import com.feiche.qqspeed.gl.Mat
import com.feiche.qqspeed.gl.RItem
import kotlin.math.*

// 标准顶点写入器：pos3 normal3 uv2 color4 = 12 floats
class VertWriter {
    val vb = FloatBuf(4096)
    val ib = IntBuf(4096)

    fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f) {
        vb.push(x); vb.push(y); vb.push(z)
        vb.push(nx); vb.push(ny); vb.push(nz)
        vb.push(u); vb.push(v)
        vb.push(r); vb.push(g); vb.push(b); vb.push(a)
    }

    fun vertUp(x: Float, y: Float, z: Float, u: Float, v: Float) = vert(x, y, z, 0f, 1f, 0f, u, v)
    val count: Int get() = vb.n / 12

    fun quad(a: Int, b: Int, c: Int, d: Int) { ib.push(a); ib.push(b); ib.push(c); ib.push(b); ib.push(d); ib.push(c) }
    fun tri(a: Int, b: Int, c: Int) { ib.push(a); ib.push(b); ib.push(c) }

    fun finish(computeNormals: Boolean = true): MeshTemplate {
        val verts = vb.trim()
        val idx = ib.trim()
        if (computeNormals && idx.isNotEmpty()) computeMeshNormals(verts, idx)
        return MeshTemplate(verts, idx)
    }
}

fun computeMeshNormals(verts: FloatArray, idx: IntArray) {
    val n = verts.size / 12
    val acc = FloatArray(n * 3)
    for (k in idx.indices step 3) {
        val a = idx[k] * 12; val b = idx[k + 1] * 12; val c = idx[k + 2] * 12
        val ax = verts[a]; val ay = verts[a + 1]; val az = verts[a + 2]
        val e1x = verts[b] - ax; val e1y = verts[b + 1] - ay; val e1z = verts[b + 2] - az
        val e2x = verts[c] - ax; val e2y = verts[c + 1] - ay; val e2z = verts[c + 2] - az
        val nx = e1y * e2z - e1z * e2y
        val ny = e1z * e2x - e1x * e2z
        val nz = e1x * e2y - e1y * e2x
        acc[idx[k] * 3] += nx; acc[idx[k] * 3 + 1] += ny; acc[idx[k] * 3 + 2] += nz
        acc[idx[k + 1] * 3] += nx; acc[idx[k + 1] * 3 + 1] += ny; acc[idx[k + 1] * 3 + 2] += nz
        acc[idx[k + 2] * 3] += nx; acc[idx[k + 2] * 3 + 1] += ny; acc[idx[k + 2] * 3 + 2] += nz
    }
    for (v in 0 until n) {
        val o = v * 12
        val l = sqrt(acc[v * 3] * acc[v * 3] + acc[v * 3 + 1] * acc[v * 3 + 1] + acc[v * 3 + 2] * acc[v * 3 + 2])
        if (l > 1e-8f) { verts[o + 3] = acc[v * 3] / l; verts[o + 4] = acc[v * 3 + 1] / l; verts[o + 5] = acc[v * 3 + 2] / l }
        else { verts[o + 3] = 0f; verts[o + 4] = 1f; verts[o + 5] = 0f }
    }
}

// ---------- 地图配置（移植 maps.js / layouts.js）----------
class BoostPadDef(val atX: Float?, val atZ: Float?, val f: Float, val lat: Float)
class TunnelDef(val fromX: Float, val fromZ: Float, val toX: Float, val toZ: Float)

class TrackTheme(
    val road: String,
    val wall: String,
    val curbA: Int, val curbB: Int,
    val deckColor: Int,
    val checkerA: Int, val checkerB: Int,
    val boostColor: Int,
    val boostPads: List<BoostPadDef>,
    val tunnels: List<TunnelDef>,
    val shoulderWidth: Float, val shoulderTex: String?,
    val tunnelOuter: Int,
    val roadMetal: Float, val roadRough: Float,
)

class MountainCfg(
    val color: Int, val cap: Int?,
    val r0: Float, val r1: Float, val h0: Float, val h1: Float, val count: Int = 48,
)

class MapDef(
    val id: String,
    val name: String,
    val en: String,
    val tag: String,
    val layoutId: String,
    val width: Float,
    val points: Array<FloatArray>, // [x, z, y]
    val skyTop: Int, val skyHorizon: Int, val skyBottom: Int,
    val sun: FloatArray, val sunColor: Int, val sunI: Float,
    val hemiSky: Int, val hemiGround: Int, val hemiI: Float,
    val fog: Int, val fogNear: Float, val fogFar: Float,
    val ground: String,
    val waterColor: Int?, val waterDeep: Int?, val waterY: Float, val waterLocal: Boolean,
    val theme: TrackTheme,
    val isBridge: (x: Float, z: Float, y: Float) -> Boolean,
    val mountains: MountainCfg,
    val snowfall: Boolean,
    val bgm: Int,
)

private fun hx(s: String): Int = android.graphics.Color.parseColor(s)

object Maps {
    private val cityLayout = arrayOf(
        floatArrayOf(-300f, -150f, 0f), floatArrayOf(-140f, -152f, 0f), floatArrayOf(20f, -150f, 0f), floatArrayOf(120f, -140f, 0f),
        floatArrayOf(185f, -95f, 1f), floatArrayOf(205f, -40f, 5f), floatArrayOf(205f, 0f, 6f), floatArrayOf(205f, 45f, 5f),
        floatArrayOf(215f, 110f, 2f), floatArrayOf(270f, 170f, 0f), floatArrayOf(360f, 190f, 0f), floatArrayOf(410f, 250f, 0f),
        floatArrayOf(385f, 330f, 0f), floatArrayOf(300f, 350f, 0f), floatArrayOf(230f, 310f, 0f), floatArrayOf(150f, 290f, 0f),
        floatArrayOf(70f, 330f, 0f), floatArrayOf(-20f, 330f, 0f), floatArrayOf(-90f, 290f, 0f), floatArrayOf(-170f, 290f, 0f),
        floatArrayOf(-240f, 240f, 0f), floatArrayOf(-265f, 160f, 2f), floatArrayOf(-262f, 70f, 5f), floatArrayOf(-262f, 0f, 6f),
        floatArrayOf(-262f, -60f, 4f), floatArrayOf(-300f, -95f, 1f), floatArrayOf(-370f, -110f, 0f), floatArrayOf(-400f, -160f, 0f),
        floatArrayOf(-370f, -190f, 0f),
    )
    private val aegeanLayout = arrayOf(
        floatArrayOf(-250f, -170f, 0f), floatArrayOf(-60f, -178f, 0f), floatArrayOf(110f, -168f, 0f), floatArrayOf(215f, -130f, 1f),
        floatArrayOf(262f, -60f, 3f), floatArrayOf(255f, 5f, 5f), floatArrayOf(200f, 32f, 7f), floatArrayOf(110f, 20f, 8f),
        floatArrayOf(20f, 12f, 9f), floatArrayOf(-50f, 40f, 10f), floatArrayOf(-55f, 95f, 11f), floatArrayOf(10f, 118f, 12f),
        floatArrayOf(120f, 115f, 14f), floatArrayOf(230f, 125f, 16f), floatArrayOf(300f, 180f, 17f), floatArrayOf(290f, 265f, 18f),
        floatArrayOf(200f, 310f, 18f), floatArrayOf(80f, 318f, 17f), floatArrayOf(-60f, 300f, 15f), floatArrayOf(-180f, 250f, 12f),
        floatArrayOf(-275f, 170f, 9f), floatArrayOf(-330f, 70f, 6f), floatArrayOf(-345f, -30f, 3f), floatArrayOf(-325f, -120f, 1f),
    )
    private val egyptLayout = arrayOf(
        floatArrayOf(0f, -330f, 0f), floatArrayOf(0f, -200f, 0f), floatArrayOf(4f, -80f, 1.5f), floatArrayOf(0f, -10f, 5f),
        floatArrayOf(0f, 22f, 6.2f), floatArrayOf(2f, 58f, 0.4f), floatArrayOf(20f, 140f, 0f), floatArrayOf(-30f, 215f, 0f),
        floatArrayOf(-130f, 235f, 0f), floatArrayOf(-215f, 290f, 0f), floatArrayOf(-190f, 375f, 0f), floatArrayOf(-70f, 400f, 0f),
        floatArrayOf(70f, 380f, 0f), floatArrayOf(190f, 330f, 0f), floatArrayOf(285f, 250f, 0f), floatArrayOf(300f, 150f, 0f),
        floatArrayOf(255f, 80f, 0f), floatArrayOf(300f, 5f, 0f), floatArrayOf(345f, -70f, 0f), floatArrayOf(320f, -170f, 0f),
        floatArrayOf(240f, -250f, 0f), floatArrayOf(160f, -320f, 0f), floatArrayOf(120f, -410f, 0f), floatArrayOf(40f, -450f, 0f),
        floatArrayOf(-20f, -420f, 0f),
    )
    private val snowLayout = arrayOf(
        floatArrayOf(-200f, -200f, 0f), floatArrayOf(-40f, -200f, 0f), floatArrayOf(110f, -190f, 0f), floatArrayOf(200f, -140f, 0f),
        floatArrayOf(220f, -60f, 2f), floatArrayOf(150f, 0f, 7f), floatArrayOf(60f, 60f, 10f), floatArrayOf(-20f, 120f, 10f),
        floatArrayOf(-60f, 200f, 8f), floatArrayOf(-10f, 280f, 5f), floatArrayOf(90f, 290f, 2f), floatArrayOf(170f, 230f, 0f),
        floatArrayOf(165f, 140f, 0f), floatArrayOf(80f, 70f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(-80f, -50f, 0f),
        floatArrayOf(-170f, -60f, 0f), floatArrayOf(-270f, -20f, 0f), floatArrayOf(-340f, -60f, 0f), floatArrayOf(-350f, -150f, 0f),
        floatArrayOf(-300f, -200f, 0f),
    )

    val ALL: List<MapDef> = listOf(
        MapDef(
            "city", "11城", "CITY 11", "经典竞速 · 11 个弯道", "city", 26f, cityLayout,
            hx("#2f7fe0"), hx("#cfeaff"), hx("#8fb8d8"), floatArrayOf(-0.45f, 0.62f, -0.55f), hx("#fff4dc"), 2.6f,
            hx("#d6ecff"), hx("#6f8a5a"), 1.1f, hx("#cfe6fb"), 380f, 2300f, "grass",
            hx("#1f7fd1"), hx("#0b3f7a"), -1.6f, false,
            TrackTheme("asphalt", "city", hx("#e53935"), hx("#ffffff"), 0xc7c3d6,
                hx("#101010"), hx("#ffffff"), hx("#27c7ff"),
                listOf(BoostPadDef(-120f, -151f, 0f, 0f), BoostPadDef(205f, -8f, 0f, 0f), BoostPadDef(-262f, 8f, 0f, -4f), BoostPadDef(-262f, 8f, 0f, 4f)),
                emptyList(), 7f, "plaza", 0, 0f, 0.85f),
            { _, z, y -> abs(z) < 80 && y > 1.5f },
            MountainCfg(hx("#6f95b8"), null, 1600f, 500f, 140f, 200f), false, 0,
        ),
        MapDef(
            "aegean", "情迷爱琴海", "AEGEAN SEA", "山城爬坡 · 连续发卡弯", "aegean", 24f, aegeanLayout,
            hx("#3a8ee6"), hx("#e4f3ff"), hx("#9cc6e6"), floatArrayOf(0.5f, 0.55f, -0.45f), hx("#fff1d6"), 2.7f,
            hx("#e8f4ff"), hx("#a39a7a"), 1.15f, hx("#dcefff"), 420f, 2400f, "aegean",
            hx("#1a8fd8"), hx("#07477f"), -1f, false,
            TrackTheme("flagstone", "aegean", hx("#2f7fd8"), hx("#ffffff"), 0xe9e4d8,
                hx("#1d4f9e"), hx("#ffffff"), hx("#ffd23a"),
                listOf(BoostPadDef(-60f, -178f, 0f, 0f), BoostPadDef(120f, 115f, 0f, 0f)),
                listOf(TunnelDef(-296f, 128f, -340f, 10f)), 5f, "plaza", 0xf2eee6, 0f, 0.85f),
            { _, _, _ -> false },
            MountainCfg(hx("#9c9a74"), null, 1500f, 500f, 140f, 220f), false, 1,
        ),
        MapDef(
            "egypt", "法老金字塔", "PHARAOH", "沙漠飞跃 · 神殿隧道", "egypt", 26f, egyptLayout,
            hx("#3b86d8"), hx("#fbe7c4"), hx("#e9c99a"), floatArrayOf(0.35f, 0.5f, 0.6f), hx("#fff0cf"), 3.0f,
            hx("#fff1d8"), hx("#c49a5a"), 1.05f, hx("#f3dfbd"), 420f, 2500f, "sand",
            hx("#23a3b8"), hx("#0d5a6a"), -0.6f, true,
            TrackTheme("sandstone", "egypt", hx("#5b3f9e"), hx("#f3e3b5"), 0xd9b77a,
                hx("#5b3f9e"), hx("#ffffff"), hx("#ff9a1f"),
                listOf(BoostPadDef(2f, -60f, 0f, 0f), BoostPadDef(300f, 20f, 0f, 0f)),
                listOf(TunnelDef(0f, -250f, 0f, -150f)), 0f, null, 0xd6b274, 0f, 0.85f),
            { _, _, _ -> false },
            MountainCfg(hx("#d9ae72"), null, 1500f, 600f, 50f, 90f, 40), false, 2,
        ),
        MapDef(
            "snow", "雪地大冒险", "SNOW ADVENTURE", "8 字立交 · 冰雪隧道", "snow", 25f, snowLayout,
            hx("#3f86d6"), hx("#e2f0ff"), hx("#c9ddf2"), floatArrayOf(-0.3f, 0.45f, 0.7f), hx("#ffffff"), 2.4f,
            hx("#eef6ff"), hx("#9fb4cc"), 1.25f, hx("#e3eefb"), 350f, 2200f, "snow",
            null, null, 0f, false,
            TrackTheme("plate", "snow", hx("#c62828"), hx("#ffffff"), 0x9fb0c8,
                hx("#101010"), hx("#ffffff"), hx("#39c5ff"),
                listOf(BoostPadDef(-60f, -200f, 0f, 0f), BoostPadDef(-20f, 120f, 0f, 0f)),
                listOf(TunnelDef(-110f, -52f, -235f, -40f)), 0f, null, 0xdfeaf6, 0.35f, 0.55f),
            { _, _, y -> y > 5.5f },
            MountainCfg(hx("#6f8fb8"), hx("#f2f7ff"), 1400f, 500f, 240f, 300f), true, 3,
        ),
    )

    fun byId(id: String): MapDef = ALL.first { it.id == id }
}

// ---------- 赛道 ----------
class SampleOut {
    var i = 0; var t = 0f; var d = 0f
    var x = 0f; var y = 0f; var z = 0f
    var hd = 0f; var tx = 0f; var tz = 0f
    var rx = 0f; var rz = 0f; var bank = 0f; var lat = 0f
    var surfY = 0f
}

class Track(val map: MapDef) {
    val layout = map
    val halfW = map.width / 2f
    val wallH = 1.25f
    var N = 0
    lateinit var px: FloatArray; lateinit var py: FloatArray; lateinit var pz: FloatArray
    var length = 0f; var ds = 0f
    lateinit var hd: FloatArray; lateinit var tx: FloatArray; lateinit var tz: FloatArray
    lateinit var rx: FloatArray; lateinit var rz: FloatArray
    lateinit var curv: FloatArray; lateinit var bank: FloatArray; lateinit var slope: FloatArray
    lateinit var vcurv: FloatArray; lateinit var sslope: FloatArray
    lateinit var bridge: BooleanArray
    var minX = 0f; var maxX = 0f; var minZ = 0f; var maxZ = 0f
    var cx = 0f; var cz = 0f

    private val cell = 16f
    private val grid = HashMap<Int, IntBuf>()

    val boostPads = ArrayList<TrackBoostPad>()
    val tunnels = ArrayList<FloatArray>()

    init {
        buildCurve()
        buildSpatialHash()
    }

    // ---------- CatmullRomCurve3 (centripetal, closed) 移植 ----------
    private class Curve(val pts: Array<FloatArray>) {
        fun point(t: Float, out: FloatArray) {
            val l = pts.size
            val p = l * t
            var intPoint = floor(p).toInt()
            val weight = p - intPoint
            if (intPoint <= 0) intPoint += ((abs(intPoint) / l).toInt() + 1) * l
            val i0 = (intPoint - 1) % l; val i1 = intPoint % l; val i2 = (intPoint + 1) % l; val i3 = (intPoint + 2) % l
            val p0 = pts[i0]; val p1 = pts[i1]; val p2 = pts[i2]; val p3 = pts[i3]
            val alpha = 0.5f
            var dt0 = dist(p0, p1).pow(alpha)
            var dt1 = dist(p1, p2).pow(alpha)
            var dt2 = dist(p2, p3).pow(alpha)
            if (dt1 < 1e-4f) dt1 = 1f
            if (dt0 < 1e-4f) dt0 = dt1
            if (dt2 < 1e-4f) dt2 = dt1
            val t1x = (p1[0] - p0[0]) / dt0 - (p2[0] - p0[0]) / (dt0 + dt1) + (p2[0] - p1[0]) / dt1
            val t1y = (p1[1] - p0[1]) / dt0 - (p2[1] - p0[1]) / (dt0 + dt1) + (p2[1] - p1[1]) / dt1
            val t1z = (p1[2] - p0[2]) / dt0 - (p2[2] - p0[2]) / (dt0 + dt1) + (p2[2] - p1[2]) / dt1
            val t2x = (p2[0] - p1[0]) / dt1 - (p3[0] - p1[0]) / (dt1 + dt2) + (p3[0] - p2[0]) / dt2
            val t2y = (p2[1] - p1[1]) / dt1 - (p3[1] - p1[1]) / (dt1 + dt2) + (p3[1] - p2[1]) / dt2
            val t2z = (p2[2] - p1[2]) / dt1 - (p3[2] - p1[2]) / (dt1 + dt2) + (p3[2] - p2[2]) / dt2
            val t2w = weight * weight; val t3w = t2w * weight
            out[0] = p1[0] * (2 * t3w - 3 * t2w + 1) + t1x * (t3w - 2 * t2w + weight) + p2[0] * (-2 * t3w + 3 * t2w) + t2x * (t3w - t2w)
            out[1] = p1[1] * (2 * t3w - 3 * t2w + 1) + t1y * (t3w - 2 * t2w + weight) + p2[1] * (-2 * t3w + 3 * t2w) + t2y * (t3w - t2w)
            out[2] = p1[2] * (2 * t3w - 3 * t2w + 1) + t1z * (t3w - 2 * t2w + weight) + p2[2] * (-2 * t3w + 3 * t2w) + t2z * (t3w - t2w)
        }

        private fun dist(a: FloatArray, b: FloatArray): Float {
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val dz = b[2] - a[2]
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun getLength(): Float {
            val div = 6000
            var sum = 0f
            val p = FloatArray(3); var prev = FloatArray(3)
            point(0f, prev)
            for (i in 1..div) {
                point(i.toFloat() / div, p)
                sum += dist(prev, p)
                prev[0] = p[0]; prev[1] = p[1]; prev[2] = p[2]
            }
            return sum
        }

        // 二分查弧长 → 均匀取点（先算一次长度表）
        var arcLens: FloatArray = FloatArray(0)
        fun buildArcTable() {
            val div = 6000
            arcLens = FloatArray(div + 1)
            arcLens[0] = 0f
            val p = FloatArray(3); var prev = FloatArray(3)
            point(0f, prev)
            var sum = 0f
            for (i in 1..div) {
                point(i.toFloat() / div, p)
                sum += dist(prev, p)
                arcLens[i] = sum
                prev[0] = p[0]; prev[1] = p[1]; prev[2] = p[2]
            }
        }

        fun getUtoTmapping(u: Float): Float {
            val arr = arcLens
            val target = u * arr[arr.size - 1]
            var low = 0; var high = arr.size - 1
            while (low < high) {
                val mid = (low + high) / 2
                if (arr[mid] < target) low = mid + 1 else high = mid
            }
            val i = if (arr[low] > target && low > 0) low - 1 else low
            val before = arr[i]; val after = arr[i + 1]
            val frac = if (after - before < 1e-9f) 0f else (target - before) / (after - before)
            return (i + frac) / (arr.size - 1)
        }

        fun spacedPoints(count: Int): Array<FloatArray> {
            val out = Array(count) { FloatArray(3) }
            for (i in 0 until count) point(getUtoTmapping(i.toFloat() / count), out[i])
            return out
        }
    }

    private fun buildCurve() {
        val mapped = Array(layout.points.size) { i ->
            val p = layout.points[i]
            floatArrayOf(p[0], p[2], p[1])
        }
        val curve = Curve(mapped)
        curve.buildArcTable()
        val approxLen = curve.arcLens.last()
        val n = (approxLen / 1.5f).roundToInt()
        val pts = curve.spacedPoints(n)
        N = n
        px = FloatArray(n); py = FloatArray(n); pz = FloatArray(n)
        for (i in 0 until n) { px[i] = pts[i][0]; py[i] = pts[i][1]; pz[i] = pts[i][2] }
        var len = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            len += sqrt((px[j] - px[i]) * (px[j] - px[i]) + (pz[j] - pz[i]) * (pz[j] - pz[i]))
        }
        length = len; ds = len / n

        hd = FloatArray(n); tx = FloatArray(n); tz = FloatArray(n)
        rx = FloatArray(n); rz = FloatArray(n)
        val rawCurv = FloatArray(n)
        slope = FloatArray(n)
        for (i in 0 until n) {
            val a = (i - 1 + n) % n; val b = (i + 1) % n
            val h = atan2(px[b] - px[a], pz[b] - pz[a])
            hd[i] = h
            tx[i] = sin(h); tz[i] = cos(h)
            rx[i] = -cos(h); rz[i] = sin(h)
            slope[i] = (py[b] - py[a]) / (2 * ds)
        }
        for (i in 0 until n) rawCurv[i] = U.wrapAngle(hd[(i + 1) % n] - hd[i]) / ds
        val W = 7
        curv = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (k in -W..W) s += rawCurv[(i + k + n) % n]
            curv[i] = s / (2 * W + 1)
        }
        val bk = FloatArray(n)
        for (i in 0 until n) bk[i] = U.clamp(curv[i] * 5.5f, -0.13f, 0.13f)
        bank = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (k in -12..12) s += bk[(i + k + n) % n]
            bank[i] = s / 25f
        }
        val sy = FloatArray(n)
        for (i in 0 until n) {
            var a = 0f
            for (k in -3..3) a += py[(i + k + n) % n]
            sy[i] = a / 7f
        }
        vcurv = FloatArray(n); sslope = FloatArray(n)
        val K = 4
        for (i in 0 until n) {
            val s0 = (sy[i] - sy[(i - K + n) % n]) / (K * ds)
            val s1 = (sy[(i + K) % n] - sy[i]) / (K * ds)
            vcurv[i] = (s1 - s0) / (K * ds)
            sslope[i] = (sy[(i + 1) % n] - sy[(i - 1 + n) % n]) / (2 * ds)
        }
        bridge = BooleanArray(n)
        for (i in 0 until n) bridge[i] = map.isBridge(px[i], pz[i], py[i])

        minX = Float.MAX_VALUE; maxX = -Float.MAX_VALUE; minZ = Float.MAX_VALUE; maxZ = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, px[i]); maxX = max(maxX, px[i])
            minZ = min(minZ, pz[i]); maxZ = max(maxZ, pz[i])
        }
        cx = (minX + maxX) / 2; cz = (minZ + maxZ) / 2
    }

    private fun key(cxg: Float, czg: Float): Int = (cxg.toInt() * 73856093) xor (czg.toInt() * 19349663)

    private fun buildSpatialHash() {
        for (i in 0 until N) {
            val k = key(floor(px[i] / cell), floor(pz[i] / cell))
            var arr = grid[k]
            if (arr == null) { arr = IntBuf(8); grid[k] = arr }
            arr.push(i)
        }
    }

    class NearestOut(var dist: Float = 0f, var i: Int = -1)

    fun nearest(x: Float, z: Float, maxDist: Float = 60f): NearestOut? {
        val r = ceil(maxDist / cell).toInt()
        val cxg = floor(x / cell); val czg = floor(z / cell)
        var best = maxDist * maxDist; var bi = -1
        for (a in -r..r) for (b in -r..r) {
            val arr = grid[key(cxg + a, czg + b)] ?: continue
            for (k in 0 until arr.n) {
                val i = arr.get(k)
                val dx = px[i] - x; val dz = pz[i] - z
                val d2 = dx * dx + dz * dz
                if (d2 < best) { best = d2; bi = i }
            }
        }
        return if (bi < 0) null else NearestOut(sqrt(best), bi)
    }

    fun forEachNear(x: Float, z: Float, maxDist: Float, fn: (Int) -> Unit) {
        val r = ceil(maxDist / cell).toInt()
        val cxg = floor(x / cell); val czg = floor(z / cell)
        val md2 = maxDist * maxDist
        for (a in -r..r) for (b in -r..r) {
            val arr = grid[key(cxg + a, czg + b)] ?: continue
            for (k in 0 until arr.n) {
                val i = arr.get(k)
                val dx = px[i] - x; val dz = pz[i] - z
                if (dx * dx + dz * dz < md2) fn(i)
            }
        }
    }

    fun isFree(x: Float, z: Float, r: Float): Boolean = nearest(x, z, halfW + r) == null

    fun sample(d: Float, out: SampleOut = SampleOut()): SampleOut {
        val L = length
        var dd = d % L
        if (dd < 0) dd += L
        val f = dd / ds
        val i = floor(f).toInt() % N
        val j = (i + 1) % N
        val t = f - floor(f)
        out.i = i; out.t = t; out.d = dd
        out.x = px[i] + (px[j] - px[i]) * t
        out.z = pz[i] + (pz[j] - pz[i]) * t
        out.y = py[i] + (py[j] - py[i]) * t
        val h = hd[i] + U.wrapAngle(hd[j] - hd[i]) * t
        out.hd = h
        out.tx = sin(h); out.tz = cos(h)
        out.rx = -out.tz; out.rz = out.tx
        out.bank = bank[i] + (bank[j] - bank[i]) * t
        out.lat = 0f
        return out
    }

    fun project(x: Float, y: Float, z: Float, hint: Int, out: SampleOut): SampleOut {
        var bi = 0; var best = Float.MAX_VALUE
        if (hint < 0) {
            for (i in 0 until N) {
                val dx = px[i] - x; val dz = pz[i] - z; val dy = (py[i] - y) * 3
                val d2 = dx * dx + dz * dz + dy * dy
                if (d2 < best) { best = d2; bi = i }
            }
        } else {
            for (k in -30..30) {
                val i = (hint + k + N) % N
                val dx = px[i] - x; val dz = pz[i] - z
                val d2 = dx * dx + dz * dz
                if (d2 < best) { best = d2; bi = i }
            }
        }
        var i = bi
        var j = (i + 1) % N
        var ex = px[j] - px[i]; var ez = pz[j] - pz[i]
        var t = ((x - px[i]) * ex + (z - pz[i]) * ez) / (ex * ex + ez * ez)
        if (t < 0f) {
            i = (bi - 1 + N) % N
            j = bi
            ex = px[j] - px[i]; ez = pz[j] - pz[i]
            t = ((x - px[i]) * ex + (z - pz[i]) * ez) / (ex * ex + ez * ez)
        }
        t = U.clamp(t, 0f, 1f)
        val s = sample((i + t) * ds, out)
        s.lat = (x - s.x) * s.rx + (z - s.z) * s.rz
        s.surfY = s.y + s.lat * sin(s.bank)
        return s
    }

    fun curvAhead(d: Float, range: Float): Float {
        val i0 = floor(d.mod(length) / ds).toInt()
        val steps = max(1, (range / ds).toInt())
        var m = 0f
        var k = 0
        while (k < steps) {
            val c = curv[(i0 + k) % N]
            if (abs(c) > abs(m)) m = c
            k += 2
        }
        return m
    }

    fun dAt(x: Float, z: Float): Float {
        val n = nearest(x, z, 400f)
        return if (n != null) n.i * ds else 0f
    }

    class TrackBoostPad(val d: Float, val lat: Float, val len: Float, val halfW: Float, var mat: Mat? = null, var item: RItem? = null)

    // ---------- 网格构建 ----------
    class TrackPart(val tpl: MeshTemplate, val mat: Mat, val order: Int = 0)

    private fun hexName(c: Int): String = Integer.toHexString(c and 0xffffff)

    fun build(theme: TrackTheme, groundAt: (Float, Float) -> Float): List<TrackPart> {
        val parts = ArrayList<TrackPart>()
        val n = N
        val hw = halfW
        val rows = n + 1
        val TEX_LEN = 24f
        val WALL_TEX_LEN = 16f

        // 路面
        run {
            val w = VertWriter()
            for (r in 0 until rows) {
                val i = r % n
                val sb = sin(bank[i])
                for (s in 0 until 2) {
                    val lat = if (s == 1) hw else -hw
                    w.vert(px[i] + rx[i] * lat, py[i] + lat * sb, pz[i] + rz[i] * lat,
                        0f, 1f, 0f, s.toFloat(), (r * ds) / TEX_LEN)
                }
            }
            for (r in 0 until n) {
                val a = r * 2; w.quad(a, a + 1, a + 2, a + 3)
            }
            parts.add(TrackPart(w.finish(), Mat(mapName = "road_${theme.road}", specStrength = 0.15f)))
        }

        // 护栏（含顶面）+ 路基（纯色）分开建
        run {
            val th = 0.6f; val H = wallH; val base = 1.6f
            val w = VertWriter()
            for (side in intArrayOf(-1, 1)) {
                val startW = w.count
                for (r in 0 until rows) {
                    val i = r % n
                    val sb = sin(bank[i])
                    val latIn = side * hw; val latOut = side * (hw + th)
                    val yIn = py[i] + latIn * sb; val yOut = py[i] + latOut * sb
                    val v = (if (side > 0) -1f else 1f) * (r * ds) / WALL_TEX_LEN
                    w.vert(px[i] + rx[i] * latIn, yIn - 0.05f, pz[i] + rz[i] * latIn, 0f, 1f, 0f, v, 0f)
                    w.vert(px[i] + rx[i] * latIn, yIn + H, pz[i] + rz[i] * latIn, 0f, 1f, 0f, v, 1f)
                    w.vert(px[i] + rx[i] * latOut, yOut + H, pz[i] + rz[i] * latOut, 0f, 1f, 0f, v, 1f)
                }
                for (r in 0 until n) {
                    val a = startW + r * 3; val b = a + 3
                    if (side < 0) {
                        w.ib.push(a, a + 1, b, b, a + 1, b + 1)
                        w.ib.push(a + 1, a + 2, b + 1, b + 1, a + 2, b + 2)
                    } else {
                        w.ib.push(a, b, a + 1, b, b + 1, a + 1)
                        w.ib.push(a + 1, b + 1, a + 2, b + 1, b + 2, a + 2)
                    }
                }
            }
            parts.add(TrackPart(w.finish(), Mat(mapName = "wall_${theme.wall}", doubleSide = true, specStrength = 0.1f)))
            // 路基（外侧立面 + 底面）
            val w2 = VertWriter()
            for (side in intArrayOf(-1, 1)) {
                for (r in 0 until rows) {
                    val i = r % n
                    val sb = sin(bank[i])
                    val latOut = side * (hw + th)
                    val yOut = py[i] + latOut * sb
                    w2.vert(px[i] + rx[i] * latOut, yOut + H, pz[i] + rz[i] * latOut, 0f, 1f, 0f, 0f, 0f)
                    w2.vert(px[i] + rx[i] * latOut, py[i] - base, pz[i] + rz[i] * latOut, 0f, 1f, 0f, 0f, 0f)
                }
                for (r in 0 until n) {
                    val a = r * 2; val b = a + 2
                    if (side > 0) w2.ib.push(a, a + 1, b, b, a + 1, b + 1)
                    else w2.ib.push(a, b, a + 1, b, b + 1, a + 1)
                }
            }
            val s3 = w2.count
            for (r in 0 until rows) {
                val i = r % n
                val l = -(hw + th); val rr = hw + th
                w2.vert(px[i] + rx[i] * l, py[i] - base, pz[i] + rz[i] * l, 0f, 1f, 0f, 0f, 0f)
                w2.vert(px[i] + rx[i] * rr, py[i] - base, pz[i] + rz[i] * rr, 0f, 1f, 0f, 0f, 0f)
            }
            for (r in 0 until n) {
                val a = s3 + r * 2; val b = a + 2
                w2.ib.push(a, a + 1, b, b, a + 1, b + 1)
            }
            val dc = theme.deckColor
            parts.add(TrackPart(w2.finish(), Mat(
                colorR = ((dc shr 16) and 0xff) / 255f, colorG = ((dc shr 8) and 0xff) / 255f, colorB = (dc and 0xff) / 255f,
                doubleSide = true, specStrength = 0.05f)))
        }

        // 弯心路肩（红白）
        run {
            val cw = 1.3f
            val w = VertWriter()
            for (side in intArrayOf(-1, 1)) {
                var run = IntArray(0)
                fun flush() {
                    if (run.size < 8) { run = IntArray(0); return }
                    val start = w.count
                    for (k in run.indices) {
                        val i = run[k]
                        val sb = sin(bank[i])
                        val l0 = side * (hw - cw); val l1 = side * hw
                        w.vert(px[i] + rx[i] * l0, py[i] + l0 * sb + 0.035f, pz[i] + rz[i] * l0, 0f, 1f, 0f, if (side < 0) 1f else 0f, (k * ds) / 3.2f)
                        w.vert(px[i] + rx[i] * l1, py[i] + l1 * sb + 0.035f, pz[i] + rz[i] * l1, 0f, 1f, 0f, if (side < 0) 0f else 1f, (k * ds) / 3.2f)
                    }
                    for (k in 0 until run.size - 1) {
                        val a = start + k * 2
                        if (side < 0) w.ib.push(a, a + 2, a + 1, a + 1, a + 2, a + 3)
                        else w.ib.push(a, a + 1, a + 2, a + 1, a + 3, a + 2)
                    }
                    run = IntArray(0)
                }
                for (i in 0 until n) {
                    val inner = curv[i] * -side > 0.011f
                    if (inner) {
                        val nr = IntArray(run.size + 1); System.arraycopy(run, 0, nr, 0, run.size); nr[run.size] = i
                        run = nr
                    } else flush()
                }
                flush()
            }
            if (w.count > 0) {
                parts.add(TrackPart(w.finish(), Mat(mapName = "curb_${hexName(theme.curbA)}_${hexName(theme.curbB)}", doubleSide = true, polyOffset = -2), order = 1))
            }
        }

        // 起终点线
        parts.add(TrackPart(roadDecal(0f, 0f, hw * 2 - 0.6f, 3.2f),
            Mat(mapName = "checker_${hexName(theme.checkerA)}_${hexName(theme.checkerB)}", nearest = true, polyOffset = -3, doubleSide = true, specStrength = 0.05f), order = 1))

        // 加速带
        for (bp in theme.boostPads) {
            val d = if (bp.atX != null) dAt(bp.atX!!, bp.atZ!!) else bp.f * length
            val mat = Mat(mapName = "boost_${hexName(theme.boostColor)}", unlit = true, doubleSide = true, polyOffset = -4, depthWrite = false, repeat = false)
            parts.add(TrackPart(roadDecal(d, bp.lat, 5f, 11f), mat, order = 1))
            boostPads.add(TrackBoostPad(d, bp.lat, 11f, 2.8f, mat))
        }

        // 隧道
        for (tn in theme.tunnels) {
            var d0 = dAt(tn.fromX, tn.fromZ)
            var d1 = dAt(tn.toX, tn.toZ)
            if (d1 < d0) d1 += length
            buildTunnel(d0, d1, theme, parts)
        }

        // 路肩人行道
        if (theme.shoulderTex != null) buildShoulders(theme, parts)

        // 桥墩
        buildPillars(groundAt, theme, parts)

        return parts
    }

    fun roadDecal(d: Float, lat: Float, width: Float, len: Float): MeshTemplate {
        val segs = max(2, ceil(len / 1.5).toInt())
        val w = VertWriter()
        val s = SampleOut()
        for (k in 0..segs) {
            sample(d + (k.toFloat() / segs) * len - len / 2, s)
            val sb = sin(s.bank)
            for (e in 0 until 2) {
                val l = lat + (if (e == 1) width / 2 else -width / 2)
                w.vert(s.x + s.rx * l, s.y + l * sb + 0.05f, s.z + s.rz * l, 0f, 1f, 0f, e.toFloat(), k.toFloat() / segs)
            }
        }
        for (k in 0 until segs) {
            val a = k * 2
            w.ib.push(a, a + 1, a + 2, a + 1, a + 3, a + 2)
        }
        return w.finish()
    }

    private fun buildTunnel(d0: Float, d1: Float, theme: TrackTheme, parts: MutableList<TrackPart>) {
        val M = 14
        val hw = halfW + 0.6f
        val hTop = 9.5f
        val segs = ceil((d1 - d0) / 3).toInt()
        val wi = VertWriter()
        val s = SampleOut()
        for (k in 0..segs) {
            sample(d0 + ((d1 - d0) * k) / segs, s)
            for (m in 0..M) {
                val a = PI.toFloat() * (m.toFloat() / M)
                val lat = -cos(a) * hw
                val y = s.y + wallH + sin(a) * (hTop - wallH)
                wi.vert(s.x + s.rx * lat, y, s.z + s.rz * lat, 0f, 1f, 0f, m.toFloat() / M * 3, (k * 3f) / 8)
            }
        }
        for (k in 0 until segs) for (m in 0 until M) {
            val a = k * (M + 1) + m; val b = a + M + 1
            wi.ib.push(a, b, a + 1, a + 1, b, b + 1)
        }
        parts.add(TrackPart(wi.finish(), Mat(mapName = "tunnel", doubleSide = true), order = 0))
        // 外壳
        val wo = VertWriter()
        for (k in 0..segs) {
            sample(d0 + ((d1 - d0) * k) / segs, s)
            for (m in 0..M) {
                val a = PI.toFloat() * (m.toFloat() / M)
                val lo = -cos(a) * (hw + 2.5f)
                val y = s.y + wallH + sin(a) * (hTop + 2.5f - wallH) - (if (m == 0 || m == M) 1.5f else 0f)
                wo.vert(s.x + s.rx * lo, y, s.z + s.rz * lo, 0f, 1f, 0f, 0f, 0f)
            }
        }
        for (k in 0 until segs) for (m in 0 until M) {
            val a = k * (M + 1) + m; val b = a + M + 1
            wo.ib.push(a, b, a + 1, a + 1, b, b + 1)
        }
        val tc = theme.tunnelOuter
        parts.add(TrackPart(wo.finish(), Mat(
            colorR = ((tc shr 16) and 0xff) / 255f, colorG = ((tc shr 8) and 0xff) / 255f, colorB = (tc and 0xff) / 255f,
            doubleSide = true)))
        // 洞口门框
        for (dd in floatArrayOf(d0, d1)) {
            sample(dd, s)
            val torus = Geom.torus(hw + 1.2f, 1.3f, 8, 20, PI.toFloat())
            val m = M4().compose(s.x, s.y + wallH, s.z, 0f, s.hd, 0f, 1f, (hTop - wallH + 1.2f) / (hw + 1.2f), 1f)
            parts.add(TrackPart(torus.transformed(m), Mat(colorR = 0.91f, colorG = 0.886f, colorB = 0.83f)))
        }
        tunnels.add(floatArrayOf(d0, d1))
    }

    private fun buildShoulders(theme: TrackTheme, parts: MutableList<TrackPart>) {
        val n = N
        val hw = halfW + 0.6f
        val w = theme.shoulderWidth
        val vw = VertWriter()
        for (side in intArrayOf(-1, 1)) {
            var run = IntArray(0)
            fun flush() {
                if (run.size > 3) {
                    for (k in run.indices) {
                        val i = run[k] % n
                        val sb = sin(bank[i])
                        for (l in floatArrayOf(hw, hw + w)) {
                            val lat = side * l
                            val x = px[i] + rx[i] * lat
                            val z = pz[i] + rz[i] * lat
                            vw.vert(x, py[i] + side * hw * sb - 0.02f - (if (l > hw) 0.15f else 0f), z, 0f, 1f, 0f, x / 8, z / 8)
                        }
                    }
                    for (k in 0 until run.size - 1) {
                        val a = k * 2
                        if (side > 0) vw.ib.push(a, a + 1, a + 2, a + 1, a + 3, a + 2)
                        else vw.ib.push(a, a + 2, a + 1, a + 1, a + 2, a + 3)
                    }
                }
                run = IntArray(0)
            }
            for (r in 0..n) {
                val i = r % n
                if (bridge[i]) flush()
                else {
                    val nr = IntArray(run.size + 1); System.arraycopy(run, 0, nr, 0, run.size); nr[run.size] = r
                    run = nr
                }
            }
            flush()
        }
        if (vw.count > 0) {
            parts.add(TrackPart(vw.finish(), Mat(mapName = "ground_${theme.shoulderTex}", doubleSide = true)))
        }
    }

    private fun buildPillars(groundAt: (Float, Float) -> Float, theme: TrackTheme, parts: MutableList<TrackPart>) {
        val b = Batch()
        val dc = theme.deckColor
        val colR = ((dc shr 16) and 0xff) / 255f
        val colG = ((dc shr 8) and 0xff) / 255f
        val colB = (dc and 0xff) / 255f
        var i = 0
        while (i < N) {
            val x = px[i]; val z = pz[i]; val y = py[i]
            val gy = groundAt(x, z)
            val top = y - 1.6f
            if (top - gy >= 1.2f) {
                var blocked = false
                forEachNear(x, z, halfW + 6) { j ->
                    val di = abs(j - i)
                    if (min(di, N - di) > 40 && py[j] < y - 3) blocked = true
                }
                if (!blocked) {
                    val h = top - gy + 0.5f
                    b.add(Geom.box(3.2f, h, 3.2f), matCompose(x, gy + h / 2 - 0.5f, z, hd[i]), colR, colG, colB)
                    b.add(Geom.box(halfW * 1.7f, 1.4f, 3.6f), matCompose(x, top - 0.5f, z, hd[i]), colR, colG, colB)
                }
            }
            i += 12
        }
        for (c in b.build()) {
            parts.add(TrackPart(MeshTemplate(c.verts, c.idx), Mat(colorR = 1f, colorG = 1f, colorB = 1f, specStrength = 0.05f)))
        }
    }
}
