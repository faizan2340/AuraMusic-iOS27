package com.apple.auramusic.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * High-speed local SQLite + FTS4 database engine for instant offline search.
 */
class TrackDao(context: Context) : SQLiteOpenHelper(context, "aura_music_ios27.db", null, 1) {

    private val allTracksFlow = MutableStateFlow<List<TrackEntity>>(emptyList())

    init {
        refreshMemoryFlow()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tracks (
                id INTEGER PRIMARY KEY,
                uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                artists_list TEXT NOT NULL,
                album TEXT NOT NULL,
                album_id INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                track_number INTEGER NOT NULL,
                disc_number INTEGER NOT NULL,
                sample_rate_hz INTEGER NOT NULL,
                bit_depth INTEGER NOT NULL,
                mime_type TEXT NOT NULL,
                audio_badge TEXT NOT NULL,
                replay_gain_db REAL NOT NULL,
                replay_peak REAL NOT NULL,
                artwork_uri TEXT NOT NULL,
                file_path TEXT NOT NULL,
                lrc_sidecar_path TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS tracks_fts USING fts4(
                title,
                artist,
                artists_list,
                album
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS tracks_fts")
        db.execSQL("DROP TABLE IF EXISTS tracks")
        onCreate(db)
    }

    fun observeAllTracks(): Flow<List<TrackEntity>> = allTracksFlow

    fun searchTracksInstant(ftsQuery: String): Flow<List<TrackEntity>> {
        val clean = ftsQuery.replace("*", "").trim().lowercase()
        return allTracksFlow.map { list ->
            if (clean.isEmpty()) list
            else list.filter {
                it.title.lowercase().contains(clean) ||
                    it.artist.lowercase().contains(clean) ||
                    it.album.lowercase().contains(clean)
            }
        }
    }

    suspend fun upsertTracks(tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tracks_fts", null, null)
            for (t in tracks) {
                val cv = ContentValues().apply {
                    put("id", t.id)
                    put("uri", t.uri)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("artists_list", t.artistsList)
                    put("album", t.album)
                    put("album_id", t.albumId)
                    put("duration_ms", t.durationMs)
                    put("track_number", t.trackNumber)
                    put("disc_number", t.discNumber)
                    put("sample_rate_hz", t.sampleRateHz)
                    put("bit_depth", t.bitDepth)
                    put("mime_type", t.mimeType)
                    put("audio_badge", t.audioBadge)
                    put("replay_gain_db", t.replayGainDb)
                    put("replay_peak", t.replayPeak)
                    put("artwork_uri", t.artworkUri)
                    put("file_path", t.filePath)
                    put("lrc_sidecar_path", t.lrcSidecarPath)
                }
                db.insertWithOnConflict("tracks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                val ftsCv = ContentValues().apply {
                    put("docid", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("artists_list", t.artistsList)
                    put("album", t.album)
                }
                db.insert("tracks_fts", null, ftsCv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshMemoryFlow()
    }

    suspend fun getTrackById(trackId: Long): TrackEntity? = withContext(Dispatchers.IO) {
        allTracksFlow.value.firstOrNull { it.id == trackId }
    }

    private fun refreshMemoryFlow() {
        val list = mutableListOf<TrackEntity>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT * FROM tracks ORDER BY album ASC, disc_number ASC, track_number ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += readTrack(cursor)
            }
        }
        allTracksFlow.value = list
    }

    private fun readTrack(c: Cursor): TrackEntity = TrackEntity(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        uri = c.getString(c.getColumnIndexOrThrow("uri")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        artist = c.getString(c.getColumnIndexOrThrow("artist")),
        artistsList = c.getString(c.getColumnIndexOrThrow("artists_list")),
        album = c.getString(c.getColumnIndexOrThrow("album")),
        albumId = c.getLong(c.getColumnIndexOrThrow("album_id")),
        durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
        trackNumber = c.getInt(c.getColumnIndexOrThrow("track_number")),
        discNumber = c.getInt(c.getColumnIndexOrThrow("disc_number")),
        sampleRateHz = c.getInt(c.getColumnIndexOrThrow("sample_rate_hz")),
        bitDepth = c.getInt(c.getColumnIndexOrThrow("bit_depth")),
        mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
        audioBadge = c.getString(c.getColumnIndexOrThrow("audio_badge")),
        replayGainDb = c.getFloat(c.getColumnIndexOrThrow("replay_gain_db")),
        replayPeak = c.getFloat(c.getColumnIndexOrThrow("replay_peak")),
        artworkUri = c.getString(c.getColumnIndexOrThrow("artwork_uri")),
        filePath = c.getString(c.getColumnIndexOrThrow("file_path")),
        lrcSidecarPath = c.getString(c.getColumnIndexOrThrow("lrc_sidecar_path"))
    )
}
