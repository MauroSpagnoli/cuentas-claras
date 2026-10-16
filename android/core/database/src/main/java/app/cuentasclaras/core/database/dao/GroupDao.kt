package app.cuentasclaras.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupMemberEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    /**
     * Fuente de datos paginada del listado de grupos, servida desde la caché.
     * El orden replica el del backend (`createdAt` desc, `id` desc).
     */
    @Query("SELECT * FROM groups ORDER BY createdAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeById(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<GroupEntity>)

    @Query("DELETE FROM groups")
    suspend fun clearGroups()

    @Query("SELECT * FROM group_sync_state WHERE id = ${GroupSyncStateEntity.SINGLETON_ID}")
    suspend fun syncState(): GroupSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: GroupSyncStateEntity)

    @Query(
        """
        SELECT * FROM group_members
        WHERE groupId = :groupId AND status != 'LEFT'
        ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END,
                 displayName COLLATE NOCASE ASC
        """,
    )
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<GroupMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun deleteMember(groupId: String, userId: String)
}
