package app.cuentasclaras.domain.repository

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de grupos.
 *
 * Room es la fuente de verdad: [observeGroups] y [observeMembers] emiten desde la
 * caché local y la red solo la rellena (paginación gestionada por la
 * implementación con `Paging 3` + `RemoteMediator`).
 */
interface GroupRepository {

    /** Listado paginado de los grupos del usuario, servido desde la caché local. */
    fun observeGroups(): Flow<PagingData<Group>>

    /** Observa un grupo concreto de la caché local. */
    fun observeGroup(groupId: String): Flow<Group?>

    /** Recarga el detalle del grupo desde la red y actualiza la caché. */
    suspend fun refreshGroup(groupId: String): AppResult<Group>

    /** Crea un grupo; al volver ya está en la caché local. */
    suspend fun createGroup(name: String, description: String?, currency: String): AppResult<Group>

    /** Observa los miembros activos/invitados de un grupo desde la caché local. */
    fun observeMembers(groupId: String): Flow<List<GroupMember>>

    /** Recarga los miembros del grupo desde la red y actualiza la caché. */
    suspend fun refreshMembers(groupId: String): AppResult<Unit>

    /** Añade un miembro ya registrado por email. */
    suspend fun addMember(groupId: String, email: String, role: GroupRole?): AppResult<GroupMember>

    /** Cambia el rol de un miembro. */
    suspend fun changeMemberRole(groupId: String, userId: String, role: GroupRole): AppResult<GroupMember>

    /** Expulsa (o deja salir) a un miembro. */
    suspend fun removeMember(groupId: String, userId: String): AppResult<Unit>

    /** Genera una invitación nueva para el grupo. */
    suspend fun createInvite(groupId: String): AppResult<Invite>

    /** Acepta una invitación por código y devuelve el grupo al que se ha unido. */
    suspend fun acceptInvite(code: String): AppResult<Group>
}
