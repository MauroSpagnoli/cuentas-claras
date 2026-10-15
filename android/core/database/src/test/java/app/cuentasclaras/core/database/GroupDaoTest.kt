package app.cuentasclaras.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupDaoTest {

    private lateinit var database: CuentasClarasDatabase

    private val groupA = GroupEntity(
        id = "A",
        name = "Piso",
        description = null,
        currency = "EUR",
        inviteCode = null,
        memberCount = 2,
        createdAt = "2026-01-02T10:00:00Z",
        version = 1,
    )
    private val groupB = groupA.copy(id = "B", name = "Viaje", createdAt = "2026-01-03T10:00:00Z")

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CuentasClarasDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `paging source orders by createdAt descending`() = runTest {
        val dao = database.groupDao()
        dao.upsertAll(listOf(groupA, groupB))

        val page = dao.pagingSource().load(refresh()) as PagingSource.LoadResult.Page

        assertEquals(listOf("B", "A"), page.data.map { it.id })
    }

    @Test
    fun `observe by id emits null when the group is not cached`() = runTest {
        assertNull(database.groupDao().observeById("missing").first())
    }

    @Test
    fun `upsert replaces the cached group`() = runTest {
        val dao = database.groupDao()
        dao.upsert(groupA)
        dao.upsert(groupA.copy(name = "Piso nuevo", memberCount = 4))

        val cached = dao.observeById("A").first()
        assertEquals("Piso nuevo", cached?.name)
        assertEquals(4, cached?.memberCount)
    }

    @Test
    fun `members hide LEFT and order owner, admin, member, then name`() = runTest {
        val dao = database.groupDao()
        dao.upsert(groupA)
        dao.upsertMembers(
            listOf(
                member("u-zoe", "Zoe", "MEMBER"),
                member("u-ana", "Ana", "OWNER"),
                member("u-beto", "Beto", "MEMBER"),
                member("u-caro", "Caro", "ADMIN"),
                member("u-gone", "Gone", "MEMBER").copy(status = "LEFT"),
            ),
        )

        val members = dao.observeMembers("A").first()

        assertEquals(listOf("u-ana", "u-caro", "u-beto", "u-zoe"), members.map { it.userId })
    }

    @Test
    fun `clear members keeps other groups untouched`() = runTest {
        val dao = database.groupDao()
        dao.upsertMembers(listOf(member("u-1", "Ana", "OWNER", groupId = "A")))
        dao.upsertMembers(listOf(member("u-2", "Beto", "OWNER", groupId = "B")))

        dao.clearMembers("A")

        assertEquals(0, dao.observeMembers("A").first().size)
        assertEquals(1, dao.observeMembers("B").first().size)
    }

    @Test
    fun `sync state is a single replaceable row`() = runTest {
        val dao = database.groupDao()
        assertNull(dao.syncState())

        dao.upsertSyncState(GroupSyncStateEntity(nextCursor = "c1", endReached = false))
        dao.upsertSyncState(GroupSyncStateEntity(nextCursor = null, endReached = true))

        val state = dao.syncState()
        assertEquals(GroupSyncStateEntity.SINGLETON_ID, state?.id)
        assertNull(state?.nextCursor)
        assertEquals(true, state?.endReached)
    }

    @Test
    fun `delete member removes only that row`() = runTest {
        val dao = database.groupDao()
        dao.upsertMembers(
            listOf(
                member("u-1", "Ana", "OWNER"),
                member("u-2", "Beto", "MEMBER"),
            ),
        )

        dao.deleteMember("A", "u-2")

        assertEquals(listOf("u-1"), dao.observeMembers("A").first().map { it.userId })
    }

    private fun member(
        userId: String,
        name: String,
        role: String,
        groupId: String = "A",
    ) = GroupMemberEntity(
        groupId = groupId,
        userId = userId,
        displayName = name,
        email = "$userId@example.com",
        role = role,
        status = "ACTIVE",
        joinedAt = null,
    )

    private fun refresh(pageSize: Int = 50) = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = pageSize,
        placeholdersEnabled = false,
    )
}
