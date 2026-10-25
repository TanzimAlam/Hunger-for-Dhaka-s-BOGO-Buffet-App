package com.buffetbogo.hunter.data

import android.content.Context
import com.buffetbogo.hunter.ingest.WebOfferSource
import com.buffetbogo.hunter.net.BuffetPriceSearch
import com.buffetbogo.hunter.net.PlacesClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OfferRepository(
    context: Context,
    private val dao: OfferDao,
    private val settingsStore: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Loaded once from assets; aliases let "Sarina" match "Hotel Sarina Dhaka". */
    private var priceBook: Map<String, VenuePrice> = emptyMap()

    /** Re-emits every minute so expired offers drop out of the list without a restart. */
    private val expiryTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val offers: Flow<List<OfferUi>> =
        combine(dao.observeOffers(), dao.observeRatings(), expiryTicker) { offers, ratings, _ ->
            val ratingsByKey = ratings.associateBy { it.nameKey }
            val now = System.currentTimeMillis()
            offers.asSequence()
                // A card is only shown once it has a price; unpriced rows wait in the database.
                .filter { it.isLive(now) && it.priceBdt != null }
                .map { offer ->
                    val cached = ratingsByKey[nameKey(offer.restaurant)]
                    OfferUi(
                        offer = offer,
                        rating = cached?.rating ?: offer.rating,
                        ratingCount = cached?.ratingCount ?: offer.ratingCount,
                    )
                }
                .sortedByDescending { it.offer.postedAt }
                .toList()
        }

    val settings: Flow<AppSettings> = settingsStore.settings

    init {
        scope.launch {
            runCatching { loadPriceBook() }
            runCatching { syncBundled() }
            runCatching { ensurePrices() }
            runCatching { refreshIfStale() }
            runCatching { refreshRatingsIfDue() }
        }
    }

    fun observeOffer(id: String): Flow<OfferUi?> =
        combine(dao.observeOffer(id), dao.observeRatings()) { offer, ratings ->
            offer?.let {
                val cached = ratings.firstOrNull { row -> row.nameKey == nameKey(it.restaurant) }
                OfferUi(it, cached?.rating ?: it.rating, cached?.ratingCount ?: it.ratingCount)
            }
        }

    suspend fun hide(id: String) = dao.hide(id)

    /** Reloads the bundled catalogue, replacing any older bundled rows. */
    suspend fun restoreBundled(now: Long = System.currentTimeMillis()) {
        dao.deleteBySource(Sources.CURATED)
        dao.upsertAll(SeedLoader.load(appContext, now))
        settingsStore.setSeedVersion(SeedLoader.VERSION)
        seedVenueRatings()
        ensurePrices(now)
    }

    suspend fun refresh(now: Long = System.currentTimeMillis()): RefreshResult {
        val current = settingsStore.settings.first()
        val outcome = runCatching { WebOfferSource(current.braveApiKey).fetch(now) }
            .getOrElse { error ->
                return RefreshResult(
                    found = 0,
                    new = emptyList(),
                    message = error.message ?: "Could not reach the offer sources.",
                    failed = true,
                )
            }

        if (outcome.allSourcesFailed) {
            return RefreshResult(
                found = 0,
                new = emptyList(),
                message = "None of the offer sources answered. Check the connection and try again.",
                failed = true,
            )
        }

        val existing = dao.all().associateBy { it.id }
        val merged = outcome.offers.map { offer ->
            val known = existing[offer.id]
            if (known != null) {
                // Keep the first-seen date and the user's hide action across refreshes.
                offer.copy(postedAt = known.postedAt, hidden = known.hidden)
            } else {
                offer
            }
        }

        if (merged.isNotEmpty()) dao.upsertAll(merged)
        runCatching { ensurePrices(now) }
        dao.pruneExpiredBefore(now - TimeUnit.DAYS.toMillis(45))
        settingsStore.markSynced(now)
        runCatching { refreshRatings() }

        // Only offers that ended up with a price are visible, so count and alert on those.
        val visible = dao.all().filter { it.isLive(now) && it.priceBdt != null }
        val newOffers = visible.filter { it.id !in existing }
        val message = when {
            visible.isEmpty() -> "No live buffet offers matched this time."
            else -> "Updated — ${visible.size} live offers, ${newOffers.size} new."
        }
        return RefreshResult(
            found = visible.size,
            new = newOffers,
            message = message,
            failed = false,
        )
    }

    /**
     * Guarantees every visible card carries a price. Ads that omit one are filled from the
     * checked price book, then from web/Facebook search; an offer that stays unpriced is hidden
     * rather than shown without a figure.
     */
    suspend fun ensurePrices(now: Long = System.currentTimeMillis()): Int {
        if (priceBook.isEmpty()) runCatching { loadPriceBook() }
        val missing = dao.offersWithoutPrice().filter { it.isLive(now) }
        if (missing.isEmpty()) return 0

        val key = settingsStore.settings.first().braveApiKey
        // Brave when configured, DuckDuckGo's keyless results otherwise.
        val search = BuffetPriceSearch(key)
        var filled = 0
        for (offer in missing.take(MAX_PRICE_LOOKUPS)) {
            val context = "${offer.title} ${offer.headline} ${offer.details} ${offer.terms}"
            val meal = when {
                context.contains("breakfast", ignoreCase = true) &&
                    !context.contains("dinner", ignoreCase = true) &&
                    !context.contains("lunch", ignoreCase = true) -> "breakfast"
                context.contains("lunch", ignoreCase = true) &&
                    !context.contains("dinner", ignoreCase = true) -> "lunch"
                else -> "dinner"
            }
            val guess = VenuePriceBook.find(priceBook, offer.restaurant, context)
                ?: search.lookup(offer.restaurant, meal)
                ?: continue
            dao.setPrice(offer.id, guess.amount, guess.unit, guess.source)
            filled++
        }
        return filled
    }

    private suspend fun loadPriceBook() {
        val raw = appContext.assets.open("venue_prices.json").bufferedReader().use { it.readText() }
        priceBook = VenuePriceBook.parse(raw)
    }

    /**
     * Refreshes Google star ratings for every live restaurant through the Places API.
     * Rows are cached for 30 days, so this is a handful of calls a month, not a call per open.
     */
    suspend fun refreshRatings(force: Boolean = false): RatingRefresh {
        val placesKey = settingsStore.settings.first().placesApiKey
        if (placesKey.isBlank()) return RatingRefresh(updated = 0, needsKey = true)

        val now = System.currentTimeMillis()
        val live = dao.all()
            .filter { it.isLive(now) }
            .distinctBy { nameKey(it.restaurant) }
        if (live.isEmpty()) return RatingRefresh(updated = 0, needsKey = false)

        val cached = dao.ratings().associateBy { it.nameKey }
        val client = PlacesClient(placesKey)
        var updated = 0

        for (offer in live.take(MAX_RATING_LOOKUPS)) {
            val key = nameKey(offer.restaurant)
            val row = cached[key]
            if (!force && row != null && now - row.updatedAt < RATING_TTL_MS) continue

            val info = runCatching { client.lookup("${offer.restaurant}, Dhaka, Bangladesh") }
                .getOrNull() ?: continue
            val rating = info.rating ?: continue

            dao.upsertRating(
                RestaurantRating(
                    nameKey = key,
                    displayName = info.name ?: offer.restaurant,
                    rating = rating,
                    ratingCount = info.ratingCount,
                    placeId = info.placeId,
                    updatedAt = now,
                ),
            )
            updated++
            delay(120)
        }
        return RatingRefresh(updated = updated, needsKey = false)
    }

    /** Keeps the bundled catalogue current across app updates, without touching hidden rows. */
    private suspend fun syncBundled(now: Long = System.currentTimeMillis()) {
        // Earlier builds shipped placeholder rows linking to example.com; they are never wanted.
        dao.deleteBySource(LEGACY_PLACEHOLDER_SOURCE)
        seedVenueRatings()

        val empty = dao.count() == 0
        if (empty) {
            dao.upsertAll(SeedLoader.load(appContext, now))
            settingsStore.setSeedVersion(SeedLoader.VERSION)
            return
        }
        val version = settingsStore.settings.first().seedVersion
        if (version >= SeedLoader.VERSION) return
        dao.deleteBySource(Sources.CURATED)
        dao.upsertAll(SeedLoader.load(appContext, now))
        settingsStore.setSeedVersion(SeedLoader.VERSION)
    }

    /** Copies the checked Google ratings shipped with the app into the cache. */
    private suspend fun seedVenueRatings() {
        val existing = dao.ratings().associateBy { it.nameKey }
        SeedLoader.loadVenueRatings(appContext).forEach { rating ->
            if ((existing[rating.nameKey]?.updatedAt ?: 0L) < rating.updatedAt) {
                dao.upsertRating(rating)
            }
        }
    }

    /** The first launch, and any launch after a long gap, fetches fresh offers immediately. */
    private suspend fun refreshIfStale() {
        val current = settingsStore.settings.first()
        val stale = System.currentTimeMillis() - current.lastSyncAt > AUTO_REFRESH_AFTER_MS
        if (current.lastSyncAt == 0L || stale) refresh()
    }

    /** Ratings are re-checked weekly rather than on every launch. */
    private suspend fun refreshRatingsIfDue() {
        if (settingsStore.settings.first().placesApiKey.isBlank()) return
        val newest = dao.ratings().maxOfOrNull { it.updatedAt } ?: 0L
        if (System.currentTimeMillis() - newest < RATING_TTL_MS) return
        refreshRatings()
    }

    data class RatingRefresh(
        val updated: Int,
        /** True when no Google Places key is configured, so ratings cannot be refreshed. */
        val needsKey: Boolean,
    )

    data class RefreshResult(
        val found: Int,
        val new: List<Offer>,
        val message: String?,
        /** True only for a transport/service failure, so the worker retries just those. */
        val failed: Boolean,
    )

    companion object {
        private const val LEGACY_PLACEHOLDER_SOURCE = "SAMPLE"
        private const val MAX_RATING_LOOKUPS = 30
        // Enough to price a whole bank page's venues in one pass; later passes retry stragglers.
        private const val MAX_PRICE_LOOKUPS = 25
        private val RATING_TTL_MS = TimeUnit.DAYS.toMillis(30)
        private val AUTO_REFRESH_AFTER_MS = TimeUnit.HOURS.toMillis(6)
    }
}
