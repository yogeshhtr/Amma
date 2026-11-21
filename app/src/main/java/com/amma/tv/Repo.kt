package com.amma.tv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The video lists of every program, saved on the device so the app opens instantly (also without internet).
 * Lists older than [FRESH_MS] are downloaded again in the background (new episodes appear by themselves).
 */
object Repo {
    private const val PREFS = "amma_videos"
    private const val FRESH_MS = 3 * 60 * 60 * 1000L          // 3 hours: serials get a new episode every day

    private val lock = Any()
    private val memory = HashMap<String, Pair<Long, List<Vid>>>()
    private val running = HashSet<String>()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)

    /** Called on the screen thread when a program's list changed. */
    var listener: ((String) -> Unit)? = null

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The saved videos of a program (may be empty when it was never downloaded). */
    fun cached(ctx: Context, show: Show): List<Vid> = entry(ctx, show)?.second ?: emptyList()

    fun isLoading(show: Show): Boolean = synchronized(lock) { show.key in running }

    private fun entry(ctx: Context, show: Show): Pair<Long, List<Vid>>? {
        synchronized(lock) { memory[show.key]?.let { return it } }
        val e = try {
            val o = JSONObject(sp(ctx).getString(show.key, null) ?: return null)
            val a = o.getJSONArray("v")
            o.getLong("t") to (0 until a.length()).map { val x = a.getJSONArray(it); 
                Vid(x.getString(0), x.getString(1), x.optString(2).ifEmpty { null }, x.optString(3).ifEmpty { null }) }
        } catch (_: Exception) { return null }
        synchronized(lock) { memory[show.key] = e }
        return e
    }

    /** Downloads the programs whose list is old (all of them when [force]). Never blocks the screen. */
    fun refresh(ctx: Context, shows: List<Show>, force: Boolean = false) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        for (show in shows) {
            val t = entry(app, show)?.first ?: 0L
            if (!force && now - t < FRESH_MS) continue
            val fresh = synchronized(lock) { running.add(show.key) }
            if (!fresh) continue                                    // already downloading
            io.launch {
                try {
                    val list = gate.withPermit { download(show) }
                    // an empty answer (no internet, page changed) never replaces a good saved list
                    if (list.isNotEmpty()) save(app, show, list)
                } catch (_: Throwable) {
                } finally {
                    synchronized(lock) { running.remove(show.key) }
                    android.os.Handler(android.os.Looper.getMainLooper()).post { listener?.invoke(show.id) }
                }
            }
        }
    }

    private fun save(ctx: Context, show: Show, list: List<Vid>) {
        val a = JSONArray()
        for (v in list) a.put(JSONArray().put(v.id).put(v.title).put(v.url ?: "").put(v.logo ?: ""))
        val now = System.currentTimeMillis()
        sp(ctx).edit().putString(show.key, JSONObject().put("t", now).put("v", a).toString()).apply()
        synchronized(lock) { memory[show.key] = now to list }
    }

    private fun download(show: Show): List<Vid> {
        val list = when (show.type) {
            "channel" -> Youtube.channel(show.value)
            "playlist" -> Youtube.playlist(show.value)
            "search" -> Youtube.search(show.value, show.minMinutes, show.latest)
            "m3u" -> M3u.load(show.value)
            "video" -> {
                // a link to one episode: show the episodes of that channel (this one included)
                val (channel, title) = Youtube.videoInfo(show.value)
                val all = channel?.let { Youtube.channel(it) } ?: emptyList()
                if (all.none { it.id == show.value }) all + Vid(show.value, title.ifEmpty { show.name }) else all
            }
            else -> emptyList()
        }
        return onlyWords(list, show.words)
    }

    /** Keeps the videos of this serial when the channel also has other programs. */
    private fun onlyWords(list: List<Vid>, words: List<String>): List<Vid> {
        if (words.isEmpty()) return list
        val m = list.filter { v -> words.any { v.title.contains(it, ignoreCase = true) } }
        return if (m.size >= 3) m else list
    }
}
