package com.feiche.qqspeed.game

import com.feiche.qqspeed.gl.Mat
import com.feiche.qqspeed.gl.RItem
import com.feiche.qqspeed.gl.TexLib
import kotlin.math.*

// 道具系统（移植 items.js）
class ItemSystem(private val game: Game, private val track: Track, private val rnd: Rnd) {
    private val renderer = game.renderer

    companion object {
        val WEIGHTS_LEAD = mapOf("banana" to 35, "shield" to 30, "nitro" to 20, "missile" to 10, "magnet" to 5)
        val WEIGHTS_MID = mapOf("missile" to 25, "nitro" to 25, "banana" to 20, "magnet" to 15, "shield" to 15)
        val WEIGHTS_BACK = mapOf("missile" to 32, "nitro" to 30, "magnet" to 25, "shield" to 10, "banana" to 3)
    }

    class BoxInst(val x: Float, val y: Float, val z: Float) {
        var respawn = 0f
        var visible = true
        lateinit var cubeItem: RItem
        lateinit var qItem1: RItem
        lateinit var qItem2: RItem
    }

    class MissileInst(val mesh: RItem, val nose: RItem, var target: Car?, val owner: Car, val dir: V3, var life: Float, var v: Float)

    class BananaInst(val mesh: RItem, val owner: Car, var age: Float)

    val boxes = ArrayList<BoxInst>()
    val missiles = ArrayList<MissileInst>()
    val bananas = ArrayList<BananaInst>()

    private val cubeT: MeshTemplate = Geom.box(1.8f, 1.8f, 1.8f)
    private val qT: MeshTemplate = Geom.plane(1.5f, 1.5f)
    private val missileBodyT: MeshTemplate = Geom.cylinder(0.22f, 0.22f, 1.6f, 10).transformed(matCompose(0f, 0f, 0f, rx = PI.toFloat() / 2f))
    private val missileNoseT: MeshTemplate = Geom.cone(0.22f, 0.5f, 10).transformed(matCompose(0f, 0f, 1.05f, rx = PI.toFloat() / 2f))
    private val bananaT: MeshTemplate = mergeTemplates(listOf(
        Geom.torus(0.55f, 0.22f, 8, 16, PI.toFloat() * 1.3f).transformed(matCompose(0f, 0f, 0f, rx = PI.toFloat() / 2f)),
        Geom.cone(0.2f, 0.7f, 6).transformed(matCompose(0.5f, 0.1f, 0f, 0f, 1f, 1f, 1f, 0f, PI.toFloat() / 2f)),
        Geom.cone(0.2f, 0.7f, 6).transformed(matCompose(cos(2.1f) * 0.5f, 0.1f, sin(2.1f) * 0.5f, 2.1f, 1f, 1f, 1f, 0f, PI.toFloat() / 2f)),
        Geom.cone(0.2f, 0.7f, 6).transformed(matCompose(cos(4.2f) * 0.5f, 0.1f, sin(4.2f) * 0.5f, 4.2f, 1f, 1f, 1f, 0f, PI.toFloat() / 2f)),
    ))

    private val cubeMat = Mat(colorR = 0.333f, colorG = 0.784f, colorB = 1f, unlit = true, alpha = 0.85f)
    private val missileMat = Mat(colorR = 1f, colorG = 1f, colorB = 1f, specStrength = 0.5f)
    private val noseMat = Mat(colorR = 1f, colorG = 0.19f, colorB = 0.19f, unlit = true)
    private val bananaMat = Mat(colorR = 1f, colorG = 0.83f, colorB = 0f, specStrength = 0.1f)

    init {
        TexLib.register("item_q") {
            TexGen.textTexture("?", 128, 128, fg = 0xffffffff.toInt(), sizePx = 110f, italic = false, stroke = 0xff1d4fb0.toInt())
        }
        val hw = track.halfW
        for (f in listOf(0.1f, 0.35f, 0.6f, 0.85f)) {
            val d = f * track.length
            val s = track.sample(d, SampleOut())
            for (latF in listOf(-0.6f, -0.3f, 0f, 0.3f, 0.6f)) {
                val L = latF * hw
                val bx = s.x + s.rx * L
                val by = s.y + L * sin(s.bank) + 1.6f
                val bz = s.z + s.rz * L
                val inst = BoxInst(bx, by, bz)
                inst.cubeItem = RItem(renderer.glOf(cubeT), cubeMat, order = 0)
                inst.qItem1 = RItem(renderer.glOf(qT), Mat(mapName = "item_q", unlit = true, doubleSide = true, depthWrite = false, repeat = false), order = 0)
                inst.qItem2 = RItem(renderer.glOf(qT), Mat(mapName = "item_q", unlit = true, doubleSide = true, depthWrite = false, repeat = false), order = 0)
                renderer.items.add(inst.cubeItem)
                renderer.items.add(inst.qItem1)
                renderer.items.add(inst.qItem2)
                boxes.add(inst)
            }
        }
    }

    private fun pick(weights: Map<String, Int>): String {
        var tot = 0
        for (k in weights.keys) tot += weights[k]!!
        var r = rnd.next() * tot
        for ((k, w) in weights) {
            r -= w
            if (r <= 0) return k
        }
        return "nitro"
    }

    fun dispose() {
        for (b in boxes) {
            renderer.items.remove(b.cubeItem); renderer.items.remove(b.qItem1); renderer.items.remove(b.qItem2)
        }
        for (m in missiles) { renderer.items.remove(m.mesh); renderer.items.remove(m.nose) }
        for (b in bananas) renderer.items.remove(b.mesh)
        boxes.clear(); missiles.clear(); bananas.clear()
    }

    fun giveRandom(r: Car, rank: Int, total: Int) {
        if (r.items.size >= 2) return
        val w = if (rank == 1) WEIGHTS_LEAD else if (rank >= total - 1) WEIGHTS_BACK else WEIGHTS_MID
        r.items.add(pick(w))
    }

    fun swap(r: Car) {
        if (r.items.size == 2) { val t = r.items[0]; r.items[0] = r.items[1]; r.items[1] = t }
    }

    fun use(r: Car) {
        val it = if (r.items.isNotEmpty()) r.items.removeAt(0) else return
        when (it) {
            "nitro" -> if (r.isPlayer) r.triggerNitro(true) else { r.nitroTime = 2.6f }
            "shield" -> { r.shield = 7f; game.audio.play("shield") }
            "magnet" -> {
                r.magnet = 3f
                r.magnetTarget = game.racerAhead(r)
            }
            "banana" -> {
                val bx = r.x - sin(r.h) * 3.2f
                val bz = r.z - cos(r.h) * 3.2f
                val p = track.project(bx, r.y, bz, r.hint, SampleOut())
                val mesh = RItem(renderer.glOf(bananaT), bananaMat, order = 0)
                renderer.items.add(mesh)
                bananas.add(BananaInst(mesh, r, 0f))
                mesh.userData = floatArrayOf(bx, p.y + p.lat * sin(p.bank) + 0.25f, bz, 1.4f)
                game.audio.play("banana")
            }
            "missile" -> {
                val target = game.racerAhead(r)
                val mesh = RItem(renderer.glOf(missileBodyT), missileMat, order = 0)
                val nose = RItem(renderer.glOf(missileNoseT), noseMat, order = 0)
                renderer.items.add(mesh); renderer.items.add(nose)
                missiles.add(MissileInst(mesh, nose, target, r,
                    V3(sin(r.h), 0f, cos(r.h)), 6f, max(80f, abs(r.s) + 30f)))
                game.audio.play("missile")
                if (target != null && target.isPlayer) game.onMissileLock()
            }
        }
    }

    fun update(dt: Float, racers: List<Car>, standings: List<Car>, t: Float) {
        // 道具箱
        for (b in boxes) {
            if (b.respawn > 0) {
                b.respawn -= dt
                if (b.respawn <= 0) b.visible = true
                if (!b.visible) continue
            }
            val ry = t * 1.6f
            val rx = sin(t * 1.3f) * 0.25f
            val bobY = b.y + sin(t * 2f + b.x) * 0.2f
            b.cubeItem.matrix.compose(b.x, bobY, b.z, rx, ry, 0f, 1f, 1f, 1f)
            b.qItem1.matrix.compose(b.x, bobY, b.z, rx, ry, 0f, 1f, 1f, 1f)
            b.qItem2.matrix.compose(b.x, bobY, b.z, rx, ry + PI.toFloat() / 2f, 0f, 1f, 1f, 1f)
            b.cubeItem.visible = b.visible; b.qItem1.visible = b.visible; b.qItem2.visible = b.visible
            if (!b.visible) continue
            for (r in racers) {
                val dx = r.x - b.x; val dz = r.z - b.z; val dy = r.y + 0.8f - b.y
                if (dx * dx + dz * dz < 6.5f && abs(dy) < 3f) {
                    b.visible = false
                    b.respawn = 2.5f
                    val rank = standings.indexOf(r) + 1
                    giveRandom(r, rank, racers.size)
                    game.burst(b.x, b.y, b.z, 0x55c8ff)
                    break
                }
            }
        }
        // 香蕉皮
        for (i in bananas.indices.reversed()) {
            val bn = bananas[i]
            bn.age += dt
            bn.mesh.rotationY += dt
            val ud = bn.mesh.userData as FloatArray
            bn.mesh.matrix.compose(ud[0], ud[1], ud[2], 0f, bn.mesh.rotationY, 0f, ud[3], ud[3], ud[3])
            var hit: Car? = null
            for (r in racers) {
                if (r === bn.owner && bn.age < 1.2f) continue
                val dx = r.x - ud[0]; val dz = r.z - ud[2]
                if (dx * dx + dz * dz < 4.4f && abs(r.y - ud[1]) < 2.5f) { hit = r; break }
            }
            if (hit != null || bn.age > 90f) {
                if (hit != null) game.hitRacer(hit, "banana")
                renderer.items.remove(bn.mesh)
                bananas.removeAt(i)
            }
        }
        // 导弹：追踪目标
        for (i in missiles.indices.reversed()) {
            val m = missiles[i]
            m.life -= dt
            var px = m.mesh.matrix.e[12]; var py = m.mesh.matrix.e[13]; var pz = m.mesh.matrix.e[14]
            if (m.target != null && !m.target!!.finished) {
                val tg = m.target!!
                val tox = tg.x - px; val toy = tg.y + 0.8f - py; val toz = tg.z - pz
                val dist = kotlin.math.sqrt(tox * tox + toy * toy + toz * toz)
                val k = min(1f, dt * 6f)
                m.dir.x = m.dir.x + (tox / dist - m.dir.x) * k
                m.dir.y = m.dir.y + (toy / dist - m.dir.y) * k
                m.dir.z = m.dir.z + (toz / dist - m.dir.z) * k
                m.dir.norm()
                m.v = max(m.v, abs(tg.s) + 35f)
                if (dist < 2.6f) {
                    game.hitRacer(tg, "missile")
                    game.explode(px, py, pz)
                    renderer.items.remove(m.mesh); renderer.items.remove(m.nose)
                    missiles.removeAt(i)
                    continue
                }
            }
            px += m.dir.x * m.v * dt; py += m.dir.y * m.v * dt; pz += m.dir.z * m.v * dt
            m.mesh.matrix.compose(px, py, pz, 0f, m.mesh.rotationY, 0f, 1f, 1f, 1f)
            // 朝向速度方向
            m.mesh.matrix.compose(px, py, pz, 0f, kotlin.math.atan2(m.dir.x, m.dir.z), 0f, 1f, 1f, 1f)
            m.nose.matrix.set(m.mesh.matrix)
            game.trail(px - m.dir.x, py - m.dir.y, pz - m.dir.z)
            if (m.life <= 0) {
                game.explode(px, py, pz)
                renderer.items.remove(m.mesh); renderer.items.remove(m.nose)
                missiles.removeAt(i)
            }
        }
    }

    // AI 使用道具的简单决策
    fun aiThink(r: Car, dt: Float, standings: List<Car>) {
        if (r.items.isEmpty()) return
        r.itemTimer -= dt
        if (r.itemTimer > 0) return
        r.itemTimer = 1f + rnd.next() * 3f
        val it = r.items[0]
        val ahead = game.racerAhead(r)
        val gap = if (ahead != null) ahead.progress - r.progress else 1e9f
        if (it == "missile" && (ahead == null || gap > 350f)) { if (r.items.size > 1) swap(r); return }
        if (it == "magnet" && (ahead == null || gap > 150f)) return
        use(r)
    }
}
