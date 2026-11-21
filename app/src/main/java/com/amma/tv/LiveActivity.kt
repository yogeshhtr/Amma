package com.amma.tv

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Live TV from an m3u list. Tap = big ⏮ ✕ ⏭ buttons. Remote: ▲ ▼ / CH+ CH- = other channel, OK = buttons.
 * A channel that does not start within 12 s (or fails) is skipped automatically.
 */
class LiveActivity : AppCompatActivity() {
    companion object {
        const val URLS = "urls"
        const val NAMES = "names"
        const val INDEX = "index"
        private const val STUCK_MS = 12_000L
    }

    private var urls: List<String> = emptyList()
    private var names: List<String> = emptyList()
    private var index = 0
    private var dir = 1
    private var skips = 0
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var overlay: LinearLayout
    private lateinit var nameView: TextView
    private lateinit var spinner: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private val stuck = Runnable { skip() }
    private val hide = Runnable { overlay.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        urls = intent.getStringArrayListExtra(URLS) ?: arrayListOf()
        names = intent.getStringArrayListExtra(NAMES) ?: arrayListOf()
        index = intent.getIntExtra(INDEX, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        if (urls.isEmpty()) { finish(); return }
        setContentView(buildLayout())
        hideBars()
        player = ExoPlayer.Builder(this).build().also { p ->
            view.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> { handler.removeCallbacks(stuck); spinner.visibility = View.GONE; skips = 0 }
                        Player.STATE_BUFFERING -> spinner.visibility = View.VISIBLE
                        else -> Unit
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        p.seekToDefaultPosition(); p.prepare(); return
                    }
                    skip()
                }
            })
        }
        play(index)
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply {
            useController = false
            isFocusable = false
        }
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val touch = View(this).apply {
            setOnClickListener { if (overlay.visibility == View.VISIBLE) overlay.visibility = View.GONE else showOverlay() }
        }
        root.addView(touch, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        spinner = ProgressBar(this).apply { isIndeterminate = true }
        val ss = Ui.dp(this, 80)
        root.addView(spinner, FrameLayout.LayoutParams(ss, ss, Gravity.CENTER))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, Ui.dp(this@LiveActivity, 24))
        }
        nameView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        overlay.addView(nameView)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(button("⏮") { dir = -1; play(index - 1) })
        row.addView(button("✕") { finish() })
        row.addView(button("⏭") { dir = 1; play(index + 1) })
        overlay.addView(row)
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun button(icon: String, onClick: () -> Unit): TextView {
        val size = Ui.dp(this, 100)
        return TextView(this).apply {
            text = icon
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            background = Ui.card(this@LiveActivity, 0xCC1B5E20.toInt())
            isFocusable = true
            setOnClickListener { onClick(); showOverlay() }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = Ui.dp(this@LiveActivity, 14); setMargins(m, 0, m, 0)
            }
            Ui.focusZoom(this)
        }
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, 5000)
    }

    private fun play(i: Int) {
        index = ((i % urls.size) + urls.size) % urls.size
        nameView.text = names.getOrNull(index) ?: ""
        spinner.visibility = View.VISIBLE
        showOverlay()
        val url = urls[index]
        val path = url.substringBefore('?').lowercase()
        val item = MediaItem.Builder().setUri(url).apply {
            if (path.endsWith(".m3u8") || !listOf(".mp4", ".ts", ".mp3", ".aac", ".mkv").any { path.endsWith(it) })
                setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player?.apply {
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
        handler.removeCallbacks(stuck)
        handler.postDelayed(stuck, STUCK_MS)
    }

    /** Not working: the next channel (same direction). Stops after trying many in a row. */
    private fun skip() {
        skips++
        if (skips > 8 || urls.size < 2) {
            Toast.makeText(this, "📶✖", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        play(index + dir)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { dir = 1; play(index + 1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { dir = -1; play(index - 1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (overlay.visibility != View.VISIBLE) { showOverlay(); return true }
            }
            if (overlay.visibility == View.VISIBLE) showOverlay()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
