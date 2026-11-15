package io.github.anjalymsajeev.nakshatracalendar.feature.common

import androidx.annotation.StringRes
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError

/** One place that turns domain errors into user-facing messages. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    is AppError.UnsupportedDate -> R.string.error_unsupported_date
    is AppError.CalculationFailed -> R.string.error_calculation
    is AppError.Database -> R.string.error_database
    is AppError.Network -> R.string.error_network
    AppError.ServiceUnavailable -> R.string.error_geocoder_unavailable
}
