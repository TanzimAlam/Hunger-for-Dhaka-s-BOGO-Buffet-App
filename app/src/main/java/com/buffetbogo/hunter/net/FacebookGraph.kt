package com.buffetbogo.hunter.net

import com.buffetbogo.hunter.ingest.OfferParser
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One post from a venue's own Facebook page. */
data class FbPost(
    val id: String,
    val message: String,
    val permalink: String?,
    val image: String?,
    val createdAt: Long,
)

/**
 * A thin client for Meta's Graph API.
 *
 * Facebook is where Dhaka's hotels actually publish their buffet campaigns, and the Graph API
 * is the only supported way to read a page's posts as structured data. Meta gates other pages'
 * content behind **Page Public Content Access**, which an app has to have approved (business
 * verification included), so a token is optional: without one the app falls back to the venue's
 * public page and search-engine results. The API version is kept current with Meta's changelog
 * rather than hard-coded at a version Meta has retired.
 */
class FacebookGraph(
    private val token: String,
    private val version: String = DEFAULT_VERSION,
) {

    /** Validates the token and returns the account it belongs to, or throws with Meta's reason. */
    suspend fun whoAmI(): String {
        val body = Http.get("$BASE/$version/me?fields=id,name&access_token=$token")
        val json = JSONObject(body)
        json.optJSONObject("error")?.let { throw IOException(graphError(it)) }
        return json.optString("name").ifBlank { json.optString("id") }
    }

    /** The page's own posts, newest first. [page] accepts a handle, a numeric id or "me". */
    suspend fun feed(page: String, limit: Int = 20): List<FbPost> {
        val encodedPage = URLEncoder.encode(page, "UTF-8")
        val fields = "id,message,created_time,permalink_url,full_picture"
        val url = "$BASE/$version/$encodedPage/feed?fields=$fields&limit=$limit" +
            "&access_token=$token"
        return parseFeed(Http.get(url))
    }

    /** Pure parser, so Meta's payload shape can be tested without a token. */
    fun parseFeed(body: String): List<FbPost> {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IOException("Facebook returned an unreadable response.")
        json.optJSONObject("error")?.let { throw IOException(graphError(it)) }
        val data = json.optJSONArray("data") ?: return emptyList()
        val posts = ArrayList<FbPost>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val message = OfferParser.cleanText(item.optString("message"))
            val created = parseTime(item.optString("created_time"))
            if (message.isBlank() || created == null) continue
            posts += FbPost(
                id = item.optString("id"),
                message = message,
                permalink = item.optString("permalink_url").takeIf { it.startsWith("http") },
                image = item.optString("full_picture")
                    .takeIf { it.startsWith("http") }
                    ?.replace("\\/", "/"),
                createdAt = created,
            )
        }
        return posts
    }

    private fun graphError(error: JSONObject): String {
        val message = error.optString("message")
        val code = error.optInt("code", -1)
        val type = error.optString("type")
        val bits = listOfNotNull(
            message.takeIf { it.isNotBlank() },
            type.takeIf { it.isNotBlank() },
            if (code > 0) "(code $code)" else null,
        )
        return if (bits.isEmpty()) "Facebook rejected the request." else bits.joinToString(" ")
    }

    private fun parseTime(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching {
                OffsetDateTime.parse(raw, GRAPH_TIME).toInstant().toEpochMilli()
            }
            .recoverCatching {
                java.time.LocalDateTime.parse(raw, LOCAL_TIME)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }
            .getOrNull()
    }

    companion object {
        /** Meta's current Graph API version at the time of writing (see Meta's changelog). */
        const val DEFAULT_VERSION = "v26.0"
        private const val BASE = "https://graph.facebook.com"
        private val GRAPH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
        private val LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
