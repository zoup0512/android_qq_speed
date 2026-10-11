package com.feiche.qqspeed.game

import kotlin.math.*

// 手感参数（米/秒）。显示时速 = 速度 × 3.6（移植 vehicle.js TUNE）
object TUNE {
    const val vmax = 55f          // ~198 km/h
    const val vmaxNitro = 76f     // ~274 km/h
    const val accel = 24f
    const val nitroAccel = 40f
    const val brake = 45f
    const val reverseMax = 12f
    const val roll = 2.2f
    const val turnRate = 1.9f
    const val grip = 12f
    const val driftMinSpeed = 13f
    const val driftYaw = 1.5f
    const val driftYawAlign = 0.85f
    const val driftGrip = 2.0f
    const val driftGripRelease = 5.2f
    const val maxDriftAngle = 1.15f
    const val smallWindow = 0.5f
    const val gaugeRate = 0.36f
    const val nitroTime = 2.8f
    const val gravity = 30f
}

class CarInput(
    var up: Boolean = false,
    var down: Boolean = false,
    var left: Boolean = false,
    var right: Boolean = false,
    var shift: Boolean = false,
    var upPressed: Boolean = false,
    var wPressed: Boolean = false,
    var nitroPressed: Boolean = false,
    var swapPressed: Boolean = false,
    var resetPressed: Boolean = false,
) {
    fun setAllFalse() { up = false; down = false; left = false; right = false; shift = false; upPressed = false; wPressed = false; nitroPressed = false; swapPressed = false; resetPressed = false }
    fun copyFrom(o: CarInput) { up = o.up; down = o.down; left = o.left; right = o.right; shift = o.shift; upPressed = o.upPressed; wPressed = o.wPressed; nitroPressed = o.nitroPressed; swapPressed = o.swapPressed; resetPressed = o.resetPressed }
}

class GameEvent(var type: String, var data: FloatArray = floatArrayOf())

// 车辆基类（玩家物理，AI 覆盖 update）
open class Car(val track: Track, val name: String) {
    var isPlayer = false
    var skin: CarSkin = CarSkin.ALL[0]
    var meshes: CarMeshes? = null

    // 事件
    val events = ArrayList<GameEvent>()
    fun emit(type: String, data: FloatArray = floatArrayOf()) { events.add(GameEvent(type, data)) }

    // 通用状态
    var x = 0f; var y = 0f; var z = 0f
    var h = 0f  // 朝向
    var m = 0f  // 速度方向
    var s = 0f  // 速度
    var vy = 0f; var vyGround = 0f
    var airborne = false; var airTime = 0f
    var steer = 0f
    var drifting = false; var driftDir = 0; var driftTime = 0f; var driftAngle = 0f
    var releaseTime = 0f
    var gauge = 0f; var nitroCount = 0; var nitroTime = 0f
    var smallBoost = 0f; var smallBoostPower = 0f; var smallWindow = 0f; var windowKind = ""
    var padTime = 0f; var startBoost = 0f
    var spin = 0f; var shield = 0f; var slowTime = 0f
    var magnet = 0f; var magnetTarget: Car? = null
    var hint = -1; var d = 0f; var lat = 0f
    var impact = 0f; var wrongWay = 0f
    var pitchVis = 0f; var rollVis = 0f
    var throttle = 0f
    var vmaxNow = 0f
    val proj = SampleOut()

    // 比赛状态
    var slot = 0
    var lapsDone = 0; var owed = false; var half = false; var lastD = 0f
    var progress = 0f
    var lapStart = 0f
    val lapTimes = ArrayList<Float>()
    val items = ArrayList<String>()
    var itemTimer = 0f
    var finished = false; var finishTime = 0f
    var dist = 0f
    var pushD = 0f
    val stats = RaceStats()
    // 视觉
    var spinAng = 0f
    var trackHd = 0f
    // 视觉渲染（由 Game 填充）
    val visualItems = ArrayList<com.feiche.qqspeed.gl.RItem>()
    val carMatrix = M4()
    var rootMatrix = M4()
    var rootLean = 0f; var rootPitch = 0f
    var underglowItem: com.feiche.qqspeed.gl.RItem? = null
    var shieldItem: com.feiche.qqspeed.gl.RItem? = null
    var shadowItem: com.feiche.qqspeed.gl.RItem? = null
    val flameItems = ArrayList<Pair<com.feiche.qqspeed.gl.RItem, com.feiche.qqspeed.gl.RItem>>()
    val wheelItems = ArrayList<WheelVisual>()
    var tagTexName = ""

    class WheelVisual(val x: Float, val z: Float, val front: Boolean,
                      val tire: com.feiche.qqspeed.gl.RItem, val rim: com.feiche.qqspeed.gl.RItem, var spinAng: Float)

    class RaceStats {
        var drift = 0; var small = 0; var perfect = 0; var doubleB = 0; var nitro = 0; var crash = 0
        var top = 0f; var land = 0
    }

    val speedKmh: Float get() = abs(s) * 3.6f
    val boosting: Boolean get() = nitroTime > 0 || smallBoost > 0 || padTime > 0 || startBoost > 0

    open fun reset(d0: Float, lat0: Float) {
        val smp = track.sample(d0, SampleOut())
        x = smp.x + smp.rx * lat0
        z = smp.z + smp.rz * lat0
        y = smp.y + 0.05f
        h = smp.hd; m = smp.hd
        s = 0f; vy = 0f; vyGround = 0f
        airborne = false; airTime = 0f
        steer = 0f
        drifting = false; driftDir = 0; driftTime = 0f; driftAngle = 0f
        gauge = 0f; nitroCount = 0; nitroTime = 0f
        smallBoost = 0f; smallBoostPower = 0f; smallWindow = 0f; windowKind = ""
        padTime = 0f; startBoost = 0f
        spin = 0f; shield = 0f; slowTime = 0f
        magnet = 0f; magnetTarget = null
        hint = -1; impact = 0f; wrongWay = 0f
        pitchVis = 0f; rollVis = 0f; throttle = 0f
        track.project(x, y, z, -1, proj)
        hint = proj.i
        d = proj.d
        lat = proj.lat
    }

    fun triggerNitro(fromItem: Boolean = false): Boolean {
        if (!fromItem) {
            if (nitroCount <= 0) return false
            nitroCount--
        }
        val wasSmall = smallBoost > 0.35f
        nitroTime = min(4.5f, max(0f, nitroTime) + TUNE.nitroTime)
        s = max(s, 20f) + 4f
        emit("nitro", floatArrayOf(if (wasSmall) 1f else 0f))
        if (wasSmall) emit("double")
        return true
    }

    fun endDrift(clean: Boolean = true) {
        if (!drifting) return
        drifting = false
        if (clean && driftTime > 0.22f) {
            smallWindow = TUNE.smallWindow
            windowKind = "drift"
        }
        emit("driftEnd", floatArrayOf(driftTime))
    }

    open fun update(dt: Float, inp: CarInput, active: Boolean, itemMode: Boolean) {
        val T = TUNE
        val tr = track
        var input = inp
        if (!active) input = NO_INPUT
        val spinning = spin > 0
        if (spinning) {
            spin -= dt
            input = NO_INPUT
        }

        // 平滑转向
        val steerT = (if (input.left) 1f else 0f) - (if (input.right) 1f else 0f)
        steer = U.damp(steer, steerT, 12f, dt)

        // 各种加速状态
        var vmax = T.vmax; var acc = T.accel
        if (nitroTime > 0) { vmax = T.vmaxNitro; acc = T.nitroAccel; nitroTime -= dt }
        if (smallBoost > 0) { vmax += smallBoostPower; acc += 14f; smallBoost -= dt }
        if (padTime > 0) { vmax += 12f; acc += 20f; padTime -= dt }
        if (startBoost > 0) { vmax += 10f; acc += 26f; startBoost -= dt }
        if (magnet > 0) { vmax += 10f; acc += 12f; magnet -= dt }
        vmax = min(vmax, 86f)
        if (slowTime > 0) { vmax *= 0.55f; slowTime -= dt }
        if (shield > 0) shield -= dt
        vmaxNow = vmax

        // 小喷 / 落地喷窗口
        if (smallWindow > 0) {
            smallWindow -= dt
            if (input.upPressed || input.wPressed) {
                val elapsed = (if (windowKind == "drift") T.smallWindow else 0.45f) - smallWindow
                val perfect = elapsed < 0.2f
                val dbl = nitroTime > 0
                smallBoost = if (perfect) 1f else 0.75f
                smallBoostPower = (if (perfect) 11f else 8f) + (if (dbl) 5f else 0f)
                s += if (perfect) 5.5f else 4f
                if (dbl) nitroTime += 0.3f
                emit(if (windowKind == "land") "landBoost" else "smallBoost", floatArrayOf(if (perfect) 1f else 0f, if (dbl) 1f else 0f))
                if (dbl) emit("double")
                smallWindow = 0f
            }
        }
        if (input.nitroPressed && !itemMode) triggerNitro()

        val onGround = !airborne
        // 纵向
        throttle = if (input.up) 1f else if (input.down) -1f else 0f
        if (onGround) {
            if (input.up) {
                if (s < 0) s += T.brake * dt
                else if (s < vmax) s += acc * (1f - (s / vmax).pow(2) * 0.85f) * dt
            } else if (input.down) {
                if (s > 0.5f) s -= T.brake * dt
                else s = max(-T.reverseMax, s - 14f * dt)
            } else {
                val dec = (T.roll + abs(s) * 0.06f) * dt
                s = if (abs(s) <= dec) 0f else s - sign(s) * dec
            }
            if (s > vmax) s -= (s - vmax) * 1.1f * dt
            // 坡度影响
            s -= tr.slope[hint] * cos(h - tr.hd[hint]) * 6f * dt
        }

        // 进入漂移
        if (!drifting && input.shift && steerT != 0f && s > T.driftMinSpeed && onGround && !spinning) {
            drifting = true
            driftDir = steerT.toInt()
            driftTime = 0f
            releaseTime = 0f
            smallWindow = 0f
            h += driftDir * 0.07f
            emit("driftStart")
        }

        if (drifting) {
            val align = steerT * driftDir
            val held = input.shift
            if (held) releaseTime = 0f else releaseTime += dt
            val yaw = if (held) T.driftYaw + T.driftYawAlign * align else (0.45f + 0.9f * align) * exp(-2.2f * releaseTime)
            h += driftDir * yaw * U.clamp(s / 26f, 0.35f, 1f) * dt * (if (onGround) 1f else 0.4f)
            val k = if (held) T.driftGrip else T.driftGripRelease
            m += U.wrapAngle(h - m) * (1f - exp(-k * dt))
            var a = U.wrapAngle(h - m)
            if (abs(a) > T.maxDriftAngle) {
                m = h - sign(a) * T.maxDriftAngle
                a = sign(a) * T.maxDriftAngle
            }
            driftAngle = a
            val sa = abs(sin(a))
            if (onGround) s -= (2.5f + 11f * sa) * dt
            if (!itemMode && onGround) {
                gauge += dt * T.gaugeRate * (0.3f + sa * 1.7f) * U.clamp(s / 35f, 0.3f, 1.1f)
                while (gauge >= 1f) {
                    if (nitroCount < 2) {
                        nitroCount++
                        gauge -= 1f
                        emit("gaugeFull")
                    } else {
                        gauge = 1f
                        break
                    }
                }
            }
            driftTime += dt
            if ((!held && (abs(a) < 0.1f || (align < 0 && abs(a) < 0.26f))) || s < 7f) endDrift(s >= 7f)
        } else {
            driftAngle = U.damp(driftAngle, 0f, 10f, dt)
            val dir = if (s >= 0) 1f else -1f
            val sp = abs(s)
            val turn = T.turnRate * U.clamp(sp / 9f, 0f, 1f) * (1f - 0.55f * U.clamp(sp / T.vmax, 0f, 1.3f))
            h += steer * turn * dt * dir * (if (onGround) 1f else 0.35f)
            if (s >= 0) m += U.wrapAngle(h - m) * (1f - exp(-T.grip * dt))
            else m = h
        }
        if (spinning) h += 9f * dt

        // 位置积分
        val dirSign = if (s >= 0) 1f else -1f
        val mv = if (s >= 0) m else h
        var vx = sin(mv) * abs(s) * dirSign
        var vz = cos(mv) * abs(s) * dirSign
        x += vx * dt
        z += vz * dt

        val p = tr.project(x, y, z, hint, proj)
        hint = p.i
        d = p.d

        // 护栏碰撞
        val lim = tr.halfW - 1.1f
        impact = 0f
        if (abs(p.lat) > lim) {
            val sg = sign(p.lat)
            val nx = p.rx * sg; val nz = p.rz * sg
            val over = abs(p.lat) - lim
            x -= nx * over
            z -= nz * over
            val vn = vx * nx + vz * nz
            if (vn > 0) {
                val sp = max(1f, sqrt(vx * vx + vz * vz))
                val imp = vn / sp
                vx -= nx * vn * 1.25f
                vz -= nz * vn * 1.25f
                val keep = 1f - 0.45f * imp
                vx *= keep; vz *= keep
                val ns = sqrt(vx * vx + vz * vz)
                if (s >= 0) {
                    s = ns
                    if (ns > 0.5f) m = atan2(vx, vz)
                    // 车头顺着墙摆正
                    val th = if (abs(U.wrapAngle(h - p.hd)) < PI / 2) p.hd else p.hd + PI.toFloat()
                    h += U.wrapAngle(th - h) * min(1f, 0.6f * imp + 0.05f)
                } else s = -ns
                impact = imp * sp
                if (imp > 0.3f && drifting) endDrift(false)
                if (imp * sp > 6f) emit("crash", floatArrayOf(imp * sp, x - nx * 0.9f, z - nz * 0.9f))
                else if (sp > 8f) emit("scrape", floatArrayOf(x + nx, z + nz))
            }
            p.lat = sg * lim
        }
        lat = p.lat

        // 垂直：贴地 / 腾空 / 落地
        val groundY = p.y + p.lat * sin(p.bank)
        if (airborne) {
            vy -= T.gravity * dt
            y += vy * dt
            airTime += dt
            if (y <= groundY) {
                y = groundY
                airborne = false
                val hard = vy
                vy = 0f; vyGround = 0f
                emit("land", floatArrayOf(airTime, hard))
                if (airTime > 0.3f) {
                    smallWindow = 0.45f
                    windowKind = "land"
                }
                airTime = 0f
            }
        } else {
            // 沿赛道方向的速度分量；竖直向心加速度超过重力即腾空
            val va = s * cos(m - p.hd)
            if (va > 18f && va * va * -tr.vcurv[p.i] > T.gravity) {
                airborne = true
                airTime = 0f
                vy = tr.sslope[p.i] * va
                y = groundY + vy * dt
                if (drifting) endDrift(false)
            } else {
                y = groundY
            }
        }

        // 逆行检测
        val fwdDot = cos(U.wrapAngle(h - p.hd))
        if (fwdDot < -0.35f && s > 4f) wrongWay += dt
        else wrongWay = max(0f, wrongWay - dt * 2)

        // 加速带
        for (bp in tr.boostPads) {
            var dd = d - (bp.d - bp.len / 2)
            if (dd < 0) dd += tr.length
            if (dd < bp.len && abs(lat - bp.lat) < bp.halfW + 0.6f && !airborne) {
                if (padTime < 0.6f) {
                    emit("pad")
                    s = min(s + 6f, 72f)
                }
                padTime = 1.1f
            }
        }
    }

    fun syncVisual(dt: Float) {
        val i = hint
        val rel = h - track.hd[i]
        val slopeAlong = track.slope[i] * cos(rel)
        val bankAlong = track.bank[i] * cos(rel)
        val pitchT = if (airborne) U.clamp(-vy * 0.012f, -0.3f, 0.3f) else -atan(slopeAlong)
        pitchVis = U.damp(pitchVis, pitchT, 10f, dt)
        rollVis = U.damp(rollVis, -bankAlong, 10f, dt)
    }

    companion object {
        val NO_INPUT = CarInput()
    }
}

// ---------- AI 车（移植 ai.js）----------
class AICar(track: Track, name: String, val skill: Float, private val rnd: Rnd) : Car(track, name) {
    val personal = (rnd.next() - 0.5f) * 0.6f
    private val s2 = SampleOut()
    var nitroCd = 0f
    var yawOff = 0f
    var startDelay = 0f
    var latV = 0f

    init {
        isPlayer = false
        nitroCd = 6f + rnd.next() * 6f
    }

    override fun reset(d0: Float, lat0: Float) {
        super.reset(d0, lat0)
        dist = d0
        lat = lat0
        latV = 0f
        s = 0f
        nitroTime = 0f
        nitroCd = 6f + rnd.next() * 6f
        yawOff = 0f
        drifting = false; driftDir = 0
        y = 0f; vy = 0f; vyGround = 0f; airborne = false
        spin = 0f; slowTime = 0f; shield = 0f; magnet = 0f
        pushD = 0f; h = 0f; x = 0f; z = 0f
        startDelay = 0.05f + rnd.next() * 0.25f
        itemTimer = 2f + rnd.next() * 3f
        val smp = track.sample(d0, s2)
        y = smp.y
        d = smp.d
        hint = smp.i
    }

    fun updateAI(dt: Float, active: Boolean, raceTime: Float, rubber: Float) {
        val tr = track
        var vmaxBase = TUNE.vmax * (0.8f + 0.2f * skill) * rubber
        if (nitroTime > 0) { vmaxBase += 17f; nitroTime -= dt }
        if (magnet > 0) { vmaxBase += 10f; magnet -= dt }
        if (slowTime > 0) { vmaxBase *= 0.55f; slowTime -= dt }
        if (shield > 0) shield -= dt
        val dNow = dist
        val cNow = tr.curvAhead(dNow, 8f)
        val look = 18f + s * 1.25f
        val cAhead = tr.curvAhead(dNow, look)
        // 过弯极限（含漂移），技术越好越敢压速
        val A = 16f + 30f * skill
        val vCorner = sqrt(A / max(abs(cAhead), 1e-4f))
        var vt = min(vmaxBase, vCorner + (if (nitroTime > 0) 6f else 0f))
        var spinning = false
        if (spin > 0) { spin -= dt; vt = 5f; spinning = true }
        if (!active || raceTime < startDelay) vt = 0f
        if (s < vt) s += (if (nitroTime > 0) 34f else 20f + 4f * skill) * (1f - (s / max(vt, 1f)).pow(2) * 0.7f) * dt
        else s -= min(s - vt, (if (spinning) 50f else 30f) * dt)
        s = max(0f, s)

        // 直道上找机会放氮气
        nitroCd -= dt
        if (active && nitroCd <= 0 && abs(tr.curvAhead(dNow, 140f)) < 0.006f && s > 35f) {
            nitroTime = 2.6f
            nitroCd = (6f + rnd.next() * 8f) / skill
        }

        // 赛车线：出入弯走内线
        val hw = tr.halfW - 2.2f
        val inside = -sign(cAhead) * U.clamp(abs(cAhead) * 45f, 0f, 1f) * hw * 0.8f
        val target = U.clamp(inside + personal * hw * 0.8f, -hw, hw)
        val accLat = U.clamp((target - lat) * 2.2f - latV * 2.4f, -14f, 14f)
        latV += accLat * dt
        lat += latV * dt
        if (abs(lat) > hw) { lat = sign(lat) * hw; latV *= -0.3f }

        dist += s * dt + pushD
        pushD = 0f
        val smp = tr.sample(dist, s2)
        d = smp.d
        hint = smp.i
        x = smp.x + smp.rx * lat
        z = smp.z + smp.rz * lat
        val gy = smp.y + lat * sin(smp.bank)
        // 腾空
        if (airborne) {
            vy -= TUNE.gravity * dt
            y += vy * dt
            if (y <= gy) { y = gy; airborne = false; vy = 0f; vyGround = 0f }
        } else if (s > 18f && s * s * -tr.vcurv[smp.i] > TUNE.gravity) {
            airborne = true
            vy = tr.sslope[smp.i] * s
            y = gy + vy * dt
        } else y = gy

        // 视觉漂移：弯中甩尾
        val wantDrift = abs(cNow) > 0.012f && s > 26f
        drifting = wantDrift
        driftDir = sign(cNow).toInt()
        val yawT = if (wantDrift) sign(cNow) * min(0.62f, abs(cNow) * 22f) else 0f
        yawOff = U.damp(yawOff, yawT, 5f, dt)
        val latHead = atan2(latV, max(s, 3f))
        h = smp.hd + latHead + yawOff
        if (spinning) spinAng += 10f * dt
        else spinAng = 0f
        trackHd = smp.hd
    }
}
