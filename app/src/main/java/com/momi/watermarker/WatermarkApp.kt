package com.momi.watermarker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; enables Hilt's generated dependency graph. */
@HiltAndroidApp
class WatermarkApp : Application()
