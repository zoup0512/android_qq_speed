package com.feiche.qqspeed.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 全部音效与背景音乐由软件合成实时渲染（移植 audio.js → AudioTrack）
class SongDef(
    val name: String, val bpm: Int, val root: Int,
    val prog: Array<IntArray>, val lead: IntArray, val bassPat: IntArray, val style: Int,
)

object Songs {
    val ALL = listOf(
        SongDef("极速都市", 128, 57,
            arrayOf(intArrayOf(0, 3, 7), intArrayOf(-4, 0, 3), intArrayOf(3, 7, 10), intArrayOf(-2, 2, 5)),
            intArrayOf(0, 7, 12, 7, 10, 7, 3, 7), intArrayOf(1, 0, 1, 1, 0, 1, 1, 0), 0),
        SongDef("爱琴海之风", 118, 62,
            arrayOf(intArrayOf(0, 4, 7), intArrayOf(-3, 0, 4), intArrayOf(5, 9, 12), intArrayOf(7, 11, 14)),
            intArrayOf(12, 11, 7, 4, 7, 11, 12, 14), intArrayOf(1, 0, 0, 1, 1, 0, 1, 0), 1),
        SongDef("法老的试炼", 132, 55,
            arrayOf(intArrayOf(0, 3, 7), intArrayOf(1, 5, 8), intArrayOf(0, 3, 7), intArrayOf(-2, 1, 5)),
            intArrayOf(0, 1, 4, 5, 7, 8, 7, 4), intArrayOf(1, 1, 0, 1, 1, 0, 1, 1), 2),
        SongDef("冰雪狂飙", 140, 60,
            arrayOf(intArrayOf(0, 4, 7), intArrayOf(7, 11, 14), intArrayOf(9, 12, 16), intArrayOf(5, 9, 12)),
            intArrayOf(7, 12, 16, 12, 14, 12, 11, 7), intArrayOf(1, 0, 1, 0, 1, 1, 1, 0), 3),
    )
}

private const val SR = 44100

// 一次性发声事件
class VoiceEvent(
    val start: Long,            // 绝对采样点
    val durSamples: Int,
    val type: String,           // sine/square/saw/triangle/noise
    val f0: Float, val f1: Float, // 结束频率（<=0 表示不变）
    val vol: Float,
    val filterType: Int,        // 0 none 1 lp 2 bp 3 hp
    val cutoff: Float, val q: Float,
    val pad: Boolean,           // 和弦垫（慢起音）
) {
    var phase = 0f
}

class GameAudio {
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var musicOn = true
    @Volatile var started = false
    @Volatile private var playing = false

    // 引擎参数（游戏线程写，音频线程读）
    @Volatile private var engRatio = 0f
    @Volatile private var engThrottle = 0f
    @Volatile private var engBoost = false
    @Volatile private var engDrift = false
    @Volatile private var engActive = false

    private var song = 0
    private var step = 0
    private var nextNoteTime = 0.0
    private val events = ArrayList<VoiceEvent>()

    // 合成状态
    private var eng1Phase = 0f; private var eng2Phase = 0f
    private var engLpY = 0f
    private var engGainNow = 0f
    private var skidY1 = 0f; private var skidY2 = 0f; private var skidX1 = 0f; private var skidX2 = 0f
    private var skidGainNow = 0f
    private var windY1 = 0f; private var windY2 = 0f; private var windX1 = 0f; private var windX2 = 0f
    private var windGainNow = 0f
    private val noise = FloatArray(SR * 2)
    private var noisePos = 0
    private var framesWritten = 0L
    private val rndAudio = java.util.Random()

    // 引擎连续声参数（平滑目标）
    private var eng1Freq = 60f; private var eng2Freq = 30f; private var engCut = 500f
    private var skidCut = 1800f

    fun songName(): String = Songs.ALL[song].name

    fun setSong(i: Int): String {
        song = ((i % Songs.ALL.size) + Songs.ALL.size) % Songs.ALL.size
        step = 0
        nextNoteTime = currentTime() + 0.1
        return Songs.ALL[song].name
    }

    fun startMusic() { playing = true; nextNoteTime = currentTime() + 0.1 }
    fun stopMusic() { playing = false }

    fun toggleMusic(): Boolean {
        musicOn = !musicOn
        return musicOn
    }

    private fun currentTime(): Double = framesWritten.toDouble() / SR

    fun init() {
        if (started) return
        started = true
        for (i in noise.indices) noise[i] = rndAudio.nextFloat() * 2f - 1f
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufFrames = maxOf(2048, minBuf / 2)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufFrames * 2)
            .build()
        running = true
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val t = track
            t?.play()
            val chunk = ShortArray(2048)
            while (running) {
                render(chunk)
                t?.write(chunk, 0, chunk.size)
            }
        }, "feiche-audio")
        thread?.start()
    }

    fun dispose() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        track?.stop(); track?.release()
        track = null
    }

    fun setEngine(speedRatio: Float, throttle: Float, boosting: Boolean, drifting: Boolean, active: Boolean) {
        engRatio = speedRatio; engThrottle = throttle; engBoost = boosting; engDrift = drifting; engActive = active
    }

    fun silence() { setEngine(0f, 0f, false, false, false) }

    fun tone(freq: Float, dur: Float, type: String = "sine", vol: Float = 0.3f, slide: Float = 0f, delay: Float = 0f) {
        enqueue(start = (currentTime() + delay).toSamples(), durSamples = (dur * SR).toInt(),
            type = type, f0 = freq, f1 = if (slide > 0) freq * slide else 0f, vol = vol, filterType = 0, cutoff = 0f, q = 0f, pad = false)
    }

    fun noiseBurst(dur: Float, freq: Float, q: Float, vol: Float, type: String = "bandpass", sweep: Float = 0f) {
        val ft = when (type) { "lowpass" -> 1; "bandpass" -> 2; else -> 3 }
        enqueue(start = currentTime().toSamples(), durSamples = (dur * SR).toInt(),
            type = "noise", f0 = freq, f1 = if (sweep > 0) freq * sweep else 0f, vol = vol, filterType = ft, cutoff = freq, q = q, pad = false)
    }

    fun play(name: String, arg: Float = 0f) {
        if (!started) return
        when (name) {
            "count" -> tone(660f, 0.25f, "square", 0.18f)
            "go" -> { tone(1320f, 0.6f, "square", 0.2f); tone(990f, 0.6f, "square", 0.1f) }
            "nitro" -> { noiseBurst(0.9f, 300f, 0.8f, 0.9f, "lowpass", 8f); tone(90f, 0.5f, "sawtooth", 0.25f, 2.5f) }
            "small" -> { noiseBurst(0.35f, 900f, 1f, 0.6f, "bandpass", 3f); tone(420f, 0.18f, "triangle", 0.18f, 2f) }
            "double" -> { tone(880f, 0.12f, "square", 0.12f); tone(1320f, 0.2f, "square", 0.12f, 1f, 0.08f) }
            "gauge" -> { tone(1047f, 0.18f, "triangle", 0.2f, 1f, 0f); tone(1319f, 0.18f, "triangle", 0.2f, 1f, 0.06f); tone(1568f, 0.18f, "triangle", 0.2f, 1f, 0.12f) }
            "crash" -> { noiseBurst(0.35f, 400f, 0.8f, min(1f, 0.3f + arg * 0.03f), "lowpass", 0.3f); tone(70f, 0.25f, "sine", 0.4f, 0.5f) }
            "scrape" -> noiseBurst(0.12f, 3000f, 2f, 0.12f)
            "lap" -> { tone(784f, 0.22f, "square", 0.12f, 1f); tone(988f, 0.22f, "square", 0.12f, 1f, 0.1f); tone(1175f, 0.22f, "square", 0.12f, 1f, 0.2f) }
            "finish" -> { val fs = floatArrayOf(523f, 659f, 784f, 1047f, 784f, 1047f); for (i in fs.indices) tone(fs[i], 0.3f, "square", 0.13f, 1f, i * 0.13f) }
            "pad" -> { noiseBurst(0.4f, 1500f, 1f, 0.5f, "bandpass", 3f); tone(600f, 0.25f, "sawtooth", 0.1f, 2f) }
            "land" -> { tone(80f, 0.2f, "sine", 0.4f, 0.6f); noiseBurst(0.15f, 300f, 1f, 0.3f, "lowpass") }
            "item" -> { tone(880f, 0.08f, "square", 0.12f); tone(1175f, 0.12f, "square", 0.12f, 1f, 0.07f) }
            "missile" -> noiseBurst(1f, 500f, 1f, 0.5f, "bandpass", 4f)
            "boom" -> { noiseBurst(0.8f, 200f, 0.7f, 1f, "lowpass", 0.2f); tone(55f, 0.6f, "sine", 0.5f, 0.4f) }
            "banana" -> tone(300f, 0.3f, "triangle", 0.25f, 0.4f)
            "shield" -> { tone(660f, 0.25f, "sine", 0.15f, 1f); tone(880f, 0.25f, "sine", 0.15f, 1f, 0.05f); tone(1100f, 0.25f, "sine", 0.15f, 1f, 0.1f) }
            "click" -> tone(1200f, 0.05f, "square", 0.08f)
            "wrong" -> tone(220f, 0.3f, "square", 0.1f)
        }
    }

    fun playSimple(name: String) = play(name)

    private fun Double.toSamples(): Long = (this * SR).toLong()

    private fun enqueue(start: Long, durSamples: Int, type: String, f0: Float, f1: Float, vol: Float,
                        filterType: Int, cutoff: Float, q: Float, pad: Boolean) {
        synchronized(events) {
            if (events.size > 96) events.removeAt(0)
            events.add(VoiceEvent(start, durSamples, type, f0, f1, vol, filterType, cutoff, q, pad))
        }
    }

    // ---------- 渲染 ----------
    private fun render(out: ShortArray) {
        val n = out.size
        val mix = FloatArray(n)
        val now = framesWritten

        // 音乐调度
        if (playing && musicOn) {
            val curT = now.toDouble() / SR
            if (nextNoteTime < curT - 0.2) nextNoteTime = curT + 0.05
            val songDef = Songs.ALL[song]
            val spb = 60.0 / songDef.bpm / 4
            while (nextNoteTime < curT + 0.12) {
                playStep(songDef, step, nextNoteTime, spb.toFloat())
                nextNoteTime += spb
                step++
            }
        }

        // 一次性事件
        synchronized(events) {
            val it = events.iterator()
            while (it.hasNext()) {
                val ev = it.next()
                val end = ev.start + ev.durSamples
                if (end <= now) { it.remove(); continue }
                if (ev.start >= now + n) continue
                renderEvent(ev, now, mix)
            }
        }

        // 引擎（锯齿 + 方波 + 低通）
        val ratio = engRatio
        val gears = 6
        val g = min((gears - 1).toFloat(), (ratio * gears * 0.999f).toInt().toFloat()).toInt()
        val inGear = ratio * gears - g
        val rpm = 0.3f + inGear * 0.7f * (0.75f + g * 0.05f)
        val targetF1 = 55f + rpm * 130f + (if (engBoost) 35f else 0f)
        val targetCut = 500f + rpm * 1600f + (if (engThrottle > 0) 500f else 0f)
        val targetGain = if (engActive) 0.085f + (if (engThrottle > 0) 0.05f else 0f) else 0f
        val k = 1f - exp(-1f / (0.05f * SR))
        eng1Freq += (targetF1 - eng1Freq) * k
        eng2Freq += (targetF1 * 0.5f - eng2Freq) * k
        engCut += (targetCut - engCut) * (1f - exp(-1f / (0.08f * SR)))
        engGainNow += (targetGain - engGainNow) * (1f - exp(-1f / (0.1f * SR)))
        val lpA = 1f - exp(-2.0 * Math.PI * engCut / SR).toFloat()
        val e1d = eng1Freq / SR; val e2d = eng2Freq / SR
        for (i in 0 until n) {
            eng1Phase += e1d
            if (eng1Phase >= 1f) eng1Phase -= 1f
            eng2Phase += e2d
            if (eng2Phase >= 1f) eng2Phase -= 1f
            val saw = 2f * eng1Phase - 1f
            val sq = if (eng2Phase < 0.5f) 1f else -1f
            val x = saw + sq * 0.35f
            engLpY += lpA * (x - engLpY)
            mix[i] += engLpY * engGainNow
        }

        // 漂移胎噪（带通噪声）
        val skidTarget = if (engDrift && engActive) 0.13f else 0f
        skidGainNow += (skidTarget - skidGainNow) * (1f - exp(-1f / (0.05f * SR)))
        skidCut += ((1800f + ratio * 1200f) - skidCut) * (1f - exp(-1f / (0.1f * SR)))
        renderBandpass(mix, n, skidCut, 3f, skidGainNow, true)

        // 氮气风声
        val windTarget = if (engActive) ratio * 0.05f + (if (engBoost) 0.18f else 0f) else 0f
        windGainNow += (windTarget - windGainNow) * (1f - exp(-1f / (0.15f * SR)))
        renderBandpass(mix, n, 700f, 0.7f, windGainNow, false)

        // 输出（软限幅 + 16bit）
        for (i in 0 until n) {
            var v = mix[i]
            if (v > 1.2f) v = 1.2f else if (v < -1.2f) v = -1.2f
            out[i] = (v * 32000f).toInt().toShort()
        }
        framesWritten += n
    }

    private fun renderBandpass(mix: FloatArray, n: Int, freq: Float, q: Float, gain: Float, advanceState: Boolean) {
        if (gain < 0.0005f) return
        val w0 = 2.0 * Math.PI * freq / SR
        val alpha = sin(w0).toFloat() / (2f * q)
        val cosW = kotlin.math.cos(w0).toFloat()
        val a0 = 1f + alpha
        val b0 = alpha / a0; val b2 = -alpha / a0
        val a1 = (-2f * cosW) / a0; val a2 = (1f - alpha) / a0
        val useSkid = advanceState
        var x1 = if (useSkid) skidX1 else windX1
        var x2 = if (useSkid) skidX2 else windX2
        var y1 = if (useSkid) skidY1 else windY1
        var y2 = if (useSkid) skidY2 else windY2
        for (i in 0 until n) {
            val xn = noise[noisePos]; noisePos = (noisePos + 1) % noise.size
            val yn = b0 * xn + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = xn; y2 = y1; y1 = yn
            mix[i] += yn * gain
        }
        if (useSkid) { skidX1 = x1; skidX2 = x2; skidY1 = y1; skidY2 = y2 }
        else { windX1 = x1; windX2 = x2; windY1 = y1; windY2 = y2 }
    }

    private fun renderEvent(ev: VoiceEvent, now: Long, mix: FloatArray) {
        val from = maxOf(ev.start, now)
        val to = minOf(ev.start + ev.durSamples, now + mix.size)
        if (to <= from) return
        val off = (from - now).toInt()
        val len = (to - from).toInt()
        // 包络
        val atk = if (ev.pad) (0.2f * SR).toInt() else (0.01f * SR).toInt()
        val t0 = (from - ev.start).toFloat()
        val f0 = ev.f0; val f1 = if (ev.f1 > 0) ev.f1 else ev.f0
        val decayK = if (ev.durSamples > atk + 1) (ln(100000f) / (ev.durSamples - atk)).toFloat() else 10f
        // 滤波系数
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        var hasFilter = ev.filterType != 0
        if (hasFilter) {
            val w0 = 2.0 * Math.PI * ev.cutoff / SR
            val alpha = kotlin.math.sin(w0).toFloat() / (2f * ev.q)
            val cosW = kotlin.math.cos(w0).toFloat()
            when (ev.filterType) {
                1 -> { // lowpass
                    val a0 = 1f + alpha
                    b0 = ((1f - kotlin.math.cos(w0).toFloat()) / 2f) / a0
                    b1 = (1f - kotlin.math.cos(w0).toFloat()) / a0
                    b2 = b0
                    a1 = (-2f * cosW) / a0; a2 = (1f - alpha) / a0
                }
                2 -> { // bandpass
                    val a0 = 1f + alpha
                    b0 = alpha / a0; b1 = 0f; b2 = -alpha / a0
                    a1 = (-2f * cosW) / a0; a2 = (1f - alpha) / a0
                }
                else -> { // highpass
                    val a0 = 1f + alpha
                    b0 = ((1f + kotlin.math.cos(w0).toFloat()) / 2f) / a0
                    b1 = (-(1f + kotlin.math.cos(w0).toFloat())) / a0
                    b2 = b0
                    a1 = (-2f * cosW) / a0; a2 = (1f - alpha) / a0
                }
            }
        }
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in 0 until len) {
            val tAbs = t0 + i
            val prog = tAbs / ev.durSamples
            // 频率滑音
            val f = if (ev.f1 > 0) f0 * (f1 / f0).pow(prog) else f0
            ev.phase += f / SR
            if (ev.phase >= 1f) ev.phase -= 1f
            val raw: Float = when (ev.type) {
                "sine" -> sin(ev.phase * 2f * Math.PI.toFloat())
                "square" -> if (ev.phase < 0.5f) 1f else -1f
                "saw" -> 2f * ev.phase - 1f
                "triangle" -> 1f - 4f * kotlin.math.abs(ev.phase - 0.5f)
                else -> { // noise
                    noisePos = (noisePos + 1) % noise.size
                    noise[noisePos]
                }
            }
            // 包络
            val env: Float
            env = if (tAbs < atk) {
                if (atk > 0) (tAbs.toFloat() / atk) else 1f
            } else {
                val e = exp(-(tAbs - atk) * decayK)
                if (e < 0.0001f) 0f else e
            }
            var v = raw * env * ev.vol
            if (hasFilter) {
                val yn = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = v; y2 = y1; y1 = yn
                v = yn
            }
            val oi = off + i
            if (oi < mix.size) mix[oi] += v
        }
    }

    // ---------- 音乐步进音序器 ----------
    private fun playStep(song: SongDef, step: Int, t: Double, spb: Float) {
        val ctx = t.toSamples()
        val bar = (step / 16) % 4
        val s16 = step % 16
        val chord = song.prog[bar]
        val root = song.root
        // 底鼓
        if (s16 % 4 == 0) {
            enqueue(ctx, (0.18f * SR).toInt(), "sine", 150f, 40f, 0.9f, 0, 0f, 0f, false)
        }
        // 军鼓 / 拍手
        if (s16 == 4 || s16 == 12) musicNoise(ctx, 0.12f, 1800f, 0.35f, 2)
        // 踩镲
        if (s16 % 2 == 1 || song.style == 3) musicNoise(ctx, 0.03f, 8000f, if (s16 % 4 == 2) 0.18f else 0.1f, 3)
        // 贝斯
        val bi = s16 / 2
        if (s16 % 2 == 0 && song.bassPat[bi % 8] == 1) {
            val n = root - 24 + chord[0] + (if (bi % 4 == 3) 12 else 0)
            musicTone(ctx, note(n), spb * 1.8f, "saw", 0.22f, 600f, false)
        }
        // 和弦垫（每小节）
        if (s16 == 0) {
            for (c in chord) musicTone(ctx, note(root + c), spb * 15f, if (song.style == 1) "triangle" else "saw", 0.045f, 1400f, true)
        }
        // 琶音主旋律
        if (s16 % 2 == 0) {
            val li = (s16 / 2 + bar * 2) % song.lead.size
            val n = root + 12 + chord[0] + song.lead[li]
            musicTone(ctx, note(n), spb * 1.6f, if (song.style == 2) "triangle" else "square", 0.06f, 3000f, false)
        }
    }

    private fun note(n: Int): Float = 440f * 2f.pow((n - 69) / 12f)

    private fun musicTone(start: Long, f: Float, dur: Float, type: String, vol: Float, cutoff: Float, pad: Boolean) {
        enqueue(start, (dur * SR).toInt(), type, f, 0f, vol, 1, cutoff, 0f, pad)
    }

    private fun musicNoise(start: Long, dur: Float, f: Float, vol: Float, filterType: Int) {
        enqueue(start, (dur * SR).toInt(), "noise", f, 0f, vol, filterType, f, 1f, false)
    }
}
