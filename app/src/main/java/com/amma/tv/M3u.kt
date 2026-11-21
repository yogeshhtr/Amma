package com.amma.tv

import okhttp3.Request

/**
 * Live TV from an m3u playlist link (added by the family in the helper menu).
 * Copy-protected (DRM) channels and non-http links are skipped; at most 1000 channels.
 */
object M3u {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"")

    fun load(url: String): List<Vid> {
        val text = try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .build()
            Youtube.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val ins = r.body?.byteStream() ?: return emptyList()
                val bos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(65536)
                while (bos.size() < MAX_BYTES) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    bos.write(buf, 0, n)
                }
                String(bos.toByteArray(), Charsets.UTF_8)
            }
        } catch (_: Throwable) { return emptyList() }
        return parse(text)
    }

    fun parse(text: String): List<Vid> {
        val out = ArrayList<Vid>()
        val seen = HashSet<String>()
        var name: String? = null
        var logo: String? = null
        var drm = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    name = line.substringAfterLast(',').trim()
                    logo = logoRe.find(line)?.groupValues?.get(1)
                    drm = false
                }
                line.startsWith("#KODIPROP", ignoreCase = true) && line.contains("license", ignoreCase = true) -> drm = true
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    if (!drm && (line.startsWith("http://") || line.startsWith("https://")) && seen.add(line)) {
                        out.add(Vid(line, name?.ifEmpty { null } ?: "TV ${out.size + 1}", line, logo))
                        if (out.size >= 1000) break
                    }
                    name = null; logo = null; drm = false
                }
            }
        }
        return out
    }
}
