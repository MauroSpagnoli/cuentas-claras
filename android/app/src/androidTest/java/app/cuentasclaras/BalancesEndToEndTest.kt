package app.cuentasclaras

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cuentasclaras.core.database.dao.GroupDao
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.PairwiseDebt
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.repository.BalanceRepository
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación end-to-end de balances contra el backend real: los netos y las
 * deudas por pares coinciden con lo que calcula el servidor a partir de un gasto
 * y, al registrar el pago que sugiere el plan de liquidación, el grupo queda a
 * cero.
 *
 * Si el backend no está accesible, el test se marca como omitido en lugar de
 * fallar, de modo que `connectedDebugAndroidTest` sigue siendo seguro sin
 * backend levantado.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BalancesEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var balanceRepository: BalanceRepository

    @Inject
    lateinit var groupDao: GroupDao

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue("Backend no accesible en $HOST:$PORT", isBackendReachable())
    }

    @Test
    fun a_payment_settles_the_group_and_the_numbers_match_the_backend() = runBlocking {
        val ownerEmail = "e2e-balance-owner-${System.currentTimeMillis()}@example.com"
        val guestEmail = "e2e-balance-guest-${System.currentTimeMillis()}@example.com"
        val today = todayIso()

        // Dueño y grupo.
        assertTrue(
            "register del dueño falló",
            authRepository.register(ownerEmail, "secret123", "Dueño E2E", "EUR") is AppResult.Success,
        )
        val createdGroup = groupRepository.createGroup("Piso Balances", "E2E", "EUR")
        assertTrue("createGroup falló: $createdGroup", createdGroup is AppResult.Success)
        val group = (createdGroup as AppResult.Success).value

        // El invitado entra por código.
        val invite = groupRepository.createInvite(group.id)
        assertTrue("createInvite falló: $invite", invite is AppResult.Success)
        val code = (invite as AppResult.Success).value.code
        assertTrue(
            "register del invitado falló",
            authRepository.register(guestEmail, "secret123", "Invitado E2E", "EUR") is AppResult.Success,
        )
        assertTrue("acceptInvite falló", groupRepository.acceptInvite(code) is AppResult.Success)

        // Volvemos a la sesión del dueño para registrar gasto y pago.
        assertTrue(
            "login del dueño falló",
            authRepository.login(ownerEmail, "secret123") is AppResult.Success,
        )
        assertTrue(
            "refreshMembers falló",
            groupRepository.refreshMembers(group.id) is AppResult.Success,
        )
        val members = groupDao.observeMembers(group.id).first()
        val ownerId = members.first { it.email == ownerEmail }.userId
        val guestId = members.first { it.email == guestEmail }.userId

        // El dueño paga 30,00 € a medias: el invitado le debe 15,00 €.
        val createdExpense = expenseRepository.createExpense(
            group.id,
            ExpenseDraft(
                description = "Cena",
                amountMinor = 3000,
                paidByUserId = ownerId,
                splitType = SplitType.EQUAL,
                expenseDate = today,
                shares = listOf(ExpenseShareInput(ownerId), ExpenseShareInput(guestId)),
            ),
        )
        assertTrue("createExpense falló: $createdExpense", createdExpense is AppResult.Success)

        // 1) Los netos son los que calcula el backend y suman cero.
        val balancesResult = balanceRepository.loadBalances(group.id)
        assertTrue("loadBalances falló: $balancesResult", balancesResult is AppResult.Success)
        val balances = (balancesResult as AppResult.Success).value
        assertEquals("EUR", balances.currency)
        assertEquals(0L, balances.members.sumOf { it.netMinor })
        assertEquals(1500L, balances.members.single { it.userId == ownerId }.netMinor)
        assertEquals(-1500L, balances.members.single { it.userId == guestId }.netMinor)
        assertEquals(
            listOf(PairwiseDebt(fromUserId = guestId, toUserId = ownerId, amountMinor = 1500)),
            balances.pairs,
        )

        // 2) El plan de liquidación propone exactamente esas deudas.
        val planResult = balanceRepository.loadSettlementPlan(group.id)
        assertTrue("loadSettlementPlan falló: $planResult", planResult is AppResult.Success)
        val plan = (planResult as AppResult.Success).value
        assertEquals(balances.pairs, plan.steps)
        assertEquals(plan.steps.size, plan.transactionCount)
        assertTrue("algoritmo inesperado: ${plan.algorithm}", plan.algorithm in ALGORITHMS)

        // 2b) `pairs` de /balances coincide con los `steps` del plan.
        assertEquals(balances.pairs, plan.steps)

        // 2') Registrar el pago que sugiere el plan.
        val step = plan.steps.single()
        val settled = balanceRepository.registerSettlement(
            group.id,
            SettlementDraft(
                fromUserId = step.fromUserId,
                toUserId = step.toUserId,
                amountMinor = step.amountMinor,
                settledOn = today,
                note = "Liquidación E2E",
            ),
        )
        assertTrue("registerSettlement falló: $settled", settled is AppResult.Success)
        val settlement = (settled as AppResult.Success).value
        assertEquals(step.fromUserId, settlement.fromUserId)
        assertEquals(step.toUserId, settlement.toUserId)
        assertEquals(step.amountMinor, settlement.amountMinor)

        // 3) El pago actualiza los balances: todos a cero y sin deudas.
        val afterResult = balanceRepository.loadBalances(group.id)
        assertTrue("loadBalances tras el pago falló: $afterResult", afterResult is AppResult.Success)
        val after = (afterResult as AppResult.Success).value
        assertTrue(
            "deberían estar todos a cero: ${after.members}",
            after.members.all { it.netMinor == 0L },
        )
        assertTrue("no deberían quedar deudas: ${after.pairs}", after.pairs.isEmpty())

        val planAfterResult = balanceRepository.loadSettlementPlan(group.id)
        assertTrue("loadSettlementPlan tras el pago falló: $planAfterResult", planAfterResult is AppResult.Success)
        assertTrue(
            "el plan ya no debería tener transferencias: $planAfterResult",
            (planAfterResult as AppResult.Success).value.steps.isEmpty(),
        )
    }

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
        val ALGORITHMS = setOf("EXACT", "GREEDY")
    }
}
