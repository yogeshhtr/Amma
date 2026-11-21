package com.amma.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/** The god picture for a moment while the programs are loaded in the background. Tap to skip. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var gone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val img = ImageView(this).apply {
            setBackgroundColor(Ui.BG)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.splash_god)
            setOnClickListener { next() }
        }
        setContentView(img)
        // start downloading new episodes now, so the lists are ready sooner
        Repo.refresh(this, Catalog.sections(this).flatMap { it.shows })
        handler.postDelayed({ next() }, 2000)
    }

    @Suppress("DEPRECATION")
    private fun next() {
        if (gone || isFinishing) return
        gone = true
        startActivity(Intent(this, HomeActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
