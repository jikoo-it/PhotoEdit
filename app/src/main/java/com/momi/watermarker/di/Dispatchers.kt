package com.momi.watermarker.di

import javax.inject.Qualifier

/** Qualifies the dispatcher used for disk/stream I/O (decode, encode, MediaStore). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Qualifies the dispatcher used for CPU-bound pixel work (rendering pipelines). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
