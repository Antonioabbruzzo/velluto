package com.velluto.tv.platform

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// I file dei caratteri stanno in shared/res/font, in comune con la versione Fire TV.

/** Big Shoulders Display: i titoli grandi, stretti, da insegna di cinema. */
val VellutoDisplayFont = FontFamily(
    Font("font/velluto_display_extrabold.ttf", FontWeight.ExtraBold),
    Font("font/velluto_display_black.ttf", FontWeight.Black),
)

/** Figtree: tutto il resto del testo. */
val VellutoBodyFont = FontFamily(
    Font("font/velluto_body_regular.ttf", FontWeight.Normal),
    Font("font/velluto_body_medium.ttf", FontWeight.Medium),
    Font("font/velluto_body_semibold.ttf", FontWeight.SemiBold),
    Font("font/velluto_body_bold.ttf", FontWeight.Bold),
)
