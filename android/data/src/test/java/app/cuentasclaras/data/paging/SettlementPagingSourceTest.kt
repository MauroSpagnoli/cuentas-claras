package app.cuentasclaras.data.paging

import androidx.paging.PagingSource
import app.cuentasclaras.core.network.api.BalancesApi
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.SettlementDto
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.result.AppResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class SettlementPagingSourceTest {

    private val api = mockk<BalancesApi>()
    private val json = Json { ignoreUnknownKeys = true }
    private val source = SettlementPagingSource("g-1", api, json, UnconfinedTestDispatcher())

    private fun dto(id: String) = SettlementDto(
        id = id,
        groupId = "g-1",
        fromUserId = "u-2",
        toUserId = "u-1",
        amountMinor = 1500,
        currency = "EUR",
        settledOn = "2026-02-01",
        note = "Bizum",
        createdAt = "2026-02-01T10:00:00Z",
    )

    @Test
    fun `load maps the page and keeps the cursor while there is more`() = runTest {
        coEvery { api.settlements("g-1", SettlementPagingSource.PAGE_SIZE, null) } returns Response.success(
            PageDto(items = listOf(dto("s-1")), nextCursor = "cursor-2", hasMore = true),
        )

        val page = source.load(refresh()) as PagingSource.LoadResult.Page<String, Settlement>

        assertEquals(listOf("s-1"), page.data.map { it.id })
        assertEquals("u-2", page.data.single().fromUserId)
        assertEquals("cursor-2", page.nextKey)
        assertNull(page.prevKey)
    }

    @Test
    fun `load stops paginating at the last page`() = runTest {
        coEvery {
            api.settlements("g-1", SettlementPagingSource.PAGE_SIZE, "cursor-2")
        } returns Response.success(
            PageDto(items = listOf(dto("s-2")), nextCursor = null, hasMore = false),
        )

        val page = source.load(refresh("cursor-2")) as PagingSource.LoadResult.Page<String, Settlement>

        assertEquals(listOf("s-2"), page.data.map { it.id })
        assertNull(page.nextKey)
    }

    @Test
    fun `load wraps the failure in an api exception`() = runTest {
        coEvery { api.settlements("g-1", SettlementPagingSource.PAGE_SIZE, null) } returns Response.error(
            404,
            """{"title":"Not Found","status":404}"""
                .toResponseBody("application/problem+json".toMediaType()),
        )

        val error = source.load(refresh()) as PagingSource.LoadResult.Error

        assertEquals(AppError.NotFound, (error.throwable as ApiException).error)
    }

    private fun refresh(key: String? = null): PagingSource.LoadParams<String> = PagingSource.LoadParams.Refresh(
        key = key,
        loadSize = SettlementPagingSource.PAGE_SIZE,
        placeholdersEnabled = false,
    )
}
