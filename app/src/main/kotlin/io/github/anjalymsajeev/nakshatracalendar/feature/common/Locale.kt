package io.github.anjalymsajeev.nakshatracalendar.feature.common

import android.content.Context

/** Whether the app is currently shown in Malayalam (per-app language or system language). */
fun Context.isMalayalamUi(): Boolean = resources.configuration.locales[0].language == "ml"
