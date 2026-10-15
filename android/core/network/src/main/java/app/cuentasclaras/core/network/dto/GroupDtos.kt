package app.cuentasclaras.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val currency: String = "EUR",
    val inviteCode: String? = null,
    val memberCount: Int = 0,
    val createdAt: String? = null,
    val version: Long = 0,
)

@Serializable
data class CreateGroupRequestDto(
    val name: String,
    val description: String? = null,
    val currency: String,
)

@Serializable
data class UpdateGroupRequestDto(
    val name: String? = null,
    val description: String? = null,
    val archived: Boolean? = null,
)

@Serializable
data class GroupMemberDto(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: String,
    val status: String,
    val joinedAt: String? = null,
)

@Serializable
data class AddMemberRequestDto(
    val email: String,
    val role: String? = null,
)

@Serializable
data class UpdateMemberRequestDto(
    val role: String,
)

@Serializable
data class InviteDto(
    val code: String,
    val url: String,
    val expiresAt: String? = null,
)
