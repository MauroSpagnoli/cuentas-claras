package app.cuentasclaras.core.network.api

import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ExpensesApi {

    /** Listado paginado por cursor de los gastos de un grupo. */
    @GET("groups/{groupId}/expenses")
    suspend fun list(
        @Path("groupId") groupId: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): Response<PageDto<ExpenseDto>>

    @POST("groups/{groupId}/expenses")
    suspend fun create(
        @Path("groupId") groupId: String,
        @Body body: CreateExpenseRequestDto,
    ): Response<ExpenseDto>

    @GET("expenses/{expenseId}")
    suspend fun get(@Path("expenseId") expenseId: String): Response<ExpenseDto>

    @PATCH("expenses/{expenseId}")
    suspend fun update(
        @Path("expenseId") expenseId: String,
        @Body body: UpdateExpenseRequestDto,
    ): Response<ExpenseDto>

    /** Baja lógica del gasto: responde `204 No Content`. */
    @DELETE("expenses/{expenseId}")
    suspend fun delete(@Path("expenseId") expenseId: String): Response<Unit>
}
