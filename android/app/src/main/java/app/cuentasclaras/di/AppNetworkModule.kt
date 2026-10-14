package app.cuentasclaras.di

import app.cuentasclaras.BuildConfig
import app.cuentasclaras.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = object : NetworkConfig {
        // En debug apunta al backend local visto desde el emulador (10.0.2.2).
        override val baseUrl: String = BuildConfig.API_BASE_URL
        override val enableLogging: Boolean = BuildConfig.DEBUG
    }
}
