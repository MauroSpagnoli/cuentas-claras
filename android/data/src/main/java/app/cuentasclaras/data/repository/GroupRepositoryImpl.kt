package app.cuentasclaras.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import androidx.room.withTransaction
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.dto.AddMemberRequestDto
import app.cuentasclaras.core.network.dto.CreateGroupRequestDto
import app.cuentasclaras.core.network.dto.UpdateMemberRequestDto
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.core.network.safeUnitApiCall
import app.cuentasclaras.data.mapper.toDomain
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.data.paging.GroupRemoteMediator
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalPagingApi::class)
class GroupRepositoryImpl @Inject constructor(
    private val api: GroupsApi,
    private val database: CuentasClarasDatabase,
    private val groupDao: GroupDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GroupRepository {

    override fun observeGroups(): Flow<PagingData<Group>> = Pager(
        config = PagingConfig(
            pageSize = GroupRemoteMediator.PAGE_SIZE,
            initialLoadSize = GroupRemoteMediator.PAGE_SIZE,
            enablePlaceholders = false,
        ),
        remoteMediator = GroupRemoteMediator(api, database, json, ioDispatcher),
        pagingSourceFactory = { groupDao.pagingSource() },
    ).flow.map { data -> data.map(GroupEntity::toDomain) }

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observeById(groupId).map { it?.toDomain() }

    override suspend fun refreshGroup(groupId: String): AppResult<Group> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.get(groupId) }) {
            is AppResult.Success -> {
                groupDao.upsert(result.value.toEntity())
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }

    override suspend fun createGroup(
        name: String,
        description: String?,
        currency: String,
    ): AppResult<Group> = withContext(ioDispatcher) {
        val body = CreateGroupRequestDto(name = name, description = description, currency = currency)
        when (val result = safeApiCall(json) { api.create(body) }) {
            is AppResult.Success -> {
                groupDao.upsert(result.value.toEntity())
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> =
        groupDao.observeMembers(groupId).map { members -> members.map(GroupMemberEntity::toDomain) }

    override suspend fun refreshMembers(groupId: String): AppResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.members(groupId) }) {
            is AppResult.Success -> {
                val members = result.value
                database.withTransaction {
                    groupDao.clearMembers(groupId)
                    groupDao.upsertMembers(members.map { it.toEntity(groupId) })
                }
                AppResult.Success(Unit)
            }

            is AppResult.Failure -> result
        }
    }

    override suspend fun addMember(
        groupId: String,
        email: String,
        role: GroupRole?,
    ): AppResult<GroupMember> = withContext(ioDispatcher) {
        val body = AddMemberRequestDto(email = email, role = role?.name)
        when (val result = safeApiCall(json) { api.addMember(groupId, body) }) {
            is AppResult.Success -> {
                groupDao.upsertMember(result.value.toEntity(groupId))
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }

    override suspend fun changeMemberRole(
        groupId: String,
        userId: String,
        role: GroupRole,
    ): AppResult<GroupMember> = withContext(ioDispatcher) {
        val body = UpdateMemberRequestDto(role = role.name)
        when (val result = safeApiCall(json) { api.changeMemberRole(groupId, userId, body) }) {
            is AppResult.Success -> {
                groupDao.upsertMember(result.value.toEntity(groupId))
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }

    override suspend fun removeMember(groupId: String, userId: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            when (val result = safeUnitApiCall(json) { api.removeMember(groupId, userId) }) {
                is AppResult.Success -> {
                    groupDao.deleteMember(groupId, userId)
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> result
            }
        }

    override suspend fun createInvite(groupId: String): AppResult<Invite> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.createInvite(groupId) }) {
            is AppResult.Success -> AppResult.Success(result.value.toDomain())
            is AppResult.Failure -> result
        }
    }

    override suspend fun acceptInvite(code: String): AppResult<Group> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.acceptInvite(code) }) {
            is AppResult.Success -> {
                groupDao.upsert(result.value.toEntity())
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }
}
