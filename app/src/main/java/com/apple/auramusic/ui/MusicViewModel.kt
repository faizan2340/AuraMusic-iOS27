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
import com.apple.auramusic.core.graphics.ArtworkMeshPalette
import com.apple.auramusic.core.graphics.GyroSpecularSensorManager
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val libraryTracks: StateFlow<List<TrackEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                trackDao.observeAllTracks()
            } else {
                trackDao.searchTracksInstant(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _volumeFraction = MutableStateFlow(0.75f)
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
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setFrostFactor(value: Float) {
        _frostFactor.value = value.coerceIn(0f, 1f)
    }

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds.coerceIn(1, 12)
    }

    fun playTrack(track: TrackEntity, queue: List<TrackEntity>) {
        _currentTrack.value = track
        viewModelScope.launch {
            _syncedLyrics.value = lyricsParser.loadLyricsForTrack(track)
        }
        if (track.uri.isBlank()) {
            _isPlaying.value = true
            _currentPositionMs.value = 0L
            return
        }
        val controller = mediaController ?: run {
            _isPlaying.value = true
            return
        }
        val items = queue.filter { it.uri.isNotBlank() }.map { item ->
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
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val current = _currentTrack.value
        if (current != null && current.uri.isBlank()) {
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
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
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
                        _syncedLyrics.value = lyricsParser.loadLyricsForTrack(entity)
                    }
                }
            })
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
