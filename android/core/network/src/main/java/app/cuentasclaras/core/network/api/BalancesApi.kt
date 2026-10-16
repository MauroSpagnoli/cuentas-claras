package app.cuentasclaras.core.network.api

import app.cuentasclaras.core.network.dto.BalancesDto
import app.cuentasclaras.core.network.dto.CreateSettlementRequestDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.SettlementDto
import app.cuentasclaras.core.network.dto.SettlementPlanDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BalancesApi {

    /** Balances netos por miembro y deudas mínimas por pares de un grupo. */
    @GET("groups/{groupId}/balances")
    suspend fun balances(@Path("groupId") groupId: String): Response<BalancesDto>

    /** Plan de liquidación: mínimo de transferencias para dejar el grupo a cero. */
    @GET("groups/{groupId}/settlement-plan")
    suspend fun settlementPlan(@Path("groupId") groupId: String): Response<SettlementPlanDto>

    /**
     * Historia de pagos del grupo, paginada por cursor: el backend devuelve los
     * pagos más recientes primero y `nextCursor` cuando quedan más.
     */
    @GET("groups/{groupId}/settlements")
    suspend fun settlements(
        @Path("groupId") groupId: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): Response<PageDto<SettlementDto>>

    /**
     * Registra un pago entre dos miembros. La cabecera `Idempotency-Key` la añade
     * `IdempotencyInterceptor`, así que un reintento no duplica el pago.
     */
    @POST("groups/{groupId}/settlements")
    suspend fun registerSettlement(
        @Path("groupId") groupId: String,
        @Body body: CreateSettlementRequestDto,
    ): Response<SettlementDto>
}
