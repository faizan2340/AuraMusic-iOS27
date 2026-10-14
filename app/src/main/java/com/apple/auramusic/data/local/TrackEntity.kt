package com.apple.auramusic.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "artists_list") val artistsList: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "disc_number") val discNumber: Int,
    @ColumnInfo(name = "sample_rate_hz") val sampleRateHz: Int,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "audio_badge") val audioBadge: String,
    @ColumnInfo(name = "replay_gain_db") val replayGainDb: Float,
    @ColumnInfo(name = "replay_peak") val replayPeak: Float,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "lrc_sidecar_path") val lrcSidecarPath: String?
)
