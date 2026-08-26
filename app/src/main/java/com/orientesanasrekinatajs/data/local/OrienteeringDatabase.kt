package com.orientesanasrekinatajs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orientesanasrekinatajs.data.local.dao.MapDao
import com.orientesanasrekinatajs.data.local.entity.ControlPointEntity
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity

/**
 * Application database for scanned maps and their detected control points.
 */
@Database(
    entities = [ScannedMapEntity::class, ControlPointEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class OrienteeringDatabase : RoomDatabase() {

    abstract fun mapDao(): MapDao

    companion object {
        private const val DATABASE_NAME = "orienteering.db"

        @Volatile
        private var instance: OrienteeringDatabase? = null

        /** Returns the process-wide database instance. */
        fun getInstance(context: Context): OrienteeringDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrienteeringDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7,
                )
                    .build().also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN name TEXT NOT NULL DEFAULT 'Route'")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN calibrationStartX REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN calibrationStartY REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN calibrationEndX REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN calibrationEndY REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN lineDistanceMeters REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routePointIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routeTotalDistanceMeters REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routeTotalScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN rotationQuarterTurns INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scanned_maps ADD COLUMN selectedRoutePointIds " +
                        "TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scanned_maps ADD COLUMN routeMode " +
                        "TEXT NOT NULL DEFAULT 'SHORTEST'",
                )
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routeBudgetMeters REAL")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routeTargetScore INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scanned_maps ADD COLUMN alternativeRoutePointIds " +
                        "TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scanned_maps ADD COLUMN routeMetadataJson " +
                        "TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN routeIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scanned_maps ADD COLUMN selectedRouteId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
