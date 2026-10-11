package com.feiche.qqspeed

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.feiche.qqspeed.game.CarSkin
import com.feiche.qqspeed.game.Game
import com.feiche.qqspeed.game.Maps
import com.feiche.qqspeed.gl.GameRenderer
import com.feiche.qqspeed.ui.HudView

class MainActivity : Activity() {
    lateinit var game: Game
    lateinit var glView: GLSurfaceView
    lateinit var hudView: HudView
    lateinit var root: FrameLayout
    lateinit var menuScreen: ScrollView
    private lateinit var pauseScreen: FrameLayout
    private lateinit var resultScreen: FrameLayout
    private var resultBuilt = false

    private val selMapViews = ArrayList<View>()
    private var skinName: TextView? = null
    private val optionViews = HashMap<String, ArrayList<Pair<View, Int>>>()

    val C_ORANGE = Color.rgb(255, 138, 0)
    val C_CYAN = Color.rgb(39, 199, 255)
    val C_TXT = Color.rgb(244, 248, 255)
    val C_MUTED = Color.rgb(169, 189, 219)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        game = Game(this)
        game.loadSettings()
        game.hudSink = null // 稍后由 HudView 提供

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        glView.setRenderer(GameRenderer(game))
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        hudView = HudView(this, game)
        game.hudSink = hudView
        hudView.onResultsCallback = { title, win, stats, rows ->
            runOnUiThread { showResults(title, win, stats, rows) }
        }
        hudView.onPauseUICallback = { runOnUiThread { syncOverlays() } }
        hudView.onPauseRequested = { queueEvent { game.togglePause() } }

        root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        root.addView(hudView, FrameLayout.LayoutParams(-1, -1))
        menuScreen = buildMenu()
        root.addView(menuScreen, FrameLayout.LayoutParams(-1, -1))
        pauseScreen = buildPauseScreen()
        root.addView(pauseScreen, FrameLayout.LayoutParams(-1, -1))
        resultScreen = FrameLayout(this)
        root.addView(resultScreen, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        hideSystemUI()
        syncOverlays()
    }

    fun queueEvent(r: Runnable) = glView.queueEvent(r)

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // ---------- 菜单 ----------
    private fun card(title: String): Pair<LinearLayout, LinearLayout> {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
        val bg = GradientDrawable().apply { setColor(Color.argb(185, 10, 20, 48)); cornerRadius = 12 * d }
        box.background = bg
        val h = TextView(this)
        h.text = title
        h.setTextColor(C_CYAN)
        h.textSize = 13f
        h.letterSpacing = 0.15f
        h.setTypeface(null, Typeface.BOLD)
        box.addView(h, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (8 * d).toInt() })
        return Pair(box, box)
    }

    private fun optionChip(label: String, sub: String? = null): LinearLayout {
        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.VERTICAL
        chip.setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        val bg = GradientDrawable().apply {
            cornerRadius = 10 * d
            setColor(Color.argb(20, 255, 255, 255))
            setStroke(1, Color.argb(50, 255, 255, 255))
        }
        chip.background = bg
        chip.gravity = Gravity.CENTER
        val t = TextView(this)
        t.text = label
        t.setTextColor(Color.rgb(232, 241, 255))
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        chip.addView(t)
        if (sub != null) {
            val s = TextView(this)
            s.text = sub
            s.setTextColor(Color.argb(200, 232, 241, 255))
            s.textSize = 10f
            s.gravity = Gravity.CENTER
            chip.addView(s)
        }
        return chip
    }

    private fun selectChip(chip: View, sel: Boolean) {
        val bg = GradientDrawable().apply {
            cornerRadius = 10 * d
            if (sel) {
                setColor(Color.rgb(255, 138, 0))
                setStroke(1, Color.rgb(255, 212, 138))
            } else {
                setColor(Color.argb(20, 255, 255, 255))
                setStroke(1, Color.argb(50, 255, 255, 255))
            }
        }
        chip.background = bg
    }

    private fun buildMenu(): ScrollView {
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((16 * d).toInt(), (18 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
        col.gravity = Gravity.CENTER_HORIZONTAL
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        scroll.background = GradientDrawable().apply {
            colors = intArrayOf(Color.argb(90, 8, 20, 55), Color.argb(235, 8, 20, 55))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }

        val logo = TextView(this)
        logo.text = "飞车 3D"
        logo.textSize = 44f
        logo.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC))
        logo.setTextColor(Color.WHITE)
        logo.rotation = -4f
        logo.setShadowLayer(6f, 0f, 3f, Color.rgb(11, 63, 176))
        col.addView(logo)
        val sub = TextView(this)
        sub.text = "QQ飞车 同人还原 · 漂移 · 集气 · 小喷 · 双喷 · 氮气"
        sub.setTextColor(Color.rgb(207, 227, 255))
        sub.textSize = 12f
        sub.letterSpacing = 0.2f
        col.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (10 * d).toInt() })

        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.HORIZONTAL
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL; left.weightSum = 1f
        val right = LinearLayout(this); right.orientation = LinearLayout.VERTICAL; right.weightSum = 1f
        grid.addView(left, LinearLayout.LayoutParams(0, -2, 1.35f).apply { rightMargin = (10 * d).toInt() })
        grid.addView(right, LinearLayout.LayoutParams(0, -2, 1f))

        // 赛道
        val (mapCard, mapBox) = card("选择赛道")
        left.addView(mapCard, LinearLayout.LayoutParams(-1, -2))
        val mapGrid = LinearLayout(this); mapGrid.orientation = LinearLayout.VERTICAL
        mapBox.addView(mapGrid)
        var row: LinearLayout? = null
        Maps.ALL.forEachIndexed { i, m ->
            if (i % 2 == 0) {
                row = LinearLayout(this); row!!.orientation = LinearLayout.HORIZONTAL
                mapGrid.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            val chip = LinearLayout(this)
            chip.orientation = LinearLayout.VERTICAL
            chip.setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            chip.background = GradientDrawable().apply {
                cornerRadius = 12 * d
                colors = intArrayOf(
                    when (m.id) {
                        "city" -> Color.rgb(0x2f, 0x7f, 0xe0); "aegean" -> Color.rgb(0x1d, 0x63, 0xc9)
                        "egypt" -> Color.rgb(0x5b, 0x3f, 0x9e); else -> Color.rgb(0x3f, 0x86, 0xd6)
                    },
                    when (m.id) {
                        "city" -> Color.rgb(0xa6, 0xe1, 0xff); "aegean" -> Color.rgb(0xf4, 0xf1, 0xea)
                        "egypt" -> Color.rgb(0xf3, 0xd5, 0x8f); else -> Color.WHITE
                    })
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            chip.setOnClickListener { onMapPicked(m.id) }
            val nm = TextView(this); nm.text = m.name; nm.textSize = 18f; nm.setTypeface(null, Typeface.BOLD); nm.setTextColor(Color.WHITE)
            nm.setShadowLayer(3f, 0f, 2f, Color.argb(100, 0, 0, 0))
            chip.addView(nm)
            val en = TextView(this); en.text = "${m.en} · ${m.tag}"; en.textSize = 9f; en.setTextColor(Color.argb(220, 255, 255, 255))
            chip.addView(en)
            selMapViews.add(chip)
            row!!.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * d).toInt(); topMargin = (6 * d).toInt() })
        }

        // 模式
        val (modeCard, modeBox) = card("比赛模式")
        left.addView(modeCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * d).toInt() })
        val modeRow = LinearLayout(this)
        modeBox.addView(modeRow)
        val modes = listOf("speed" to Pair("竞速模式", "漂移集气、小喷、双喷"), "item" to Pair("道具模式", "导弹、香蕉皮、天使、磁铁"))
        modes.forEachIndexed { i, (id, label) ->
            val chip = optionChip(label.first, label.second)
            chip.setOnClickListener { game.mode = id; refreshMenu(); clickSfx() }
            selectChip(chip, game.mode == id)
            optionViews.getOrPut("mode") { ArrayList() }.add(Pair(chip, i))
            modeRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * d).toInt() })
        }

        // 赛车
        val (skinCard, skinBox) = card("选择赛车")
        right.addView(skinCard, LinearLayout.LayoutParams(-1, -2))
        val skinRow = HorizontalScrollView(this)
        skinRow.isHorizontalScrollBarEnabled = false
        val skinLine = LinearLayout(this)
        skinRow.addView(skinLine)
        skinBox.addView(skinRow, LinearLayout.LayoutParams(-1, -2))
        CarSkin.ALL.forEachIndexed { i, s ->
            val sw = View(this)
            val bg = GradientDrawable().apply {
                cornerRadius = 10 * d
                colors = intArrayOf(s.body or 0xFF000000.toInt(), s.accent or 0xFF000000.toInt())
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            sw.background = bg
            sw.setOnClickListener { game.skinIdx = i; refreshMenu(); clickSfx(); queueEvent { if (game.state == "menu") game.startDemo() } }
            sw.minimumWidth = (44 * d).toInt()
            sw.minimumHeight = (44 * d).toInt()
            optionViews.getOrPut("skin") { ArrayList() }.add(Pair(sw, i))
            skinLine.addView(sw, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply { marginEnd = (8 * d).toInt() })
        }
        val sn = TextView(this)
        sn.setTextColor(Color.rgb(255, 210, 58)); sn.textSize = 12f
        skinBox.addView(sn)
        skinName = sn

        fun addOpts(cardTitle: String, key: String, items: List<String>, sel: (Int) -> Boolean, pick: (Int) -> Unit): LinearLayout {
            val (c, b) = card(cardTitle)
            right.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * d).toInt() })
            val r = LinearLayout(this)
            b.addView(r)
            items.forEachIndexed { i, label ->
                val chip = optionChip(label)
                chip.setOnClickListener { pick(i); refreshMenu(); clickSfx() }
                optionViews.getOrPut(key) { ArrayList() }.add(Pair(chip, i))
                r.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = if (i < items.size - 1) (6 * d).toInt() else 0 })
            }
            return r
        }
        addOpts("圈数", "laps", Game.LAPS.map { "$it 圈" }, { Game.LAPS[it] == game.laps }, { game.laps = Game.LAPS[it] })
        addOpts("对手水平", "diff", Game.DIFFS.map { it.name }, { it == game.diffIdx }, { game.diffIdx = it })
        addOpts("画质", "quality", com.feiche.qqspeed.game.Qualities.NAMES, { it == game.quality }, { game.quality = it })

        // 操作说明
        val (helpCard, helpBox) = card("操作（还原端游默认键位）")
        col.addView(helpCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * d).toInt() })
        val help = TextView(this)
        help.text = "触屏：自动油门 · 左下 ←/→/刹 转向与刹车 · 漂移键 + 方向 = 漂移集气\n" +
                "出弯回正瞬间点【小喷】= 小喷（越早越完美）；小喷 + 氮气 = 双喷 · 落地瞬间点【小喷】= 落地喷\n" +
                "键盘：↑↓←→ 驾驶 · Shift 漂移 · Ctrl/空格 氮气/道具 · W 小喷 · R 复位 · C 视角 · Esc 暂停\n" +
                "手柄：A 油门 · B/R2 漂移 · X/Y 氮气 · L2 刹车 · Start 暂停"
        help.setTextColor(Color.rgb(220, 232, 255))
        help.textSize = 11f
        help.setLineSpacing(0f, 1.4f)
        helpBox.addView(help)
        val tip = TextView(this)
        tip.text = "技巧：漂移集气集满获得氮气（最多 2 个）；蓝色箭头是加速带；道具赛里 Ctrl 使用道具。"
        tip.setTextColor(C_MUTED); tip.textSize = 11f
        helpBox.addView(tip, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (4 * d).toInt() })

        // 开始
        val start = Button(this)
        start.text = "开始比赛"
        start.textSize = 20f
        start.setTextColor(Color.WHITE)
        start.setTypeface(null, Typeface.BOLD)
        start.background = GradientDrawable().apply {
            cornerRadius = 40 * d
            colors = intArrayOf(Color.rgb(255, 177, 59), Color.rgb(255, 122, 0))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        start.setShadowLayer(8f, 0f, 4f, Color.argb(140, 255, 138, 0))
        start.setOnClickListener {
            clickSfx()
            menuScreen.visibility = View.GONE
            queueEvent { game.startRace() }
        }
        col.addView(start, LinearLayout.LayoutParams(-2, (56 * d).toInt()).apply { topMargin = (12 * d).toInt(); leftMargin = (40 * d).toInt(); rightMargin = (40 * d).toInt() })

        val foot = TextView(this)
        foot.text = "非官方同人作品，致敬 QQ飞车。所有模型、贴图、音乐均为程序实时生成。"
        foot.setTextColor(Color.argb(150, 200, 220, 255))
        foot.textSize = 10f
        foot.gravity = Gravity.CENTER
        col.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * d).toInt() })

        refreshMenu()
        return scroll
    }

    private fun onMapPicked(id: String) {
        if (game.mapId == id) return
        game.mapId = id
        refreshMenu()
        clickSfx()
        queueEvent {
            game.loadMap(id)
            game.startDemo()
        }
    }

    private fun refreshMenu() {
        Maps.ALL.forEachIndexed { i, m ->
            val chip = selMapViews[i]
            val gd = chip.background as GradientDrawable
            gd.setStroke(if (game.mapId == m.id) (2 * d).toInt() else 0, Color.rgb(255, 210, 58))
        }
        for ((key, pairs) in optionViews) {
            for ((view, idx) in pairs) {
                when (key) {
                    "mode" -> selectChip(view, (if (idx == 0) "speed" else "item") == game.mode)
                    "skin" -> {
                        val gd = view.background as GradientDrawable
                        gd.setStroke(if (idx == game.skinIdx) (3 * d).toInt() else (1 * d).toInt(),
                            if (idx == game.skinIdx) Color.rgb(255, 210, 58) else Color.argb(60, 255, 255, 255))
                        view.scaleX = if (idx == game.skinIdx) 1.12f else 1f
                        view.scaleY = if (idx == game.skinIdx) 1.12f else 1f
                    }
                    "laps" -> selectChip(view, Game.LAPS[idx] == game.laps)
                    "diff" -> selectChip(view, idx == game.diffIdx)
                    "quality" -> selectChip(view, idx == game.quality)
                }
            }
        }
        skinName?.text = CarSkin.ALL[game.skinIdx].name
        game.saveSettings()
    }

    private fun clickSfx() = game.audio.playSimple("click")

    // ---------- 暂停 ----------
    private fun buildPauseScreen(): FrameLayout {
        val fl = FrameLayout(this)
        fl.setBackgroundColor(Color.argb(185, 6, 14, 36))
        fl.visibility = View.GONE
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding((26 * d).toInt(), (20 * d).toInt(), (26 * d).toInt(), (20 * d).toInt())
        val bg = GradientDrawable().apply { setColor(Color.argb(220, 10, 20, 48)); cornerRadius = 16 * d }
        box.background = bg
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
        lp.leftMargin = (30 * d).toInt(); lp.rightMargin = (30 * d).toInt()
        fl.addView(box, lp)
        val title = TextView(this)
        title.text = "暂停"
        title.textSize = 26f
        title.setTypeface(null, Typeface.BOLD)
        title.letterSpacing = 0.2f
        title.setTextColor(Color.WHITE)
        box.addView(title, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (12 * d).toInt() })
        fun btn(text: String, primary: Boolean, fn: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.textSize = 16f
            b.setTextColor(Color.WHITE)
            b.background = GradientDrawable().apply {
                cornerRadius = 40 * d
                colors = if (primary) intArrayOf(Color.rgb(255, 177, 59), Color.rgb(255, 122, 0))
                else intArrayOf(Color.rgb(74, 140, 255), Color.rgb(29, 95, 224))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            b.setPadding((30 * d).toInt(), 0, (30 * d).toInt(), 0)
            b.setOnClickListener { fn() }
            return b
        }
        box.addView(btn("继续比赛", true) { queueEvent { game.togglePause() } },
            LinearLayout.LayoutParams(-2, (46 * d).toInt()).apply { bottomMargin = (8 * d).toInt() })
        box.addView(btn("重新开始", false) { pauseScreen.visibility = View.GONE; queueEvent { game.startRace() } },
            LinearLayout.LayoutParams(-2, (46 * d).toInt()).apply { bottomMargin = (8 * d).toInt() })
        box.addView(btn("返回大厅", false) { queueEvent { game.toMenu() } },
            LinearLayout.LayoutParams(-2, (46 * d).toInt()))
        return fl
    }

    // ---------- 结算 ----------
    private fun showResults(title: String, win: Boolean, stats: com.feiche.qqspeed.game.Car.RaceStats,
                            rows: List<com.feiche.qqspeed.game.ResultRow>) {
        resultScreen.removeAllViews()
        val fl = FrameLayout(this)
        fl.setBackgroundColor(Color.argb(185, 6, 14, 36))
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding((26 * d).toInt(), (16 * d).toInt(), (26 * d).toInt(), (18 * d).toInt())
        box.background = GradientDrawable().apply { setColor(Color.argb(225, 10, 20, 48)); cornerRadius = 16 * d }
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
        lp.leftMargin = (26 * d).toInt(); lp.rightMargin = (26 * d).toInt()
        fl.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val boxLp = FrameLayout.LayoutParams(-1, -2)
        boxLp.leftMargin = (26 * d).toInt(); boxLp.rightMargin = (26 * d).toInt()
        scroll.addView(box, boxLp)
        val t = TextView(this)
        t.text = title
        t.textSize = 26f
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC))
        t.setTextColor(if (win) Color.rgb(255, 210, 58) else Color.WHITE)
        box.addView(t, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (8 * d).toInt(); topMargin = (4 * d).toInt() })

        // 统计
        val statRow = LinearLayout(this)
        statRow.orientation = LinearLayout.HORIZONTAL
        statRow.gravity = Gravity.CENTER
        val statData = listOf(
            "漂移" to stats.drift.toString(), "小喷" to stats.small.toString(), "完美" to stats.perfect.toString(),
            "双喷" to stats.doubleB.toString(), "氮气" to stats.nitro.toString(),
            "最高时速" to "${stats.top.toInt()}", "碰撞" to stats.crash.toString())
        for ((k, v) in statData) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            cell.background = GradientDrawable().apply { setColor(Color.argb(20, 255, 255, 255)); cornerRadius = 8 * d }
            val vb = TextView(this); vb.text = v; vb.textSize = 15f; vb.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC)); vb.setTextColor(Color.WHITE)
            cell.addView(vb)
            val kb = TextView(this); kb.text = k; kb.textSize = 9f; kb.setTextColor(C_MUTED)
            cell.addView(kb)
            statRow.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(statRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * d).toInt() })

        // 表头
        fun tableRow(cells: List<Pair<String, Float>>, me: Boolean = false, header: Boolean = false): LinearLayout {
            val r = LinearLayout(this)
            if (me) {
                r.background = GradientDrawable().apply { setColor(Color.argb(75, 255, 138, 0)); cornerRadius = 6 * d }
            }
            for ((txt, w) in cells) {
                val tv = TextView(this)
                tv.text = txt
                tv.textSize = if (header) 10f else 13f
                tv.setTextColor(if (header) C_MUTED else Color.WHITE)
                tv.setTypeface(if (header) null else Typeface.create("sans-serif-black", Typeface.ITALIC))
                tv.gravity = Gravity.CENTER_VERTICAL
                r.addView(tv, LinearLayout.LayoutParams(0, -2, w).apply { marginEnd = (6 * d).toInt(); topMargin = (4 * d).toInt(); bottomMargin = (4 * d).toInt() })
            }
            return r
        }
        box.addView(tableRow(listOf("名次" to 0.6f, "车手" to 1.6f, "总时间" to 1.6f, "最佳单圈" to 1.4f), header = true))
        for (row in rows) {
            box.addView(tableRow(listOf("${row.rank}" to 0.6f, row.name to 1.6f, row.total to 1.6f, row.best to 1.4f), me = row.me))
        }

        fun btn(text: String, primary: Boolean, fn: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.textSize = 16f
            b.setTextColor(Color.WHITE)
            b.background = GradientDrawable().apply {
                cornerRadius = 40 * d
                colors = if (primary) intArrayOf(Color.rgb(255, 177, 59), Color.rgb(255, 122, 0))
                else intArrayOf(Color.rgb(74, 140, 255), Color.rgb(29, 95, 224))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
            b.setPadding((30 * d).toInt(), 0, (30 * d).toInt(), 0)
            b.setOnClickListener { fn() }
            return b
        }
        val btnRow = LinearLayout(this)
        btnRow.gravity = Gravity.CENTER
        btnRow.addView(btn("再来一局", true) {
            resultScreen.visibility = View.GONE
            queueEvent { game.startRace() }
        }, LinearLayout.LayoutParams(-2, (46 * d).toInt()).apply { rightMargin = (12 * d).toInt() })
        btnRow.addView(btn("返回大厅", false) {
            resultScreen.visibility = View.GONE
            menuScreen.visibility = View.VISIBLE
            queueEvent { game.toMenu() }
        }, LinearLayout.LayoutParams(-2, (46 * d).toInt()))
        box.addView(btnRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (10 * d).toInt(); gravity = Gravity.CENTER_HORIZONTAL })

        resultScreen.addView(fl)
        resultScreen.visibility = View.VISIBLE
        resultBuilt = true
    }

    // ---------- 覆盖层同步 ----------
    private fun syncOverlays() {
        val st = game.state
        val inMenu = st == "menu"
        menuScreen.visibility = if (inMenu) View.VISIBLE else View.GONE
        pauseScreen.visibility = if (st == "paused") View.VISIBLE else View.GONE
    }

    // ---------- 输入 ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleKey(keyCode, true, event?.repeatCount ?: 0)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleKey(keyCode, false, event?.repeatCount ?: 0)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleKey(code: Int, down: Boolean, repeat: Int): Boolean {
        val inp = game.input
        val edge = down && repeat == 0
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { if (edge) inp.upPressed = true; inp.up = down }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> inp.down = down
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> inp.left = down
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> inp.right = down
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_R2 -> inp.shift = down
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> { if (edge) inp.nitroPressed = true }
            KeyEvent.KEYCODE_BUTTON_A -> { if (edge) inp.upPressed = true; inp.up = down }
            KeyEvent.KEYCODE_BUTTON_L2 -> inp.down = down
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_BUTTON_L1 -> { if (edge) inp.swapPressed = true }
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_BUTTON_SELECT -> { if (edge) inp.resetPressed = true }
            KeyEvent.KEYCODE_C -> { if (edge) queueEvent { game.camMode = (game.camMode + 1) % 3 } }
            KeyEvent.KEYCODE_M -> { if (edge) { val on = game.audio.toggleMusic() } }
            KeyEvent.KEYCODE_ENTER -> {
                if (edge && game.state == "menu" && menuScreen.visibility == View.VISIBLE) {
                    menuScreen.visibility = View.GONE
                    queueEvent { game.startRace() }
                }
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_P -> {
                if (edge) {
                    if (menuScreen.visibility == View.VISIBLE) finish()
                    else queueEvent { game.togglePause() }
                }
            }
            else -> return false
        }
        return true
    }

    // ---------- 生命周期 ----------
    override fun onPause() {
        super.onPause()
        glView.onPause()
        game.audio.silence()
        game.audio.stopMusic()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        glView.onResume()
        if (game.state == "race" || game.state == "countdown" || game.state == "finish") {
            game.audio.startMusic()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hudView.shutdown()
        game.audio.dispose()
    }

    private val d: Float get() = resources.displayMetrics.density
}
