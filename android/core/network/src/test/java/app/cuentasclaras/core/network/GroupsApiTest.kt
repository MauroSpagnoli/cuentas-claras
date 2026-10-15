package app.cuentasclaras.core.network

import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.dto.AddMemberRequestDto
import app.cuentasclaras.core.network.dto.CreateGroupRequestDto
import app.cuentasclaras.core.network.dto.UpdateMemberRequestDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class GroupsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GroupsApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroupsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list sends cursor and parses the page`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "items": [
                    {
                      "id": "g-1",
                      "name": "Piso",
                      "description": "Gastos comunes",
                      "currency": "EUR",
                      "inviteCode": "ABCDEFGHIJ",
                      "memberCount": 3,
                      "createdAt": "2026-01-01T10:00:00Z",
                      "version": 2
                    }
                  ],
                  "nextCursor": "cursor-2",
                  "hasMore": true
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.list(limit = 20, cursor = null) }

        val page = (result as AppResult.Success).value
        assertEquals(1, page.items.size)
        assertEquals("Piso", page.items.first().name)
        assertEquals(3, page.items.first().memberCount)
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/groups?limit=20", recorded.path)
    }

    @Test
    fun `list tolerates a page without cursor nor description`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"items":[{"id":"g-1","name":"Piso","currency":"EUR","memberCount":1}],"hasMore":false}""",
            ),
        )

        val result = safeApiCall(json) { api.list() }

        val page = (result as AppResult.Success).value
        assertEquals(null, page.items.single().description)
        assertEquals(null, page.nextCursor)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `create posts the group payload`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"id":"g-9","name":"Viaje","currency":"USD","memberCount":1,"version":0}""",
                code = 201,
            ),
        )

        val result = safeApiCall(json) {
            api.create(CreateGroupRequestDto(name = "Viaje", description = null, currency = "USD"))
        }

        assertEquals("g-9", (result as AppResult.Success).value.id)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/groups", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"name\":\"Viaje\""))
        assertTrue(body.contains("\"currency\":\"USD\""))
    }

    @Test
    fun `members returns the full list`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                [
                  {
                    "userId": "u-1",
                    "displayName": "Ana",
                    "email": "ana@example.com",
                    "role": "OWNER",
                    "status": "ACTIVE",
                    "joinedAt": "2026-01-01T10:00:00Z"
                  },
                  {
                    "userId": "u-2",
                    "displayName": "Beto",
                    "email": "beto@example.com",
                    "role": "MEMBER",
                    "status": "INVITED"
                  }
                ]
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.members("g-1") }

        val members = (result as AppResult.Success).value
        assertEquals(2, members.size)
        assertEquals("ana@example.com", members[0].email)
        assertEquals("INVITED", members[1].status)
        assertEquals("/api/v1/groups/g-1/members", server.takeRequest().path)
    }

    @Test
    fun `add member posts email and role`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"userId":"u-3","displayName":"Caro","email":"caro@example.com","role":"ADMIN","status":"ACTIVE"}""",
                code = 201,
            ),
        )

        val result = safeApiCall(json) {
            api.addMember("g-1", AddMemberRequestDto(email = "caro@example.com", role = "ADMIN"))
        }

        assertEquals("ADMIN", (result as AppResult.Success).value.role)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/groups/g-1/members", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"email\":\"caro@example.com\""))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
    }

    @Test
    fun `add member without role omits the field`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"userId":"u-4","displayName":"Dan","email":"dan@example.com","role":"MEMBER","status":"ACTIVE"}""",
                code = 201,
            ),
        )

        safeApiCall(json) { api.addMember("g-1", AddMemberRequestDto(email = "dan@example.com")) }

        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("\"role\""))
    }

    @Test
    fun `change member role patches the member`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"userId":"u-2","displayName":"Beto","email":"beto@example.com","role":"ADMIN","status":"ACTIVE"}""",
            ),
        )

        val result = safeApiCall(json) {
            api.changeMemberRole("g-1", "u-2", UpdateMemberRequestDto(role = "ADMIN"))
        }

        assertEquals("ADMIN", (result as AppResult.Success).value.role)

        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("/api/v1/groups/g-1/members/u-2", recorded.path)
    }

    @Test
    fun `remove member accepts an empty 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = safeUnitApiCall(json) { api.removeMember("g-1", "u-2") }

        assertTrue(result is AppResult.Success)
        assertEquals("/api/v1/groups/g-1/members/u-2", server.takeRequest().path)
    }

    @Test
    fun `create invite parses code and url`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "code": "ABCDEFGHIJ",
                  "url": "https://cuentas-claras.app/invite/ABCDEFGHIJ",
                  "expiresAt": "2026-01-08T10:00:00Z"
                }
                """.trimIndent(),
                code = 201,
            ),
        )

        val result = safeApiCall(json) { api.createInvite("g-1") }

        val invite = (result as AppResult.Success).value
        assertEquals("ABCDEFGHIJ", invite.code)
        assertEquals("https://cuentas-claras.app/invite/ABCDEFGHIJ", invite.url)
        assertEquals("/api/v1/groups/g-1/invites", server.takeRequest().path)
    }

    @Test
    fun `accept invite returns the group`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"id":"g-1","name":"Piso","currency":"EUR","memberCount":4,"version":1}""",
            ),
        )

        val result = safeApiCall(json) { api.acceptInvite("ABCDEFGHIJ") }

        assertEquals("g-1", (result as AppResult.Success).value.id)
        assertEquals("/api/v1/invites/ABCDEFGHIJ/accept", server.takeRequest().path)
    }

    @Test
    fun `conflict response maps to Conflict with detail`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"title":"Conflict","status":409,"detail":"Ya perteneces a este grupo","code":"ALREADY_MEMBER"}""",
                code = 409,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) { api.acceptInvite("ABCDEFGHIJ") }

        val error = (result as AppResult.Failure).error
        assertEquals(AppError.Conflict("Ya perteneces a este grupo"), error)
    }

    @Test
    fun `not found maps to NotFound`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"title":"Not Found","status":404,"detail":"El grupo no existe"}""",
                code = 404,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) { api.get("missing") }

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
    }

    private fun jsonResponse(
        body: String,
        code: Int = 200,
        contentType: String = "application/json",
    ): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", contentType)
        .setBody(body)
}
