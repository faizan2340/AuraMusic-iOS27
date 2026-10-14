package com.apple.auramusic.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY album ASC, disc_number ASC, track_number ASC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE title LIKE '%' || :ftsQuery || '%'
           OR artist LIKE '%' || :ftsQuery || '%'
           OR album LIKE '%' || :ftsQuery || '%'
        ORDER BY title ASC
    """)
    fun searchTracksInstant(ftsQuery: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): TrackEntity?
}

@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
