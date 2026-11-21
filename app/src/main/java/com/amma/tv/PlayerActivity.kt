package com.amma.tv

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen YouTube player with a few BIG icon buttons, no words (tap the picture to see them):
 *   ⏪ back 10 s   ⏯ pause / play   ⏩ forward 30 s   ⏭ next
 * A video continues where it stopped. When it ends the next one plays by itself
 * (serials: the next NEWER episode). TV remote: OK = pause, ◀ ▶ = back / forward, CH+/CH- = next / previous.
 */
class PlayerActivity : AppCompatActivity() {
    companion object {
        const val IDS = "ids"
        const val TITLES = "titles"
        const val INDEX = "index"
        const val SERIAL = "serial"
        const val SHOW = "show"
        const val SHOWS = "shows"          // one program id per video (the mixed home page)
        private const val HIDE_MS = 5000L
        private const val STUCK_MS = 30_000L
    }

    private lateinit var web: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var seek: SeekBar
    private lateinit var playBtn: TextView
    private lateinit var spinner: ProgressBar

    private var ids: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var index = 0
    private var serial = false
    private var showId = ""
    private var showIds: List<String> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var gen = 0                 // which video the JavaScript messages belong to
    private var playing = false
    private var started = false
    private var cur = 0
    private var dur = 0
    private var dragging = false
    private var lastSave = 0L
    private var errors = 0

    private val hideRunnable = Runnable { if (playing) overlay.visibility = View.GONE }
    private val stuckRunnable = Runnable { if (!started) failed() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ids = intent.getStringArrayListExtra(IDS) ?: arrayListOf()
        titles = intent.getStringArrayListExtra(TITLES) ?: arrayListOf()
        index = intent.getIntExtra(INDEX, 0).coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        serial = intent.getBooleanExtra(SERIAL, false)
        showId = intent.getStringExtra(SHOW) ?: ""
        showIds = intent.getStringArrayListExtra(SHOWS) ?: arrayListOf()
        if (ids.isEmpty()) { finish(); return }

        setContentView(buildLayout())
        hideBars()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        playIndex(index)
    }

    // ------------------------------------------------------------------ screen

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // a see-through layer over the video: a tap shows / hides the buttons
        val touch = View(this).apply { setOnClickListener { toggleOverlay() } }
        root.addView(touch, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        spinner = ProgressBar(this).apply { isIndeterminate = true }
        val ss = Ui.dp(this, 80)
        root.addView(spinner, FrameLayout.LayoutParams(ss, ss, Gravity.CENTER))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC000000.toInt(), 0x00000000, 0x00000000, 0xDD000000.toInt()))
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // top: [✕] (no words: Amma cannot read)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@PlayerActivity, 14)
            setPadding(p, p, p, p)
        }
        val close = TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            val p = Ui.dp(this@PlayerActivity, 14)
            setPadding(p, p / 2, p, p / 2)
            background = Ui.card(this@PlayerActivity, Ui.GOLD)
            isFocusable = true
            setOnClickListener { finish() }
        }
        Ui.focusZoom(close)
        val cs = Ui.dp(this, 84)
        top.addView(close, LinearLayout.LayoutParams(cs, cs))
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
            setPadding(Ui.dp(this@PlayerActivity, 16), 0, 0, 0)
        }
        top.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        overlay.addView(top)
        overlay.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // bottom: time bar, then the big buttons
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@PlayerActivity, 20), 0, Ui.dp(this@PlayerActivity, 20), 0)
        }
        seek = SeekBar(this).apply {
            max = 1000
            isFocusable = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser && dur > 0) timeView.text = "${clock(p * dur / 1000)} / ${clock(dur)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar) { dragging = true; keepOverlay() }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    dragging = false
                    if (dur > 0) js("seekTo(${sb.progress * dur / 1000})")
                    scheduleHide()
                }
            })
        }
        bar.addView(seek, LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f))
        timeView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(Ui.dp(this@PlayerActivity, 12), 0, 0, 0)
        }
        bar.addView(timeView)
        overlay.addView(bar)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@PlayerActivity, 6), 0, Ui.dp(this@PlayerActivity, 18))
        }
        buttons.addView(bigButton("⏮") { previous() })
        buttons.addView(bigButton("⏪") { js("seekBy(-10)"); keepOverlay(); scheduleHide() })
        playBtn = bigButton("⏸") { togglePause() }
        buttons.addView(playBtn)
        buttons.addView(bigButton("⏩") { js("seekBy(30)"); keepOverlay(); scheduleHide() })
        buttons.addView(bigButton("⏭") { next() })
        overlay.addView(buttons)
        return root
    }

    /** A big round button with only a symbol. */
    private fun bigButton(symbol: String, onClick: () -> Unit): TextView {
        val size = Ui.dp(this, 100)
        return TextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            background = Ui.card(this@PlayerActivity, 0xCC1B5E20.toInt())
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = Ui.dp(this@PlayerActivity, 10); setMargins(m, 0, m, 0)
            }
            Ui.focusZoom(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun toggleOverlay() {
        if (overlay.visibility == View.VISIBLE) overlay.visibility = View.GONE
        else { keepOverlay(); scheduleHide() }
    }

    private fun keepOverlay() {
        handler.removeCallbacks(hideRunnable)
        if (overlay.visibility != View.VISIBLE) {
            overlay.visibility = View.VISIBLE
            playBtn.requestFocus()
        }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_MS)
    }

    private fun clock(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    // ------------------------------------------------------------------ playing

    private fun playIndex(i: Int) {
        savePos()
        index = i
        val id = ids[i]
        gen++
        started = false
        playing = false
        cur = 0
        dur = 0
        seek.progress = 0
        timeView.text = ""
        titleView.text = titles.getOrNull(i) ?: ""
        spinner.visibility = View.VISIBLE
        keepOverlay()
        scheduleHide()
        Watch.setLast(this, showIds.getOrNull(i) ?: showId, Vid(id, titles.getOrNull(i) ?: ""))
        handler.removeCallbacks(stuckRunnable)
        handler.postDelayed(stuckRunnable, STUCK_MS)
        val start = Watch.position(this, id).let { if (it > 10) it - 3 else 0 }
        val html = """<html><body style="margin:0;background:#000">
<div id="p" style="position:fixed;top:0;left:0;width:100%;height:100%"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player = new YT.Player('p', {width:'100%', height:'100%', videoId:'__ID__',
    playerVars:{autoplay:1, controls:0, rel:0, modestbranding:1, playsinline:1, fs:0, disablekb:1,
      iv_load_policy:3, start:__START__},
    events:{
      onReady:function(e){ e.target.playVideo();
        setInterval(function(){ try { if(player && player.getCurrentTime){
          Android.tick(__GEN__, Math.floor(player.getCurrentTime()), Math.floor(player.getDuration())); } } catch(x){} }, 1000); },
      onStateChange:function(e){ Android.state(__GEN__, e.data); },
      onError:function(e){ Android.failed(__GEN__); }
    }});
}
function toggle(){ if(!player) return; if(player.getPlayerState()===1){ player.pauseVideo(); } else { player.playVideo(); } }
function pause(){ try { player.pauseVideo(); } catch(x){} }
function seekBy(d){ if(!player) return; player.seekTo(Math.max(0, player.getCurrentTime()+d), true); }
function seekTo(s){ if(!player) return; player.seekTo(Math.max(0, s), true); }
</script></body></html>"""
            .replace("__ID__", id)
            .replace("__START__", start.toString())
            .replace("__GEN__", gen.toString())
        // the address must be a real https origin, otherwise YouTube refuses to play inside the app
        web.loadDataWithBaseURL("https://com.amma.tv/", html, "text/html", "utf-8", null)
    }

    private fun js(code: String) { web.evaluateJavascript(code, null) }

    private fun togglePause() {
        js("toggle()")
        keepOverlay()
        scheduleHide()
    }

    /** Next video. Serials: the next NEWER episode (the list starts with the newest). */
    private fun next() {
        val n = if (serial) index - 1 else index + 1
        if (n in ids.indices) playIndex(n)
        else {
            Toast.makeText(this, if (serial) "⭐" else "✔", Toast.LENGTH_LONG).show()
            keepOverlay()
        }
    }

    private fun previous() {
        val n = if (serial) index + 1 else index - 1
        if (n in ids.indices) playIndex(n) else keepOverlay()
    }

    /** This video cannot be played here (removed, or the owner does not allow it in apps): try the next one. */
    private fun failed() {
        errors++
        if (errors >= 5) {
            Toast.makeText(this, "📶✖", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Toast.makeText(this, "⚠  ⏭", Toast.LENGTH_SHORT).show()
        val n = if (serial) index - 1 else index + 1
        val target = if (n in ids.indices) n else if (serial) index + 1 else index - 1
        if (target in ids.indices) handler.postDelayed({ if (!isFinishing) playIndex(target) }, 1500)
        else finish()
    }

    private fun savePos() {
        if (ids.isEmpty() || !started || cur < 5) return
        Watch.save(this, ids[index], cur, dur)
    }

    inner class Bridge {
        @JavascriptInterface fun tick(g: Int, sec: Int, length: Int) {
            runOnUiThread {
                if (g != gen) return@runOnUiThread
                cur = sec
                if (length > 0) dur = length
                if (!dragging && dur > 0) {
                    seek.progress = (cur * 1000L / dur).toInt()
                    timeView.text = "${clock(cur)} / ${clock(dur)}"
                }
                val now = System.currentTimeMillis()
                if (started && now - lastSave > 10_000) { lastSave = now; savePos() }
            }
        }

        /** YouTube states: 1 playing, 2 paused, 3 loading, 0 ended. */
        @JavascriptInterface fun state(g: Int, s: Int) {
            runOnUiThread {
                if (g != gen) return@runOnUiThread
                when (s) {
                    1 -> {
                        started = true
                        playing = true
                        errors = 0
                        spinner.visibility = View.GONE
                        handler.removeCallbacks(stuckRunnable)
                        playBtn.text = "⏸"
                        scheduleHide()
                    }
                    2 -> {
                        playing = false
                        playBtn.text = "▶"
                        keepOverlay()
                    }
                    3 -> spinner.visibility = View.VISIBLE
                    0 -> {
                        playing = false
                        if (dur > 0) Watch.save(this@PlayerActivity, ids[index], dur, dur)   // finished: green tick
                        next()
                    }
                }
            }
        }

        @JavascriptInterface fun failed(g: Int) { runOnUiThread { if (g == gen) this@PlayerActivity.failed() } }
    }

    // ------------------------------------------------------------------ remote control

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val hidden = overlay.visibility != View.VISIBLE
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePause(); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { previous(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { js("seekBy(-10)"); keepOverlay(); scheduleHide(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { js("seekBy(30)"); keepOverlay(); scheduleHide(); return true }
        }
        if (hidden) {
            when (event.keyCode) {
                // buttons hidden: OK = pause / play, ◀ ▶ = back / forward, ▲ ▼ = show the buttons
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { togglePause(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { js("seekBy(-10)"); keepOverlay(); scheduleHide(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { js("seekBy(30)"); keepOverlay(); scheduleHide(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { keepOverlay(); scheduleHide(); return true }
            }
        } else if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            scheduleHide()                    // buttons shown: the remote moves between them; keep them a bit longer
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        savePos()
        js("pause()")
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            web.loadUrl("about:blank")
            web.destroy()
        }
        super.onDestroy()
    }
}
