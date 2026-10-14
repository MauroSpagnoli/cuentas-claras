package app.cuentasclaras.core.network.di

import javax.inject.Qualifier

/** Cliente OkHttp principal (con interceptores y authenticator). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApiClient

/** Cliente OkHttp sin authenticator, usado para renovar tokens. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class RefreshClient
