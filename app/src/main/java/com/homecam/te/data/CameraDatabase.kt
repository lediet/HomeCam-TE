package com.homecam.te.data

import androidx.room.*

/**
 * Room Entity for persisting camera device list
 */
@Entity(tableName = "camera_devices")
data class CameraDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isAutoDiscovered: Boolean = false,
    val lastSeen: Long = 0L
)

@Dao
interface CameraDao {
    @Query("SELECT * FROM camera_devices ORDER BY lastSeen DESC")
    suspend fun getAll(): List<CameraDeviceEntity>

    @Query("SELECT * FROM camera_devices WHERE id = :id")
    suspend fun getById(id: String): CameraDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: CameraDeviceEntity)

    @Delete
    suspend fun delete(device: CameraDeviceEntity)

    @Query("DELETE FROM camera_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM camera_devices")
    suspend fun deleteAll()
}

@Database(entities = [CameraDeviceEntity::class], version = 1, exportSchema = false)
abstract class CameraDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao
}
