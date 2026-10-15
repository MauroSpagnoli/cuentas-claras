package app.cuentasclaras

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.runner.AndroidJUnit4
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.ExpenseDao
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.core.database.migration.MIGRATION_1_2
import app.cuentasclaras.core.database.migration.MIGRATION_2_3
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación end-to-end de gastos contra el backend real: alta con los cuatro
 * tipos de reparto, detalle servido desde Room, edición, conflicto de versión,
 * listado paginado y persistencia en disco.
 *
 * Si el backend no está accesible, el test se marca como omitido en lugar de
 * fallar, de modo que `connectedDebugAndroidTest` sigue siendo seguro sin
 * backend levantado.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExpensesEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var expenseDao: ExpenseDao

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
    fun create_edit_delete_caches_and_survives_a_restart() = runBlocking {
        val (group, ownerId) = newGroupWithOwner()
        val today = todayIso()

        // 1) Alta EQUAL contra el backend.
        val created = expenseRepository.createExpense(
            group.id,
            draft("Cena", 4250, today, listOf(ExpenseShareInput(ownerId))),
        )
        assertTrue("createExpense falló: $created", created is AppResult.Success)
        val expense = (created as AppResult.Success).value
        assertEquals("Cena", expense.description)
        assertEquals(4250L, expense.amountMinor)

        // 2) Room es la fuente de verdad: el gasto recién creado queda cacheado.
        val cached = expenseDao.observeById(expense.id).first()
        assertNotNull("el gasto no se persistió en Room", cached)
        assertEquals("Cena", cached!!.description)

        // 3) El detalle servido desde Room llega con su reparto resuelto.
        val detail = expenseRepository.observeExpense(expense.id).first()
        assertNotNull(detail)
        assertEquals(1, detail!!.shares.size)
        assertEquals(4250L, detail.shares.sumOf { it.amountMinor })

        // 4) Edición (PATCH) y reflejo inmediato en la caché.
        val updated = expenseRepository.updateExpense(
            expense.id,
            draft("Cena y postre", 5000, today, listOf(ExpenseShareInput(ownerId)), version = expense.version),
        )
        assertTrue("updateExpense falló: $updated", updated is AppResult.Success)
        assertEquals("Cena y postre", (updated as AppResult.Success).value.description)
        assertEquals("Cena y postre", expenseDao.observeById(expense.id).first()?.description)

        // 5) El listado paginado se rellena desde la red y sirve desde Room.
        val snapshot = withContext(Dispatchers.IO) { expenseRepository.observeExpenses(group.id).asSnapshot() }
        assertTrue("el listado no contiene el gasto", snapshot.any { it.id == expense.id })

        // 6) Simulación de reinicio: se abre de nuevo el mismo fichero SQLite.
        val reopened = Room.databaseBuilder(context, CuentasClarasDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            val persisted = reopened.expenseDao().observeById(expense.id).first()
            assertNotNull("el gasto no sobrevivió al reinicio", persisted)
            assertEquals("Cena y postre", persisted!!.description)
            assertEquals(1, reopened.expenseDao().observeShares(expense.id).first().size)
        } finally {
            reopened.close()
        }

        // 7) Borrado lógico: la caché se limpia.
        val deleted = expenseRepository.deleteExpense(expense.id)
        assertTrue("deleteExpense falló: $deleted", deleted is AppResult.Success)
        assertNull("el gasto sigue cacheado tras el borrado", expenseDao.observeById(expense.id).first())
    }

    @Test
    fun creates_expenses_with_each_split_type() = runBlocking {
        val (group, ownerId) = newGroupWithOwner()
        val today = todayIso()

        val drafts = listOf(
            SplitType.EQUAL to listOf(ExpenseShareInput(ownerId)),
            SplitType.EXACT to listOf(ExpenseShareInput(ownerId, amountMinor = 3000L)),
            SplitType.PERCENTAGE to listOf(ExpenseShareInput(ownerId, percentage = BigDecimal("100"))),
            SplitType.SHARES to listOf(ExpenseShareInput(ownerId, weight = BigDecimal("1"))),
        )

        drafts.forEach { (splitType, shares) ->
            val result = expenseRepository.createExpense(
                group.id,
                draft("Gasto $splitType", 3000, today, shares, splitType = splitType),
            )
            assertTrue("createExpense($splitType) falló: $result", result is AppResult.Success)
            val expense = (result as AppResult.Success).value
            assertEquals(splitType, expense.splitType)
            assertEquals(1, expense.shares.size)
            assertEquals(3000L, expense.shares.sumOf { it.amountMinor })
        }

        val snapshot = withContext(Dispatchers.IO) { expenseRepository.observeExpenses(group.id).asSnapshot() }
        assertEquals(4, snapshot.size)
    }

    @Test
    fun editing_with_a_stale_version_reports_a_conflict() = runBlocking {
        val (group, ownerId) = newGroupWithOwner()
        val today = todayIso()

        val created = expenseRepository.createExpense(
            group.id,
            draft("Taxi", 1200, today, listOf(ExpenseShareInput(ownerId))),
        )
        val expense = (created as AppResult.Success).value

        // Edición correcta: sube la versión.
        expenseRepository.updateExpense(
            expense.id,
            draft("Taxi", 1200, today, listOf(ExpenseShareInput(ownerId)), version = expense.version),
        )

        // Reintento con la versión antigua: el backend responde 409.
        val stale = expenseRepository.updateExpense(
            expense.id,
            draft("Taxi", 1300, today, listOf(ExpenseShareInput(ownerId)), version = expense.version),
        )
        val error = (stale as AppResult.Failure).error
        assertTrue("esperaba un conflicto de versión, fue $error", error is AppError.Conflict)
    }

    private suspend fun newGroupWithOwner(): Pair<Group, String> {
        val email = "e2e-expenses-${System.currentTimeMillis()}@example.com"
        assertTrue(
            "register falló",
            authRepository.register(email, "secret123", "Gastos E2E", "EUR") is AppResult.Success,
        )
        val created = groupRepository.createGroup("Piso Gastos", "", "EUR")
        assertTrue("createGroup falló: $created", created is AppResult.Success)
        val group = (created as AppResult.Success).value

        val members = groupRepository.refreshMembers(group.id)
        assertTrue("refreshMembers falló: $members", members is AppResult.Success)
        val owner = groupDao.observeMembers(group.id).first().first { it.status == "ACTIVE" }
        return group to owner.userId
    }

    private fun draft(
        description: String,
        amountMinor: Long,
        date: String,
        shares: List<ExpenseShareInput>,
        splitType: SplitType = SplitType.EQUAL,
        version: Long? = null,
    ) = ExpenseDraft(
        description = description,
        amountMinor = amountMinor,
        paidByUserId = shares.first().userId,
        splitType = splitType,
        expenseDate = date,
        shares = shares,
        version = version,
    )

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

    private fun isBackendReachable(): Boolean = try {
        val connection = URL("http://$HOST:$PORT/actuator/health").openConnection() as HttpURLConnection
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
        const val DATABASE_NAME = "cuentas-claras.db"
    }
}
