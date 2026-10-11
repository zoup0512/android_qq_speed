package com.feiche.qqspeed.game

import android.content.Context
import com.feiche.qqspeed.gl.GlMesh
import com.feiche.qqspeed.gl.Mat
import com.feiche.qqspeed.gl.RItem
import com.feiche.qqspeed.gl.Renderer
import com.feiche.qqspeed.gl.TexLib
import kotlin.math.*

// 排行/难度配置
class DiffDef(val name: String, val skill: FloatArray, val rubber: FloatArray)
object Qualities { val NAMES = listOf("流畅", "均衡", "极致") }

// 颜色辅助
fun red(c: Int): Float = ((c shr 16) and 0xff) / 255f
fun green(c: Int): Float = ((c shr 8) and 0xff) / 255f
fun blue(c: Int): Float = (c and 0xff) / 255f
fun normalize(v: FloatArray): FloatArray {
    val l = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
    return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
}
// sRGB → 线性
fun toLin(c: Int): FloatArray = floatArrayOf(
    red(c).pow(2.2f), green(c).pow(2.2f), blue(c).pow(2.2f))

class GameInput {
    @Volatile var up = false; @Volatile var down = false
    @Volatile var left = false; @Volatile var right = false
    @Volatile var shift = false
    // 边沿触发
    @Volatile var upPressed = false; @Volatile var wPressed = false
    @Volatile var nitroPressed = false; @Volatile var swapPressed = false
    @Volatile var resetPressed = false

    fun consume(): CarInput {
        val c = CarInput(up, down, left, right, shift, upPressed, wPressed, nitroPressed, swapPressed, resetPressed)
        upPressed = false; wPressed = false; nitroPressed = false; swapPressed = false; resetPressed = false
        return c
    }
}

// HUD 快照
class HudData {
    var lap = "1/3"; var time = 0f; var best = 0f; var kmh = 0f
    var rank = 1; var total = 6
    var gauge = 0f; var nitro = 0
    var nitroOn = false; var smallOn = false
    var itemMode = false
    var standings: List<HudRow> = emptyList()
    var item0 = ""; var item1 = ""
    var warn: String? = null
    var finalCount: String? = null
    var countdown = ""
    var show = false
    class HudRow(val name: String, val me: Boolean, val fin: Boolean, val color: Int, val rank: Int)
}

interface HudSink {
    fun setupMinimap(track: Track)
    fun showResults(title: String, win: Boolean, stats: Car.RaceStats, rows: List<ResultRow>)
    fun onTogglePause() // 请求 UI 显示/隐藏暂停
}

class ResultRow(val rank: Int, val name: String, val total: String, val best: String, val me: Boolean)

class Game(private val context: Context) {
    val renderer = Renderer()
    val audio = GameAudio()
    val input = GameInput()

    // 设置
    var mapId = "city"; var mode = "speed"; var skinIdx = 0
    var laps = 3; var diffIdx = 1; var quality = 1 // 0 low 1 mid 2 high
    var song = 0

    // 场景
    var map: MapDef? = null
    var track: Track? = null
    var world: World? = null
    var gate: StartGate? = null
    private var skyMesh: GlMesh? = null
    private var carShadowMesh: GlMesh? = null
    private var shadowMat: Mat? = null
    var itemsSys: ItemSystem? = null

    // 状态
    var state = "menu" // menu/countdown/race/finish/paused
    var time = 0f
    var camMode = 0
    var shake = 0f
    private var pausedFrom = "race"
    private var acc = 0f
    private var raceTime = 0f
    private var cdT = 0f; private var cdShown = -1
    private var firstFinish = -1f
    private var startBoostReady = false; private var startTried = false
    private var resultShown = false
    private var finishT = 0f
    private var missileWarn = 0f
    var itemMode = false

    // 车手
    val racers = ArrayList<Car>()
    var player: Car? = null
    var standings: List<Car> = emptyList()
    private var demoTarget = 0; private var demoT = 0f; private var demoCut = false
    var camYaw = 0f
    private val camPos = V3(0f, 50f, 0f)
    private val camLook = V3()

    // 特效
    val smoke = Particles(900, false)
    val glow = Particles(900, true)
    val skids = SkidMarks(2400)
    private var softDotTex = 0

    var hudSink: HudSink? = null
    val hudData = HudData()
    private val hudLock = Any()
    // 消息队列（GL 线程 → UI）
    private val messages = ArrayDeque<Triple<String, Int, Boolean>>()
    private var speedLineK = 0f
    var speedLines = ArrayList<FloatArray>() // [angle, r, v, len, w]
    var hudSpeedLinesOn = false

    private var rnd = Rnd(1)

    companion object {
        val DIFFS = listOf(
            DiffDef("新手", floatArrayOf(0.55f, 0.75f), floatArrayOf(0.88f, 1.03f)),
            DiffDef("熟练", floatArrayOf(0.85f, 1.05f), floatArrayOf(0.93f, 1.06f)),
            DiffDef("车神", floatArrayOf(1.2f, 1.38f), floatArrayOf(0.97f, 1.1f)),
        )
        val LAPS = listOf(1, 2, 3, 5)
        val AI_NAMES = listOf("小橘子", "秋名山车神", "漂移少女", "氮气小王子", "风之子", "夜の车神", "闪电旋风", "小飞侠")
        const val DT = 1f / 120f
    }

    // ---------- 设置持久化 ----------
    fun loadSettings() {
        val sp = context.getSharedPreferences("feiche3d", Context.MODE_PRIVATE)
        mapId = sp.getString("map", "city") ?: "city"
        mode = sp.getString("mode", "speed") ?: "speed"
        skinIdx = sp.getInt("skin", 0).coerceIn(0, CarSkin.ALL.size - 1)
        laps = sp.getInt("laps", 3)
        if (!LAPS.contains(laps)) laps = 3
        diffIdx = sp.getInt("diff", 1).coerceIn(0, 2)
        quality = sp.getInt("quality", 1).coerceIn(0, 2)
        song = sp.getInt("song", 0)
    }

    fun saveSettings() {
        val sp = context.getSharedPreferences("feiche3d", Context.MODE_PRIVATE)
        sp.edit()
            .putString("map", mapId).putString("mode", mode)
            .putInt("skin", skinIdx).putInt("laps", laps)
            .putInt("diff", diffIdx).putInt("quality", quality)
            .putInt("song", song)
            .apply()
    }

    // ---------- 初始化（GL 线程） ----------
    fun initGL() {
        com.feiche.qqspeed.gl.Shaders.build()
        GlMesh.uintSupported = true
        TexLib.register("softdot") { TexGen.softDotTexture() }
        softDotTex = TexLib.get("softdot")
        val shadowT = Geom.ground(3.6f, 6.2f)
        carShadowMesh = GlMesh.fromTemplate(shadowT)
        shadowMat = Mat(mapName = "softdot", unlit = true, depthWrite = false, polyOffset = -6, alpha = 0.55f)
        loadMap(mapId)
        startDemo()
    }

    fun registerMapTextures(map: MapDef) {
        val th = map.theme
        TexLib.register("road_${th.road}") { TexGen.roadTexture(th.road) }
        TexLib.register("wall_${th.wall}") { TexGen.wallTexture(th.wall) }
        TexLib.register("curb_${hex(th.curbA)}_${hex(th.curbB)}") { TexGen.curbTexture(th.curbA, th.curbB) }
        TexLib.register("checker_${hex(th.checkerA)}_${hex(th.checkerB)}") { TexGen.checkerTexture(th.checkerA, th.checkerB) }
        TexLib.register("boost_${hex(th.boostColor)}") { TexGen.boostPadTexture(th.boostColor) }
        TexLib.register("ground_${map.ground}") { TexGen.groundTexture(map.ground) }
        if (th.shoulderTex != null) TexLib.register("ground_${th.shoulderTex}") { TexGen.groundTexture(th.shoulderTex!!) }
        if (th.tunnels.isNotEmpty()) TexLib.register("tunnel") { TexGen.tunnelTexture() }
        TexLib.register("cloud") { TexGen.cloudTexture() }
        for (kind in listOf("glass", "office", "modern", "aegean", "egypt", "lodge")) {
            TexLib.register("facade_$kind") { TexGen.facadeTexture(kind) }
            TexLib.register(kind) { TexGen.facadeTexture(kind) }
        }
        TexLib.register("crowd") { TexGen.crowdTexture() }
    }

    private fun hex(c: Int): String = Integer.toHexString(c and 0xffffff)    fun loadMap(id: String) {
        mapId = id
        val map = Maps.byId(id)
        this.map = map
        registerMapTextures(map)
        renderer.clearItems()
        track = Track(map)
        val tr = track!!
        rnd = Rnd(id.hashCode() * 31 + 7)
        val oases = ArrayList<FloatArray>()
        if (id == "egypt") {
            val cand = arrayOf(floatArrayOf(-110f, -60f, 22f), floatArrayOf(200f, -330f, 24f), floatArrayOf(-120f, 320f, 20f), floatArrayOf(140f, 200f, 18f), floatArrayOf(-300f, 60f, 24f))
            for (c in cand) if (tr.isFree(c[0], c[1], c[2] + 14)) oases.add(c)
        }
        world = World(WorldCtx(tr, map, Noise2D(id[0].code * 31 + 7), oases.toTypedArray()))
        val w = world!!

        // 环境光 / 雾 / 天空
        val env = renderer.env
        val sl = normalize(map.sun)
        env.sunX = sl[0]; env.sunY = sl[1]; env.sunZ = sl[2]
        env.sunR = red(map.sunColor) * map.sunI * 0.82f
        env.sunG = green(map.sunColor) * map.sunI * 0.82f
        env.sunB = blue(map.sunColor) * map.sunI * 0.82f
        env.hemiSkyR = red(map.hemiSky); env.hemiSkyG = green(map.hemiSky); env.hemiSkyB = blue(map.hemiSky)
        env.hemiGroundR = red(map.hemiGround); env.hemiGroundG = green(map.hemiGround); env.hemiGroundB = blue(map.hemiGround)
        env.hemiI = map.hemiI * 0.62f * 1.35f
        env.fogR = red(map.fog); env.fogG = green(map.fog); env.fogB = blue(map.fog)
        env.fogNear = map.fogNear; env.fogFar = map.fogFar
        renderer.skyTop = toLin(map.skyTop)
        renderer.skyHorizon = toLin(map.skyHorizon)
        renderer.skyBottom = toLin(map.skyBottom)
        renderer.waterColor = if (map.waterColor != null) toLin(map.waterColor!!) else floatArrayOf(0.1f, 0.4f, 0.7f)
        renderer.waterDeep = if (map.waterDeep != null) toLin(map.waterDeep!!) else floatArrayOf(0.03f, 0.15f, 0.3f)

        // 天空
        skyMesh = w.skyMeshRaw()
        renderer.gameSkyMesh = skyMesh

        // 地面
        val groundChunks = w.buildGround(true)
        for (g in groundChunks) {
            renderer.items.add(RItem(GlMesh.fromTemplate(g), Mat(mapName = "ground_${map.ground}", specStrength = 0.03f)))
        }
        // 赛道
        val parts = tr.build(map.theme, w.ground::heightAt)
        for (p in parts) {
            renderer.items.add(RItem(GlMesh.fromTemplate(p.tpl), p.mat, order = p.order))
        }
        // 加速带 RItem 引用（滚动贴图）
        var boostIdx = 0
        for (item in renderer.items) {
            if (item.mat.scrollUv || (item.mat.mapName?.startsWith("boost_") == true && item.order == 1)) {
                if (boostIdx < tr.boostPads.size) {
                    tr.boostPads[boostIdx].item = item
                    boostIdx++
                }
            }
        }
        // 水面
        if (map.waterColor != null) {
            val waterMesh = w.waterMeshRaw(if (map.waterLocal) 60f * 2.2f else 7000f)
            if (map.waterLocal) {
                for (o in oases) {
                    val it = RItem(waterMesh, Mat(unlit = true, alpha = 0f), order = 4) // 占位由着色器画
                    it.matrix.compose(o[0], map.waterY, o[1], 0f, 0f, 0f, (o[2] + 12) / 30f, 1f, (o[2] + 12) / 30f)
                    it.visible = true
                    renderer.items.add(it)
                    waterItems.add(it)
                }
            } else {
                val it = RItem(waterMesh, Mat(), order = 4)
                it.matrix.compose(tr.cx, map.waterY, tr.cz, 0f, 0f, 0f, 1f, 1f, 1f)
                renderer.items.add(it)
                waterItems.add(it)
            }
        }
        // 山脉
        for (m in w.mountainMeshes(rnd, map.mountains)) {
            renderer.items.add(RItem(GlMesh.fromTemplate(m), Mat(specStrength = 0f, doubleSide = true)))
        }
        // 云
        w.buildClouds(rnd, 26)
        // 装饰
        val gate = StartGate()
        this.gate = gate
        val chunks = Props.build(map, tr, rnd, w.ground::heightAt, Batch(), gate)
        for (c in chunks) {
            renderer.items.add(RItem(GlMesh.fromTemplate(c.tpl), c.mat, order = c.order))
        }
        for (lamp in gate.lampItems) renderer.items.add(lamp)

        hudSink?.setupMinimap(tr)
        skids.clear()
        song = map.bgm
        if (audio.started) audio.setSong(song)
    }

    // ---------- 车手 ----------
    fun clearRacers() {
        for (r in racers) {
            for (it in r.visualItems) renderer.items.remove(it)
            r.visualItems.clear()
        }
        racers.clear()
        player = null
    }

    fun makeRacers(withPlayer: Boolean) {
        clearRacers()
        val rnd = Rnd((System.currentTimeMillis() and 0xffff).toInt() + 7)
        val diff = DIFFS[diffIdx]
        val skins = CarSkin.ALL.indices.filter { it != skinIdx }.toMutableList()
        for (i in skins.indices.reversed()) {
            val j = rnd.int(i + 1)
            val t = skins[i]; skins[i] = skins[j]; skins[j] = t
        }
        val names = AI_NAMES.toMutableList()
        for (i in names.indices.reversed()) {
            val j = rnd.int(i + 1)
            val t = names[i]; names[i] = names[j]; names[j] = t
        }
        val total = 6
        val playerSlot = if (withPlayer) 3 else -1
        var ai = 0
        for (k in 0 until total) {
            val d = -14f - (k / 2) * 11f
            val lat = (if (k % 2 == 1) 1 else -1) * 5.5f
            val r: Car
            if (k == playerSlot) {
                r = Car(track!!, "我")
                r.isPlayer = true
                r.reset(d, lat)
                r.skin = CarSkin.ALL[skinIdx]
            } else {
                val skinIdxAI = if (withPlayer) skins[ai % skins.size] else (k + skinIdx) % CarSkin.ALL.size
                val name = names[ai % names.size]
                val skill = if (withPlayer) U.lerp(diff.skill[0], diff.skill[1], rnd.next()) else U.lerp(diff.skill[0], diff.skill[1], 0.8f + rnd.next() * 0.2f)
                r = AICar(track!!, name, skill, rnd)
                r.reset(d, lat)
                r.skin = CarSkin.ALL[skinIdxAI]
                ai++
            }
            r.slot = k
            r.lapTimes.clear()
            r.items.clear()
            r.itemTimer = 2f + rnd.next() * 2f
            r.finished = false
            r.finishTime = 0f
            if (r.isPlayer) {
                r.lapsDone = -1
                r.owed = true
                r.half = false
                r.lastD = r.d
                player = r
            }
            buildCarVisual(r)
            racers.add(r)
        }
        for (r in racers) updateProgress(r, true)
        standings = racers.toList()
    }

    private fun buildCarVisual(r: Car) {
        val meshes = CarMeshes.get(r.skin, { GlMesh.fromTemplate(it) }, { GlMesh.fromTemplate(it) })
        r.meshes = meshes
        val skin = r.skin
        // root 部件（共享一个矩阵实例）
        val rootMat = M4()
        for (p in meshes.parts) {
            val item = RItem(p.mesh, p.mat, rootMat, order = 0)
            r.visualItems.add(item)
            renderer.items.add(item)
        }
        r.rootMatrix = rootMat
        // 底盘光
        val under = RItem(meshes.underglow, Mat(mapName = "softdot", unlit = true, additive = true, depthWrite = false, colorR = ((skin.glow shr 16) and 0xff) / 255f, colorG = ((skin.glow shr 8) and 0xff) / 255f, colorB = (skin.glow and 0xff) / 255f, alpha = 0.85f, polyOffset = -4), M4(), order = 5)
        r.underglowItem = under
        renderer.items.add(under); r.visualItems.add(under)
        // 护盾
        val shield = RItem(meshes.shield, Mat(colorR = 1f, colorG = 0.886f, colorB = 0.54f, unlit = true, additive = true, depthWrite = false, alpha = 0.25f), M4(), order = 5)
        shield.visible = false
        r.shieldItem = shield
        renderer.items.add(shield); r.visualItems.add(shield)
        // 尾焰
        val fm = meshes.flameMat!!
        for (e in meshes.flames) {
            val outer = RItem(meshes.flameOuter, fm, M4(), order = 5)
            val inner = RItem(meshes.flameInner, Mat(colorR = 1f, colorG = 1f, colorB = 1f, unlit = true, additive = true, doubleSide = true, depthWrite = false), M4(), order = 5)
            outer.visible = false; inner.visible = false
            outer.userData = e; inner.userData = e
            renderer.items.add(outer); renderer.items.add(inner)
            r.visualItems.add(outer); r.visualItems.add(inner)
            r.flameItems.add(Pair(outer, inner))
        }
        // 车轮
        for (w in meshes.wheels) {
            val tire = RItem(w.spinMesh, Mat(colorR = 0.1f, colorG = 0.1f, colorB = 0.1f, specStrength = 0.05f), M4(), order = 0)
            val rim = RItem(w.rimMesh, Mat(colorR = ((skin.rim shr 16) and 0xff) / 255f, colorG = ((skin.rim shr 8) and 0xff) / 255f, colorB = (skin.rim and 0xff) / 255f, specStrength = 0.7f), M4(), order = 0)
            renderer.items.add(tire); renderer.items.add(rim)
            r.visualItems.add(tire); r.visualItems.add(rim)
            r.wheelItems.add(Car.WheelVisual(w.x, w.z, w.front, tire, rim, 0f))
        }
        // 名牌（AI）
        if (!r.isPlayer) {
            val texName = "tag_${r.name}_${r.slot}"
            TexLib.register(texName) { TexGen.textTexture(r.name, 512, 96, sizePx = 56f, italic = false, stroke = 0xbf000000.toInt()) }
            r.tagTexName = texName
        }
        // 阴影
        val shadow = RItem(carShadowMesh, shadowMat!!, M4(), order = 3)
        renderer.items.add(shadow); r.visualItems.add(shadow)
        r.shadowItem = shadow
    }



    // ---------- 比赛流程 ----------
    fun startDemo() {
        val tr = track ?: return
        state = "menu"
        makeRacers(false)
        raceTime = 0f
        demoTarget = 0
        demoT = 0f
        itemsSys?.dispose()
        itemsSys = null
        synchronized(hudLock) { hudData.show = false }
    }

    fun startRace() {
        val tr = track ?: return
        itemMode = mode == "item"
        makeRacers(true)
        itemsSys?.dispose()
        itemsSys = if (itemMode) ItemSystem(this, tr, Rnd((System.currentTimeMillis() and 0xffff).toInt())) else null
        state = "countdown"
        cdT = 0f
        cdShown = -1
        raceTime = 0f
        firstFinish = -1f
        startBoostReady = false
        startTried = false
        resultShown = false
        skids.clear()
        camYaw = player!!.h
        snapCamera()
        audio.setSong(song)
        audio.startMusic()
        gate?.lampState = 0
        gate?.apply()
        synchronized(hudLock) { hudData.show = true }
    }

    fun togglePause() {
        if (state == "paused") {
            state = pausedFrom
            audio.setEngine(0f, 0f, false, false, false)
        } else if (state == "race" || state == "countdown" || state == "finish") {
            pausedFrom = state
            state = "paused"
            audio.silence()
        }
        hudSink?.onTogglePause()
    }

    fun toMenu() {
        startDemo()
        hudSink?.onTogglePause()
    }

    // ---------- 主循环 ----------
    fun frame(dtRaw: Float) {
        val dt = min(0.05f, dtRaw)
        time += dt
        val inp = input.consume()
        if (state != "paused") step(dt, inp)
        render(dt)
    }

    private fun step(dt: Float, inp: CarInput) {
        val st = state
        val racing = st == "race"
        if (st == "countdown") {
            cdT += dt
            val n = floor(cdT).toInt()
            if (n != cdShown && n <= 3) {
                cdShown = n
                postMessage(if (n < 3) "${3 - n}" else "GO!", 0xffffffff.toInt(), false, countdown = true)
                gate?.lampState = if (n < 3) n + 1 else 4
                gate?.apply()
                audio.play(if (n < 3) "count" else "go")
            }
            if (inp.upPressed && !startTried) {
                startTried = true
                if (cdT > 2.72f) startBoostReady = true
            }
            if (cdT >= 3f) {
                state = "race"
                raceTime = 0f
                if (startBoostReady) applyStartBoost()
                postMessage("", 0, false, countdown = true, clear = true)
            }
        }
        if (st == "finish") raceTime += dt
        if (st == "race") {
            raceTime += dt
            if (inp.upPressed && !startTried && raceTime < 0.28f) { startTried = true; applyStartBoost() }
            if (inp.upPressed) startTried = true
        }
        if (st == "menu") raceTime += dt

        val P = player
        // 玩家输入：完赛后自动驾驶
        var pin = inp
        if (P != null) {
            if (P.finished || resultShown) pin = autopilot(P)
            else if (racing) inp.up = true // 触屏自动油门
            if (inp.resetPressed && racing && !P.finished) resetPlayer()
            if (itemMode && racing && !P.finished) {
                if (inp.nitroPressed) itemsSys?.use(P)
                if (inp.swapPressed) itemsSys?.swap(P)
            }
        }
        val active = st == "race" || st == "finish"
        val aiActive = st == "race" || st == "menu" || st == "finish"
        // 固定步长
        acc += dt
        var first = true
        val noEdge = CarInput().also { it.up = pin.up; it.down = pin.down; it.left = pin.left; it.right = pin.right; it.shift = pin.shift }
        while (acc >= DT) {
            acc -= DT
            if (P != null) P.update(DT, if (first) pin else noEdge, active, itemMode)
            first = false
            for (r in racers) if (!r.isPlayer) (r as AICar).updateAI(DT, aiActive, raceTime, rubber(r))
            collide()
        }
        for (r in racers) {
            syncCarVisual(r, dt)
            updateProgress(r)
        }
        standings = racers.sortedWith(Comparator { a, b ->
            if (a.finished && b.finished) a.finishTime.compareTo(b.finishTime)
            else if (a.finished) -1
            else if (b.finished) 1
            else b.progress.compareTo(a.progress)
        })
        if (itemsSys != null && (racing || st == "finish")) {
            itemsSys!!.update(dt, racers, standings, time)
            for (r in racers) if (!r.isPlayer && !r.finished) itemsSys!!.aiThink(r, dt, standings)
        }
        if (P != null) handleEvents(P)
        emitEffects(dt)
        smoke.update(dt)
        glow.update(dt)
        // 速度线
        if (P != null && (racing || st == "finish")) {
            val k = U.clamp((abs(P.s) - 42f) / 30f, 0f, 1f) * 0.6f + (if (P.nitroTime > 0) 0.6f else 0f) + (if (P.smallBoost > 0) 0.25f else 0f)
            speedLineK = k
        } else speedLineK = 0f

        if (P != null && (racing || st == "finish" || st == "countdown")) updateRaceHUD()
        if (st == "race" || st == "finish") checkRaceEnd(dt)

        // 引擎声
        if (P != null && st != "menu") {
            audio.setEngine(min(1.2f, abs(P.s) / TUNE.vmaxNitro), P.throttle, P.boosting, P.drifting && !P.airborne, true)
        } else audio.silence()
    }

    fun rubber(r: Car): Float {
        if (player == null || state == "menu") return 1f
        val diff = DIFFS[diffIdx]
        val gap = r.progress - player!!.progress
        return if (gap > 0) max(diff.rubber[0], 1f - gap * 0.00045f)
        else min(diff.rubber[1], 1f - gap * 0.0004f)
    }

    fun applyStartBoost() {
        val P = player ?: return
        P.startBoost = 1.4f
        P.s = max(P.s, 16f)
        postMessage("起步加速!", 0xffd23a.toInt())
        audio.play("small")
    }

    fun resetPlayer() {
        val P = player ?: return
        val tr = track!!
        tr.sample(P.d, SampleOut())
        val keepLaps = P.lapsDone; val keepOwed = P.owed; val keepHalf = P.half; val keepLastD = P.lastD
        val keepGauge = P.gauge; val keepNitro = P.nitroCount
        P.reset(P.d, U.clamp(P.lat, -4f, 4f))
        P.lapsDone = keepLaps; P.owed = keepOwed; P.half = keepHalf; P.lastD = keepLastD
        P.gauge = keepGauge; P.nitroCount = keepNitro
        P.h = tr.hd[P.hint]; P.m = P.h
        P.s = 12f
        postMessage("复位", 0xffffffff.toInt(), small = true)
    }

    fun autopilot(r: Car): CarInput {
        val s = track!!.sample(r.d + 18f, SampleOut())
        val want = atan2(s.x - r.x, s.z - r.z)
        val e = U.wrapAngle(want - r.h)
        val c = CarInput()
        c.up = abs(r.s) < 38f
        c.left = e > 0.04f
        c.right = e < -0.04f
        return c
    }

    // ---------- 进度 / 圈数 ----------
    fun updateProgress(r: Car, init: Boolean = false) {
        val L = track!!.length
        if (r.isPlayer) {
            val d = r.d
            if (!init) {
                if (r.lastD > 0.75f * L && d < 0.25f * L) {
                    if (r.owed) { r.lapsDone++; r.owed = false }
                    else if (r.half) { r.lapsDone++; r.half = false; onLap(r) }
                } else if (r.lastD < 0.25f * L && d > 0.75f * L) {
                    r.lapsDone--
                    r.owed = true
                }
                if (d > 0.4f * L && d < 0.6f * L) r.half = true
            }
            r.lastD = d
            r.progress = r.lapsDone * L + d
        } else {
            val lapsN = floor(r.dist / L).toInt()
            if (!init && r.lapsDone != lapsN && lapsN >= 1 && lapsN > r.lapsDone) {
                r.lapsDone = lapsN
                onLap(r)
            } else r.lapsDone = lapsN
            r.progress = r.dist
        }
    }

    fun onLap(r: Car) {
        if (state != "race" && state != "finish") return
        val t = raceTime - r.lapStart
        r.lapStart = raceTime
        r.lapTimes.add(t)
        val lapsTotal = laps
        if (r.lapsDone >= lapsTotal && !r.finished) {
            r.finished = true
            r.finishTime = raceTime
            if (firstFinish < 0) firstFinish = raceTime
            if (r.isPlayer) {
                postMessage("完成比赛!", 0xffd23a.toInt())
                audio.play("finish")
                state = "finish"
                finishT = 0f
            }
            return
        }
        if (r.isPlayer) {
            val best = r.lapTimes.min()
            audio.play("lap")
            val bestMark = if (t <= best && r.lapTimes.size > 1) " 最佳!" else ""
            postMessage("第 ${r.lapsDone} 圈 ${U.formatTime(t)}$bestMark", 0x6dff9e.toInt(), small = true)
            if (r.lapsDone == lapsTotal - 1) postMessage("最后一圈!", 0xff5fa8.toInt(), delay = 0.9f)
        }
    }

    fun checkRaceEnd(dt: Float) {
        val P = player ?: return
        if (resultShown) return
        if (state == "finish") {
            finishT += dt
            val allDone = racers.all { it.finished }
            if (finishT > 4f && (allDone || raceTime - firstFinish > 10f || finishT > 12f)) showResults()
            return
        }
        if (firstFinish >= 0 && !P.finished) {
            val left = 10f - (raceTime - firstFinish)
            synchronized(hudLock) { hudData.finalCount = "有车手已冲线！剩余 ${max(0, ceil(left).toInt())} 秒" }
            if (left <= 0) showResults()
        }
    }

    fun showResults() {
        resultShown = true
        state = "finish"
        synchronized(hudLock) { hudData.finalCount = null }
        val P = player ?: return
        val L = track!!.length
        val order = standings
        val place = order.indexOf(P) + 1
        val title = if (!P.finished) "未完成比赛" else if (place == 1) "冠军！" else "第 $place 名"
        val rows = order.mapIndexed { i, r ->
            val best = if (r.lapTimes.isNotEmpty()) r.lapTimes.min() else 0f
            val total = if (r.finished) U.formatTime(r.finishTime)
            else "未完成 (${floor(max(0f, min(99f, r.progress / L * 100f / laps))).toInt()}%)"
            ResultRow(i + 1, r.name, total, U.formatTime(best), r.isPlayer)
        }
        hudSink?.showResults(title, P.finished && place == 1, P.stats, rows)
    }

    fun racerAhead(r: Car): Car? {
        val i = standings.indexOf(r)
        return if (i > 0) standings[i - 1] else null
    }

    // ---------- 碰撞 ----------
    private fun collide() {
        val rs = racers
        val R = 3.1f
        for (i in rs.indices) for (j in i + 1 until rs.size) {
            val a = rs[i]; val b = rs[j]
            val dx = b.x - a.x; val dz = b.z - a.z
            val d2 = dx * dx + dz * dz
            if (d2 > R * R || abs(a.y - b.y) > 2.5f) continue
            val d = sqrt(d2).coerceAtLeast(0.01f)
            val nx = dx / d; val nz = dz / d
            val over = R - d
            pushRacer(a, -nx * over * 0.5f, -nz * over * 0.5f)
            pushRacer(b, nx * over * 0.5f, nz * over * 0.5f)
            // 速度交换（简化）
            val va = vel(a); val vb = vel(b)
            val rel = (vb[0] - va[0]) * nx + (vb[1] - va[1]) * nz
            if (rel < 0) {
                val imp = -rel * 0.5f
                kick(a, -nx * imp, -nz * imp)
                kick(b, nx * imp, nz * imp)
                if ((a.isPlayer || b.isPlayer) && imp > 3f) {
                    audio.play("crash", imp)
                    shake = max(shake, 0.3f)
                    fxSparks((a.x + b.x) / 2, (a.y + b.y) / 2 + 0.6f, (a.z + b.z) / 2, 10)
                }
            }
        }
    }

    private fun vel(r: Car): FloatArray {
        if (r.isPlayer) return floatArrayOf(sin(r.m) * r.s, cos(r.m) * r.s)
        val s = track!!.sample(r.dist, SampleOut())
        return floatArrayOf(s.tx * r.s + s.rx * (r as AICar).latV, s.tz * r.s + s.rz * (r as AICar).latV)
    }

    private fun pushRacer(r: Car, px: Float, pz: Float) {
        if (r.isPlayer) { r.x += px; r.z += pz; return }
        val s = track!!.sample(r.dist, SampleOut())
        (r as AICar).lat += px * s.rx + pz * s.rz
        r.pushD += px * s.tx + pz * s.tz
    }

    private fun kick(r: Car, vx: Float, vz: Float) {
        if (r.isPlayer) {
            val cx = sin(r.m) * r.s + vx; val cz = cos(r.m) * r.s + vz
            val ns = sqrt(cx * cx + cz * cz)
            if (r.s >= 0 && ns > 0.5f) { r.s = ns; r.m = atan2(cx, cz) }
            return
        }
        val s = track!!.sample(r.dist, SampleOut())
        r.s = max(0f, r.s + vx * s.tx + vz * s.tz)
        (r as AICar).latV += vx * s.rx + vz * s.rz
    }

    fun hitRacer(r: Car, kind: String) {
        if (r.shield > 0) {
            r.shield = 0f
            sfx("shield", r)
            if (r.isPlayer) postMessage("天使护体!", 0xffe28a.toInt(), small = true)
            return
        }
        r.spin = if (kind == "missile") 1.3f else 1f
        r.s *= 0.35f
        if (kind == "missile") { r.airborne = true; r.vy = 8f }
        if (r.isPlayer) {
            r.endDrift(false)
            postMessage(if (kind == "missile") "被导弹击中!" else "踩到香蕉皮!", 0xff4b4b.toInt())
            flash = 1f
            shake = 0.8f
        }
        sfx(if (kind == "missile") "boom" else "banana", r)
    }

    private fun sfx(name: String, r: Car) {
        val P = player ?: return
        val d = sqrt((r.x - P.x).pow(2) + (r.z - P.z).pow(2))
        if (d < 120f) audio.play(name)
    }

    fun onMissileLock() {
        missileWarn = 1.6f
        audio.play("wrong")
    }

    // ---------- 玩家事件 → 提示/音效/特效 ----------
    private fun handleEvents(P: Car) {
        val st = P.stats
        st.top = max(st.top, P.speedKmh)
        for (e in P.events) {
            when (e.type) {
                "driftStart" -> st.drift++
                "smallBoost" -> {
                    st.small++
                    if (e.data[0] > 0.5f) st.perfect++
                    postMessage(if (e.data[0] > 0.5f) "完美小喷!" else "小喷!", 0xff9a1f.toInt())
                    audio.play("small")
                    boostBurst(P, 0xffa040)
                }
                "landBoost" -> {
                    st.small++
                    postMessage("落地喷!", 0xff9a1f.toInt())
                    audio.play("small")
                    boostBurst(P, 0xffa040)
                }
                "double" -> {
                    st.doubleB++
                    postMessage("双喷!!", 0xff5fa8.toInt(), delay = 0.12f)
                    audio.play("double")
                }
                "nitro" -> {
                    st.nitro++
                    postMessage("氮气加速!", 0x27c7ff.toInt())
                    audio.play("nitro")
                    boostBurst(P, 0x39a8ff)
                    shake = max(shake, 0.25f)
                }
                "gaugeFull" -> {
                    postMessage("集气完成 +1 N₂O", 0x7af0ff.toInt(), small = true)
                    audio.play("gauge")
                }
                "crash" -> {
                    st.crash++
                    audio.play("crash", e.data[0])
                    shake = max(shake, min(0.9f, e.data[0] * 0.05f))
                    fxSparks(e.data[1], P.y + 0.6f, e.data[2], 18)
                }
                "scrape" -> {
                    if (Math.random() < 0.3) fxSparks(e.data[0], P.y + 0.5f, e.data[1], 2)
                    if (Math.random() < 0.08) audio.play("scrape")
                }
                "land" -> {
                    if (e.data[0] > 0.25f) {
                        audio.play("land")
                        shake = max(shake, 0.3f)
                        dust(P.x, P.y, P.z)
                    }
                }
                "pad" -> {
                    postMessage("加速带!", 0x27c7ff.toInt(), small = true)
                    audio.play("pad")
                }
            }
        }
        P.events.clear()
    }

    private fun updateRaceHUD() {
        val P = player ?: return
        val lapsTotal = laps
        val data = hudData
        val standRows = ArrayList<HudData.HudRow>(standings.size)
        for (r in standings) {
            standRows.add(HudData.HudRow(r.name, r.isPlayer, r.finished, r.skin.body, standRows.size + 1))
        }
        synchronized(hudLock) {
            data.standings = standRows
            data.lap = "${U.clamp((P.lapsDone + 1).toFloat(), 1f, lapsTotal.toFloat()).toInt()}/$lapsTotal"
            data.time = if (P.finished) P.finishTime else raceTime
            data.best = if (P.lapTimes.isNotEmpty()) P.lapTimes.min() else 0f
            data.kmh = P.speedKmh
            data.rank = standings.indexOf(P) + 1
            data.total = racers.size
            data.gauge = P.gauge
            data.nitro = P.nitroCount
            data.nitroOn = P.nitroTime > 0
            data.smallOn = P.smallBoost > 0 || P.startBoost > 0 || P.padTime > 0
            data.itemMode = itemMode
            if (itemMode) {
                data.item0 = P.items.getOrElse(0) { "" }
                data.item1 = P.items.getOrElse(1) { "" }
            }
            var warn: String? = null
            if (P.wrongWay > 1.2f && state == "race") warn = "⚠ 方向错误！"
            if (missileWarn > 0) { missileWarn -= 0.016f; warn = "⚠ 导弹锁定！" }
            data.warn = warn
        }
    }

    fun pollHud(): HudData {
        synchronized(hudLock) { return hudData }
    }

    fun pollMessages(): List<Triple<String, Int, Boolean>> {
        synchronized(messages) {
            val out = messages.toList()
            messages.clear()
            return out
        }
    }

    private fun postMessage(text: String, color: Int, small: Boolean = false, delay: Float = 0f, countdown: Boolean = false, clear: Boolean = false) {
        if (clear) { synchronized(messages) { messages.clear() }; return }
        synchronized(messages) {
            if (countdown) messages.add(Triple("\u0000CD\u0000" + text, color, small))
            else {
                messages.add(Triple(text, color, small))
                while (messages.size > 3) messages.removeFirst()
            }
        }
    }

    // ---------- 特效 ----------
    private val R = java.util.Random()

    fun fxSparks(x: Float, y: Float, z: Float, n: Int) {
        for (i in 0 until n) glow.emit(x, y, z, (R.nextFloat() - 0.5f) * 16f, R.nextFloat() * 7f, (R.nextFloat() - 0.5f) * 16f,
            0.4f + R.nextFloat() * 0.4f, 0.5f, 0.1f, 1f, 0.75f + R.nextFloat() * 0.25f, 0.3f, 1f, 18f, 1f)
    }

    fun dust(x: Float, y: Float, z: Float) {
        for (i in 0 until 24) smoke.emit(x + (R.nextFloat() - 0.5f) * 3f, y + 0.3f, z + (R.nextFloat() - 0.5f) * 3f,
            (R.nextFloat() - 0.5f) * 8f, R.nextFloat() * 2f, (R.nextFloat() - 0.5f) * 8f,
            1f + R.nextFloat(), 2f, 6f, 0.85f, 0.8f, 0.72f, 0.5f, -0.5f, 1.5f)
    }

    fun burst(x: Float, y: Float, z: Float, color: Int) {
        val r = ((color shr 16) and 0xff) / 255f
        val g = ((color shr 8) and 0xff) / 255f
        val b = (color and 0xff) / 255f
        for (i in 0 until 26) glow.emit(x, y, z, (R.nextFloat() - 0.5f) * 14f, (R.nextFloat() - 0.2f) * 10f, (R.nextFloat() - 0.5f) * 14f,
            0.5f + R.nextFloat() * 0.3f, 1.2f, 0.2f, r, g, b, 1f, 6f, 2f)
    }

    fun explode(x: Float, y: Float, z: Float) {
        for (i in 0 until 40) glow.emit(x, y, z, (R.nextFloat() - 0.5f) * 22f, R.nextFloat() * 14f, (R.nextFloat() - 0.5f) * 22f,
            0.5f + R.nextFloat() * 0.5f, 3f, 0.5f, 1f, 0.5f + R.nextFloat() * 0.4f, 0.15f, 1f, 10f, 2f)
        for (i in 0 until 20) smoke.emit(x, y, z, (R.nextFloat() - 0.5f) * 8f, R.nextFloat() * 6f, (R.nextFloat() - 0.5f) * 8f,
            1.2f + R.nextFloat(), 3f, 9f, 0.3f, 0.3f, 0.32f, 0.6f, -1f, 1.5f)
    }

    fun trail(x: Float, y: Float, z: Float) {
        glow.emit(x, y, z, (R.nextFloat() - 0.5f), (R.nextFloat() - 0.5f), (R.nextFloat() - 0.5f), 0.25f, 1.2f, 0.2f, 1f, 0.6f, 0.2f, 1f)
        smoke.emit(x, y, z, (R.nextFloat() - 0.5f), R.nextFloat(), (R.nextFloat() - 0.5f), 0.8f, 0.8f, 3f, 0.85f, 0.85f, 0.88f, 0.4f)
    }

    fun boostBurst(r: Car, color: Int) {
        val cr = ((color shr 16) and 0xff) / 255f
        val cg = ((color shr 8) and 0xff) / 255f
        val cb = (color and 0xff) / 255f
        val bx = r.x - sin(r.h) * 2.5f
        val bz = r.z - cos(r.h) * 2.5f
        for (i in 0 until 30) glow.emit(bx, r.y + 0.6f, bz,
            (R.nextFloat() - 0.5f) * 10f - sin(r.h) * 10f, R.nextFloat() * 4f, (R.nextFloat() - 0.5f) * 10f - cos(r.h) * 10f,
            0.35f + R.nextFloat() * 0.3f, 1.6f, 0.2f, cr, cg, cb, 1f, 0f, 3f)
    }

    private fun emitEffects(dt: Float) {
        val cam = renderer.cam
        for (r in racers) {
            val far = (r.x - cam.x) * (r.x - cam.x) + (r.z - cam.z) * (r.z - cam.z) > 250 * 250
            val h = r.h
            val fxv = sin(h); val fzv = cos(h)
            val lx = cos(h); val lz = -sin(h)
            val sp = abs(r.s)
            val drifting = r.drifting && !r.airborne && sp > 10
            for (w in 0 until 2) {
                val sx = if (w == 1) 0.93f else -0.93f
                val wx = r.x + lx * sx - fxv * 1.38f
                val wz = r.z + lz * sx - fzv * 1.38f
                val key = (if (r.isPlayer) "p" else r.slot.toString()) + "-" + w
                if (drifting) {
                    skids.add(key, wx, r.y, wz, lx, lz, 0.2f, if (r.isPlayer) 1f else 0.7f)
                    if (!far) {
                        val rate = (if (r.isPlayer) 55f else 22f) * dt
                        var k = rate.toInt()
                        if (R.nextFloat() < rate % 1f) k++
                        for (q in 0 until k)
                            smoke.emit(wx + (R.nextFloat() - 0.5f) * 0.6f, r.y + 0.25f, wz + (R.nextFloat() - 0.5f) * 0.6f,
                                (R.nextFloat() - 0.5f) * 2f - fxv * sp * 0.08f, 0.8f + R.nextFloat() * 1.2f, (R.nextFloat() - 0.5f) * 2f - fzv * sp * 0.08f,
                                0.8f + R.nextFloat() * 0.7f, 1.3f, 5f + R.nextFloat() * 2f, 0.95f, 0.95f, 0.97f, if (r.isPlayer) 0.42f else 0.3f, -0.6f, 1.2f)
                        if (r.isPlayer && R.nextFloat() < 0.5f) {
                            val c = if (r.gauge >= 1f || r.nitroCount >= 2) floatArrayOf(1f, 0.85f, 0.3f) else floatArrayOf(0.5f, 0.85f, 1f)
                            glow.emit(wx, r.y + 0.2f, wz, (R.nextFloat() - 0.5f) * 3f, R.nextFloat() * 3f, (R.nextFloat() - 0.5f) * 3f,
                                0.25f, 0.5f, 0.1f, c[0], c[1], c[2], 1f, 8f, 1f)
                        }
                    }
                } else skids.cut(key)
            }
            val nitro = r.nitroTime > 0
            val small = r.isPlayer && (r.smallBoost > 0 || r.padTime > 0 || r.startBoost > 0)
            if ((nitro || small) && !far) {
                for (sx in floatArrayOf(-0.42f, 0.42f)) {
                    val ex = r.x + lx * sx - fxv * 2.6f
                    val ez = r.z + lz * sx - fzv * 2.6f
                    val n = if (nitro) 2 else 1
                    for (k in 0 until n) {
                        val c = if (nitro) (if (R.nextFloat() < 0.5f) floatArrayOf(0.3f, 0.7f, 1f) else floatArrayOf(0.85f, 0.95f, 1f)) else floatArrayOf(1f, 0.6f, 0.2f)
                        glow.emit(ex, r.y + 0.45f, ez, -fxv * 6f + (R.nextFloat() - 0.5f) * 2f, R.nextFloat() * 1.5f, -fzv * 6f + (R.nextFloat() - 0.5f) * 2f,
                            0.18f + R.nextFloat() * 0.1f, if (nitro) 1.3f else 0.9f, 0.2f, c[0], c[1], c[2], 1f)
                    }
                }
            }
            if (r.isPlayer && r.magnet > 0 && r.magnetTarget != null && !far) {
                val t = r.magnetTarget!!
                for (k in 0 until 3) {
                    val f = R.nextFloat()
                    glow.emit(U.lerp(r.x, t.x, f), U.lerp(r.y, t.y, f) + 1f, U.lerp(r.z, t.z, f), 0f, 0f, 0f, 0.12f, 0.8f, 0.3f, 0.8f, 0.4f, 1f, 1f)
                }
            }
        }
        // 雪花
        if (map?.snowfall == true) {
            for (k in 0 until 6) {
                val a = R.nextFloat() * PI.toFloat() * 2f
                val r = R.nextFloat() * 40f
                smoke.emit(renderer.cam.x + cos(a) * r, renderer.cam.y + 8f + R.nextFloat() * 8f, renderer.cam.z + sin(a) * r,
                    (R.nextFloat() - 0.5f) * 2f, -2.5f - R.nextFloat() * 1.5f, (R.nextFloat() - 0.5f) * 2f,
                    3f + R.nextFloat() * 2f, 0.15f, 0.15f, 1f, 1f, 1f, 0.8f, 0f, 0f)
            }
        }
    }

    // ---------- 车辆视觉同步 ----------
    private fun syncCarVisual(r: Car, dt: Float) {
        val tr = track!!
        r.syncVisual(dt)
        val carMat = r.carMatrix
        val hVis = r.h + (if (r is AICar) r.spinAng else 0f)
        carMat.compose(r.x, r.y, r.z, r.pitchVis, hVis, r.rollVis, 1f, 1f, 1f)
        // root（车身姿态动态）
        val sp = abs(r.s)
        val lean = if (r.drifting) r.driftDir * 0.07f else r.steer * 0.045f * U.clamp(sp / 30f, 0f, 1f)
        val pitchDyn = if (r.throttle > 0 && sp < 30f) -0.025f else if (r.throttle < 0 && r.s > 5f) 0.04f else 0f
        val targetPitchDyn = pitchDyn - (if (r.nitroTime > 0) 0.02f else 0f)
        if (r.isPlayer) {
            r.rootLean = U.damp(r.rootLean, lean, 8f, dt)
            r.rootPitch = U.damp(r.rootPitch, targetPitchDyn, 6f, dt)
        } else {
            r.rootLean = U.damp(r.rootLean, if (r.drifting) r.driftDir * 0.07f else 0f, 6f, dt)
            r.rootPitch = 0f
        }
        val spinAng = if (r is AICar) r.spinAng else 0f
        val rootRot = tmpM1.compose(0f, 0f, 0f, r.rootPitch, 0f, r.rootLean, 1f, 1f, 1f)
        mulM(r.rootMatrix, carMat, rootRot)
        // 车轮
        for (w in r.wheelItems) {
            w.spinAng += (r.s * dt) / 0.47f
            val steerA = if (r.drifting) -r.driftDir * 0.3f else r.steer * 0.38f
            tmpM2.compose(w.x, 0.47f, w.z, 0f, steerA, 0f, 1f, 1f, 1f)
            tmpM3.compose(0f, 0f, 0f, w.spinAng, 0f, 0f, 1f, 1f, 1f)
            mulM(tmpM4, tmpM2, tmpM3)
            mulM(tmpM5, carMat, tmpM4)
            w.tire.matrix.set(tmpM5)
            w.rim.matrix.set(tmpM5)
        }
        // 尾焰
        val nitro = r.nitroTime > 0
        val small = r.smallBoost > 0 || r.padTime > 0 || r.startBoost > 0
        val on = nitro || small
        val t = time
        for (f in r.flameItems) {
            val e = f.first.userData as FloatArray
            val k = if (nitro) 1f else 0.6f
            val fl = 0.85f + sin(t * 60f + e[0] * 10f) * 0.12f + R.nextFloat() * 0.12f
            tmpM2.compose(e[0], e[1], e[2], 0f, 0f, 0f, k * fl, k * fl, k * (1.1f + R.nextFloat() * 0.5f))
            mulM(tmpM3, r.rootMatrix, tmpM2)
            f.first.matrix.set(tmpM3)
            f.first.visible = on
            f.first.colorMul = if (nitro) floatArrayOf(0.22f, 0.66f, 1f, 0.8f) else floatArrayOf(1f, 0.54f, 0.16f, 0.8f)
            mulM(f.second.matrix, tmpM3, tmpM2)
            f.second.visible = on
        }
        // 护盾/底盘光/阴影
        r.shieldItem?.matrix?.set(carMat)
        r.shieldItem?.visible = r.shield > 0
        r.underglowItem?.matrix?.set(carMat)
        val sh = r.shadowItem!!
        val smp = tr.project(r.x, r.y, r.z, r.hint, tmpSample)
        sh.matrix.compose(r.x, smp.y + smp.lat * sin(smp.bank) + 0.04f, r.z, 0f, r.h, 0f, 1f, 1f, 1f)
        sh.colorMul = floatArrayOf(0.05f, 0.05f, 0.06f, if (r.airborne) 0.2f else 0.5f)
    }

    private val tmpM1 = M4(); private val tmpM2 = M4(); private val tmpM3 = M4()
    private val tmpM4 = M4(); private val tmpM5 = M4()
    private val tmpSample = SampleOut()
    val waterItems = ArrayList<RItem>()

    private fun mulM(out: M4, a: M4, b: M4) {
        val ae = a.e; val be = b.e; val oe = out.e
        for (c in 0 until 4) for (rr in 0 until 4) {
            oe[c * 4 + rr] = ae[rr] * be[c * 4] + ae[4 + rr] * be[c * 4 + 1] + ae[8 + rr] * be[c * 4 + 2] + ae[12 + rr] * be[c * 4 + 3]
        }
    }

    // ---------- 相机 ----------
    fun snapCamera() {
        val P = player ?: return
        camYaw = P.h
        camPos.set(P.x - sin(P.h) * 9f, P.y + 3.4f, P.z - cos(P.h) * 9f)
        camLook.set(P.x, P.y + 1f, P.z)
    }

    fun updateCamera(dt: Float) {
        val cam = renderer.cam
        val st = state
        var target: Car? = player
        if (st == "menu") {
            demoT += dt
            if (demoT > 9) {
                demoT = 0f
                demoTarget = (demoTarget + 1) % racers.size.coerceAtLeast(1)
                demoCut = true
            }
            if (racers.isNotEmpty()) target = racers[demoTarget % racers.size]
        }
        val T = target ?: return
        val tx = T.x; val ty = T.y; val tz = T.z
        if (st == "menu" || (st == "finish" && resultShown)) {
            val a = time * 0.25f + demoTarget
            val wx = tx + sin(a) * 11f
            val wy = ty + 3.2f + sin(time * 0.4f) * 1.2f
            val wz = tz + cos(a) * 11f
            if (demoCut) { camPos.set(wx, wy, wz); demoCut = false }
            val l = 1f - exp(-3f * dt)
            camPos.x += (wx - camPos.x) * l
            camPos.y += (wy - camPos.y) * l
            camPos.z += (wz - camPos.z) * l
            camLook.set(tx, ty + 1f, tz)
            cam.x = camPos.x; cam.y = camPos.y; cam.z = camPos.z
            cam.lx = camLook.x; cam.ly = camLook.y; cam.lz = camLook.z
            cam.fov = U.damp(cam.fov, 55f, 3f, dt)
            return
        }
        val P = T
        val dists = floatArrayOf(8.2f, 12.5f, 5.2f)
        val hs = floatArrayOf(3.0f, 4.6f, 1.9f)
        val looks = floatArrayOf(5f, 6f, 8f)
        val lookHs = floatArrayOf(1.3f, 1.5f, 1.1f)
        val m = camMode.coerceIn(0, 2)
        // 漂移时相机跟随速度方向，能看到车身侧滑
        val yawT = if (P.s >= 0) P.m + U.wrapAngle(P.h - P.m) * 0.35f else P.h
        camYaw = U.dampAngle(camYaw, yawT, if (P.drifting) 4.5f else 7f, dt)
        val boostPull = if (P.nitroTime > 0) 1.6f else if (P.smallBoost > 0) 0.8f else 0f
        val dist = dists[m] + abs(P.s) * 0.018f + boostPull
        var wantY = P.y + hs[m]
        val wx = P.x - sin(camYaw) * dist
        val wz = P.z - cos(camYaw) * dist
        // 相机不钻地
        val gy = world!!.ground.heightAt(wx, wz) + 1f
        if (wantY < gy) wantY = gy
        if (P.airborne) wantY = max(wantY, P.y + hs[m])
        val l = 1f - exp(-14f * dt)
        camPos.x += (wx - camPos.x) * l
        camPos.z += (wz - camPos.z) * l
        camPos.y = U.damp(camPos.y, wantY, 8f, dt)
        val lk = 1f - exp(-18f * dt)
        camLook.x += (P.x + sin(camYaw) * looks[m] - camLook.x) * lk
        camLook.y += (P.y + lookHs[m] - camLook.y) * lk
        camLook.z += (P.z + cos(camYaw) * looks[m] - camLook.z) * lk
        cam.x = camPos.x; cam.y = camPos.y; cam.z = camPos.z
        if (shake > 0) {
            val k = shake * shake
            cam.x += (R.nextFloat() - 0.5f) * k * 1.2f
            cam.y += (R.nextFloat() - 0.5f) * k * 1.0f
            cam.z += (R.nextFloat() - 0.5f) * k * 1.2f
            shake = max(0f, shake - dt * 1.8f)
        }
        if (P.nitroTime > 0) {
            cam.x += (R.nextFloat() - 0.5f) * 0.06f
            cam.y += (R.nextFloat() - 0.5f) * 0.06f
        }
        cam.lx = camLook.x; cam.ly = camLook.y; cam.lz = camLook.z
        val fovT = 66f + abs(P.s) * 0.12f + (if (P.nitroTime > 0) 9f else 0f) + (if (P.smallBoost > 0) 4f else 0f)
        cam.fov = U.damp(cam.fov, fovT, 4f, dt)
    }

    var flash = 0f

    // ---------- 渲染 ----------
    fun render(dt: Float) {
        if (track == null) return
        updateCamera(dt)
        val cam = renderer.cam
        cam.update()
        // 水面时间
        renderer.waterTime = time
        // 加速带滚动
        for (bp in track!!.boostPads) bp.item?.let { it.uvOffY = -time * 1.2f }
        // 云精灵
        renderer.beginSprites()
        val cloudTex = TexLib.get("cloud")
        for (c in world!!.clouds) {
            renderer.addSprite(cloudTex, c[0], c[1], c[2], c[3], c[3] * 0.45f, 1f, 1f, 1f, 0.8f)
        }
        for (r in racers) {
            if (r.isPlayer || r.tagTexName.isEmpty()) continue
            if ((r.x - cam.x).pow(2) + (r.z - cam.z).pow(2) > 250 * 250) continue
            val tex = TexLib.get(r.tagTexName)
            renderer.addSprite(tex, r.x, r.y + 2.5f, r.z, 4.2f, 0.8f, 1f, 1f, 1f, 0.95f)
        }
        renderer.render()
        // 粒子与轮胎印
        val (sv, si) = smoke.upload()
        renderer.drawParticles(smoke.ensureMesh(), false, sv, si, softDotTex)
        val (gv, gi) = glow.upload()
        renderer.drawParticles(glow.ensureMesh(), true, gv, gi, softDotTex)
        val (_, ski) = skids.update()
        renderer.drawSkids(skids.ensureMesh(), ski)
        // 受击闪白
        if (flash > 0) flash = max(0f, flash - dt * 2.5f)
    }

    fun speedLineIntensity(): Float = speedLineK
}
