package com.amma.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * What was watched: where each video stopped (it continues there), which ones were finished (green tick),
 * and the last video (the big "फेरि हेर्नुहोस्" tile on the home screen).
 */
object Watch {
    private const val PREFS = "amma_watch"
    private const val MAX = 400

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var cache: JSONObject? = null

    private fun all(ctx: Context): JSONObject {
        cache?.let { return it }
        val o = try { JSONObject(sp(ctx).getString("pos", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        cache = o
        return o
    }

    /** Seconds where the video stopped (0 = from the start). */
    fun position(ctx: Context, id: String): Int {
        val a = all(ctx).optJSONArray(id) ?: return 0
        val sec = a.optInt(0); val dur = a.optInt(1)
        return if (dur > 0 && sec > dur - 60) 0 else sec
    }

    /** 0..1 how much was watched, -1 when never opened. */
    fun progress(ctx: Context, id: String): Float {
        val a = all(ctx).optJSONArray(id) ?: return -1f
        val sec = a.optInt(0); val dur = a.optInt(1)
        if (dur <= 0) return 0f
        return (sec.toFloat() / dur).coerceIn(0f, 1f)
    }

    fun finished(ctx: Context, id: String): Boolean {
        val a = all(ctx).optJSONArray(id) ?: return false
        val dur = a.optInt(1)
        return dur > 0 && a.optInt(0) > dur - 60
    }

    fun save(ctx: Context, id: String, sec: Int, dur: Int) {
        val o = all(ctx)
        o.remove(id)                                // re-added at the end = newest
        o.put(id, JSONArray().put(sec).put(dur))
        if (o.length() > MAX) {
            val first = o.keys().next()
            o.remove(first)
        }
        sp(ctx).edit().putString("pos", o.toString()).apply()
    }

    // ---------------- the last video, for the "watch again" tile

    class Last(val showId: String, val videoId: String, val title: String)

    fun setLast(ctx: Context, showId: String, v: Vid) {
        sp(ctx).edit().putString("last", JSONObject().put("show", showId).put("id", v.id).put("title", v.title).toString()).apply()
    }

    fun last(ctx: Context): Last? = try {
        val o = JSONObject(sp(ctx).getString("last", null) ?: "")
        Last(o.getString("show"), o.getString("id"), o.optString("title"))
    } catch (_: Exception) { null }
}
