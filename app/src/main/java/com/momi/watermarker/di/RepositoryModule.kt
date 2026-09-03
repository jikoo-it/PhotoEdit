package com.momi.watermarker.di

import com.momi.watermarker.data.repository.ImageProcessingRepositoryImpl
import com.momi.watermarker.data.repository.MediaRepositoryImpl
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds domain repository abstractions to their data-layer implementations.
 * Presentation and use cases depend only on the interfaces (Dependency
 * Inversion), so an implementation can be swapped here without touching them.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImageProcessingRepository(
        impl: ImageProcessingRepositoryImpl,
    ): ImageProcessingRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository
}
