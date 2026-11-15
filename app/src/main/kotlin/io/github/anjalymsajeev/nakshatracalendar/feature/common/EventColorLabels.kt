package io.github.anjalymsajeev.nakshatracalendar.feature.common

import androidx.annotation.StringRes
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor

/** Accessible names for the colour swatches (Google Calendar colour names). */
@StringRes
fun EventColor.labelRes(): Int = when (this) {
    EventColor.LAVENDER -> R.string.color_lavender
    EventColor.SAGE -> R.string.color_sage
    EventColor.GRAPE -> R.string.color_grape
    EventColor.FLAMINGO -> R.string.color_flamingo
    EventColor.BANANA -> R.string.color_banana
    EventColor.TANGERINE -> R.string.color_tangerine
    EventColor.PEACOCK -> R.string.color_peacock
    EventColor.GRAPHITE -> R.string.color_graphite
    EventColor.BLUEBERRY -> R.string.color_blueberry
    EventColor.BASIL -> R.string.color_basil
    EventColor.TOMATO -> R.string.color_tomato
}
