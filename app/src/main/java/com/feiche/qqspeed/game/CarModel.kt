package com.feiche.qqspeed.game

import com.feiche.qqspeed.gl.GlMesh
import com.feiche.qqspeed.gl.Mat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 车辆涂装（移植 carModel.js CAR_SKINS）
class CarSkin(
    val id: String, val name: String,
    val body: Int, val accent: Int, val glow: Int, val rim: Int,
) {
    companion object {
        val ALL = listOf(
            CarSkin("red", "烈焰战神", 0xe0262b, 0xffc93a, 0xff5a1f, 0xd9d9d9),
            CarSkin("blue", "冰蓝幻影", 0x1f6ff2, 0x6ff3ff, 0x27c7ff, 0xe6f2ff),
            CarSkin("purple", "紫电魅影", 0x7b3df0, 0xff6fd8, 0xc26bff, 0xf0e0ff),
            CarSkin("yellow", "黄金闪电", 0xffc21a, 0x1b1b1b, 0xffe066, 0x2b2b2b),
            CarSkin("white", "白鲸号", 0xf3f5f8, 0x2a7bff, 0x5ab0ff, 0x9aa7b8),
            CarSkin("pink", "粉红甜心", 0xff7eb6, 0xffffff, 0xff9ed0, 0xffffff),
            CarSkin("green", "翡翠之星", 0x19b86a, 0xe8ff5a, 0x6dff9e, 0xe8ffe8),
            CarSkin("black", "暗夜猎手", 0x1b1d22, 0xff2d55, 0xff2d55, 0x707782),
        )
    }
    val bodyR = ((body shr 16) and 0xff) / 255f
    val bodyG = ((body shr 8) and 0xff) / 255f
    val bodyB = (body and 0xff) / 255f
}

// 单个部件
class CarPart(
    val mesh: GlMesh,
    val mat: Mat,
    val local: M4,          // 相对 root 的固定变换（多数为 identity）
    val underRoot: Boolean, // true=挂在 root（受侧倾/俯仰），false=挂在 car
)

class WheelDef(val x: Float, val z: Float, val front: Boolean, val spinMesh: GlMesh, val rimMesh: GlMesh)

class CarMeshes(val skin: CarSkin) {
    val parts = ArrayList<CarPart>()
    val wheels = ArrayList<WheelDef>()
    val flames = ArrayList<FloatArray>() // exhaust 位置 [x,y,z]
    var flameOuter: GlMesh? = null
    var flameInner: GlMesh? = null
    var underglow: GlMesh? = null
    var shield: GlMesh? = null
    var flameMat: Mat? = null

    companion object {
        private val cache = HashMap<String, CarMeshes>()
        private var tireT: MeshTemplate? = null
        private var rimT: MeshTemplate? = null
        private var spokeT: MeshTemplate? = null
        private var bodyT: MeshTemplate? = null
        private var cabinT: MeshTemplate? = null
        private var skirtT: MeshTemplate? = null

        private fun profileOf(pts: List<FloatArray>): FloatArray {
            val out = FloatArray(pts.size * 2)
            for (i in pts.indices) { out[i * 2] = pts[i][0]; out[i * 2 + 1] = pts[i][1] }
            return out
        }

        private fun arc(pts: MutableList<FloatArray>, cx: Float, cy: Float, r: Float, a0: Float, a1: Float, segs: Int) {
            for (i in 0..segs) {
                val a = a0 + (a1 - a0) * (i.toFloat() / segs)
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                val last = pts.lastOrNull()
                if (last != null && kotlin.math.abs(last[0] - x) < 1e-3f && kotlin.math.abs(last[1] - y) < 1e-3f) continue
                pts.add(floatArrayOf(x, y))
            }
        }

        private fun quadTo(pts: MutableList<FloatArray>, cpx: Float, cpy: Float, x: Float, y: Float, segs: Int) {
            val last = pts.last()
            for (i in 1..segs) {
                val t = i.toFloat() / segs
                val x0 = U.lerp(last[0], cpx, t); val y0 = U.lerp(last[1], cpy, t)
                val x1 = U.lerp(cpx, x, t); val y1 = U.lerp(cpy, y, t)
                pts.add(floatArrayOf(U.lerp(x0, x1, t), U.lerp(y0, y1, t)))
            }
        }

        fun get(skin: CarSkin, makeMesh: (MeshTemplate) -> GlMesh, makeMesh3: (MeshTemplate) -> GlMesh): CarMeshes {
            cache[skin.id]?.let { return it }
            val cm = CarMeshes(skin)

            // ---------- 几何（全部 skin 共享） ----------
            if (bodyT == null) {
                // 车身轮廓
                val bodyPts = mutableListOf(floatArrayOf(-2.18f, 0.34f))
                bodyPts.add(floatArrayOf(-1.92f, 0.3f))
                arc(bodyPts, -1.38f, 0.3f, 0.54f, PI.toFloat(), 0f, 12)
                bodyPts.add(floatArrayOf(0.84f, 0.3f))
                arc(bodyPts, 1.38f, 0.3f, 0.54f, PI.toFloat(), 0f, 12)
                bodyPts.add(floatArrayOf(2.12f, 0.3f))
                quadTo(bodyPts, 2.34f, 0.34f, 2.3f, 0.5f, 6)
                bodyPts.add(floatArrayOf(2.12f, 0.66f))
                quadTo(bodyPts, 1.5f, 0.78f, 0.72f, 0.86f, 6)
                bodyPts.add(floatArrayOf(-0.2f, 0.92f))
                bodyPts.add(floatArrayOf(-1.6f, 0.96f))
                quadTo(bodyPts, -2.1f, 0.98f, -2.24f, 0.86f, 6)
                bodyPts.add(floatArrayOf(-2.26f, 0.5f))
                val rot = matCompose(0f, 0f, 0f, ry = -PI.toFloat() / 2f)
                bodyT = extrudeProfile(profileOf(bodyPts), 2.0f, 0.14f).transformed(rot)

                val cabinPts = mutableListOf(
                    floatArrayOf(0.78f, 0.84f), floatArrayOf(0.05f, 1.3f), floatArrayOf(-0.95f, 1.32f),
                    floatArrayOf(-1.62f, 0.96f), floatArrayOf(-1.2f, 0.9f), floatArrayOf(0.4f, 0.84f))
                cabinT = extrudeProfile(profileOf(cabinPts), 1.46f, 0.12f).transformed(rot)

                val skirtPts = mutableListOf(
                    floatArrayOf(-1.9f, 0.3f), floatArrayOf(0.84f, 0.3f), floatArrayOf(0.84f, 0.42f), floatArrayOf(-1.9f, 0.42f))
                skirtT = extrudeProfile(profileOf(skirtPts), 2.08f, 0.03f).transformed(rot)

                tireT = Geom.cylinder(0.47f, 0.47f, 0.4f, 20).transformed(matCompose(0f, 0f, 0f, rz = PI.toFloat() / 2f))
                rimT = mergeTemplates(listOf(
                    Geom.cylinder(0.31f, 0.31f, 0.42f, 14).transformed(matCompose(0f, 0f, 0f, rz = PI.toFloat() / 2f)),
                    Geom.box(0.44f, 0.07f, 0.55f).transformed(matCompose(0f, 0f, 0f, rx = 0f)),
                    Geom.box(0.44f, 0.07f, 0.55f).transformed(matCompose(0f, 0f, 0f, rx = PI.toFloat() / 3f)),
                    Geom.box(0.44f, 0.07f, 0.55f).transformed(matCompose(0f, 0f, 0f, rx = PI.toFloat() * 2f / 3f)),
                ))
            }
            val mk = makeMesh

            fun part(t: MeshTemplate, m: Mat, underRoot: Boolean = true, local: M4 = M4()) {
                cm.parts.add(CarPart(mk(t), m, local, underRoot))
            }
            fun col(c: Int) = Mat(
                colorR = ((c shr 16) and 0xff) / 255f, colorG = ((c shr 8) and 0xff) / 255f, colorB = (c and 0xff) / 255f)

            // 车身漆面 / 座舱玻璃 / 侧裙
            part(bodyT!!, Mat(colorR = skin.bodyR, colorG = skin.bodyG, colorB = skin.bodyB, specStrength = 0.5f))
            part(cabinT!!, Mat(colorR = 0.047f, colorG = 0.07f, colorB = 0.125f, specStrength = 0.85f))
            part(skirtT!!, col(skin.accent))

            // 车顶赛车条纹
            part(Geom.box(0.34f, 0.03f, 1.5f).transformed(matCompose(0f, 0.84f, 1.45f, rx = 0.1f)), col(skin.accent))
            // 尾翼
            part(Geom.box(2.1f, 0.07f, 0.46f).transformed(matCompose(0f, 1.36f, -1.95f, rx = -0.12f)), col(skin.accent))
            for (sx in floatArrayOf(-0.62f, 0.62f))
                part(Geom.box(0.08f, 0.42f, 0.18f).transformed(matCompose(sx, 1.12f, -1.95f)), col(0x15171c))
            for (sx in floatArrayOf(-1.06f, 1.06f))
                part(Geom.box(0.05f, 0.28f, 0.6f).transformed(matCompose(sx, 1.32f, -1.97f)), Mat(colorR = skin.bodyR, colorG = skin.bodyG, colorB = skin.bodyB))
            // 车灯（自发光）
            for (sx in floatArrayOf(-0.62f, 0.62f))
                part(Geom.box(0.5f, 0.1f, 0.12f).transformed(matCompose(sx, 0.64f, 2.3f, rx = -0.5f)),
                    Mat(colorR = 0.91f, colorG = 0.957f, colorB = 1f, unlit = true))
            // 贯穿式尾灯 + 两侧灯组
            part(Geom.box(1.5f, 0.07f, 0.08f).transformed(matCompose(0f, 0.78f, -2.39f)),
                Mat(colorR = 1f, colorG = 0.125f, colorB = 0.19f, unlit = true))
            for (sx in floatArrayOf(-0.72f, 0.72f))
                part(Geom.box(0.34f, 0.16f, 0.08f).transformed(matCompose(sx, 0.74f, -2.39f)),
                    Mat(colorR = 1f, colorG = 0.125f, colorB = 0.19f, unlit = true))
            // 后扩散器 / 前格栅
            part(Geom.box(1.6f, 0.16f, 0.3f).transformed(matCompose(0f, 0.36f, -2.3f)), col(0x15171c))
            part(Geom.box(1.1f, 0.12f, 0.1f).transformed(matCompose(0f, 0.42f, 2.4f)), col(0x15171c))
            // 侧面霓虹灯条
            for (sx in floatArrayOf(-1.05f, 1.05f))
                part(Geom.box(0.03f, 0.05f, 2.2f).transformed(matCompose(sx, 0.46f, -0.5f)),
                    Mat(colorR = ((skin.glow shr 16) and 0xff) / 255f, colorG = ((skin.glow shr 8) and 0xff) / 255f, colorB = (skin.glow and 0xff) / 255f, unlit = true))
            // 排气管
            for (sx in floatArrayOf(-0.42f, 0.42f)) {
                part(Geom.cylinder(0.1f, 0.12f, 0.3f, 10).transformed(matCompose(sx, 0.4f, -2.42f, rx = PI.toFloat() / 2f)), col(skin.rim))
                cm.flames.add(floatArrayOf(sx, 0.4f, -2.56f))
            }

            // 底盘光（加色面片）
            cm.underglow = makeMesh(Geom.ground(3.2f, 5.6f))
            // 护盾
            cm.shield = makeMesh(Geom.sphere(2.9f, 20, 12).transformed(matCompose(0f, 0.8f, 0f, 0f, 0f, 0f, 1f, 0.6f, 1.25f)))
            // 尾焰锥体
            cm.flameOuter = mk(Geom.cone(0.2f, 1.6f, 10).transformed(matCompose(0f, 0f, -0.8f, rx = -PI.toFloat() / 2f)))
            cm.flameInner = mk(Geom.cone(0.1f, 0.9f, 8).transformed(matCompose(0f, 0f, -0.45f, rx = -PI.toFloat() / 2f)))
            cm.flameMat = Mat(colorR = 0.22f, colorG = 0.63f, colorB = 1f, unlit = true, additive = true, doubleSide = true, depthWrite = false)

            // 车轮 ×4
            val wheelPos = listOf(floatArrayOf(-0.93f, 1.38f), floatArrayOf(0.93f, 1.38f), floatArrayOf(-0.93f, -1.38f), floatArrayOf(0.93f, -1.38f))
            for (wp in wheelPos) {
                cm.wheels.add(WheelDef(wp[0], wp[1], wp[1] > 0, mk(tireT!!), mk(rimT!!)))
            }
            cache[skin.id] = cm
            return cm
        }
    }
}
