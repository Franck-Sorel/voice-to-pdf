package com.sttapp.di

import android.content.Context
import androidx.room.Room
import com.sttapp.core.audio.AudioRecorder
import com.sttapp.core.pdf.PdfExporter
import com.sttapp.core.recognition.Transcriber
import com.sttapp.data.RoomSessionRepository
import com.sttapp.data.SessionRepository
import com.sttapp.data.audio.AndroidAudioRecorder
import com.sttapp.data.local.AppDatabase
import com.sttapp.data.local.SessionDao
import com.sttapp.data.pdf.AndroidPdfExporter
import com.sttapp.data.recognition.MediaCodecPcmDecoder
import com.sttapp.data.recognition.PcmDecoder
import com.sttapp.data.recognition.WhisperTranscriber
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "voicetopdf.db").build()

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(impl: AndroidAudioRecorder): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindPcmDecoder(impl: MediaCodecPcmDecoder): PcmDecoder

    @Binds
    @Singleton
    abstract fun bindTranscriber(impl: WhisperTranscriber): Transcriber

    @Binds
    @Singleton
    abstract fun bindPdfExporter(impl: AndroidPdfExporter): PdfExporter
}
