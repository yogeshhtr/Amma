package com.amma.tv

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * A YouTube video (id + title), or a live TV channel from an m3u list ([url] = stream, [logo] = its picture).
 */
class Vid(val id: String, val title: String, val url: String? = null, val logo: String? = null) {
    val isLive: Boolean get() = url != null
    val thumb: String? get() = if (isLive) logo?.takeIf { it.startsWith("http") } else "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/**
 * Reads YouTube pages without an API key: the videos of a channel, of a playlist, of a search, and the
 * channel of a single video. Every function returns an empty list / null on any problem (never throws).
 */
object Youtube {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    val http: OkHttpClient =OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true)
        .build()

    private fun fetch(url: String, maxChars: Int = 4_000_000): String? = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb; SOCS=CAI")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val reader = r.body?.charStream() ?: return@use null
            val sb = StringBuilder()
            val chunk = CharArray(65536)
            while (sb.length < maxChars) {
                val n = reader.read(chunk)
                if (n <= 0) break
                sb.append(chunk, 0, n)
            }
            sb.toString()
        }
    } catch (_: Throwable) { null }

    // ---------------------------------------------------------------- channel

    /** ~30 newest normal videos of a channel (@handle or UC id; several comma separated candidates allowed). */
    fun channel(target: String): List<Vid> {
        for (t in target.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val url = when {
                t.startsWith("@") -> "https://www.youtube.com/$t/videos?hl=en"
                t.startsWith("UC") -> "https://www.youtube.com/channel/$t/videos?hl=en"
                else -> continue
            }
            fetch(url)?.let { html ->
                val v = safe { parseLockups(html) }
                if (v.isNotEmpty()) return v
                // older page layout
                val r = safe { parseRenderers(html, "\"gridVideoRenderer\":{\"videoId\":\"", 0) }
                if (r.isNotEmpty()) return r
            }
            // last chance: the channel's news feed (15 newest)
            channelId(t)?.let { id -> rss("channel_id=$id").let { if (it.isNotEmpty()) return it } }
        }
        return emptyList()
    }

    // ---------------------------------------------------------------- playlist

    /** Videos of a playlist (up to ~100), in the playlist's order. */
    fun playlist(listId: String): List<Vid> {
        fetch("https://www.youtube.com/playlist?list=$listId&hl=en")?.let { html ->
            val v = safe { parseRenderers(html, "\"playlistVideoRenderer\":{\"videoId\":\"", 0) }
            if (v.isNotEmpty()) return v
            val l = safe { parseLockups(html) }
            if (l.isNotEmpty()) return l
        }
        return rss("playlist_id=$listId")
    }

    // ---------------------------------------------------------------- search

    /** Videos found by a YouTube search; only videos at least [minMinutes] long when > 0 (e.g. full movies). */
    fun search(query: String, minMinutes: Int, latest: Boolean = false): List<Vid> {
        val q = URLEncoder.encode(query, "UTF-8")
        // sp: EgIQAQ== = "videos only"; CAISAhAB = "videos only, newest first"
        val sp = if (latest) "CAISAhAB" else "EgIQAQ%253D%253D"
        val html = fetch("https://www.youtube.com/results?search_query=$q&sp=$sp&hl=en") ?: return emptyList()
        val v = safe { parseRenderers(html, "\"videoRenderer\":{\"videoId\":\"", minMinutes) }
        // newer layout has no length on the page: only usable when the length does not matter
        return if (v.isEmpty() && minMinutes <= 0) safe { parseLockups(html) } else v
    }

    // ---------------------------------------------------------------- single video

    private val metaChannel = Regex("itemprop=\"channelId\" content=\"(UC[\\w-]{22})\"")
    private val jsonChannel = Regex("\"channelId\":\"(UC[\\w-]{22})\"")
    private val metaTitle = Regex("<meta name=\"title\" content=\"([^\"]*)\"")

    /** The channel (UC id) and title of one video. */
    fun videoInfo(videoId: String): Pair<String?, String> {
        val html = fetch("https://www.youtube.com/watch?v=$videoId&hl=en", 1_500_000) ?: return null to ""
        val ch = metaChannel.find(html)?.groupValues?.get(1)
            ?: html.indexOf("\"videoDetails\"").takeIf { it >= 0 }?.let { jsonChannel.find(html, it)?.groupValues?.get(1) }
            ?: jsonChannel.find(html)?.groupValues?.get(1)
        val title = metaTitle.find(html)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
        return ch to title
    }

    // ---------------------------------------------------------------- helpers

    private val chanCanon = Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/(UC[\\w-]{22})")

    /** @handle -> UC id */
    private fun channelId(target: String): String? {
        if (target.startsWith("UC") && target.length == 24) return target
        if (!target.startsWith("@")) return null
        val html = fetch("https://www.youtube.com/$target?hl=en", 600_000) ?: return null
        return chanCanon.find(html)?.groupValues?.get(1)
            ?: Regex("\"(?:channelId|externalId)\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
    }

    private val rssId = Regex("<yt:videoId>([^<]+)</yt:videoId>")
    private val rssTitle = Regex("<title>([^<]*)</title>")

    /** YouTube's feed: 15 newest videos of a channel or playlist. */
    private fun rss(query: String): List<Vid> {
        val xml = fetch("https://www.youtube.com/feeds/videos.xml?$query", 500_000) ?: return emptyList()
        return xml.split("<entry>").drop(1).mapNotNull { e ->
            val id = rssId.find(e)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = rssTitle.find(e)?.groupValues?.get(1)?.let { unescape(it) } ?: return@mapNotNull null
            Vid(id, title)
        }
    }

    private const val LOCKUP_TITLE = "\"lockupMetadataViewModel\":{\"title\":{\"content\":\""
    private val videoIdRe = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** New page layout: each video's id comes just before its title. */
    private fun parseLockups(t: String): List<Vid> {
        val out = ArrayList<Vid>()
        val seen = HashSet<String>()
        var segStart = 0
        var idx = t.indexOf(LOCKUP_TITLE)
        while (idx >= 0 && out.size < 100) {
            val before = t.substring(maxOf(segStart, idx - 8000), idx)
            val id = videoIdRe.findAll(before).lastOrNull()?.groupValues?.get(1)
            val (title, end) = readJsonString(t, idx + LOCKUP_TITLE.length)
            if (id != null && usable(title) && seen.add(id)) out.add(Vid(id, title))
            segStart = end
            idx = t.indexOf(LOCKUP_TITLE, end)
        }
        return out
    }

    /** Older layout (search, playlists): "xxxRenderer":{"videoId":"…", … "title":{"runs":[{"text":"…"}]} … */
    private fun parseRenderers(t: String, marker: String, minMinutes: Int): List<Vid> {
        val out = ArrayList<Vid>()
        val seen = HashSet<String>()
        var idx = t.indexOf(marker)
        while (idx >= 0 && out.size < 100) {
            val idStart = idx + marker.length
            val id = t.substring(idStart, minOf(t.length, idStart + 11))
            val next = t.indexOf(marker, idStart).let { if (it < 0) minOf(t.length, idStart + 12000) else it }
            val block = t.substring(idStart, minOf(next, idStart + 12000))
            val title = titleIn(block)
            val minutes = lengthMinutes(block)
            val longEnough = minMinutes <= 0 || (minutes != null && minutes >= minMinutes)
            if (id.length == 11 && title != null && usable(title) && longEnough && seen.add(id)) out.add(Vid(id, title))
            idx = if (next < t.length && t.startsWith(marker, next)) next else t.indexOf(marker, idStart)
        }
        return out
    }

    private fun titleIn(block: String): String? {
        for (m in listOf("\"title\":{\"runs\":[{\"text\":\"", "\"title\":{\"simpleText\":\"")) {
            val i = block.indexOf(m)
            if (i >= 0) return readJsonString(block, i + m.length).first
        }
        return null
    }

    /** "1:45:20" -> 105 minutes; null when the page shows no length (e.g. a live stream). */
    private fun lengthMinutes(block: String): Int? {
        val i = block.indexOf("\"lengthText\":")
        if (i < 0) return null
        val m = "\"simpleText\":\""
        val j = block.indexOf(m, i)
        if (j < 0 || j - i > 600) return null
        val parts = readJsonString(block, j + m.length).first.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 60 + parts[1]
            2 -> parts[0]
            else -> null
        }
    }

    /** Skips deleted / private videos. */
    private fun usable(title: String) =
        title.isNotBlank() && !(title.startsWith("[") && title.endsWith("video]", ignoreCase = true))

    /** Reads a JSON string value starting just after its opening quote. Returns the text and the end index. */
    private fun readJsonString(t: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start
        while (i < t.length) {
            val c = t[i]
            if (c == '"') break
            if (c == '\\' && i + 1 < t.length) {
                when (val n = t[i + 1]) {
                    'n', 't', 'r' -> sb.append(' ')
                    'u' -> if (i + 5 < t.length) {
                        t.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString().trim() to i
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()

    private inline fun safe(block: () -> List<Vid>): List<Vid> = try { block() } catch (_: Throwable) { emptyList() }
}
