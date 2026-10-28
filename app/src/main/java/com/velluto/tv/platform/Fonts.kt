package com.velluto.tv.platform

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.velluto.tv.R

// I file dei caratteri stanno in shared/res/font, in comune con la versione Windows.

/** Big Shoulders Display: i titoli grandi, stretti, da insegna di cinema. */
val VellutoDisplayFont = FontFamily(
    Font(R.font.velluto_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.velluto_display_black, FontWeight.Black),
)

/** Figtree: tutto il resto del testo. */
val VellutoBodyFont = FontFamily(
    Font(R.font.velluto_body_regular, FontWeight.Normal),
    Font(R.font.velluto_body_medium, FontWeight.Medium),
    Font(R.font.velluto_body_semibold, FontWeight.SemiBold),
    Font(R.font.velluto_body_bold, FontWeight.Bold),
)
