package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/** How the app chooses light vs. dark colors. Persisted in settings. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    /** Follow the system light/dark setting. */
    SYSTEM(R.string.theme_system),

    /** Always use the light color scheme. */
    LIGHT(R.string.theme_light),

    /** Always use the dark color scheme. */
    DARK(R.string.theme_dark);

    companion object {
        val DEFAULT = SYSTEM

        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
