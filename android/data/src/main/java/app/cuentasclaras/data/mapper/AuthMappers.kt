package app.cuentasclaras.data.mapper

import app.cuentasclaras.core.database.entity.UserEntity
import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.UserDto
import app.cuentasclaras.domain.model.StoredTokens
import app.cuentasclaras.domain.model.User

fun UserDto.toDomain(): User = User(
    id = id,
    email = email,
    displayName = displayName,
    defaultCurrency = defaultCurrency,
    locale = locale,
    createdAt = createdAt,
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    email = email,
    displayName = displayName,
    defaultCurrency = defaultCurrency,
    locale = locale,
    createdAt = createdAt,
)

fun UserEntity.toDomain(): User = User(
    id = id,
    email = email,
    displayName = displayName,
    defaultCurrency = defaultCurrency,
    locale = locale,
    createdAt = createdAt,
)

/** Convierte la respuesta del backend en tokens persistibles con caducidad absoluta. */
fun AuthTokensDto.toStoredTokens(nowEpochSeconds: Long): StoredTokens = StoredTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAtEpochSeconds = nowEpochSeconds + expiresIn,
)
