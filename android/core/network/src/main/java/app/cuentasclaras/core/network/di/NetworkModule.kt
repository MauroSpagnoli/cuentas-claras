package app.cuentasclaras.core.network.di

import app.cuentasclaras.core.network.NetworkConfig
import app.cuentasclaras.core.network.api.AuthApi
import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.interceptor.AuthInterceptor
import app.cuentasclaras.core.network.interceptor.IdempotencyInterceptor
import app.cuentasclaras.core.network.interceptor.TokenRefreshAuthenticator
import app.cuentasclaras.core.network.normalizedBaseUrl
import app.cuentasclaras.domain.auth.AuthTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @OptIn(ExperimentalSerializationApi::class)
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(config: NetworkConfig): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (config.enableLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

    @Provides
    @Singleton
    fun provideAuthenticator(
        tokenStore: AuthTokenStore,
        json: Json,
        config: NetworkConfig,
        @RefreshClient refreshClient: OkHttpClient,
    ): TokenRefreshAuthenticator = TokenRefreshAuthenticator(
        tokenStore = tokenStore,
        json = json,
        baseUrl = config.normalizedBaseUrl,
        refreshClient = refreshClient,
    )

    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(
        tokenStore: AuthTokenStore,
        authenticator: TokenRefreshAuthenticator,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(IdempotencyInterceptor())
        .addInterceptor(logging)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @ApiClient client: OkHttpClient,
        config: NetworkConfig,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory(APPLICATION_JSON))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideGroupsApi(retrofit: Retrofit): GroupsApi = retrofit.create(GroupsApi::class.java)

    private val APPLICATION_JSON = "application/json".toMediaType()
}
