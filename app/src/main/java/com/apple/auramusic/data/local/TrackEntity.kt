package com.apple.auramusic.data.local

data class TrackEntity(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val artistsList: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val mimeType: String,
    val audioBadge: String,
    val replayGainDb: Float,
    val replayPeak: Float,
    val artworkUri: String,
    val filePath: String,
    val lrcSidecarPath: String?
)
