package io.github.anjalymsajeev.nakshatracalendar.core.common

import javax.inject.Qualifier

/** Dispatcher for CPU-bound calendar and astronomy work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Dispatcher for database and file I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
