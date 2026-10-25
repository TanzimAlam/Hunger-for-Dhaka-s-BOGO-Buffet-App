package com.buffetbogo.hunter.net

import org.json.JSONObject

data class PlaceInfo(
    val placeId: String?,
    val name: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val address: String?,
)

/**
 * Google Places API (New) Text Search, used only to enrich offers with the restaurant's
 * star rating and review count. Each lookup is one "Enterprise" SKU event, so the repository
 * caches results for 30 days and caps lookups per sync.
 */
class PlacesClient(private val apiKey: String) {

    suspend fun lookup(textQuery: String): PlaceInfo? {
        val body = JSONObject()
            .put("textQuery", textQuery)
            .put("maxResultCount", 1)
            .toString()
        val response = Http.postJson(
            "https://places.googleapis.com/v1/places:searchText",
            mapOf(
                "X-Goog-Api-Key" to apiKey,
                "X-Goog-FieldMask" to
                    "places.id,places.displayName,places.rating,places.userRatingCount,places.formattedAddress",
            ),
            body,
        )
        val place = JSONObject(response).optJSONArray("places")?.optJSONObject(0) ?: return null
        return PlaceInfo(
            placeId = place.optString("id").takeIf { it.isNotBlank() },
            name = place.optJSONObject("displayName")?.optString("text")?.takeIf { it.isNotBlank() },
            rating = if (place.has("rating")) place.optDouble("rating") else null,
            ratingCount = if (place.has("userRatingCount")) place.optInt("userRatingCount") else null,
            address = place.optString("formattedAddress").takeIf { it.isNotBlank() },
        )
    }
}
