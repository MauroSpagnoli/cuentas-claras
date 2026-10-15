package app.cuentasclaras

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.core.database.migration.MIGRATION_1_2
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación end-to-end de grupos contra el backend real: listado paginado
 * servido desde Room, creación, miembros, invitación y persistencia en disco.
 *
 * Si el backend no está accesible, el test se marca como omitido en lugar de
 * fallar, de modo que `connectedDebugAndroidTest` sigue siendo seguro sin
 * backend levantado.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GroupsEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var groupDao: GroupDao

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue("Backend no accesible en $HOST:$PORT", isBackendReachable())
    }

    @Test
    fun create_group_caches_it_in_room_and_survives_a_restart() = runBlocking {
        val email = "e2e-groups-${System.currentTimeMillis()}@example.com"
        val register = authRepository.register(email, "secret123", "Grupos E2E", "EUR")
        assertTrue("register falló: $register", register is AppResult.Success)

        // 1) Creación contra el backend.
        val created = groupRepository.createGroup(name = GROUP_NAME, description = "Alquiler", currency = "EUR")
        assertTrue("createGroup falló: $created", created is AppResult.Success)
        val group = (created as AppResult.Success).value
        assertEquals(GROUP_NAME, group.name)

        // 2) Room es la fuente de verdad: el grupo creado queda cacheado.
        val cached = groupDao.observeById(group.id).first()
        assertNotNull("el grupo no se persistió en Room", cached)
        assertEquals(GROUP_NAME, cached!!.name)

        // 3) Los miembros se cachean al abrir el detalle (sync desde la red).
        val refreshedMembers = groupRepository.refreshMembers(group.id)
        assertTrue("refreshMembers falló: $refreshedMembers", refreshedMembers is AppResult.Success)
        val members = groupDao.observeMembers(group.id).first()
        assertEquals(1, members.size)
        assertEquals("OWNER", members.first().role)
        assertEquals("ACTIVE", members.first().status)

        // 4) El listado paginado se rellena desde la red y sirve desde Room.
        val snapshot = withContext(Dispatchers.IO) { groupRepository.observeGroups().asSnapshot() }
        assertTrue("la lista no contiene el grupo creado", snapshot.any { it.id == group.id })

        // 5) Invitación real del backend (código corto + URL).
        val invite = groupRepository.createInvite(group.id)
        assertTrue("createInvite falló: $invite", invite is AppResult.Success)
        val code = (invite as AppResult.Success).value.code
        assertEquals(10, code.length)

        // 6) Simulación de reinicio: se vuelve a abrir el mismo fichero SQLite
        //    con una instancia nueva de Room, sin caché en memoria de por medio.
        val reopened = Room.databaseBuilder(context, CuentasClarasDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val persisted = reopened.groupDao().observeById(group.id).first()
            assertNotNull("el grupo no sobrevivió al reinicio", persisted)
            assertEquals(group.id, persisted!!.id)
            assertEquals(GROUP_NAME, persisted.name)
        } finally {
            reopened.close()
        }

        // 6) Otro usuario acepta la invitación por código y pasa a ser miembro.
        val guestEmail = "e2e-guest-${System.currentTimeMillis()}@example.com"
        assertTrue(
            "register del invitado falló",
            authRepository.register(guestEmail, "secret123", "Invitado E2E", "EUR") is AppResult.Success,
        )
        val accepted = groupRepository.acceptInvite(code)
        assertTrue("acceptInvite falló: $accepted", accepted is AppResult.Success)
        assertEquals(group.id, (accepted as AppResult.Success).value.id)

        assertTrue(
            "refreshMembers del invitado falló",
            groupRepository.refreshMembers(group.id) is AppResult.Success,
        )
        assertEquals(
            "el invitado no quedó en la caché de miembros",
            true,
            groupDao.observeMembers(group.id).first().any { it.email == guestEmail },
        )
    }

    @Test
    fun duplicate_group_names_are_allowed_and_listed() = runBlocking {
        val email = "e2e-list-${System.currentTimeMillis()}@example.com"
        assertTrue(
            "register falló",
            authRepository.register(email, "secret123", "Lista E2E", "EUR") is AppResult.Success,
        )

        val first = groupRepository.createGroup("Viaje", "", "EUR")
        val second = groupRepository.createGroup("Viaje", "", "EUR")
        assertTrue(first is AppResult.Success)
        assertTrue(second is AppResult.Success)

        val snapshot = withContext(Dispatchers.IO) { groupRepository.observeGroups().asSnapshot() }
        val viaje = snapshot.filter { it.name == "Viaje" }
        assertEquals(2, viaje.size)
    }

    private fun isBackendReachable(): Boolean = try {
        val connection = URL("http://$HOST:$PORT/actuator/health")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.responseCode in 200..299
    } catch (throwable: Throwable) {
        false
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val PORT = 8080
        const val TIMEOUT_MILLIS = 3_000
        const val GROUP_NAME = "Piso E2E"
        const val DATABASE_NAME = "cuentas-claras.db"
    }
}
