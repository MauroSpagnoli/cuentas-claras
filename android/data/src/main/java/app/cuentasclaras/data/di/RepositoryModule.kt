package app.cuentasclaras.data.di

import app.cuentasclaras.data.repository.AuthRepositoryImpl
import app.cuentasclaras.data.repository.GroupRepositoryImpl
import app.cuentasclaras.data.token.EncryptedAuthTokenStore
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.repository.GroupRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthTokenStore(impl: EncryptedAuthTokenStore): AuthTokenStore

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository
}
