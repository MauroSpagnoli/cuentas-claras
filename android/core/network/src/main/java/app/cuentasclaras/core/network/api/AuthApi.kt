package app.cuentasclaras.core.network.api

import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.LoginRequestDto
import app.cuentasclaras.core.network.dto.RefreshRequestDto
import app.cuentasclaras.core.network.dto.RegisterRequestDto
import app.cuentasclaras.core.network.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<AuthTokensDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthTokensDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<AuthTokensDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequestDto): Response<Unit>

    @GET("users/me")
    suspend fun currentUser(): Response<UserDto>
}
