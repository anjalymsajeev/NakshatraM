package io.github.anjalymsajeev.nakshatracalendar.core.common

/** Outcome of an operation that can fail in an expected way. Exceptions are mapped, never swallowed. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Expected failures, mapped from library and platform exceptions at the boundaries. */
sealed interface AppError {
    /** The date is outside the range the Malayalam calendar library supports (1900–2100). */
    data class UnsupportedDate(val detail: String) : AppError

    /** A calendar or nakshatra calculation failed unexpectedly. */
    data class CalculationFailed(val cause: Throwable) : AppError

    /** A local database operation failed. */
    data class Database(val cause: Throwable) : AppError

    /** A network service could not be reached (offline, timeout). */
    data class Network(val cause: Throwable) : AppError

    /** A device service (for example the platform geocoder) is not available on this device. */
    data object ServiceUnavailable : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
