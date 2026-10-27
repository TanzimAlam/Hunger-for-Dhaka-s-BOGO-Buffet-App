package com.buffetbogo.hunter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Offer::class, RestaurantRating::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun offerDao(): OfferDao

    companion object {
        /** v2 added the price unit and price provenance columns. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN priceUnit TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN priceSource TEXT")
            }
        }

        /** v3 added the restaurant's own ad link, its label, timings and the check date. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN adUrl TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN adLabel TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN timing TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN adCheckedAt INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "buffet-hunter.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
