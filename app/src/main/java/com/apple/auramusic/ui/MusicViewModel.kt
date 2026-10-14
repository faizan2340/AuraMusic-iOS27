package com.apple.auramusic.ui

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.apple.auramusic.audio.BassAmplitudeBufferSink
import com.apple.auramusic.audio.MusicPlaybackService
import com.apple.auramusic.core.design.ArtworkBitmapCache
import com.apple.auramusic.core.graphics.ArtworkMeshPalette
import com.apple.auramusic.core.graphics.GyroSpecularSensorManager
import com.apple.auramusic.core.graphics.extractArtworkMeshPalette
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.data.indexer.LocalAudioIndexer
import com.apple.auramusic.data.local.TrackDao
import com.apple.auramusic.data.local.TrackEntity
import com.apple.auramusic.data.lyrics.SyncedLyricLine
import com.apple.auramusic.data.lyrics.SyncedLyricsParser
import com.apple.auramusic.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All Songs"),
    LOSSLESS("Lossless"),
    FAVORITES("Favorites ♥"),
    FULL_LENGTH("2m+ Songs")
}

data class AlbumGroup(
    val albumName: String,
    val artistName: String,
    val artworkUri: String,
    val tracks: List<TrackEntity>
)

class MusicViewModel(
    private val context: Context,
    private val trackDao: TrackDao,
    private val localAudioIndexer: LocalAudioIndexer,
    private val lyricsParser: SyncedLyricsParser,
    val gyroSensorManager: GyroSpecularSensorManager,
    val hapticsController: IosHapticsController,
    bassBufferSink: BassAmplitudeBufferSink
) : ViewModel() {

    val bassEnergy: StateFlow<Float> = bassBufferSink.bassEnergy

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

    private val _hideCallRecordings = MutableStateFlow(true)
    val hideCallRecordings: StateFlow<Boolean> = _hideCallRecordings.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawTracksFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            trackDao.observeAllTracks()
        } else {
            trackDao.searchTracksInstant(query)
        }
    }

    val libraryTracks: StateFlow<List<TrackEntity>> = combine(
        rawTracksFlow,
        _selectedFilter,
        _hideCallRecordings,
        _favoriteIds
    ) { tracks, filter, hideCalls, favs ->
        tracks.filter { track ->
            val isCallRecording = track.album.contains("Call Rec", ignoreCase = true) ||
                track.title.contains("Call Rec", ignoreCase = true) ||
                track.title.contains("CNC-", ignoreCase = true) ||
                track.filePath.contains("/Call", ignoreCase = true) ||
                track.filePath.contains("/Recordings", ignoreCase = true)

            val callPass = if (hideCalls) !isCallRecording else true
            val filterPass = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.LOSSLESS -> track.audioBadge.contains("Lossless", ignoreCase = true)
                LibraryFilter.FAVORITES -> favs.contains(track.id)
                LibraryFilter.FULL_LENGTH -> track.durationMs >= 110_000L
            }
            callPass && filterPass
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumsList: StateFlow<List<AlbumGroup>> = combine(
        trackDao.observeAllTracks(),
        _hideCallRecordings
    ) { tracks, hideCalls ->
        tracks
            .filter { track ->
                if (!hideCalls) true
                else !(track.album.contains("Call Rec", ignoreCase = true) ||
                    track.title.contains("CNC-", ignoreCase = true))
            }
            .groupBy { it.album }
            .map { (albumName, albumTracks) ->
                val first = albumTracks.first()
                AlbumGroup(
                    albumName = albumName,
                    artistName = first.artist,
                    artworkUri = first.artworkUri,
                    tracks = albumTracks
                )
            }
            .sortedBy { it.albumName.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val activeQueue: StateFlow<List<TrackEntity>> = _activeQueue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(0) // 0 = Off, 1 = All, 2 = One
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // 5-Band Parametric EQ Gains in [0.0f .. 1.0f] where 0.5f = 0dB flat
    private val _eqBands = MutableStateFlow(listOf(0.58f, 0.54f, 0.50f, 0.55f, 0.60f))
    val eqBands: StateFlow<List<Float>> = _eqBands.asStateFlow()

    private val _selectedEqPreset = MutableStateFlow("Hi-Res Direct")
    val selectedEqPreset: StateFlow<String> = _selectedEqPreset.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _volumeFraction = MutableStateFlow(0.78f)
    val volumeFraction: StateFlow<Float> = _volumeFraction.asStateFlow()

    private val _frostFactor = MutableStateFlow(0.28f)
    val frostFactor: StateFlow<Float> = _frostFactor.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(6)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<SyncedLyricLine>>(emptyList())
    val syncedLyrics: StateFlow<List<SyncedLyricLine>> = _syncedLyrics.asStateFlow()

    private val _artworkPalette = MutableStateFlow(ArtworkMeshPalette())
    val artworkPalette: StateFlow<ArtworkMeshPalette> = _artworkPalette.asStateFlow()

    private var mediaController: MediaController? = null

    init {
        connectMediaSession()
        refreshLocalLibrary()
        startPositionTicker()
    }

    fun refreshLocalLibrary() {
        viewModelScope.launch {
            localAudioIndexer.indexLocalLibrary()
            val tracks = libraryTracks.value
            if (_currentTrack.value == null && tracks.isNotEmpty()) {
                val first = tracks.first()
                _currentTrack.value = first
                _activeQueue.value = tracks
                updatePaletteAndLyrics(first)
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
        hapticsController.onGlassSnapConfirmation()
    }

    fun toggleHideCallRecordings() {
        _hideCallRecordings.value = !_hideCallRecordings.value
        hapticsController.onGlassSnapConfirmation()
    }

    fun toggleFavorite(trackId: Long) {
        val current = _favoriteIds.value.toMutableSet()
        if (!current.add(trackId)) {
            current.remove(trackId)
        }
        _favoriteIds.value = current
        hapticsController.onGlassSnapConfirmation()
    }

    fun toggleShuffle() {
        val next = !_isShuffleEnabled.value
        _isShuffleEnabled.value = next
        mediaController?.shuffleModeEnabled = next
        hapticsController.onGlassSnapConfirmation()
    }

    fun cycleRepeatMode() {
        val next = (_repeatMode.value + 1) % 3
        _repeatMode.value = next
        mediaController?.repeatMode = when (next) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        hapticsController.onGlassSnapConfirmation()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        mediaController?.setPlaybackSpeed(clamped)
        hapticsController.onGlassSnapConfirmation()
    }

    fun setEqBand(index: Int, value: Float) {
        val list = _eqBands.value.toMutableList()
        if (index in list.indices) {
            list[index] = value.coerceIn(0f, 1f)
            _eqBands.value = list
            _selectedEqPreset.value = "Custom Studio"
        }
    }

    fun applyEqPreset(presetName: String) {
        _selectedEqPreset.value = presetName
        _eqBands.value = when (presetName) {
            "Flat Reference" -> listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.50f)
            "Sub-Bass Boost" -> listOf(0.82f, 0.72f, 0.48f, 0.54f, 0.62f)
            "Vocal Presence" -> listOf(0.45f, 0.48f, 0.68f, 0.74f, 0.58f)
            "Late Night" -> listOf(0.64f, 0.58f, 0.46f, 0.44f, 0.52f)
            else -> listOf(0.58f, 0.54f, 0.50f, 0.55f, 0.60f)
        }
        hapticsController.onGlassSnapConfirmation()
    }

    fun setFrostFactor(value: Float) {
        _frostFactor.value = value.coerceIn(0f, 1f)
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds.coerceIn(1, 12)
    }

    fun playTrack(track: TrackEntity, queue: List<TrackEntity>) {
        val effectiveQueue = queue.ifEmpty { listOf(track) }
        _currentTrack.value = track
        _activeQueue.value = effectiveQueue
        updatePaletteAndLyrics(track)

        if (track.uri.isBlank()) {
            _isPlaying.value = true
            _currentPositionMs.value = 0L
            return
        }
        val controller = mediaController ?: run {
            _isPlaying.value = true
            return
        }
        val validQueue = effectiveQueue.filter { it.uri.isNotBlank() }
        val items = validQueue.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build()
                )
                .build()
        }
        val startIndex = validQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun shufflePlayQueue(queue: List<TrackEntity>) {
        if (queue.isEmpty()) return
        val shuffled = queue.shuffled()
        _isShuffleEnabled.value = true
        mediaController?.shuffleModeEnabled = true
        playTrack(shuffled.first(), shuffled)
        hapticsController.onGlassSnapConfirmation()
    }

    private fun updatePaletteAndLyrics(track: TrackEntity) {
        viewModelScope.launch {
            _syncedLyrics.value = lyricsParser.loadLyricsForTrack(track)
        }
        viewModelScope.launch {
            val bmp = ArtworkBitmapCache.loadArtworkBitmap(context, track.artworkUri)
            _artworkPalette.value = extractArtworkMeshPalette(bmp)
        }
    }

    fun togglePlayPause() {
        val current = _currentTrack.value
        if (current == null) {
            val all = libraryTracks.value
            if (all.isNotEmpty()) {
                playTrack(all.first(), all)
            }
            return
        }
        if (current.uri.isBlank()) {
            _isPlaying.value = !_isPlaying.value
            return
        }
        val controller = mediaController ?: run {
            _isPlaying.value = !_isPlaying.value
            return
        }
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        val controller = mediaController
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else {
            val q = _activeQueue.value
            val cur = _currentTrack.value
            if (q.isNotEmpty() && cur != null) {
                val idx = q.indexOfFirst { it.id == cur.id }
                val nextTrack = q[(idx + 1) % q.size]
                playTrack(nextTrack, q)
            }
        }
    }

    fun skipPrevious() {
        val controller = mediaController
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            val q = _activeQueue.value
            val cur = _currentTrack.value
            if (q.isNotEmpty() && cur != null) {
                val idx = q.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
                val prevTrack = q[(idx - 1 + q.size) % q.size]
                playTrack(prevTrack, q)
            }
        }
    }

    fun seekToFraction(fraction: Float) {
        val duration = _currentTrack.value?.durationMs ?: return
        val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
        seekToMs(targetMs)
    }

    fun seekToMs(positionMs: Long) {
        _currentPositionMs.value = positionMs
        mediaController?.seekTo(positionMs)
    }

    fun setVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        _volumeFraction.value = clamped
        mediaController?.volume = clamped
    }

    private fun connectMediaSession() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val controller = future.get()
            mediaController = controller
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                    viewModelScope.launch {
                        val entity = trackDao.getTrackById(id) ?: return@launch
                        _currentTrack.value = entity
                        updatePaletteAndLyrics(entity)
                    }
                }
            })
        }, context.mainExecutor)
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                val current = _currentTrack.value
                if (current != null && current.uri.isBlank() && _isPlaying.value) {
                    _currentPositionMs.value = (_currentPositionMs.value + 16L) % current.durationMs.coerceAtLeast(1L)
                } else if (controller != null && controller.isPlaying) {
                    _currentPositionMs.value = controller.currentPosition
                }
                delay(16L)
            }
        }
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MusicViewModel(
                        context = container.appContext,
                        trackDao = container.trackDao,
                        localAudioIndexer = container.localAudioIndexer,
                        lyricsParser = container.lyricsParser,
                        gyroSensorManager = container.gyroSensorManager,
                        hapticsController = container.hapticsController,
                        bassBufferSink = container.bassBufferSink
                    ) as T
                }
            }
    }
}
