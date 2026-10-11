package com.feiche.qqspeed.gl

import android.opengl.GLES20
import com.feiche.qqspeed.game.M4
import com.feiche.qqspeed.game.V3
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// 场景渲染项
class RItem(
    val mesh: GlMesh?,
    val mat: Mat,
    val matrix: M4 = M4(),
    var order: Int = 0,
) {
    var colorMul: FloatArray? = null // [r,g,b,a] 覆盖材质色
    var uvOffY: Float = 0f
    var visible = true
    var alphaMul: Float = 1f
    var rotationY: Float = 0f
    var userData: Any? = null
}

class Camera {
    var fov = 68f
    var x = 0f; var y = 50f; var z = 0f
    var lx = 0f; var ly = 0f; var lz = 1f // look at
    val pos = V3()
    var aspect = 1f
    var near = 0.3f
    var far = 9000f
    val pv = FloatArray(16)
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val planes = FloatArray(24) // 6 平面 * (a,b,c,d)
    val right = V3(1f, 0f, 0f)
    val up = V3(0f, 1f, 0f)

    fun update() {
        pos.set(x, y, z)
        perspective(proj, fov, aspect, near, far)
        lookAt(view, x, y, z, lx, ly, lz)
        mul(pv, proj, view)
        extractPlanes()
        // 相机右/上向量（从 view 矩阵）
        right.set(view[0], view[4], view[8])
        up.set(view[1], view[5], view[9])
    }

    private fun extractPlanes() {
        // Gribb-Hartmann：行主序访问 pv（列主序存储 → m[col*4+row]）
        fun row(i: Int) = floatArrayOf(pv[i], pv[4 + i], pv[8 + i], pv[12 + i])
        val r0 = row(0); val r1 = row(1); val r2 = row(2); val r3 = row(3)
        fun setPlane(pi: Int, a: Float, b: Float, c: Float, d: Float) {
            val l = kotlin.math.sqrt(a * a + b * b + c * c)
            planes[pi * 4] = a / l; planes[pi * 4 + 1] = b / l; planes[pi * 4 + 2] = c / l; planes[pi * 4 + 3] = d / l
        }
        setPlane(0, r3[0] + r0[0], r3[1] + r0[1], r3[2] + r0[2], r3[3] + r0[3]) // left
        setPlane(1, r3[0] - r0[0], r3[1] - r0[1], r3[2] - r0[2], r3[3] - r0[3]) // right
        setPlane(2, r3[0] + r1[0], r3[1] + r1[1], r3[2] + r1[2], r3[3] + r1[3]) // bottom
        setPlane(3, r3[0] - r1[0], r3[1] - r1[1], r3[2] - r1[2], r3[3] - r1[3]) // top
        setPlane(4, r3[0] + r2[0], r3[1] + r2[1], r3[2] + r2[2], r3[3] + r2[3]) // near
        setPlane(5, r3[0] - r2[0], r3[1] - r2[1], r3[2] - r2[2], r3[3] - r2[3]) // far
    }

    fun sphereVisible(cx: Float, cy: Float, cz: Float, r: Float): Boolean {
        for (p in 0 until 6) {
            val o = p * 4
            if (planes[o] * cx + planes[o + 1] * cy + planes[o + 2] * cz + planes[o + 3] < -r) return false
        }
        return true
    }

    companion object {
        fun perspective(m: FloatArray, fovyDeg: Float, aspect: Float, near: Float, far: Float) {
            val f = 1f / tan(Math.toRadians(fovyDeg.toDouble()) / 2.0).toFloat()
            java.util.Arrays.fill(m, 0f)
            m[0] = f / aspect; m[5] = f
            m[10] = (far + near) / (near - far); m[11] = -1f
            m[14] = 2f * far * near / (near - far)
        }

        fun lookAt(m: FloatArray, ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float) {
            var zx = ex - tx; var zy = ey - ty; var zz = ez - tz
            var l = kotlin.math.sqrt(zx * zx + zy * zy + zz * zz)
            if (l < 1e-6f) { zx = 0f; zy = 0f; zz = 1f; l = 1f }
            zx /= l; zy /= l; zz /= l
            // up = (0,1,0)
            var xx = 1f * zz - 0f * zy
            var xy = 0f * zx - 0f * zz
            var xz = 0f * zy - 1f * zx
            l = kotlin.math.sqrt(xx * xx + xy * xy + xz * xz)
            if (l < 1e-6f) { xx = 1f; xy = 0f; xz = 0f } else { xx /= l; xy /= l; xz /= l }
            val yx = zy * xz - zz * xy
            val yy = zz * xx - zx * xz
            val yz = zx * xy - zy * xx
            m[0] = xx; m[1] = yx; m[2] = zx; m[3] = 0f
            m[4] = xy; m[5] = yy; m[6] = zy; m[7] = 0f
            m[8] = xz; m[9] = yz; m[10] = zz; m[11] = 0f
            m[12] = -(xx * ex + xy * ey + xz * ez)
            m[13] = -(yx * ex + yy * ey + yz * ez)
            m[14] = -(zx * ex + zy * ey + zz * ez)
            m[15] = 1f
        }

        fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
            for (c in 0 until 4) for (r in 0 until 4) {
                out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3]
            }
        }
    }
}

// 场景环境参数（每帧设置）
class Env {
    var sunX = 0f; var sunY = 1f; var sunZ = 0f
    var sunR = 1f; var sunG = 1f; var sunB = 1f
    var hemiSkyR = 0.7f; var hemiSkyG = 0.8f; var hemiSkyB = 1f
    var hemiGroundR = 0.4f; var hemiGroundG = 0.4f; var hemiGroundB = 0.35f
    var hemiI = 0.7f
    var fogR = 0.8f; var fogG = 0.9f; var fogB = 1f
    var fogNear = 400f; var fogFar = 2300f
    var fogOn = true
}

class Renderer {
    val cam = Camera()
    val env = Env()
    val items = ArrayList<RItem>()
    private val identity = M4()
    private val tmpM = M4()
    private val fbScratch: FloatBuffer = java.nio.ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // 精灵批（每帧重建）：云、名牌 — 顶点布局与常规网格一致（12 floats）
    class SpriteGroup {
        val dyn = DynMesh(4096, 12, 6144)
        val verts = FloatArray(4096 * 12)
        val idx = ShortArray(6144)
        var nV = 0; var nI = 0
    }
    private val sprites = HashMap<Int, SpriteGroup>() // key: texture id

    fun clearItems() {
        for (it in items) it.mesh?.dispose()
        items.clear()
        sprites.clear()
    }

    fun glOf(t: com.feiche.qqspeed.game.MeshTemplate): GlMesh = GlMesh.fromTemplate(t)

    fun beginSprites() {
        for (g in sprites.values) { g.nV = 0; g.nI = 0 }
    }

    fun addSprite(tex: Int, x: Float, y: Float, z: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
        var grp = sprites[tex]
        if (grp == null) { grp = SpriteGroup(); sprites[tex] = grp }
        if (grp.nV + 4 > 4096 || grp.nI + 6 > 6144) return
        val rx = cam.right.x; val ry = cam.right.y; val rz = cam.right.z
        val ux = cam.up.x; val uy = cam.up.y; val uz = cam.up.z
        val hw = w / 2; val hh = h / 2
        val base = grp.nV
        val v = grp.verts
        val cs = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val us = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
        for (k in 0 until 4) {
            val o = (grp.nV + k) * 12
            val cu = cs[k * 2]; val cv = cs[k * 2 + 1]
            v[o] = x + rx * cu * hw + ux * cv * hh
            v[o + 1] = y + ry * cu * hw + uy * cv * hh
            v[o + 2] = z + rz * cu * hw + uz * cv * hh
            v[o + 3] = 0f; v[o + 4] = 1f; v[o + 5] = 0f
            v[o + 6] = us[k * 2]; v[o + 7] = us[k * 2 + 1]
            v[o + 8] = r; v[o + 9] = g; v[o + 10] = b; v[o + 11] = a
        }
        grp.nV += 4
        val i = grp.idx
        var io = grp.nI
        i[io] = base.toShort(); i[io + 1] = (base + 1).toShort(); i[io + 2] = (base + 2).toShort()
        i[io + 3] = (base + 1).toShort(); i[io + 4] = (base + 3).toShort(); i[io + 5] = (base + 2).toShort()
        grp.nI += 6
    }

    fun flushSprites() {
        if (sprites.isEmpty()) return
        val sh = Shaders.lit
        sh.use()
        bindLitUniforms(unlit = true, fog = false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        val uHasMap = sh.u("uHasMap"); val uMap = sh.u("uMap")
        GLES20.glUniform1f(uHasMap, 1f)
        val aPos = sh.a("aPos"); val aN = sh.a("aNormal"); val aUV = sh.a("aUV"); val aC = sh.a("aColor")
        for ((tex, grp) in sprites) {
            if (grp.nI == 0) continue
            grp.dyn.upload(grp.verts, grp.nV, grp.idx, grp.nI)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glUniform1i(uMap, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, grp.dyn.vbo)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 48, 0)
            GLES20.glEnableVertexAttribArray(aN)
            GLES20.glVertexAttribPointer(aN, 3, GLES20.GL_FLOAT, false, 48, 12)
            GLES20.glEnableVertexAttribArray(aUV)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 48, 24)
            GLES20.glEnableVertexAttribArray(aC)
            GLES20.glVertexAttribPointer(aC, 4, GLES20.GL_FLOAT, false, 48, 32)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, grp.dyn.ibo)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, grp.dyn.countI, GLES20.GL_UNSIGNED_SHORT, 0)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun bindLitUniforms(unlit: Boolean, fog: Boolean) {
        val sh = Shaders.lit
        fbScratch.clear(); fbScratch.put(cam.pv).position(0)
        GLES20.glUniformMatrix4fv(sh.u("uPV"), 1, false, fbScratch)
        GLES20.glUniform3f(sh.u("uCamPos"), cam.x, cam.y, cam.z)
        GLES20.glUniform3f(sh.u("uSunDir"), env.sunX, env.sunY, env.sunZ)
        GLES20.glUniform3f(sh.u("uSunColor"), env.sunR, env.sunG, env.sunB)
        GLES20.glUniform3f(sh.u("uHemiSky"), env.hemiSkyR, env.hemiSkyG, env.hemiSkyB)
        GLES20.glUniform3f(sh.u("uHemiGround"), env.hemiGroundR, env.hemiGroundG, env.hemiGroundB)
        GLES20.glUniform1f(sh.u("uHemiI"), env.hemiI)
        GLES20.glUniform3f(sh.u("uFogColor"), env.fogR, env.fogG, env.fogB)
        GLES20.glUniform1f(sh.u("uFogNear"), env.fogNear)
        GLES20.glUniform1f(sh.u("uFogFar"), env.fogFar)
        GLES20.glUniform1f(sh.u("uFogOn"), if (fog && env.fogOn) 1f else 0f)
        GLES20.glUniform1f(sh.u("uUnlit"), if (unlit) 1f else 0f)
        GLES20.glUniform1f(sh.u("uSpec"), 0.22f)
        GLES20.glUniform3f(sh.u("uEmissive"), 0f, 0f, 0f)
        GLES20.glUniform2f(sh.u("uUvOff"), 0f, 0f)
    }

    private fun drawItem(it: RItem) {
        val mesh = it.mesh ?: return
        if (mesh.radius >= 0f && !it.matrix.isIdentity()) {
            // 动态变换的带包围球项也做粗剔除
        }
        val sh = Shaders.lit
        val m = it.matrix
        if (!m.isIdentity()) {
            GLES20.glUniformMatrix4fv(sh.u("uModel"), 1, false, m.e, 0)
        } else {
            GLES20.glUniformMatrix4fv(sh.u("uModel"), 1, false, identity.e, 0)
        }
        val mat = it.mat
        val hasMap = mat.mapName != null
        GLES20.glUniform1f(sh.u("uHasMap"), if (hasMap) 1f else 0f)
        if (hasMap) {
            TexLib.ensureLoaded(mat)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mat.glTex)
            GLES20.glUniform1i(sh.u("uMap"), 0)
            if (mat.scrollUv) GLES20.glUniform2f(sh.u("uUvOff"), 0f, it.uvOffY)
        }
        GLES20.glUniform1f(sh.u("uUnlit"), if (mat.unlit) 1f else 0f)
        val cm = it.colorMul
        if (cm != null) {
            GLES20.glUniform4f(sh.u("uColorMul"), cm[0], cm[1], cm[2], cm[3] * it.alphaMul)
            if (cm.size >= 7) GLES20.glUniform3f(sh.u("uEmissive"), cm[4], cm[5], cm[6])
        } else {
            GLES20.glUniform4f(sh.u("uColorMul"), mat.colorR, mat.colorG, mat.colorB, mat.alpha * it.alphaMul)
        }
        if (mat.polyOffset != 0) {
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(mat.polyOffset.toFloat(), mat.polyOffset.toFloat())
        }
        if (mat.doubleSide) GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo)
        val aPos = sh.a("aPos"); val aN = sh.a("aNormal"); val aUV = sh.a("aUV"); val aC = sh.a("aColor")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 48, 0)
        GLES20.glEnableVertexAttribArray(aN)
        GLES20.glVertexAttribPointer(aN, 3, GLES20.GL_FLOAT, false, 48, 12)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 48, 24)
        GLES20.glEnableVertexAttribArray(aC)
        GLES20.glVertexAttribPointer(aC, 4, GLES20.GL_FLOAT, false, 48, 32)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, if (mesh.uintIdx) GLES20.GL_UNSIGNED_INT else GLES20.GL_UNSIGNED_SHORT, 0)
        if (mat.polyOffset != 0) GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        if (mat.doubleSide) GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    fun render() {
        GLES20.glClearColor(env.fogR, env.fogG, env.fogB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        // 注：几何由多套生成器产生，统一不剔除背面，避免绕向差异导致整面消失
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)

        // ---- 天空 ----
        if (gameSkyMesh != null) drawSky()

        // ---- 不透明 ----
        val sh = Shaders.lit
        sh.use()
        bindLitUniforms(unlit = false, fog = true)
        for (it in items) {
            if (!it.visible || it.order != 0) continue
            drawItem(it)
        }
        // 贴花/带多边形偏移的不透明（order 1）
        for (it in items) {
            if (!it.visible || it.order != 1) continue
            drawItem(it)
        }

        // ---- 轮胎印（order 2，由 Game 通过 drawSkids 绘制）----
        // ---- 水面（order 4）----
        for (it in items) {
            if (!it.visible || it.order != 4) continue
            drawWater(it)
        }

        // ---- 加色混合（order 5）----
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        for (it in items) {
            if (!it.visible || it.order != 5) continue
            drawItem(it)
        }
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        // ---- 精灵 ----
        flushSprites()
    }

    var gameSkyMesh: GlMesh? = null
    private var skyInited = false

    private fun drawSky() {
        val sh = Shaders.sky
        sh.use()
        fbScratch.clear(); fbScratch.put(cam.pv).position(0)
        GLES20.glUniformMatrix4fv(sh.u("uPV"), 1, false, fbScratch)
        GLES20.glUniform3f(sh.u("uTop"), skyTop[0], skyTop[1], skyTop[2])
        GLES20.glUniform3f(sh.u("uHorizon"), skyHorizon[0], skyHorizon[1], skyHorizon[2])
        GLES20.glUniform3f(sh.u("uBottom"), skyBottom[0], skyBottom[1], skyBottom[2])
        GLES20.glUniform3f(sh.u("uSunDir"), env.sunX, env.sunY, env.sunZ)
        GLES20.glUniform3f(sh.u("uSunColor"), env.sunR, env.sunG, env.sunB)
        GLES20.glDepthMask(false)
        GLES20.glFrontFace(GLES20.GL_CW) // BackSide 球
        val aPos = sh.a("aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gameSkyMesh!!.vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, gameSkyMesh!!.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, gameSkyMesh!!.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glFrontFace(GLES20.GL_CCW)
        GLES20.glDepthMask(true)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    var skyTop = floatArrayOf(0.2f, 0.5f, 0.9f)
    var skyHorizon = floatArrayOf(0.8f, 0.9f, 1f)
    var skyBottom = floatArrayOf(0.5f, 0.7f, 0.85f)

    private fun drawWater(it: RItem) {
        val sh = Shaders.water
        sh.use()
        fbScratch.clear(); fbScratch.put(cam.pv).position(0)
        GLES20.glUniformMatrix4fv(sh.u("uPV"), 1, false, fbScratch)
        GLES20.glUniformMatrix4fv(sh.u("uModel"), 1, false, it.matrix.e, 0)
        GLES20.glUniform1f(sh.u("uTime"), waterTime)
        GLES20.glUniform3f(sh.u("uColor"), waterColor[0], waterColor[1], waterColor[2])
        GLES20.glUniform3f(sh.u("uDeep"), waterDeep[0], waterDeep[1], waterDeep[2])
        GLES20.glUniform3f(sh.u("uSky"), skyHorizon[0], skyHorizon[1], skyHorizon[2])
        GLES20.glUniform3f(sh.u("uSun"), env.sunX, env.sunY, env.sunZ)
        GLES20.glUniform3f(sh.u("uCamPos"), cam.x, cam.y, cam.z)
        GLES20.glUniform3f(sh.u("uFogColor"), env.fogR, env.fogG, env.fogB)
        GLES20.glUniform1f(sh.u("uFogNear"), env.fogNear)
        GLES20.glUniform1f(sh.u("uFogFar"), env.fogFar)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        val aPos = sh.a("aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, it.mesh!!.vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, it.mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, it.mesh.indexCount, if (it.mesh.uintIdx) GLES20.GL_UNSIGNED_INT else GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    var waterTime = 0f
    var waterColor = floatArrayOf(0.12f, 0.5f, 0.82f)
    var waterDeep = floatArrayOf(0.04f, 0.25f, 0.48f)

    // ---- 粒子（由 Game 调用）----
    fun drawParticles(dyn: DynMesh, additive: Boolean, vertCount: Int, idxCount: Int, softDotTex: Int) {
        if (idxCount == 0) return
        val sh = Shaders.particle
        sh.use()
        fbScratch.clear(); fbScratch.put(cam.pv).position(0)
        GLES20.glUniformMatrix4fv(sh.u("uPV"), 1, false, fbScratch)
        GLES20.glUniform1f(sh.u("uScale"), particleScale)
        GLES20.glUniform1f(sh.u("uFogOn"), if (env.fogOn) 1f else 0f)
        GLES20.glUniform3f(sh.u("uFogColor"), env.fogR, env.fogG, env.fogB)
        GLES20.glUniform1f(sh.u("uFogNear"), env.fogNear)
        GLES20.glUniform1f(sh.u("uFogFar"), env.fogFar)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, softDotTex)
        GLES20.glUniform1i(sh.u("uMap"), 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        if (additive) GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        else GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dyn.vbo)
        val aPos = sh.a("aPos"); val aColor = sh.a("aColor"); val aSize = sh.a("aSize")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, 0)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 32, 12)
        GLES20.glEnableVertexAttribArray(aSize)
        GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, 32, 28)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, dyn.ibo)
        GLES20.glDrawElements(GLES20.GL_POINTS, idxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    var particleScale = 600f

    // ---- 轮胎印 ----
    fun drawSkids(dyn: DynMesh, idxCount: Int) {
        if (idxCount == 0) return
        val sh = Shaders.skid
        sh.use()
        fbScratch.clear(); fbScratch.put(cam.pv).position(0)
        GLES20.glUniformMatrix4fv(sh.u("uPV"), 1, false, fbScratch)
        GLES20.glUniformMatrix4fv(sh.u("uModel"), 1, false, identity.e, 0)
        GLES20.glUniform3f(sh.u("uColor"), 0.066f, 0.066f, 0.066f)
        GLES20.glUniform3f(sh.u("uFogColor"), env.fogR, env.fogG, env.fogB)
        GLES20.glUniform1f(sh.u("uFogNear"), env.fogNear)
        GLES20.glUniform1f(sh.u("uFogFar"), env.fogFar)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-4f, -4f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dyn.vbo)
        val aPos = sh.a("aPos"); val aAlpha = sh.a("aAlpha")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(aAlpha)
        GLES20.glVertexAttribPointer(aAlpha, 1, GLES20.GL_FLOAT, false, 16, 12)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, dyn.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, idxCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}

// M4 判等（identity 缓存）
private val IDENT = M4()
fun M4.isIdentity(): Boolean {
    for (k in this.e.indices) {
        val want = if (k == 0 || k == 5 || k == 10 || k == 15) 1f else 0f
        if (this.e[k] != want) return false
    }
    return true
}
