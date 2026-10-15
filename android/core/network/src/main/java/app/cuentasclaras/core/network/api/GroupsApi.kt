package app.cuentasclaras.core.network.api

import app.cuentasclaras.core.network.dto.AddMemberRequestDto
import app.cuentasclaras.core.network.dto.CreateGroupRequestDto
import app.cuentasclaras.core.network.dto.GroupDto
import app.cuentasclaras.core.network.dto.GroupMemberDto
import app.cuentasclaras.core.network.dto.InviteDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.UpdateGroupRequestDto
import app.cuentasclaras.core.network.dto.UpdateMemberRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GroupsApi {

    @GET("groups")
    suspend fun list(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): Response<PageDto<GroupDto>>

    @POST("groups")
    suspend fun create(@Body body: CreateGroupRequestDto): Response<GroupDto>

    @GET("groups/{groupId}")
    suspend fun get(@Path("groupId") groupId: String): Response<GroupDto>

    @PATCH("groups/{groupId}")
    suspend fun update(
        @Path("groupId") groupId: String,
        @Body body: UpdateGroupRequestDto,
    ): Response<GroupDto>

    @DELETE("groups/{groupId}")
    suspend fun delete(@Path("groupId") groupId: String): Response<Unit>

    @GET("groups/{groupId}/members")
    suspend fun members(@Path("groupId") groupId: String): Response<List<GroupMemberDto>>

    @POST("groups/{groupId}/members")
    suspend fun addMember(
        @Path("groupId") groupId: String,
        @Body body: AddMemberRequestDto,
    ): Response<GroupMemberDto>

    @PATCH("groups/{groupId}/members/{userId}")
    suspend fun changeMemberRole(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
        @Body body: UpdateMemberRequestDto,
    ): Response<GroupMemberDto>

    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
    ): Response<Unit>

    @POST("groups/{groupId}/invites")
    suspend fun createInvite(@Path("groupId") groupId: String): Response<InviteDto>

    @POST("invites/{code}/accept")
    suspend fun acceptInvite(@Path("code") code: String): Response<GroupDto>
}
