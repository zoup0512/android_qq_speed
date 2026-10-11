package com.feiche.qqspeed.game

import com.feiche.qqspeed.gl.Mat
import com.feiche.qqspeed.gl.RItem
import com.feiche.qqspeed.gl.TexLib
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// 每张地图的装饰物件（风格简化移植 props.js）
class PropChunk(val tpl: MeshTemplate, val mat: Mat, val order: Int = 0)

class StartGate {
    val lampItems = ArrayList<RItem>()
    var lampState = 0

    fun apply() {
        // 0=熄灭 1/2=红 4=绿
        for (i in lampItems.indices) {
            val on = when {
                lampState >= 4 -> true
                lampState == 1 -> i == 0
                lampState == 2 -> i <= 1
                else -> false
            }
            val green = lampState >= 4
            val it = lampItems[i]
            it.colorMul = if (on) {
                if (green) floatArrayOf(0.2f, 1.6f, 0.4f, 1f)
                else floatArrayOf(1.6f, 0.12f, 0.1f, 1f)
            } else floatArrayOf(0.12f, 0.12f, 0.14f, 1f)
        }
    }
}

object Props {
    private val treeTrunk by lazy { Geom.cylinder(0.22f, 0.34f, 3.2f, 6).transformed(matCompose(0f, 1.6f, 0f)) }
    private val treeCrown by lazy { mergeTemplates(listOf(
        Geom.sphere(2.3f, 7, 5).transformed(matCompose(0f, 4.6f, 0f)),
        Geom.sphere(1.7f, 7, 5).transformed(matCompose(0.9f, 5.6f, 0.4f)),
    )) }
    private val pineTrunk by lazy { Geom.cylinder(0.25f, 0.35f, 2.4f, 6).transformed(matCompose(0f, 1.2f, 0f)) }
    private val pineTiers by lazy {
        val greens = ArrayList<MeshTemplate>()
        val snows = ArrayList<MeshTemplate>()
        val tiers = listOf(floatArrayOf(2.8f, 3.6f, 2.8f), floatArrayOf(2.2f, 3.2f, 4.8f), floatArrayOf(1.5f, 2.8f, 6.6f), floatArrayOf(0.9f, 2.2f, 8.2f))
        for (t in tiers) {
            greens.add(Geom.cone(t[0], t[1], 8).transformed(matCompose(0f, t[2], 0f)))
            snows.add(Geom.cone(t[0] * 0.72f, t[1] * 0.55f, 8).transformed(matCompose(0f, t[2] + t[1] * 0.26f, 0f)))
        }
        Pair(mergeTemplates(greens), mergeTemplates(snows))
    }
    private val lampPole by lazy { Geom.cylinder(0.12f, 0.18f, 8f, 6).transformed(matCompose(0f, 4f, 0f)) }
    private val lampHead by lazy { Geom.box(0.5f, 0.18f, 1.0f).transformed(matCompose(0f, 7.8f, 2.1f)) }
    private val bbPost by lazy { Geom.box(0.5f, 1f, 0.5f).transformed(matCompose(0f, 0.5f, 0f)) }

    fun std(c: Int) = Mat(
        colorR = ((c shr 16) and 0xff) / 255f, colorG = ((c shr 8) and 0xff) / 255f, colorB = (c and 0xff) / 255f,
        specStrength = 0.1f)

    // 开始构建
    fun build(map: MapDef, track: Track, rnd: Rnd, groundAt: (Float, Float) -> Float,
              batch: Batch, gate: StartGate): List<PropChunk> {
        val oases = ArrayList<FloatArray>()
        if (map.id == "egypt") {
            val cand = arrayOf(floatArrayOf(-110f, -60f, 22f), floatArrayOf(200f, -330f, 24f), floatArrayOf(-120f, 320f, 20f), floatArrayOf(140f, 200f, 18f), floatArrayOf(-300f, 60f, 24f))
            for (c in cand) if (track.isFree(c[0], c[1], c[2] + 14)) oases.add(c)
        }
        val cx = track.cx; val cz = track.cz
        val b = batch

        fun addTree(x: Float, y: Float, z: Float, s: Float, rot: Float, leaf: Int) {
            b.add(treeTrunk, matCompose(x, y, z, rot, s), 0.478f, 0.322f, 0.188f)
            b.add(treeCrown, matCompose(x, y, z, rot, s), leaf)
        }
        fun addPine(x: Float, y: Float, z: Float, s: Float, rot: Float, snowy: Boolean) {
            b.add(pineTrunk, matCompose(x, y, z, rot, s), 0.42f, 0.29f, 0.184f)
            b.add(pineTiers.first, matCompose(x, y, z, rot, s), 0.184f, 0.42f, 0.27f)
            if (snowy) b.add(pineTiers.second, matCompose(x, y, z, rot, s), 0.957f, 0.973f, 1f)
        }
        fun addPalm(x: Float, y: Float, z: Float, s: Float, rot: Float) {
            b.add(Geom.cylinder(0.18f, 0.3f, 9f, 6).transformed(matCompose(x, y + 4.5f, z, 0.12f, rot, 0.06f, s)),
                0x9c7a4f)
            for (k in 0 until 7) {
                val a = k / 7f * PI.toFloat() * 2f + rot
                b.add(Geom.plane(1.1f, 4.6f).transformed(matCompose(
                    x + cos(a) * 1.5f, y + 9f + s * 0.2f, z + sin(a) * 1.5f, a, s, s, s, -0.15f, 0f)),
                    0x3f9c3a)
            }
        }
        fun addLamp(x: Float, y: Float, z: Float, faceRot: Float) {
            b.add(lampPole, matCompose(x, y, z, faceRot), 0.365f, 0.392f, 0.439f)
            b.add(Geom.box(0.14f, 0.14f, 2.4f).transformed(matCompose(x, y + 7.9f, z, faceRot)), 0.365f, 0.392f, 0.439f)
            b.add(lampHead, matCompose(x, y, z, faceRot), 1.9f, 1.8f, 1.3f)
        }
        fun building(x: Float, z: Float, w: Float, d: Float, hgt: Float, facade: String, rot: Float) {
            val gy = groundAt(x, z)
            val tpl = Geom.box(w, hgt, d).transformed(matCompose(x, gy + hgt / 2 - 2f, z, rot))
            b.add(tpl, 0xdddddd)
            // 立面贴图面片（四侧）
            for (k in 0 until 4) {
                val a = rot + k * PI.toFloat() / 2f
                val ox = sin(a) * (if (k % 2 == 0) d / 2 + 0.15f else w / 2 + 0.15f)
                val oz = cos(a) * (if (k % 2 == 0) w / 2 + 0.15f else d / 2 + 0.15f)
                val fw = if (k % 2 == 0) w else d
                b.add(Geom.plane(fw, hgt - 4f).transformed(matCompose(
                    x + ox, gy + hgt / 2 - 2f, z + oz, 0f, a + PI.toFloat(), 0f)),
                    1f, 1f, 1f, "facade_$facade")
            }
        }

        when (map.id) {
            "city" -> {
                // 摩天楼群（避开赛道与河流）
                var placed = 0
                var tries = 0
                while (placed < 46 && tries < 900) {
                    tries++
                    val x = track.minX - 180 + rnd.next() * (track.maxX - track.minX + 360)
                    val z = track.minZ - 180 + rnd.next() * (track.maxZ - track.minZ + 360)
                    if (abs(z) < 80) continue // 河流
                    if (!track.isFree(x, z, 16f)) continue
                    val hgt = 26 + rnd.next() * 62
                    val w = 12 + rnd.next() * 14
                    val kinds = listOf("glass", "office", "modern")
                    building(x, z, w, w * (0.7f + rnd.next() * 0.5f), hgt, kinds[rnd.int(3)], rnd.next() * PI.toFloat())
                    placed++
                }
                // 路灯（沿路）
                val step = 60
                var i = 0
                while (i < track.N) {
                    val s = track.sample(i * track.ds, SampleOut())
                    val side = if ((i / step) % 2 == 0) 1 else -1
                    val lat = side * (track.halfW + 3.5f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (track.isFree(x, z, 2.5f)) addLamp(x, groundAt(x, z), z, s.hd)
                    i += step
                }
                // 广告牌
                for (k in 0 until 4) {
                    val s = track.sample((0.15f + k * 0.2f) * track.length, SampleOut())
                    val side = if (k % 2 == 0) 1 else -1
                    val lat = side * (track.halfW + 14f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 4f)) continue
                    val gy = groundAt(x, z)
                    val titles = listOf("SPEED" to "极速世界", "NITRO" to "氮气饮料", "DRIFT" to "漂移学院", "TURBO" to "涡轮加油站")
                    val t = titles[k]
                    val texName = "bb_${t.first}_$k"
                    TexLib.register(texName) { TexGen.billboardTexture(t.first, t.second, if (k % 2 == 0) 0xff7a00 else 0x1d6ff2, if (k % 2 == 0) 0xffd000 else 0x27c7ff) }
                    val rot = s.hd + (if (side > 0) PI.toFloat() else 0f)
                    b.add(bbPost, matCompose(x - cos(rot) * 3.8f, gy, z + sin(rot) * 3.8f, 0f, 1f, 12f), 0.27f, 0.29f, 0.33f)
                    b.add(bbPost, matCompose(x + cos(rot) * 3.8f, gy, z - sin(rot) * 3.8f, 0f, 1f, 12f), 0.27f, 0.29f, 0.33f)
                    b.add(Geom.plane(12f, 6f).transformed(matCompose(x, gy + 9f, z, 0f, rot, 0f)), 1f, 1f, 1f, texName)
                    b.add(Geom.box(12.8f, 6.8f, 0.3f).transformed(matCompose(x - sin(rot) * 0.2f, gy + 9f, z - cos(rot) * 0.2f, 0f, rot, 0f)), 0x2b2f38)
                }
                // 吉祥物气球
                for (k in 0 until 3) {
                    val s = track.sample((0.2f + k * 0.3f) * track.length, SampleOut())
                    val lat = (if (k % 2 == 0) 1 else -1) * (track.halfW + 22f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 6f)) continue
                    val col = listOf(0xff9a1f, 0x27c7ff, 0xff5fa8)[k]
                    b.add(Geom.sphere(5f, 14, 10).transformed(matCompose(x, groundAt(x, z) + 26f, z)), col)
                    b.add(Geom.box(1.6f, 1.2f, 1.6f).transformed(matCompose(x, groundAt(x, z) + 19f, z)), 0xc7a06a)
                }
                // 飞艇
                val bl = track.sample(0.5f * track.length, SampleOut())
                val bx = bl.x + bl.rx * 120f; val bz = bl.z + bl.rz * 120f
                b.add(Geom.sphere(11f, 16, 10).transformed(matCompose(bx, 95f, bz, 0f, bl.hd, 0f, 1f, 1f, 2.2f)), 0xf2f4f8)
                b.add(Geom.box(4f, 3f, 7f).transformed(matCompose(bx - sin(bl.hd) * 2f, 88f, bz - cos(bl.hd) * 2f, 0f, bl.hd, 0f)), 0xd8dee6)
            }
            "aegean" -> {
                // 白墙小镇
                var placed = 0
                var tries = 0
                while (placed < 40 && tries < 900) {
                    tries++
                    val s = track.sample(rnd.next() * track.length, SampleOut())
                    val side = if (rnd.next() > 0.5) 1 else -1
                    val lat = side * (track.halfW + 10f + rnd.next() * 60f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 8f)) continue
                    val gy = groundAt(x, z)
                    if (gy < -2f) continue
                    val w = 6 + rnd.next() * 6
                    val hgt = 5 + rnd.next() * 6
                    building(x, z, w, w * 0.9f, hgt, "aegean", rnd.next() * PI.toFloat())
                    // 蓝顶
                    b.add(Geom.sphere(w * 0.42f, 10, 6).transformed(matCompose(x, gy + hgt - 1.4f, z)), 0x2466b8)
                    placed++
                }
                // 棕榈
                for (k in 0 until 26) {
                    val s = track.sample(rnd.next() * track.length, SampleOut())
                    val side = if (rnd.next() > 0.5) 1 else -1
                    val lat = side * (track.halfW + 6f + rnd.next() * 16f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 3.5f)) continue
                    val gy = groundAt(x, z)
                    if (gy < -1f) continue
                    addPalm(x, gy, z, 0.8f + rnd.next() * 0.5f, rnd.next() * PI.toFloat())
                }
                // 风车
                val ws = track.sample(0.62f * track.length, SampleOut())
                val wx = ws.x + ws.rx * (track.halfW + 30f); val wz = ws.z + ws.rz * (track.halfW + 30f)
                if (track.isFree(wx, wz, 8f)) {
                    val gy = groundAt(wx, wz)
                    b.add(Geom.cylinder(2.2f, 3.2f, 14f, 10).transformed(matCompose(wx, gy + 7f, wz)), 0xf2eee6)
                    b.add(Geom.cone(2.5f, 3f, 10).transformed(matCompose(wx, gy + 15.5f, wz)), 0x2466b8)
                    for (k in 0 until 4) {
                        b.add(Geom.plane(2.2f, 9f).transformed(matCompose(wx + cos(k * PI.toFloat() / 2f) * 2f, gy + 16f, wz + 1.8f,
                            k * PI.toFloat() / 2f, 1f, 1f, 1f, 0.5f, -1.4f)), 0xe8e2d4)
                    }
                }
                // 帆船
                for (k in 0 until 5) {
                    val a = k / 5f * PI.toFloat() * 2f
                    val r = 500 + rnd.next() * 500
                    val x = cx + cos(a) * r; val z = cz + sin(a) * r
                    b.add(Geom.box(2.4f, 1.2f, 7f).transformed(matCompose(x, 0.2f, z, 0f, rnd.next() * PI.toFloat(), 0f)), 0xf5f2ea)
                    b.add(Geom.plane(3.4f, 6f).transformed(matCompose(x, 4.4f, z, 0f, k * 1.3f, 0f)), 0xffffff)
                }
            }
            "egypt" -> {
                // 金字塔群
                val pyrSpots = listOf(
                    floatArrayOf(cx + 320f, cz + 220f, 70f), floatArrayOf(cx - 380f, cz - 160f, 90f),
                    floatArrayOf(cx + 60f, cz + 480f, 55f), floatArrayOf(cx - 200f, cz + 520f, 65f))
                for (p in pyrSpots) {
                    if (!track.isFree(p[0], p[1], p[2] * 0.9f)) continue
                    val gy = groundAt(p[0], p[1])
                    val hgt = p[2]
                    val cone = Geom.cone(hgt * 0.9f, hgt, 4).transformed(matCompose(p[0], gy + hgt / 2, p[1], 0f, PI.toFloat() / 4f, 0f))
                    b.add(cone, 0xd9b77a)
                    // 金字塔尖
                    b.add(Geom.cone(hgt * 0.12f, hgt * 0.13f, 4).transformed(matCompose(p[0], gy + hgt * 0.95f, p[1], 0f, PI.toFloat() / 4f, 0f)), 0xe8c547)
                }
                // 狮身像（近似）
                val sp = track.sample(0.45f * track.length, SampleOut())
                val sxp = sp.x + sp.rx * (track.halfW + 20f); val szp = sp.z + sp.rz * (track.halfW + 20f)
                if (track.isFree(sxp, szp, 10f)) {
                    val gy = groundAt(sxp, szp)
                    b.add(Geom.box(6f, 3.2f, 12f).transformed(matCompose(sxp, gy + 1.6f, szp, 0f, sp.hd, 0f)), 0xc9a36a)
                    b.add(Geom.box(3.4f, 3.4f, 3.4f).transformed(matCompose(sxp - sin(sp.hd) * 7f, gy + 3.2f + 1.7f, szp - cos(sp.hd) * 7f, 0f, sp.hd, 0f)), 0xc9a36a)
                    b.add(Geom.box(1.6f, 1f, 2.2f).transformed(matCompose(sxp - sin(sp.hd) * 8.8f, gy + 4.4f, szp - cos(sp.hd) * 8.8f, 0f, sp.hd, 0f)), 0xb8935c)
                }
                // 方尖碑
                for (k in 0 until 4) {
                    val s = track.sample((0.1f + k * 0.25f) * track.length, SampleOut())
                    val side = if (k % 2 == 0) 1 else -1
                    val lat = side * (track.halfW + 9f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 4f)) continue
                    val gy = groundAt(x, z)
                    b.add(Geom.cylinder(0.9f, 1.4f, 14f, 4).transformed(matCompose(x, gy + 7f, z, 0f, PI.toFloat() / 4f, 0f)), 0xc9a36a)
                    b.add(Geom.cone(1.05f, 2f, 4).transformed(matCompose(x, gy + 15f, z, 0f, PI.toFloat() / 4f, 0f)), 0xe8c547)
                }
                // 棕榈（绿洲）
                for (o in oases) {
                    for (k in 0 until 7) {
                        val a = rnd.next() * PI.toFloat() * 2f
                        val r = o[2] * (0.5f + rnd.next() * 0.5f)
                        val x = o[0] + cos(a) * r; val z = o[1] + sin(a) * r
                        addPalm(x, groundAt(x, z), z, 0.9f + rnd.next() * 0.4f, rnd.next() * PI.toFloat())
                    }
                }
                // 岩石
                for (k in 0 until 30) {
                    val x = track.minX - 100 + rnd.next() * (track.maxX - track.minX + 200)
                    val z = track.minZ - 100 + rnd.next() * (track.maxZ - track.minZ + 200)
                    if (!track.isFree(x, z, 8f)) continue
                    val s = 1f + rnd.next() * 2.5f
                    b.add(Geom.sphere(1.6f, 6, 4).transformed(matCompose(x, groundAt(x, z) + s * 0.6f, z, 0f, rnd.next() * PI.toFloat(), 0f, s, s * 0.7f, s)),
                        0xb5532f)
                }
            }
            "snow" -> {
                // 雪松林
                var placed = 0
                var tries = 0
                while (placed < 90 && tries < 1600) {
                    tries++
                    val x = track.minX - 220 + rnd.next() * (track.maxX - track.minX + 440)
                    val z = track.minZ - 220 + rnd.next() * (track.maxZ - track.minZ + 440)
                    if (!track.isFree(x, z, 7f)) continue
                    addPine(x, groundAt(x, z), z, 0.8f + rnd.next() * 1.1f, rnd.next() * PI.toFloat(), true)
                    placed++
                }
                // 木屋
                for (k in 0 until 8) {
                    val s = track.sample(rnd.next() * track.length, SampleOut())
                    val side = if (k % 2 == 0) 1 else -1
                    val lat = side * (track.halfW + 12f + rnd.next() * 20f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 8f)) continue
                    val gy = groundAt(x, z)
                    val w = 7f + rnd.next() * 4f
                    b.add(Geom.box(w, 4.5f, w * 0.8f).transformed(matCompose(x, gy + 2.25f, z, 0f, rnd.next() * PI.toFloat(), 0f)), 1f, 1f, 1f, "lodge")
                    b.add(Geom.cone(w * 0.8f, 3f, 4).transformed(matCompose(x, gy + 6f, z, 0f, PI.toFloat() / 4f, 0f)), 0xf4f8ff)
                }
                // 热气球
                for (k in 0 until 4) {
                    val a = rnd.next() * PI.toFloat() * 2f
                    val r = 260 + rnd.next() * 300
                    val x = cx + cos(a) * r; val z = cz + sin(a) * r
                    val col = listOf(0xff5252, 0xffd740, 0x40c4ff, 0x69f0ae)[k]
                    b.add(Geom.sphere(8f, 14, 10).transformed(matCompose(x, 60f + k * 12f, z, 0f, 0f, 0f, 1f, 1.15f, 1f)), col)
                    b.add(Geom.box(2f, 1.6f, 2f).transformed(matCompose(x, 46f + k * 12f, z)), 0x8b5a36)
                }
                // 终点看台
                val gs = track.sample(0.985f * track.length, SampleOut())
                for (side in intArrayOf(-1, 1)) {
                    val lat = side * (track.halfW + 6.5f)
                    val x = gs.x + gs.rx * lat; val z = gs.z + gs.rz * lat
                    val gy = groundAt(x, z)
                    val rot = gs.hd + (if (side > 0) -PI.toFloat() / 2f else PI.toFloat() / 2f)
                    for (k in 0 until 6) {
                        b.add(Geom.box(30f, 0.7f, 1.6f).transformed(matCompose(x - sin(rot) * (k * 1.5f), gy + 0.35f + k * 0.75f, z - cos(rot) * (k * 1.5f), 0f, rot, 0f)),
                            (if (k % 2 == 0) 0xc2c8d4 else 0xd9dde6))
                    }
                    b.add(Geom.plane(29f, 5f).transformed(matCompose(
                        x - sin(rot) * 5f, gy + 4.2f, z - cos(rot) * 5f, 0f, rot + PI.toFloat(), -0.46f)),
                        1f, 1f, 1f, "crowd")
                    b.add(Geom.box(32f, 0.5f, 14f).transformed(matCompose(x - sin(rot) * 5.6f, gy + 11f, z - cos(rot) * 5.6f, rot, 1f, 1f, 1f, -0.08f, 0f)), 0x2e6fd6)
                }
                // 雪人
                for (k in 0 until 8) {
                    val s = track.sample(rnd.next() * track.length, SampleOut())
                    val side = if (rnd.next() > 0.5) 1 else -1
                    val lat = side * (track.halfW + 5f + rnd.next() * 8f)
                    val x = s.x + s.rx * lat; val z = s.z + s.rz * lat
                    if (!track.isFree(x, z, 3f)) continue
                    val gy = groundAt(x, z)
                    b.add(Geom.sphere(0.9f, 10, 8).transformed(matCompose(x, gy + 0.9f, z)), 0xffffff)
                    b.add(Geom.sphere(0.65f, 10, 8).transformed(matCompose(x, gy + 2.1f, z)), 0xffffff)
                    b.add(Geom.sphere(0.45f, 10, 8).transformed(matCompose(x, gy + 3f, z)), 0xffffff)
                    b.add(Geom.cone(0.35f, 1f, 8).transformed(matCompose(x, gy + 3.9f, z, 0f, 1f, 1f, 1f, 0.35f, 0f)), 0xff5252)
                }
            }
        }

        // 起点门架（通用）
        buildStartGate(map, track, groundAt, b, gate)

        return b.build().map { c -> PropChunk(MeshTemplate(c.verts, c.idx), if (c.texKey != null) Mat(mapName = c.texKey) else Mat(specStrength = 0.1f)) }
    }

    private fun buildStartGate(map: MapDef, track: Track, groundAt: (Float, Float) -> Float, b: Batch, gate: StartGate) {
        val s = track.sample(0f, SampleOut())
        val hw = track.halfW + 0.8f
        val hgt = 9f
        val gy = track.py[0] // 以路面高度为基准
        val rot = s.hd
        val px = s.x; val pz = s.z
        // 两根立柱 + 横梁
        val pR = ((0x2e6fd6 shr 16) and 0xff) / 255f; val pG = ((0x2e6fd6 shr 8) and 0xff) / 255f; val pB = 0xd6 / 255f
        for (side in intArrayOf(-1, 1)) {
            val x = px + s.rx * side * hw; val z = pz + s.rz * side * hw
            b.add(Geom.box(1.6f, hgt, 1.6f).transformed(matCompose(x, gy + hgt / 2, z, 0f, rot, 0f)), pR, pG, pB)
        }
        b.add(Geom.box(hw * 2 + 1.6f, 1.4f, 1.2f).transformed(matCompose(px, gy + hgt + 0.7f, pz, 0f, rot, 0f)), pR, pG, pB)
        // 横幅
        TexLib.register("gate_banner") { TexGen.textTexture("SPEED", 512, 128, fg = 0xffffffff.toInt(), sizePx = 90f, stroke = 0xff1d4fb0.toInt()) }
        b.add(Geom.plane(hw * 2, 2.6f).transformed(matCompose(px, gy + hgt + 0.7f, pz, 0f, rot + PI.toFloat(), 0f)),
            1f, 1f, 1f, "gate_banner")
        // 信号灯 5 盏
        for (k in 0 until 5) {
            val lat = (k - 2) * (hw * 2 / 5f)
            val x = px + s.rx * lat
            val z = pz + s.rz * lat
            b.add(Geom.box(1.2f, 0.9f, 0.5f).transformed(matCompose(x, gy + hgt - 1.2f, pz, 0f, rot, 0f)),
                0.1f, 0.1f, 0.12f)
            // 灯珠（动态颜色）
            val lampTpl = Geom.sphere(0.32f, 10, 6).transformed(matCompose(x, gy + hgt - 0.65f, pz, 0f, rot, 0f))
            val lampItem = RItem(GlMeshHolder.fromTemplate(lampTpl), Mat(unlit = true), order = 0)
            gate.lampItems.add(lampItem)
        }
        gate.apply()
    }
}

// GlMeshHolder：在 GL 上下文外缓存模板、进入时创建
object GlMeshHolder {
    fun fromTemplate(t: MeshTemplate): com.feiche.qqspeed.gl.GlMesh = com.feiche.qqspeed.gl.GlMesh.fromTemplate(t)
}
