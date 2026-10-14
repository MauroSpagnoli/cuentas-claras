package app.cuentasclaras.core.common.di

import javax.inject.Qualifier

/** Dispatcher para trabajo de E/S (red, disco). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

/** Dispatcher para trabajo intensivo en CPU. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

/** Dispatcher principal (UI). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher
