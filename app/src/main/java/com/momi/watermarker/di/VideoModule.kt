package com.momi.watermarker.di

import com.momi.watermarker.data.repository.VideoRepositoryImpl
import com.momi.watermarker.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the video-editing repository abstraction to its data-layer
 * implementation.
 *
 * Kept as a separate module (rather than adding to [RepositoryModule]) so the
 * video-editing feature stays fully self-contained and never edits the
 * image-processing DI wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VideoModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
