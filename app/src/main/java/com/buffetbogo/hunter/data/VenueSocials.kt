package com.buffetbogo.hunter.data

import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

/** How an ad link relates to the restaurant. Displayed next to the link in the detail screen. */
enum class AdKind(val label: String) {
    FACEBOOK_POST("Facebook post"),
    FACEBOOK_PAGE("Facebook page"),
    WEBSITE("Restaurant website"),
    FACEBOOK_SEARCH("Facebook search"),
}

/** One venue's own public pages: the Facebook page it posts offers on, and its dining page. */
data class VenueSocial(
    val displayName: String,
    val facebook: String?,
    val website: String?,
    val checkedAt: String?,
)

/**
 * The bundled directory of official venue pages.
 *
 * A bank page proves an offer exists but is not the ad: it usually omits the timings, the price
 * and the restaurant's own wording. Every venue listed here has its own Facebook page (where
 * Bangladeshi restaurants actually publish promotions) and, where one exists, a buffet page on
 * its own website. These links are what "Open the original ad" uses for offers discovered
 * anywhere else.
 */
object VenueSocials {

    fun parse(raw: String): Map<String, VenueSocial> {
        val array = JSONArray(raw)
        val directory = LinkedHashMap<String, VenueSocial>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.getString("restaurant")
            val social = VenueSocial(
                displayName = name,
                facebook = item.optString("facebook").takeIf { it.isNotBlank() },
                website = item.optString("website").takeIf { it.isNotBlank() },
                checkedAt = item.optString("checkedAt").takeIf { it.isNotBlank() },
            )
            directory[nameKey(name)] = social
            item.optJSONArray("aliases")?.let { aliases ->
                for (index in 0 until aliases.length()) {
                    val alias = aliases.optString(index)
                    if (alias.isNotBlank()) directory[nameKey(alias)] = social
                }
            }
        }
        return directory
    }

    fun find(directory: Map<String, VenueSocial>, restaurant: String): VenueSocial? {
        val key = nameKey(restaurant)
        directory[key]?.let { return it }
        // Bank pages sometimes append the outlet name ("… – The Garden Kitchen").
        return directory.entries
            .firstOrNull { (alias, _) -> alias.length >= 6 && key.contains(alias) }
            ?.value
    }

    /** A page handle or numeric id the Graph API accepts, extracted from a page URL. */
    fun facebookHandle(url: String): String? {
        if (!url.contains("facebook.com", ignoreCase = true)) return null
        val path = url.substringAfter("facebook.com/", "")
            .substringBefore("?")
            .substringBefore("#")
            .trim('/')
        if (path.isBlank()) return null
        val parts = path.split('/').filter { it.isNotBlank() }
        return when (parts.firstOrNull()?.lowercase(Locale.ROOT)) {
            "p" -> parts.getOrNull(1)?.substringAfterLast('-')?.takeIf { it.all(Char::isDigit) }
            "pages" -> parts.getOrNull(2)
            "profile.php" -> null
            else -> parts.firstOrNull()?.takeIf { it != "people" }
        }
    }

    fun kindOf(url: String): AdKind {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            !lower.contains("facebook.com") -> AdKind.WEBSITE
            POST_MARKERS.any { lower.contains(it) } -> AdKind.FACEBOOK_POST
            else -> AdKind.FACEBOOK_PAGE
        }
    }

    fun facebookSearchUrl(restaurant: String): String {
        // URLEncoder writes spaces as '+', which Facebook's query parser does not read back.
        val query = URLEncoder.encode("$restaurant buffet Dhaka", "UTF-8").replace("+", "%20")
        return "https://www.facebook.com/search/top?q=$query"
    }

    private val POST_MARKERS = listOf("/posts/", "/videos/", "/reel/", "/permalink/", "pfbid")
}
