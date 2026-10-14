package com.apple.auramusic.di

import android.content.Context
import com.apple.auramusic.audio.BassAmplitudeBufferSink
import com.apple.auramusic.core.graphics.GyroSpecularSensorManager
import com.apple.auramusic.core.physics.IosHapticsController
import com.apple.auramusic.data.indexer.LocalAudioIndexer
import com.apple.auramusic.data.local.TrackDao
import com.apple.auramusic.data.lyrics.SyncedLyricsParser

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val trackDao = TrackDao(appContext)
    val localAudioIndexer = LocalAudioIndexer(appContext, trackDao)
    val lyricsParser = SyncedLyricsParser()
    val gyroSensorManager = GyroSpecularSensorManager(appContext)
    val hapticsController = IosHapticsController(appContext)
    val bassBufferSink = BassAmplitudeBufferSink()
}
