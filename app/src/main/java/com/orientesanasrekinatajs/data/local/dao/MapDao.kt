package com.orientesanasrekinatajs.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.orientesanasrekinatajs.data.local.entity.ControlPointEntity
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [ScannedMapEntity] and its child [ControlPointEntity] rows.
 *
 * Reactive reads are exposed as [Flow]s so the UI (history screen, map detail) can
 * collect them and update automatically whenever a row changes. One-shot `suspend`
 * variants are provided for background work, e.g. checking for an existing map before
 * persisting a freshly processed one.
 *
 * All write operations are `suspend` and must be invoked from a background coroutine
 * (e.g. with a `Dispatchers.IO` context).
 */
@Dao
interface MapDao {

    // region Create

    /**
     * Inserts a scanned map.
     *
     * @return the rowid of the inserted row.
     */
    @Upsert
    suspend fun insertMap(map: ScannedMapEntity): Long

    /**
     * Inserts a single control point.
     *
     * @return the rowid of the inserted row.
     */
    @Upsert
    suspend fun insertPoint(point: ControlPointEntity): Long

    /**
     * Inserts several control points in a single SQL statement.
     *
     * @return the rowids of the inserted rows.
     */
    @Upsert
    suspend fun insertPoints(points: List<ControlPointEntity>): List<Long>

    /**
     * Persists a map together with all of its control points atomically.
     *
     * Both statements execute inside a single transaction, so a failure in either one
     * rolls back the whole operation (no orphaned points, no empty maps).
     */
    @Transaction
    suspend fun insertMapWithPoints(
        map: ScannedMapEntity,
        points: List<ControlPointEntity>,
    ) {
        insertMap(map)
        insertPoints(points)
    }

    /** Replaces a saved route and its point snapshot atomically. */
    @Transaction
    suspend fun replaceMapWithPoints(
        map: ScannedMapEntity,
        points: List<ControlPointEntity>,
    ) {
        deletePoints(map.id)
        insertMap(map)
        insertPoints(points)
    }

    // endregion

    // region Read

    /**
     * Observes a map together with all of its control points.
     *
     * Emits the combined [MapWithPoints] whenever the map row or any of its point rows
     * is inserted, updated, or deleted.
     */
    @Transaction
    @Query("SELECT * FROM scanned_maps WHERE id = :mapId")
    fun observeMapWithPoints(mapId: String): Flow<MapWithPoints?>

    /**
     * One-shot variant of [observeMapWithPoints].
     *
     * @return the map with its points, or `null` if no map with [mapId] exists.
     */
    @Transaction
    @Query("SELECT * FROM scanned_maps WHERE id = :mapId")
    suspend fun getMapWithPoints(mapId: String): MapWithPoints?

    /**
     * Observes all scanned maps, newest first (history screen).
     */
    @Query("SELECT * FROM scanned_maps ORDER BY timestamp DESC")
    fun observeAllMaps(): Flow<List<ScannedMapEntity>>

    /**
     * Observes the control points of one map, ordered by control number.
     */
    @Query("SELECT * FROM control_points WHERE mapId = :mapId ORDER BY code")
    fun observePoints(mapId: String): Flow<List<ControlPointEntity>>

    /**
     * One-shot read of a single map.
     *
     * @return the map, or `null` if it does not exist.
     */
    @Query("SELECT * FROM scanned_maps WHERE id = :mapId")
    suspend fun getMap(mapId: String): ScannedMapEntity?

    @Query("SELECT * FROM scanned_maps ORDER BY timestamp DESC")
    suspend fun getAllMaps(): List<ScannedMapEntity>

    // endregion

    // region Update

    /**
     * Replaces a map row (e.g. after recalibrating [ScannedMapEntity.pixelsPerMeter]).
     */
    @Update
    suspend fun updateMap(map: ScannedMapEntity)

    /**
     * Replaces a single control point (e.g. after a manual correction of its code).
     */
    @Update
    suspend fun updatePoint(point: ControlPointEntity)

    /**
     * Replaces several control points.
     */
    @Update
    suspend fun updatePoints(points: List<ControlPointEntity>)

    // endregion

    // region Delete

    /**
     * Deletes a map. Its control points are removed automatically by the
     * `ON DELETE CASCADE` foreign key declared on [ControlPointEntity].
     */
    @Query("DELETE FROM scanned_maps WHERE id = :mapId")
    suspend fun deleteMap(mapId: String)

    /**
     * Deletes a single control point by its primary key.
     */
    @Query("DELETE FROM control_points WHERE id = :id")
    suspend fun deletePoint(id: String)

    /**
     * Deletes all control points of a map while keeping the map row itself.
     */
    @Query("DELETE FROM control_points WHERE mapId = :mapId")
    suspend fun deletePoints(mapId: String)

    @Query("DELETE FROM scanned_maps")
    suspend fun deleteAllMaps()

    // endregion
}
