package com.buffetbogo.hunter.data

import android.content.Context
import com.buffetbogo.hunter.ingest.AdEnricher
import com.buffetbogo.hunter.ingest.AdLinkResolver
import com.buffetbogo.hunter.ingest.AdPost
import com.buffetbogo.hunter.ingest.FacebookPageSource
import com.buffetbogo.hunter.ingest.FacebookResult
import com.buffetbogo.hunter.ingest.WebOfferSource
import com.buffetbogo.hunter.net.BuffetPriceSearch
import com.buffetbogo.hunter.net.PlacesClient
import com.buffetbogo.hunter.net.VenueAdFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Each venue's own Facebook page and website, used for the "original ad" link. */
    private var socials: Map<String, VenueSocial> = emptyMap()

    /** Keeps the launch refresh and a worker/user refresh from fetching every source twice. */
    private val refreshMutex = Mutex()

    /** When this process last started a check, so a failed launch attempt is not repeated at once. */
    @Volatile
    private var lastAttemptAt = 0L

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
            runCatching { loadSocials() }
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

    /** A user- or worker-started check; serialised with the launch refresh. */
    suspend fun refresh(now: Long = System.currentTimeMillis()): RefreshResult =
        refreshMutex.withLock { refreshNow(now) }

    /**
     * The launch path: refreshes only when the last successful check is older than [maxAgeMs].
     * Returns null when the feed was still fresh; safe to call from both the repository and the
     * screen, because the second caller re-checks inside the lock.
     */
    suspend fun refreshIfStale(
        maxAgeMs: Long = AUTO_REFRESH_AFTER_MS,
    ): RefreshResult? = refreshMutex.withLock {
        val last = maxOf(settingsStore.settings.first().lastSyncAt, lastAttemptAt)
        val now = System.currentTimeMillis()
        if (last != 0L && now - last <= maxAgeMs) return@withLock null
        refreshNow(now)
    }

    private suspend fun refreshNow(now: Long): RefreshResult {
        lastAttemptAt = System.currentTimeMillis()
        val current = settingsStore.settings.first()
        if (socials.isEmpty()) runCatching { loadSocials() }

        val outcome = runCatching { WebOfferSource(current.braveApiKey, socials).fetch(now) }
            .getOrElse { error ->
                return RefreshResult(
                    found = 0,
                    new = emptyList(),
                    message = error.message ?: "Could not reach the offer sources.",
                    failed = true,
                )
            }

        // The venues' own posts. Optional: without a token Meta cannot be read, and the offers
        // still get their restaurant's public page as the ad link.
        val facebook = runCatching {
            FacebookPageSource(current.facebookToken).fetch(socials, now)
        }.getOrElse { FacebookResult(emptyList(), 0, 1, it.message) }

        if (outcome.allSourcesFailed && facebook.offers.isEmpty()) {
            return RefreshResult(
                found = 0,
                new = emptyList(),
                message = "None of the offer sources answered. Check the connection and try again.",
                failed = true,
            )
        }

        val resolver = AdLinkResolver(socials, VenueAdFinder(current.braveApiKey))

        // Settle each offer on its venue's canonical name and own page before matching posts.
        val base = (outcome.offers + facebook.offers).map { resolver.withDirectoryLink(it, now) }

        // Posts become both an enrichment of the discovered offers and offers in their own right.
        val posts = facebook.offers.map { it.asAdPost() }
        val discovered = AdEnricher.enrich(base, posts, now)

        val existing = dao.all().associateBy { it.id }
        val merged = discovered.map { offer ->
            existing[offer.id]?.let { known -> carryForward(offer, known, now) } ?: offer
        }

        // Every card must open the restaurant's own ad, never a bank's listing page.
        val resolved = resolver.resolve(merged, now)

        if (resolved.isNotEmpty()) dao.upsertAll(resolved)
        runCatching { ensurePrices(now) }
        dao.pruneExpiredBefore(now - TimeUnit.DAYS.toMillis(45))
        settingsStore.markSynced(now)
        runCatching { refreshRatings() }

        // Only offers that ended up with a price are visible, so count and alert on those.
        val visible = dao.all().filter { it.isLive(now) && it.priceBdt != null }
        val newOffers = visible.filter { it.id !in existing }
        val status = when {
            visible.isEmpty() -> "No live buffet offers matched this time."
            else -> "Updated — ${visible.size} live offers, ${newOffers.size} new."
        }
        val facebookNote = when {
            current.facebookToken.isBlank() -> null
            facebook.error != null -> "Facebook: ${facebook.error}"
            facebook.pagesRead > 0 ->
                "Facebook (${facebook.account ?: "connected"}): " +
                    "${facebook.pagesRead} venue pages read, ${facebook.offers.size} buffet posts."
            else -> null
        }
        val bankNote = when {
            outcome.bankPagesRead == 0 -> null
            else ->
                "Banks: ${outcome.bankPagesRead} dining pages read, " +
                    "${outcome.bankVenues} restaurant${if (outcome.bankVenues == 1) "" else "s"} listed."
        }
        return RefreshResult(
            found = visible.size,
            new = newOffers,
            message = listOfNotNull(status, bankNote, facebookNote).joinToString(" "),
            failed = false,
        )
    }

    /**
     * Keeps what must not change between syncs: the first date we saw the offer, the user's hide
     * action, and a recently resolved ad link (so it is not looked up again on every refresh).
     */
    private fun carryForward(offer: Offer, known: Offer, now: Long): Offer {
        val linkFresh = known.adUrl != null &&
            known.adCheckedAt != null &&
            now - known.adCheckedAt < AD_LINK_TTL_MS
        return offer.copy(
            postedAt = known.postedAt,
            hidden = known.hidden,
            adUrl = offer.adUrl ?: known.adUrl.takeIf { linkFresh },
            adLabel = offer.adLabel ?: known.adLabel.takeIf { linkFresh },
            timing = offer.timing ?: known.timing,
            adCheckedAt = when {
                offer.adUrl != null -> offer.adCheckedAt
                linkFresh -> known.adCheckedAt
                else -> offer.adCheckedAt
            },
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

    private suspend fun loadSocials() {
        val raw = appContext.assets.open("venue_socials.json").bufferedReader().use { it.readText() }
        socials = VenueSocials.parse(raw)
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
        private val AUTO_REFRESH_AFTER_MS = TimeUnit.MINUTES.toMillis(30)
        private val AD_LINK_TTL_MS = TimeUnit.DAYS.toMillis(7)
    }
}

/** A restaurant post viewed as an ad: the link, its wording and any figure it states. */
private fun Offer.asAdPost(): AdPost = AdPost(
    restaurant = restaurant,
    url = adUrl ?: sourceUrl,
    text = listOf(details, terms).filter { it.isNotBlank() }.joinToString(" — "),
    image = imageUrl,
    postedAt = postedAt,
    kind = offerKind,
    priceBdt = priceBdt,
    validUntil = validUntil,
    timing = timing,
)
