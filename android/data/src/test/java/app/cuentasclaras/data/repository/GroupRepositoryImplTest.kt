package app.cuentasclaras.data.repository

import androidx.paging.testing.asSnapshot
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.dto.GroupDto
import app.cuentasclaras.core.network.dto.GroupMemberDto
import app.cuentasclaras.core.network.dto.InviteDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class GroupRepositoryImplTest {

    private lateinit var database: CuentasClarasDatabase
    private val api: GroupsApi = mockk()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var repository: GroupRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CuentasClarasDatabase::class.java,
        ).build()
        repository = GroupRepositoryImpl(api, database, database.groupDao(), json, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createGroup caches the created group`() = runTest {
        coEvery { api.create(any()) } returns Response.success(groupDto("g-1", name = "Piso"))

        val result = repository.createGroup("Piso", null, "EUR")

        assertEquals("Piso", (result as AppResult.Success).value.name)
        assertEquals("Piso", repository.observeGroup("g-1").first()?.name)
    }

    @Test
    fun `createGroup maps a server error`() = runTest {
        coEvery { api.create(any()) } returns problemResponse(400, "Bad Request", "datos inválidos")

        val result = repository.createGroup("", null, "EUR")

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Validation("datos inválidos"), (result as AppResult.Failure).error)
    }

    @Test
    fun `refreshGroup updates the cache`() = runTest {
        coEvery { api.get("g-1") } returns Response.success(groupDto("g-1", name = "Piso", memberCount = 3))

        val result = repository.refreshGroup("g-1")

        assertEquals(3, (result as AppResult.Success).value.memberCount)
        assertEquals(3, repository.observeGroup("g-1").first()?.memberCount)
    }

    @Test
    fun `refreshGroup keeps the previous cache on failure`() = runTest {
        coEvery { api.get("g-1") } returns Response.success(groupDto("g-1", name = "Piso"))
        repository.refreshGroup("g-1")
        coEvery { api.get("g-1") } returns problemResponse(404, "Not Found", "el grupo no existe")

        val result = repository.refreshGroup("g-1")

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
        assertEquals("Piso", repository.observeGroup("g-1").first()?.name)
    }

    @Test
    fun `refreshMembers replaces the cached members`() = runTest {
        database.groupDao().upsertMembers(listOf(memberEntity("u-old", "Old")))

        coEvery { api.members("g-1") } returns Response.success(
            listOf(
                memberDto("u-1", "Ana", role = "OWNER"),
                memberDto("u-2", "Beto", role = "MEMBER", status = "INVITED"),
            ),
        )

        val result = repository.refreshMembers("g-1")

        assertTrue(result is AppResult.Success)
        val members = repository.observeMembers("g-1").first()
        assertEquals(listOf("u-1", "u-2"), members.map { it.userId })
        assertEquals(GroupRole.OWNER, members[0].role)
        assertEquals(MemberStatus.INVITED, members[1].status)
    }

    @Test
    fun `addMember caches the new member`() = runTest {
        coEvery { api.addMember(any(), any()) } returns Response.success(memberDto("u-3", "Caro", role = "ADMIN"))

        val result = repository.addMember("g-1", "caro@example.com", GroupRole.ADMIN)

        assertEquals(GroupRole.ADMIN, (result as AppResult.Success).value.role)
        assertEquals(listOf("u-3"), repository.observeMembers("g-1").first().map { it.userId })
    }

    @Test
    fun `changeMemberRole updates the cached member`() = runTest {
        database.groupDao().upsertMember(memberEntity("u-2", "Beto"))
        coEvery { api.changeMemberRole(any(), any(), any()) } returns
            Response.success(memberDto("u-2", "Beto", role = "ADMIN"))

        repository.changeMemberRole("g-1", "u-2", GroupRole.ADMIN)

        assertEquals(GroupRole.ADMIN, repository.observeMembers("g-1").first().single().role)
    }

    @Test
    fun `removeMember drops the cached member`() = runTest {
        database.groupDao().upsertMember(memberEntity("u-2", "Beto"))
        coEvery { api.removeMember(any(), any()) } returns Response.success(Unit)

        val result = repository.removeMember("g-1", "u-2")

        assertTrue(result is AppResult.Success)
        assertEquals(0, repository.observeMembers("g-1").first().size)
    }

    @Test
    fun `createInvite maps the response to the domain model`() = runTest {
        coEvery { api.createInvite("g-1") } returns Response.success(
            InviteDto(code = "ABCDEFGHIJ", url = "https://cuentas-claras.app/invite/ABCDEFGHIJ"),
        )

        val result = repository.createInvite("g-1")

        val invite = (result as AppResult.Success).value
        assertEquals("ABCDEFGHIJ", invite.code)
        assertEquals("https://cuentas-claras.app/invite/ABCDEFGHIJ", invite.url)
    }

    @Test
    fun `acceptInvite caches the joined group`() = runTest {
        coEvery { api.acceptInvite("ABCDEFGHIJ") } returns Response.success(groupDto("g-7", name = "Viaje"))

        val result = repository.acceptInvite("ABCDEFGHIJ")

        assertEquals("Viaje", (result as AppResult.Success).value.name)
        assertEquals("Viaje", repository.observeGroup("g-7").first()?.name)
        coVerify(exactly = 1) { api.acceptInvite("ABCDEFGHIJ") }
    }

    @Test
    fun `observeGroups serves the cache refreshed from the network`() = runTest {
        coEvery { api.list(any(), any()) } returns Response.success(
            PageDto(items = listOf(groupDto("g-1", "Piso"), groupDto("g-2", "Viaje")), nextCursor = null, hasMore = false),
        )

        val snapshot = withContext(Dispatchers.IO) { repository.observeGroups().asSnapshot() }

        assertEquals(setOf("Piso", "Viaje"), snapshot.map { it.name }.toSet())
    }

    @Test
    fun `a failing refresh does not wipe the cached groups`() = runTest {
        coEvery { api.list(any(), any()) } returns problemResponse(500, "Boom", "servidor caído")
        database.groupDao().upsert(groupEntity("g-cached", "Cacheado"))

        val snapshot = runCatching {
            withContext(Dispatchers.IO) { repository.observeGroups().asSnapshot() }
        }

        // Paging propaga el fallo del refresco remoto...
        assertTrue(snapshot.isFailure)
        // ...pero Room sigue siendo la fuente de verdad y conserva lo cacheado.
        assertEquals("Cacheado", repository.observeGroup("g-cached").first()?.name)
        assertEquals(listOf("Cacheado"), cachedGroupNames())
    }

    private fun groupDto(id: String, name: String = "Grupo $id", memberCount: Int = 1) = GroupDto(
        id = id,
        name = name,
        description = null,
        currency = "EUR",
        inviteCode = null,
        memberCount = memberCount,
        createdAt = "2026-01-01T10:00:00Z",
        version = 0,
    )

    private fun groupEntity(id: String, name: String) = GroupEntity(
        id = id,
        name = name,
        description = null,
        currency = "EUR",
        inviteCode = null,
        memberCount = 1,
        createdAt = "2026-01-01T10:00:00Z",
        version = 0,
    )

    private fun memberDto(
        userId: String,
        displayName: String,
        role: String = "MEMBER",
        status: String = "ACTIVE",
    ) = GroupMemberDto(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = role,
        status = status,
        joinedAt = "2026-01-01T10:00:00Z",
    )

    private fun memberEntity(userId: String, displayName: String) = GroupMemberEntity(
        groupId = "g-1",
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = "MEMBER",
        status = "ACTIVE",
        joinedAt = null,
    )

    private fun <T> problemResponse(code: Int, title: String, detail: String): Response<T> = Response.error(
        code,
        """{"title":"$title","status":$code,"detail":"$detail"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )

    private suspend fun cachedGroupNames(): List<String> {
        val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 50, placeholdersEnabled = false)
        val page = database.groupDao().pagingSource().load(params) as PagingSource.LoadResult.Page
        return page.data.map { it.name }
    }
}
