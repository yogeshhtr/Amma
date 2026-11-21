package com.amma.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

/**
 * Made for someone who cannot read: only pictures and icons.
 *
 *  top row  = round icon buttons: 🏠 home, 📺 serials, 🎬 movies, 📖 Herne Katha, 🙏 god, 🕉 pravachan,
 *             📡 live TV (only when an m3u list was added), 🔀 mix (home only)
 *  🏠 home  = the big ▶ "watch again" tile, then the newest episodes / movies / pravachan of everything mixed
 *             (newest first, not-yet-watched first) – or shuffled
 *  a tile   = its programs (each with the picture of its newest video) → the videos
 *
 * Long-press the logo (or the Menu key) opens a small helper menu for the family.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var grid: GridLayoutManager
    private lateinit var iconRow: LinearLayout
    private lateinit var message: TextView
    private lateinit var spinner: ProgressBar
    private val adapter = TileAdapter()

    private var section: Section? = null
    private var show: Show? = null
    private var shuffleSeed = Random.nextInt()

    private val prefs by lazy { getSharedPreferences("amma_home", Context.MODE_PRIVATE) }
    private var shuffle: Boolean
        get() = prefs.getBoolean("shuffle", false)
        set(v) { prefs.edit().putBoolean("shuffle", v).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        render()
    }

    override fun onResume() {
        super.onResume()
        Repo.listener = { id -> if (show == null || show?.id == id) render(keepFocus = true) }
        Repo.refresh(this, Catalog.sections(this).flatMap { it.shows })   // only the old lists
        render(keepFocus = true)                                          // new ticks / red bars
    }

    override fun onPause() {
        Repo.listener = null
        super.onPause()
    }

    // ------------------------------------------------------------------ layout

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@HomeActivity, 12), Ui.dp(this@HomeActivity, 8), 0, Ui.dp(this@HomeActivity, 4))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            setOnLongClickListener { helperMenu(); true }
        }
        val ls = Ui.dp(this, 52)
        header.addView(logo, LinearLayout.LayoutParams(ls, ls).apply { marginEnd = Ui.dp(this@HomeActivity, 6) })
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(iconRow)
        header.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val body = FrameLayout(this)
        recycler = RecyclerView(this).apply {
            clipToPadding = false
            val p = Ui.dp(this@HomeActivity, 8)
            setPadding(p, p, p, Ui.dp(this@HomeActivity, 30))
        }
        grid = GridLayoutManager(this, 2)
        // the "watch again" tile is as wide as the screen
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.isWide(position)) grid.spanCount else 1
        }
        recycler.layoutManager = grid
        recycler.adapter = adapter
        recycler.itemAnimator = null
        body.addView(recycler, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        spinner = ProgressBar(this).apply { isIndeterminate = true }
        val ss = Ui.dp(this, 90)
        body.addView(spinner, FrameLayout.LayoutParams(ss, ss, Gravity.CENTER))
        // nothing found: "no internet ✖  ↻" – tap to try again
        message = TextView(this).apply {
            text = "📶✖   🔄"
            setTextColor(Ui.CREAM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            gravity = Gravity.CENTER
            val p = Ui.dp(this@HomeActivity, 24)
            setPadding(p, p, p, p)
            background = Ui.card(this@HomeActivity, Ui.CARD)
            isFocusable = true
            setOnClickListener {
                val list = show?.let { listOf(it) } ?: Catalog.sections(this@HomeActivity).flatMap { it.shows }
                Repo.refresh(this@HomeActivity, list, force = true)
                render()
            }
        }
        body.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    /** The round icon buttons on top; the current place has a gold ring. */
    private var iconsKey = ""

    private fun buildIcons() {
        val home = section == null
        // rebuilt only when something changed (keeps the remote's gold frame where it is)
        val key = "${section?.id}|$shuffle|" + Catalog.sections(this).joinToString(",") { it.id }
        if (key == iconsKey) return
        iconsKey = key
        iconRow.removeAllViews()
        iconRow.addView(iconButton("🏠", 0xFF2E7D32.toInt(), home) { goHome() })
        for (sec in Catalog.sections(this)) {
            iconRow.addView(iconButton(sec.emoji, sec.color, section?.id == sec.id) { openSection(sec) })
        }
        if (home) iconRow.addView(iconButton(if (shuffle) "🔀" else "🆕", 0xFF4E342E.toInt(), false) {
            shuffle = !shuffle
            shuffleSeed = Random.nextInt()
            render()
            recycler.scrollToPosition(0)
        })
    }

    private fun iconButton(icon: String, color: Int, current: Boolean, onClick: () -> Unit): TextView {
        val size = Ui.dp(this, 72)
        return TextView(this).apply {
            text = icon
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTextColor(Color.WHITE)
            background = if (current) Ui.round(color, 40, this@HomeActivity, 5, Ui.GOLD) else Ui.card(this@HomeActivity, color)
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = Ui.dp(this@HomeActivity, 6); setMargins(m, m, m, m)
            }
            Ui.focusZoom(this)
        }
    }

    private fun columns(): Int = maxOf(2, resources.configuration.screenWidthDp / 240)

    // ------------------------------------------------------------------ screens

    private fun render(keepFocus: Boolean = false) {
        // newest version of the tile (programs may have been added / hidden in the helper menu)
        section = section?.let { old -> Catalog.sections(this).firstOrNull { it.id == old.id } }
        if (section == null) show = null
        val focused = if (keepFocus) recycler.focusedChild?.let { recycler.getChildAdapterPosition(it) } ?: -1 else -1
        grid.spanCount = columns()
        val s = show
        val sec = section
        when {
            s != null && sec != null -> renderVideos(s, sec)
            sec != null -> renderSection(sec)
            else -> renderHome()
        }
        buildIcons()
        if (Ui.isTv(this)) {
            val target = if (focused >= 0) focused else 0
            recycler.post {
                if (recycler.focusedChild == null && currentFocus !is TextView) {
                    grid.scrollToPosition(target)
                    recycler.post { grid.findViewByPosition(target)?.requestFocus() }
                }
            }
        }
    }

    private fun renderHome() {
        val tiles = ArrayList<Tile>()
        Watch.last(this)?.let { last ->
            if (!Watch.finished(this, last.videoId)) {
                tiles.add(Tile(0xFF2E7D32.toInt(), "▶", "https://i.ytimg.com/vi/${last.videoId}/mqdefault.jpg",
                    play = true, progress = Watch.progress(this, last.videoId), wide = true) { playLast(last) })
            }
        }
        val items = feed()
        items.forEachIndexed { i, (sec, _, v) ->
            tiles.add(Tile(sec.color, sec.emoji, v.thumb, progress = Watch.progress(this, v.id),
                done = Watch.finished(this, v.id), badge = sec.emoji) { playFeed(items, i) })
        }
        val loading = items.isEmpty() && Catalog.sections(this).any { s -> s.shows.any { Repo.isLoading(it) } }
        showTiles(tiles, loading)
    }

    /**
     * Everything mixed: the newest video of every program, then the second newest of every program, ...
     * Not-yet-watched videos first. Shuffled when 🔀 is on, or when everything on top was already watched.
     */
    private fun feed(): List<Triple<Section, Show, Vid>> {
        val lists = Catalog.sections(this).filter { it.id in Catalog.FEED }
            .flatMap { s -> s.shows.map { sh -> Triple(s, sh, Repo.cached(this, sh)) } }
            .filter { it.third.isNotEmpty() }
        val out = ArrayList<Triple<Section, Show, Vid>>()
        val seen = HashSet<String>()
        var i = 0
        while (out.size < 300) {
            var any = false
            for ((s, sh, l) in lists) {
                if (i < l.size) {
                    any = true
                    if (seen.add(l[i].id)) out.add(Triple(s, sh, l[i]))
                }
            }
            if (!any) break
            i++
        }
        val sorted = out.sortedBy { if (Watch.finished(this, it.third.id)) 1 else 0 }
        val allWatchedOnTop = sorted.take(8).let { top -> top.isNotEmpty() && top.all { Watch.progress(this, it.third.id) > 0f } }
        return if (shuffle || allWatchedOnTop) sorted.shuffled(Random(shuffleSeed))
            .sortedBy { if (Watch.finished(this, it.third.id)) 1 else 0 } else sorted
    }

    private fun renderSection(sec: Section) {
        val tiles = sec.shows.map { sh ->
            val first = Repo.cached(this, sh).firstOrNull()
            Tile(sec.color, sec.emoji, first?.thumb, badge = sec.emoji,
                title = if (sec.id == "tv") sh.name else "") { openShow(sec, sh) }
        }
        showTiles(tiles, loading = false)
    }

    private fun renderVideos(sh: Show, sec: Section) {
        val list = Repo.cached(this, sh)
        val tiles = list.mapIndexed { i, v ->
            Tile(sec.color, if (v.isLive) "📡" else sec.emoji, v.thumb,
                progress = if (v.isLive) -1f else Watch.progress(this, v.id),
                done = !v.isLive && Watch.finished(this, v.id),
                badge = if (sec.serial && i == 0) "⭐" else null,
                title = if (v.isLive) v.title else "") { play(sh, sec, list, i) }
        }
        showTiles(tiles, loading = Repo.isLoading(sh))
    }

    private fun showTiles(tiles: List<Tile>, loading: Boolean) {
        adapter.submit(tiles)
        spinner.visibility = if (tiles.isEmpty() && loading) View.VISIBLE else View.GONE
        message.visibility = if (tiles.isEmpty() && !loading) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------ moving around

    private fun goHome() {
        section = null
        show = null
        render()
        recycler.scrollToPosition(0)
    }

    private fun openSection(sec: Section) {
        section = sec
        if (sec.shows.size == 1) openShow(sec, sec.shows[0])
        else { show = null; render(); recycler.scrollToPosition(0) }
    }

    private fun openShow(sec: Section, sh: Show) {
        section = sec
        show = sh
        Repo.refresh(this, listOf(sh))
        render()
        recycler.scrollToPosition(0)
    }

    private fun goBack() {
        val sec = section
        if (show != null && sec != null && sec.shows.size > 1) {
            show = null
            render()
            recycler.scrollToPosition(0)
        } else goHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (show != null || section != null) goBack()
        else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { helperMenu(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ playing

    private fun play(sh: Show, sec: Section, list: List<Vid>, index: Int) {
        if (list.getOrNull(index)?.isLive == true) {
            startActivity(Intent(this, LiveActivity::class.java)
                .putStringArrayListExtra(LiveActivity.URLS, ArrayList(list.map { it.url ?: "" }))
                .putStringArrayListExtra(LiveActivity.NAMES, ArrayList(list.map { it.title }))
                .putExtra(LiveActivity.INDEX, index))
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java)
            .putStringArrayListExtra(PlayerActivity.IDS, ArrayList(list.map { it.id }))
            .putStringArrayListExtra(PlayerActivity.TITLES, ArrayList(list.map { it.title }))
            .putExtra(PlayerActivity.INDEX, index)
            .putExtra(PlayerActivity.SERIAL, sec.serial)
            .putExtra(PlayerActivity.SHOW, sh.id))
    }

    /** From the mixed home page: the next video is the next tile of the home page. */
    private fun playFeed(items: List<Triple<Section, Show, Vid>>, index: Int) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putStringArrayListExtra(PlayerActivity.IDS, ArrayList(items.map { it.third.id }))
            .putStringArrayListExtra(PlayerActivity.TITLES, ArrayList(items.map { it.third.title }))
            .putStringArrayListExtra(PlayerActivity.SHOWS, ArrayList(items.map { it.second.id }))
            .putExtra(PlayerActivity.INDEX, index)
            .putExtra(PlayerActivity.SERIAL, false))
    }

    private fun playLast(last: Watch.Last) {
        val sh = Catalog.findShow(this, last.showId)
        val sec = Catalog.sectionOf(this, last.showId)
        val list = sh?.let { Repo.cached(this, it) } ?: emptyList()
        val i = list.indexOfFirst { it.id == last.videoId }
        if (sh != null && sec != null && i >= 0) play(sh, sec, list, i)
        else startActivity(Intent(this, PlayerActivity::class.java)
            .putStringArrayListExtra(PlayerActivity.IDS, arrayListOf(last.videoId))
            .putStringArrayListExtra(PlayerActivity.TITLES, arrayListOf(last.title))
            .putExtra(PlayerActivity.SHOW, last.showId))
    }

    // ------------------------------------------------------------------ helper menu (for the family)

    private fun helperMenu() {
        val items = arrayListOf(
            "↻  Download everything again",
            "＋  Add a YouTube channel / playlist / video / search words",
            "📡  Add live TV (m3u link)",
            "👁  Show / hide programs"
        )
        val added = Catalog.addedShows(this)
        if (added.isNotEmpty()) items.add("🗑  Remove an added program")
        AlertDialog.Builder(this).setTitle("AMMA – helper")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        Repo.refresh(this, Catalog.sections(this).flatMap { it.shows }, force = true)
                        Toast.makeText(this, "↻", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    1 -> addProgram()
                    2 -> Catalog.allSections().firstOrNull { it.id == "tv" }?.let { addProgramTo(it) }
                    3 -> showHide()
                    4 -> removeAdded()
                }
            }.setNegativeButton("✖", null).show()
    }

    private fun addProgram() {
        val secs = Catalog.allSections().filter { it.id != "tv" }
        AlertDialog.Builder(this).setTitle("Which tile?")
            .setItems(secs.map { "${it.emoji}  ${it.title}" }.toTypedArray()) { _, i -> addProgramTo(secs[i]) }
            .setNegativeButton("✖", null).show()
    }

    private fun addProgramTo(sec: Section) {
        val pad = Ui.dp(this, 20)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val link = EditText(this).apply {
            hint = if (sec.id == "tv") "m3u playlist link (http…)" else "YouTube link / @name / search words"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val name = EditText(this).apply { hint = "Name (for you; Amma sees the picture)" }
        box.addView(link)
        box.addView(name)
        AlertDialog.Builder(this).setTitle("${sec.emoji}  ${sec.title}").setView(box)
            .setPositiveButton("＋") { _, _ ->
                val parsed = Catalog.parseLink(link.text.toString())
                if (parsed == null || (sec.id == "tv") != (parsed.first == "m3u")) {
                    Toast.makeText(this, "Link not understood", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val (type, value) = parsed
                val label = name.text.toString().trim().ifEmpty { value.removePrefix("@").take(30) }
                val sh = Show("add_${System.currentTimeMillis()}", label, type, value,
                    minMinutes = if (type == "search" && sec.id == "movie") 60 else 0, added = true,
                    latest = type == "search" && sec.serial)
                Catalog.add(this, sec.id, sh)
                Repo.refresh(this, listOf(sh), force = true)
                Toast.makeText(this, "✔ $label", Toast.LENGTH_SHORT).show()
                render()
            }.setNegativeButton("✖", null).show()
    }

    private fun showHide() {
        val all = Catalog.allShows(this)
        val hidden = Catalog.hidden(this).toMutableSet()
        val names = all.map { (sec, sh) -> "${sec.emoji} ${sec.title} – ${sh.name}" }.toTypedArray()
        val checked = BooleanArray(all.size) { all[it].second.id !in hidden }
        AlertDialog.Builder(this).setTitle("Ticked = shown")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("✔") { _, _ ->
                all.forEachIndexed { i, p -> if (checked[i]) hidden.remove(p.second.id) else hidden.add(p.second.id) }
                Catalog.setHidden(this, hidden)
                goHome()
            }.setNegativeButton("✖", null).show()
    }

    private fun removeAdded() {
        val added = Catalog.addedShows(this)
        AlertDialog.Builder(this).setTitle("Remove")
            .setItems(added.map { it.second.name }.toTypedArray()) { _, i ->
                Catalog.remove(this, added[i].second.id)
                goHome()
            }.setNegativeButton("✖", null).show()
    }
}
