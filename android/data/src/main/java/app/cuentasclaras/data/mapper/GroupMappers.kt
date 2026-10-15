package app.cuentasclaras.data.mapper

import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.network.dto.GroupDto
import app.cuentasclaras.core.network.dto.GroupMemberDto
import app.cuentasclaras.core.network.dto.InviteDto
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.model.MemberStatus

fun GroupDto.toDomain(): Group = Group(
    id = id,
    name = name,
    description = description,
    currency = currency,
    inviteCode = inviteCode,
    memberCount = memberCount,
    createdAt = createdAt,
    version = version,
)

fun GroupDto.toEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    description = description,
    currency = currency,
    inviteCode = inviteCode,
    memberCount = memberCount,
    createdAt = createdAt,
    version = version,
)

fun GroupEntity.toDomain(): Group = Group(
    id = id,
    name = name,
    description = description,
    currency = currency,
    inviteCode = inviteCode,
    memberCount = memberCount,
    createdAt = createdAt,
    version = version,
)

fun GroupMemberDto.toDomain(): GroupMember = GroupMember(
    userId = userId,
    displayName = displayName,
    email = email,
    role = GroupRole.fromApi(role),
    status = MemberStatus.fromApi(status),
    joinedAt = joinedAt,
)

fun GroupMemberDto.toEntity(groupId: String): GroupMemberEntity = GroupMemberEntity(
    groupId = groupId,
    userId = userId,
    displayName = displayName,
    email = email,
    role = GroupRole.fromApi(role).name,
    status = MemberStatus.fromApi(status).name,
    joinedAt = joinedAt,
)

fun GroupMemberEntity.toDomain(): GroupMember = GroupMember(
    userId = userId,
    displayName = displayName,
    email = email,
    role = GroupRole.fromApi(role),
    status = MemberStatus.fromApi(status),
    joinedAt = joinedAt,
)

fun InviteDto.toDomain(): Invite = Invite(
    code = code,
    url = url,
    expiresAt = expiresAt,
)
