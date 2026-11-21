package com.amma.tv

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * One tile: a picture (or a big symbol) and icons only, no words to read.
 *  image    = video / channel picture (16:9); without it the big [emoji] is shown
 *  play     = a big ▶ in the middle (the "watch again" tile)
 *  progress = red bar: how much was watched;  done = green ✔
 *  badge    = small icon in the corner (⭐ newest episode, or the tile's symbol)
 *  title    = small text under the picture; empty = no text at all (default)
 */
class Tile(
    val color: Int,
    val emoji: String? = null,
    val image: String? = null,
    val play: Boolean = false,
    val progress: Float = -1f,
    val done: Boolean = false,
    val badge: String? = null,
    val title: String = "",
    val wide: Boolean = false,
    val onClick: () -> Unit
)

class TileAdapter : RecyclerView.Adapter<TileAdapter.Holder>() {
    private var items: List<Tile> = emptyList()

    fun submit(list: List<Tile>) {
        items = list
        notifyDataSetChanged()
    }

    fun isWide(position: Int) = items.getOrNull(position)?.wide == true

    override fun getItemCount() = items.size

    class Holder(
        val root: FrameLayout,
        val picture: FrameLayout,
        val image: ImageView,
        val emoji: TextView,
        val playIcon: TextView,
        val tick: TextView,
        val badge: TextView,
        val bar: ProgressBar,
        val title: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val m = Ui.dp(ctx, 8)
        val root = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(m, m, m, m) }
            isFocusable = true
            isClickable = true
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6))
        }
        Ui.focusZoom(root)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val picture = Frame16x9(ctx)
        picture.clipToOutline = true
        col.addView(picture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        picture.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val emoji = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
        }
        picture.addView(emoji, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // big ▶ in the middle
        val playIcon = TextView(ctx).apply {
            text = "▶"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            background = Ui.round(0xAA000000.toInt(), 60, ctx, 4, Ui.GOLD)
        }
        val ps = Ui.dp(ctx, 96)
        picture.addView(playIcon, FrameLayout.LayoutParams(ps, ps, Gravity.CENTER))

        // finished: big green tick in the corner
        val tick = TextView(ctx).apply {
            text = "✔"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            background = Ui.round(0xFF2E7D32.toInt(), 30, ctx, 2, Color.WHITE)
        }
        val ts = Ui.dp(ctx, 44)
        picture.addView(tick, FrameLayout.LayoutParams(ts, ts, Gravity.TOP or Gravity.END).apply { setMargins(m, m, m, m) })

        val badge = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            background = Ui.round(0xCC000000.toInt(), 30, ctx)
        }
        picture.addView(badge, FrameLayout.LayoutParams(ts, ts, Gravity.TOP or Gravity.START).apply { setMargins(m, m, m, m) })

        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xFFE53935.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0x66000000)
        }
        picture.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 10), Gravity.BOTTOM))

        val title = TextView(ctx).apply {
            setTextColor(Ui.CREAM)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6), 0)
        }
        col.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Holder(root, picture, image, emoji, playIcon, tick, badge, bar, title)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val t = items[position]
        val ctx = h.root.context
        h.root.background = Ui.card(ctx, if (t.play) 0xFF2E7D32.toInt() else Ui.CARD)
        h.picture.background = Ui.round(t.color, 12, ctx)
        h.emoji.text = t.emoji ?: ""
        h.emoji.visibility = if (t.emoji != null && t.image == null) View.VISIBLE else View.GONE
        if (t.image != null) {
            h.image.visibility = View.VISIBLE
            Glide.with(h.image).load(t.image).centerCrop().into(h.image)
        } else {
            Glide.with(h.image).clear(h.image)
            h.image.setImageDrawable(null)
            h.image.visibility = View.GONE
        }
        h.playIcon.visibility = if (t.play) View.VISIBLE else View.GONE
        h.tick.visibility = if (t.done) View.VISIBLE else View.GONE
        h.badge.visibility = if (t.badge != null) View.VISIBLE else View.GONE
        h.badge.text = t.badge ?: ""
        val showBar = !t.done && t.progress > 0.02f
        h.bar.visibility = if (showBar) View.VISIBLE else View.GONE
        if (showBar) h.bar.progress = (t.progress * 1000).toInt()
        h.title.text = t.title
        h.title.visibility = if (t.title.isEmpty()) View.GONE else View.VISIBLE
        h.root.setOnClickListener { t.onClick() }
    }
}
