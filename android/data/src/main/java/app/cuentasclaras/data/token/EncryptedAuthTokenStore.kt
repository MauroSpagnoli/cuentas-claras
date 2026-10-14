package app.cuentasclaras.data.token

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.model.StoredTokens
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste los tokens cifrados con `EncryptedSharedPreferences` (clave maestra
 * en el Android Keystore) y expone el estado en memoria para lecturas síncronas
 * desde los interceptores de OkHttp.
 */
@Singleton
class EncryptedAuthTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : AuthTokenStore {

    private val preferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val state = MutableStateFlow(readTokens())

    override fun snapshot(): StoredTokens? = state.value

    override fun save(tokens: StoredTokens) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAtEpochSeconds)
            .apply()
        state.value = tokens
    }

    override fun clear() {
        preferences.edit().clear().apply()
        state.value = null
    }

    override fun observe(): Flow<StoredTokens?> = state.asStateFlow()

    private fun readTokens(): StoredTokens? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        return StoredTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "cuentas_claras_secure_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at_epoch_seconds"
    }
}
