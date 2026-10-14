package app.cuentasclaras.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
    val defaultCurrency: String = "EUR",
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class AuthTokensDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserDto? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val defaultCurrency: String = "EUR",
    val locale: String? = null,
    val createdAt: String? = null,
)

/** Error RFC 7807 devuelto por el backend. */
@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val correlationId: String? = null,
    val errors: List<ValidationErrorDto>? = null,
)

@Serializable
data class ValidationErrorDto(
    val field: String? = null,
    val message: String? = null,
)
