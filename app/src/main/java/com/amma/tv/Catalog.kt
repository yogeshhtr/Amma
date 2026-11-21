package com.amma.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One program to watch: a YouTube channel, a playlist, the channel of one video, or a search.
 *  type  = "channel" (value = @handle / UC id), "playlist" (value = list id),
 *          "video" (value = video id: the videos of its channel are shown), "search" (value = search words)
 *          "m3u" (value = playlist link: live TV channels, shown after the YouTube tiles)
 *  words = when given, only videos whose title has one of these words (if at least 3 are found; else all)
 *  latest = search: newest uploads first (serials) instead of best match
 */
data class Show(
    val id: String,
    val name: String,
    val type: String,
    val value: String,
    val words: List<String> = emptyList(),
    val minMinutes: Int = 0,
    val added: Boolean = false,
    val latest: Boolean = false
) {
    /** Cache key: changes when the link changes, so an edited program is downloaded again. */
    val key: String get() = "$id|$type|$value"
}

/** A big tile on the home screen. [serial]: after an episode ends, the NEXT (newer) episode plays. */
data class Section(
    val id: String,
    val title: String,
    val emoji: String,
    val color: Int,
    val serial: Boolean,
    val shows: List<Show>
)

object Catalog {
    private const val PREFS = "amma_catalog"

    /** Granny's programs. Wrong @names simply show nothing; programs can be added / hidden in the helper menu. */
    private val builtIn: List<Section> = listOf(
        Section("serial", "सिरियल", "📺", 0xFF1565C0.toInt(), true, listOf(
            Show("bhunti", "भुन्टी", "channel", "@sukumaya98tv", listOf("भुन्टी", "भुण्टी", "bhunti", "vunti", "bhuntee")),
            Show("buhari", "बुहारी", "video", "-JNJqN3L__I", listOf("बुहारी", "buhari")),
            Show("sakas", "सकस", "channel", "@MasterTVNepal", listOf("सकस", "sakas")),
            Show("kanchhi", "कान्छी", "channel", "@kanchhiofficial8023", listOf("कान्छी", "kanchhi", "kanchi")),
            // found by YouTube search (newest first), only titles with the serial's name
            serial("meribassai", "मेरी बास्सै", "meri bassai", "meri bassai", "मेरी बास्सै", "बास्सै"),
            serial("juthe", "जुठे", "juthe nepali serial", "juthe", "जुठे"),
            serial("keshari", "केशरी", "jeevan ko rang keshari", "keshari", "kesari", "केशरी"),
            serial("halkaramailo", "हल्का रमाइलो", "halka ramailo", "halka ramailo", "हल्का रमाइलो"),
            serial("sakkigoni", "सक्किगोनी", "sakkigoni", "sakkigoni", "सक्किगोनी"),
            serial("bhadragol", "भद्रगोल", "bhadragol", "bhadragol", "भद्रगोल"),
            serial("jirekhursani", "जिरे खुर्सानी", "jire khursani", "jire khursani", "जिरे खुर्सानी")
        )),
        Section("movie", "चलचित्र", "🎬", 0xFFC62828.toInt(), false, listOf(
            Show("movies_new", "नेपाली चलचित्र", "search", "nepali full movie", minMinutes = 60),
            Show("movies_old", "पुराना चलचित्र", "search", "old nepali movie full", minMinutes = 60),
            Show("movies_osr", "OSR चलचित्र", "channel", "@osrmovies,@OSRMovies,@osrdigital")
        )),
        Section("katha", "हेर्ने कथा", "📖", 0xFF6A1B9A.toInt(), false, listOf(
            Show("hernekatha", "हेर्ने कथा", "channel", "@hernekatha")
        )),
        Section("bhakti", "भगवान", "🙏", 0xFFEF6C00.toInt(), false, listOf(
            Show("bhakti_list", "भक्ति कार्यक्रम", "playlist", "PL3jpwwk1eUXSvZb6Zq197etgUbE-Lh5d1"),
            Show("bhajan_np", "नेपाली भजन", "search", "nepali bhajan", minMinutes = 3),
            Show("bhajan_hi", "हिन्दी भजन", "search", "hindi bhajan", minMinutes = 3)
        )),
        Section("prabachan", "प्रवचन", "🕉", 0xFF00695C.toInt(), false, listOf(
            Show("gurukul", "प्रवचन", "channel", "@GuruKulProduction")
        )),
        // live TV: empty until an m3u link is added in the helper menu; always after the YouTube tiles
        Section("tv", "टिभी", "📡", 0xFF37474F.toInt(), false, emptyList())
    )

    /** Sections whose videos are mixed into the home page. */
    val FEED = setOf("serial", "movie", "katha", "bhakti", "prabachan")

    private fun serial(id: String, name: String, query: String, vararg words: String) =
        Show(id, name, "search", query, words.toList(), latest = true)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Sections with the programs added in the helper menu, without the hidden ones. Empty sections are left out. */
    fun sections(ctx: Context): List<Section> {
        val hidden = hidden(ctx)
        val added = added(ctx)
        return builtIn.map { s ->
            s.copy(shows = (s.shows + added.filter { it.first == s.id }.map { it.second }).filter { it.id !in hidden })
        }.filter { it.shows.isNotEmpty() }
    }

    fun allSections(): List<Section> = builtIn

    fun allShows(ctx: Context): List<Pair<Section, Show>> {
        val added = added(ctx)
        return builtIn.flatMap { s -> (s.shows + added.filter { it.first == s.id }.map { it.second }).map { s to it } }
    }

    fun findShow(ctx: Context, id: String): Show? = allShows(ctx).firstOrNull { it.second.id == id }?.second
    fun sectionOf(ctx: Context, showId: String): Section? = allShows(ctx).firstOrNull { it.second.id == showId }?.first

    // ---------------- hidden programs

    fun hidden(ctx: Context): Set<String> = sp(ctx).getStringSet("hidden", emptySet()) ?: emptySet()
    fun setHidden(ctx: Context, ids: Set<String>) { sp(ctx).edit().putStringSet("hidden", HashSet(ids)).apply() }

    // ---------------- programs added in the helper menu

    private fun added(ctx: Context): List<Pair<String, Show>> = try {
        val a = JSONArray(sp(ctx).getString("added", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            o.getString("section") to Show(o.getString("id"), o.getString("name"), o.getString("type"),
                o.getString("value"), emptyList(), o.optInt("min", 0), added = true, latest = o.optBoolean("latest"))
        }
    } catch (_: Exception) { emptyList() }

    private fun saveAdded(ctx: Context, list: List<Pair<String, Show>>) {
        val a = JSONArray()
        for ((sec, s) in list) a.put(JSONObject().put("section", sec).put("id", s.id).put("name", s.name)
            .put("type", s.type).put("value", s.value).put("min", s.minMinutes).put("latest", s.latest))
        sp(ctx).edit().putString("added", a.toString()).apply()
    }

    fun addedShows(ctx: Context): List<Pair<String, Show>> = added(ctx)

    fun add(ctx: Context, sectionId: String, show: Show) { saveAdded(ctx, added(ctx) + (sectionId to show)) }

    fun remove(ctx: Context, showId: String) { saveAdded(ctx, added(ctx).filter { it.second.id != showId }) }

    /**
     * Understands a pasted YouTube link: @name, channel link, playlist link, video link, or other words (= search).
     * Returns type and value, or null when nothing usable was typed.
     */
    fun parseLink(text: String): Pair<String, String>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http") && Regex("\\.m3u8?(\\?|$)|m3u|playlist\\.txt", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
            !t.contains("youtube.com") && !t.contains("youtu.be")) return "m3u" to t
        Regex("[?&]list=([A-Za-z0-9_-]{10,})").find(t)?.let { return "playlist" to it.groupValues[1] }
        Regex("(?:v=|youtu\\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})").find(t)?.let { return "video" to it.groupValues[1] }
        Regex("/channel/(UC[\\w-]{22})").find(t)?.let { return "channel" to it.groupValues[1] }
        Regex("(@[\\w.\\-]+)").find(t)?.let { return "channel" to it.groupValues[1] }
        if (t.startsWith("http")) return null
        return "search" to t
    }
}
