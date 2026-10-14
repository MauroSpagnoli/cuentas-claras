package app.cuentasclaras.domain.auth

import app.cuentasclaras.domain.model.StoredTokens
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de persistencia de tokens. La implementación (almacenamiento cifrado)
 * vive en la capa `data`; la capa de red la usa de forma síncrona desde los
 * interceptores de OkHttp.
 */
interface AuthTokenStore {

    /** Lectura síncrona para interceptores/authenticator. */
    fun snapshot(): StoredTokens?

    /** Persiste (reemplaza) los tokens. */
    fun save(tokens: StoredTokens)

    /** Borra los tokens (logout o refresh inválido). */
    fun clear()

    /** Observa cambios para derivar la sesión reactiva. */
    fun observe(): Flow<StoredTokens?>
}
