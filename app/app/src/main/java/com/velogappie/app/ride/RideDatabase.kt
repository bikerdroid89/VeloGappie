package com.velogappie.app.ride

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RideEntity::class, RideGroupEntity::class, LocationPointEntity::class], version = 6, exportSchema = false)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var instance: RideDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN avgCadenceRpm INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN maxCadenceRpm INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ride_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE rides ADD COLUMN groupId INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS location_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, rideId INTEGER NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitude REAL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN startBatterySoc INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN endBatterySoc INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN elevationGainM REAL DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN bikeSerial TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): RideDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, RideDatabase::class.java, "rides.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
