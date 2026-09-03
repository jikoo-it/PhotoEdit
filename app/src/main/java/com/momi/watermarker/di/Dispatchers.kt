package com.momi.watermarker.di

import javax.inject.Qualifier

/** Qualifies the dispatcher used for disk/CPU-bound image work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
