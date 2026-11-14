package io.github.anjalymsajeev.nakshatracalendar.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.core.common.IoDispatcher
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /** Injected so that "today" is testable. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
