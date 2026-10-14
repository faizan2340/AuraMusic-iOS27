package com.apple.auramusic.data.indexer

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.apple.auramusic.data.local.TrackDao
import com.apple.auramusic.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

class LocalAudioIndexer constructor(
    private val context: Context,
    private val trackDao: TrackDao
) {
    private val multiArtistRegex = Regex("""\s*(?:;|/|\bfeat\.?\b|\bft\.?\b|&)\s*""", RegexOption.IGNORE_CASE)

    suspend fun indexLocalLibrary(): Int = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"

        val rawRows = mutableListOf<RawMediaRow>()
        try {
            context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                rawRows += RawMediaRow(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "Unknown Title",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    albumId = cursor.getLong(albumIdCol),
                    durationMs = cursor.getLong(durationCol),
                    rawTrackCode = cursor.getInt(trackCol),
                    mimeType = cursor.getString(mimeCol) ?: "audio/flac",
                    filePath = cursor.getString(dataCol) ?: ""
                )
            }
        }
        } catch (_: SecurityException) {
            // Permission not granted yet; fallback showcase track will be seeded below
        }

        // Parallel metadata extraction in bounded chunks of 16
        val entities = coroutineScope {
            rawRows.chunked(16).flatMap { batch ->
                batch.map { row ->
                    async { enrichTrackMetadata(row) }
                }.awaitAll()
            }
        }

        val finalEntities = entities.ifEmpty {
            listOf(
                TrackEntity(
                    id = 1L,
                    uri = "",
                    title = "Liquid Glass Odyssey (ProMotion 120Hz)",
                    artist = "Cupertino Sound Lab",
                    artistsList = "Cupertino Sound Lab",
                    album = "iOS 27 Human Interface Suite",
                    albumId = 1L,
                    durationMs = 214_000L,
                    trackNumber = 1,
                    discNumber = 1,
                    sampleRateHz = 192000,
                    bitDepth = 24,
                    mimeType = "audio/flac",
                    audioBadge = "Hi-Res Lossless 24-bit/192kHz",
                    replayGainDb = -1.2f,
                    replayPeak = 0.94f,
                    artworkUri = "",
                    filePath = "",
                    lrcSidecarPath = null
                )
            )
        }
        trackDao.upsertTracks(finalEntities)
        finalEntities.size
    }

    private fun enrichTrackMetadata(row: RawMediaRow): TrackEntity {
        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, row.id)
        val artworkUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            row.albumId
        )

        // MediaStore encodes disc + track as DTTT (e.g., 2004 -> Disc 2, Track 4)
        val discNumber = if (row.rawTrackCode >= 1000) row.rawTrackCode / 1000 else 1
        val trackNumber = if (row.rawTrackCode >= 1000) row.rawTrackCode % 1000 else row.rawTrackCode.coerceAtLeast(1)

        var sampleRateHz = 44100
        var bitDepth = 16
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, contentUri, null)
            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (format.containsKey("bits-per-sample")) {
                    bitDepth = format.getInteger("bits-per-sample")
                } else if (row.mimeType.contains("flac", true) || row.mimeType.contains("alac", true)) {
                    bitDepth = if (sampleRateHz >= 88200) 24 else 16
                }
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }

        // Parse multi-artist delimiters
        val parsedArtists = row.artist.split(multiArtistRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("|")

        // Discover external .lrc sidecar file in the same directory
        val lrcSidecarPath = if (row.filePath.isNotEmpty()) {
            val audioFile = File(row.filePath)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) lrcFile.absolutePath else null
        } else null

        val isLossless = row.mimeType.contains("flac", true) ||
            row.mimeType.contains("alac", true) ||
            row.mimeType.contains("wav", true) ||
            row.filePath.endsWith(".flac", true) ||
            row.filePath.endsWith(".m4a", true)

        val kHzFormatted = if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000}kHz"
        } else {
            "${sampleRateHz / 1000f}kHz"
        }

        val audioBadge = when {
            isLossless && (bitDepth >= 24 && sampleRateHz >= 88200) ->
                "Hi-Res Lossless ${bitDepth}-bit/$kHzFormatted"
            isLossless ->
                "Lossless ${bitDepth}-bit/$kHzFormatted"
            row.mimeType.contains("opus", true) ->
                "Dolby Audio • Opus $kHzFormatted"
            else ->
                "High Efficiency $kHzFormatted"
        }

        return TrackEntity(
            id = row.id,
            uri = contentUri.toString(),
            title = row.title,
            artist = row.artist,
            artistsList = parsedArtists,
            album = row.album,
            albumId = row.albumId,
            durationMs = row.durationMs,
            trackNumber = trackNumber,
            discNumber = discNumber,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            mimeType = row.mimeType,
            audioBadge = audioBadge,
            replayGainDb = 0f,
            replayPeak = 1.0f,
            artworkUri = artworkUri.toString(),
            filePath = row.filePath,
            lrcSidecarPath = lrcSidecarPath
        )
    }

    private data class RawMediaRow(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val durationMs: Long,
        val rawTrackCode: Int,
        val mimeType: String,
        val filePath: String
    )
}
