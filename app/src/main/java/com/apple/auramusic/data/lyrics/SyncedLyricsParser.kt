package com.apple.auramusic.data.lyrics

import com.apple.auramusic.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class LyricSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class SyncedLyricLine(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val rawText: String,
    val syllables: List<LyricSyllable>
) {
    val isWordSynced: Boolean get() = syllables.isNotEmpty()
}

@Singleton
class SyncedLyricsParser @Inject constructor() {

    private val lineTimestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestampRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]+)""")

    suspend fun loadLyricsForTrack(track: TrackEntity): List<SyncedLyricLine> =
        withContext(Dispatchers.IO) {
            // 1. Check .lrc sidecar file first
            track.lrcSidecarPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val parsed = parseLrcText(file.readText())
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            }

            // 2. Inspect embedded ID3v2 SYLT/USLT or Vorbis LYRICS comments inside the audio file
            val audioFile = File(track.filePath)
            if (audioFile.exists() && audioFile.canRead()) {
                when {
                    audioFile.extension.equals("mp3", ignoreCase = true) -> {
                        val id3Lines = parseId3v2Frames(audioFile)
                        if (id3Lines.isNotEmpty()) return@withContext id3Lines
                    }
                    audioFile.extension.equals("flac", ignoreCase = true) -> {
                        val vorbisLrc = extractFlacVorbisLyrics(audioFile)
                        if (!vorbisLrc.isNullOrBlank()) {
                            val parsed = parseLrcText(vorbisLrc)
                            if (parsed.isNotEmpty()) return@withContext parsed
                        }
                    }
                }
            }

            emptyList()
        }

    /**
     * Parses standard LRC + Enhanced LRC word-by-word timestamps (<mm:ss.xx>Syllable).
     */
    fun parseLrcText(rawLrc: String): List<SyncedLyricLine> {
        val rawEntries = mutableListOf<Pair<Long, String>>()

        rawLrc.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val matches = lineTimestampRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val contentStart = matches.last().range.last + 1
                val body = if (contentStart < trimmed.length) trimmed.substring(contentStart).trim() else ""
                for (match in matches) {
                    val startMs = parseTimeMatch(
                        minStr = match.groupValues[1],
                        secStr = match.groupValues[2],
                        fracStr = match.groupValues[3]
                    )
                    rawEntries += startMs to body
                }
            }
        }

        val sorted = rawEntries.sortedBy { it.first }
        return sorted.mapIndexed { idx, (lineStartMs, body) ->
            val lineEndMs = sorted.getOrNull(idx + 1)?.first ?: (lineStartMs + 5000L)
            val wordMatches = wordTimestampRegex.findAll(body).toList()

            if (wordMatches.isNotEmpty()) {
                val syllables = wordMatches.mapIndexed { wIdx, wMatch ->
                    val wStart = parseTimeMatch(
                        minStr = wMatch.groupValues[1],
                        secStr = wMatch.groupValues[2],
                        fracStr = wMatch.groupValues[3]
                    )
                    val wEnd = wordMatches.getOrNull(wIdx + 1)?.let { nextMatch ->
                        parseTimeMatch(
                            nextMatch.groupValues[1],
                            nextMatch.groupValues[2],
                            nextMatch.groupValues[3]
                        )
                    } ?: lineEndMs
                    LyricSyllable(
                        text = wMatch.groupValues[4],
                        startMs = wStart,
                        endMs = wEnd.coerceAtLeast(wStart + 40L)
                    )
                }
                val cleanText = syllables.joinToString(separator = "") { it.text }.trim()
                SyncedLyricLine(
                    index = idx,
                    startMs = lineStartMs,
                    endMs = lineEndMs,
                    rawText = cleanText,
                    syllables = syllables
                )
            } else {
                SyncedLyricLine(
                    index = idx,
                    startMs = lineStartMs,
                    endMs = lineEndMs,
                    rawText = body,
                    syllables = emptyList()
                )
            }
        }
    }

    /**
     * Parses ID3v2.3 / ID3v2.4 SYLT (Synchronised Lyric/Text) and USLT frames directly from MP3 header.
     */
    private fun parseId3v2Frames(file: File): List<SyncedLyricLine> {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return emptyList()
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return emptyList()
            }
            val version = header[3].toInt()
            val tagSize = synchsafeToInt(header[6], header[7], header[8], header[9])
            val maxPos = (10 + tagSize).toLong().coerceAtMost(raf.length())

            while (raf.filePointer + 10 < maxPos) {
                val frameHeader = ByteArray(10)
                raf.readFully(frameHeader)
                val frameId = String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1)
                if (frameId.all { it == '\u0000' }) break

                val frameSize = if (version == 4) {
                    synchsafeToInt(frameHeader[4], frameHeader[5], frameHeader[6], frameHeader[7])
                } else {
                    ByteBuffer.wrap(frameHeader, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                }
                if (frameSize <= 0 || raf.filePointer + frameSize > maxPos) break

                val payload = ByteArray(frameSize)
                raf.readFully(payload)

                if (frameId == "USLT" && payload.size > 4) {
                    val charset = id3Charset(payload[0].toInt())
                    val text = String(payload, 4, payload.size - 4, charset)
                        .substringAfter('\u0000')
                    val parsed = parseLrcText(text)
                    if (parsed.isNotEmpty()) return parsed
                } else if (frameId == "SYLT" && payload.size > 6) {
                    val syltLines = decodeSyltPayload(payload)
                    if (syltLines.isNotEmpty()) return syltLines
                }
            }
        }
        return emptyList()
    }

    private fun decodeSyltPayload(payload: ByteArray): List<SyncedLyricLine> {
        val charset = id3Charset(payload[0].toInt())
        // Skip encoding(1) + language(3) + timeStampFormat(1) + contentType(1) + descriptor
        var pos = 6
        while (pos < payload.size && payload[pos] != 0.toByte()) pos++
        pos++ // Skip null terminator of content descriptor

        val entries = mutableListOf<Pair<Long, String>>()
        while (pos < payload.size) {
            val startText = pos
            while (pos < payload.size && payload[pos] != 0.toByte()) pos++
            val text = String(payload, startText, pos - startText, charset).trim()
            pos++ // Null byte
            if (pos + 4 > payload.size) break
            val timeMs = ByteBuffer.wrap(payload, pos, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
            pos += 4
            if (text.isNotEmpty()) {
                entries += timeMs to text
            }
        }
        return entries.sortedBy { it.first }.mapIndexed { idx, (startMs, text) ->
            val endMs = entries.getOrNull(idx + 1)?.first ?: (startMs + 4000L)
            SyncedLyricLine(
                index = idx,
                startMs = startMs,
                endMs = endMs,
                rawText = text,
                syllables = emptyList()
            )
        }
    }

    /**
     * Reads FLAC Vorbis Comment metadata block (Block Type 4) and extracts LYRICS= or UNSYNCEDLYRICS=.
     */
    private fun extractFlacVorbisLyrics(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 8) return null
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, StandardCharsets.US_ASCII) != "fLaC") return null

            var isLastBlock = false
            while (!isLastBlock && raf.filePointer + 4 < raf.length()) {
                val blockHeader = raf.readUnsignedByte()
                isLastBlock = (blockHeader and 0x80) != 0
                val blockType = blockHeader and 0x7F
                val length = (raf.readUnsignedByte() shl 16) or
                    (raf.readUnsignedByte() shl 8) or
                    raf.readUnsignedByte()

                if (blockType == 4 && length > 8 && raf.filePointer + length <= raf.length()) {
                    val block = ByteArray(length)
                    raf.readFully(block)
                    val buf = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
                    val vendorLen = buf.int
                    buf.position(buf.position() + vendorLen)
                    val commentCount = buf.int
                    repeat(commentCount) {
                        if (buf.remaining() < 4) return null
                        val cLen = buf.int
                        if (cLen < 0 || cLen > buf.remaining()) return null
                        val bytes = ByteArray(cLen)
                        buf.get(bytes)
                        val comment = String(bytes, StandardCharsets.UTF_8)
                        if (comment.startsWith("LYRICS=", ignoreCase = true)) {
                            return comment.substring(7)
                        } else if (comment.startsWith("UNSYNCEDLYRICS=", ignoreCase = true)) {
                            return comment.substring(15)
                        }
                    }
                } else {
                    raf.seek(raf.filePointer + length)
                }
            }
        }
        return null
    }

    private fun parseTimeMatch(minStr: String, secStr: String, fracStr: String): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val millis = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            3 -> (fracStr.toLongOrNull() ?: 0L)
            else -> 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private fun synchsafeToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
            ((b1.toInt() and 0x7F) shl 14) or
            ((b2.toInt() and 0x7F) shl 7) or
            (b3.toInt() and 0x7F)
    }

    private fun id3Charset(encodingByte: Int): Charset = when (encodingByte) {
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }
}
