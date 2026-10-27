package com.buffetbogo.hunter.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {

    @Query("SELECT * FROM offers ORDER BY postedAt DESC")
    fun observeOffers(): Flow<List<Offer>>

    @Query("SELECT * FROM offers WHERE id = :id")
    fun observeOffer(id: String): Flow<Offer?>

    @Query("SELECT id FROM offers")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM offers")
    suspend fun count(): Int

    @Query("SELECT * FROM offers")
    suspend fun all(): List<Offer>

    @Query("SELECT * FROM offers WHERE priceBdt IS NULL")
    suspend fun offersWithoutPrice(): List<Offer>

    @Query("DELETE FROM offers WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    @Upsert
    suspend fun upsertAll(offers: List<Offer>)

    @Query("UPDATE offers SET hidden = 1 WHERE id = :id")
    suspend fun hide(id: String)

    @Query(
        "UPDATE offers SET priceBdt = :price, priceUnit = :unit, priceSource = :source WHERE id = :id",
    )
    suspend fun setPrice(id: String, price: Int, unit: String?, source: String)

    @Query(
        "UPDATE offers SET adUrl = :url, adLabel = :label, " +
            "timing = COALESCE(:timing, timing), adCheckedAt = :checkedAt WHERE id = :id",
    )
    suspend fun setAdReference(
        id: String,
        url: String?,
        label: String?,
        timing: String?,
        checkedAt: Long,
    )

    @Query("DELETE FROM offers WHERE validUntil IS NOT NULL AND validUntil < :cutoff")
    suspend fun pruneExpiredBefore(cutoff: Long): Int

    @Query("SELECT * FROM restaurant_ratings")
    fun observeRatings(): Flow<List<RestaurantRating>>

    @Query("SELECT * FROM restaurant_ratings")
    suspend fun ratings(): List<RestaurantRating>

    @Upsert
    suspend fun upsertRating(rating: RestaurantRating)
}
