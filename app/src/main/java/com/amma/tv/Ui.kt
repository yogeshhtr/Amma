package com.amma.tv

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.FrameLayout

object Ui {
    const val BG = 0xFF0B3D2E.toInt()          // dark green
    const val GOLD = 0xFFFFC107.toInt()
    const val CREAM = 0xFFFFF8E1.toInt()
    const val CARD = 0xFF14513D.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** TV box (no touch screen): the remote moves a gold frame over the tiles. */
    fun isTv(ctx: Context): Boolean =
        !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun round(color: Int, radiusDp: Int, ctx: Context, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (stroke > 0) setStroke(dp(ctx, stroke), strokeColor)
        }

    /** Card background: a thick gold frame when chosen with the remote, lighter while pressed. */
    fun card(ctx: Context, color: Int): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), round(color, 18, ctx, 5, GOLD))
        addState(intArrayOf(android.R.attr.state_pressed), round(lighter(color), 18, ctx, 3, GOLD))
        addState(intArrayOf(), round(color, 18, ctx))
    }

    fun lighter(c: Int): Int = Color.rgb(
        minOf(255, Color.red(c) + 40), minOf(255, Color.green(c) + 40), minOf(255, Color.blue(c) + 40))

    /** Grows a little when chosen with the remote. */
    fun focusZoom(v: View) {
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
        }
    }
}

/** A frame that is always 16:9 (video pictures). */
class Frame16x9(ctx: Context) : FrameLayout(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY))
    }
}
