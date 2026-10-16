package app.cuentasclaras.core.network.contract

import app.cuentasclaras.core.network.dto.AddMemberRequestDto
import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.BalancesDto
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.CreateGroupRequestDto
import app.cuentasclaras.core.network.dto.CreateSettlementRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.ExpenseShareDto
import app.cuentasclaras.core.network.dto.ExpenseShareInputDto
import app.cuentasclaras.core.network.dto.GroupDto
import app.cuentasclaras.core.network.dto.GroupMemberDto
import app.cuentasclaras.core.network.dto.InviteDto
import app.cuentasclaras.core.network.dto.LoginRequestDto
import app.cuentasclaras.core.network.dto.MemberBalanceDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.PairwiseDebtDto
import app.cuentasclaras.core.network.dto.ProblemDto
import app.cuentasclaras.core.network.dto.RefreshRequestDto
import app.cuentasclaras.core.network.dto.RegisterRequestDto
import app.cuentasclaras.core.network.dto.SettlementDto
import app.cuentasclaras.core.network.dto.SettlementPlanDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import app.cuentasclaras.core.network.dto.UpdateGroupRequestDto
import app.cuentasclaras.core.network.dto.UpdateMemberRequestDto
import app.cuentasclaras.core.network.dto.UserDto
import app.cuentasclaras.core.network.dto.ValidationErrorDto
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.SplitType
import kotlinx.serialization.SerialName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

/**
 * Test de contrato entre el cliente Android y `docs/api/openapi.yaml`.
 *
 * No genera código: comprueba que los DTO escritos a mano en `core:network` siguen
 * encajando con el contrato (la fuente de verdad del backend) y que los enums del
 * dominio no se han desviado. Así, un cambio en la API que rompa el cliente falla
 * aquí —rápido y sin red— en vez de en producción.
 *
 * El YAML se expone como recurso de test desde `core/network/build.gradle`.
 */
class OpenApiContractTest {

    private val schemas: Map<String, Map<String, Any?>> = readSchemas(loadSpec())

    /** Esquemas del contrato que el cliente sí consume, con su DTO. */
    private val dtoBySchema: Map<String, Class<*>> = mapOf(
        "Problem" to ProblemDto::class.java,
        "ValidationError" to ValidationErrorDto::class.java,
        "RegisterRequest" to RegisterRequestDto::class.java,
        "LoginRequest" to LoginRequestDto::class.java,
        "RefreshRequest" to RefreshRequestDto::class.java,
        "AuthTokens" to AuthTokensDto::class.java,
        "User" to UserDto::class.java,
        "Group" to GroupDto::class.java,
        "CreateGroupRequest" to CreateGroupRequestDto::class.java,
        "UpdateGroupRequest" to UpdateGroupRequestDto::class.java,
        "GroupMember" to GroupMemberDto::class.java,
        "AddMemberRequest" to AddMemberRequestDto::class.java,
        "UpdateMemberRequest" to UpdateMemberRequestDto::class.java,
        "Invite" to InviteDto::class.java,
        "ExpenseShare" to ExpenseShareDto::class.java,
        "ShareInput" to ExpenseShareInputDto::class.java,
        "Expense" to ExpenseDto::class.java,
        "CreateExpenseRequest" to CreateExpenseRequestDto::class.java,
        "UpdateExpenseRequest" to UpdateExpenseRequestDto::class.java,
        "Settlement" to SettlementDto::class.java,
        "CreateSettlementRequest" to CreateSettlementRequestDto::class.java,
        "MemberBalance" to MemberBalanceDto::class.java,
        "PairwiseDebt" to PairwiseDebtDto::class.java,
        "BalancesResponse" to BalancesDto::class.java,
        "SettlementStep" to PairwiseDebtDto::class.java,
        "SettlementPlanResponse" to SettlementPlanDto::class.java,
        "PageExpense" to PageDto::class.java,
        "PageSettlement" to PageDto::class.java,
        "PageActivity" to PageDto::class.java,
    )

    /** Esquemas que el cliente aún no consume; enumerarlos los hace explícitos. */
    private val schemasOutOfScope = setOf("UpdateUserRequest", "ActivityItem")

    /** Campos de un DTO que no viajan en el contrato (documentados a propósito). */
    private val extraDtoFields = mapOf("Expense" to setOf("createdBy"))

    @Test
    fun `todos los esquemas del contrato estan mapeados o declarados fuera de alcance`() {
        val objectSchemas = schemas.filterValues { it["properties"] != null }.keys
        val missing = objectSchemas - dtoBySchema.keys - schemasOutOfScope

        assertTrue(
            "Esquemas del contrato sin DTO ni declaración de fuera de alcance: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `toda propiedad obligatoria del contrato existe en su DTO`() {
        val missing = buildList {
            dtoBySchema.forEach { (schemaName, dto) ->
                val fields = dto.jsonFields()
                schemas.getValue(schemaName).requiredProperties().forEach { property ->
                    if (property !in fields) {
                        add("$schemaName.$property → falta en ${dto.simpleName}")
                    }
                }
            }
        }

        assertTrue(
            "Propiedades obligatorias del contrato sin campo en el DTO:\n${missing.joinToString("\n")}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `cada campo de un DTO existe en el contrato`() {
        val unknown = buildList {
            dtoBySchema.forEach { (schemaName, dto) ->
                val allowed = schemas.getValue(schemaName).propertyNames() +
                    extraDtoFields.getOrDefault(schemaName, emptySet())
                dto.jsonFields().forEach { field ->
                    if (field !in allowed) {
                        add("$schemaName: ${dto.simpleName}.$field no existe en el contrato")
                    }
                }
            }
        }

        assertTrue(
            "Campos de DTO que no existen en el contrato:\n${unknown.joinToString("\n")}",
            unknown.isEmpty(),
        )
    }

    @Test
    fun `los enums de dominio coinciden con los del contrato`() {
        assertEnumMatches("SplitType", SplitType.entries.map { it.name })
        assertEnumMatches("GroupRole", GroupRole.entries.map { it.name })
        assertEnumMatches("MemberStatus", MemberStatus.entries.map { it.name })
    }

    private fun assertEnumMatches(schemaName: String, appValues: List<String>) {
        val specValues = (schemas.getValue(schemaName)["enum"] as List<*>).map { it.toString() }
        assertEquals(
            "El enum '$schemaName' del contrato y el del dominio no coinciden",
            specValues.toSet(),
            appValues.toSet(),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun loadSpec(): Map<String, Any?> {
    val stream = OpenApiContractTest::class.java.getResourceAsStream("/openapi.yaml")
        ?: error("No se encontró /openapi.yaml en los recursos de test")
    return stream.use { Yaml().load(it) as Map<String, Any?> }
}

@Suppress("UNCHECKED_CAST")
private fun readSchemas(spec: Map<String, Any?>): Map<String, Map<String, Any?>> {
    val components = spec["components"] as? Map<String, Any?>
        ?: error("El contrato no declara 'components'")
    return components["schemas"] as? Map<String, Map<String, Any?>>
        ?: error("El contrato no declara 'components.schemas'")
}

private fun Map<String, Any?>.propertyNames(): Set<String> =
    (this["properties"] as? Map<*, *>)?.keys?.map { it.toString() }?.toSet().orEmpty()

private fun Map<String, Any?>.requiredProperties(): Set<String> =
    (this["required"] as? List<*>)?.map { it.toString() }?.toSet().orEmpty()

/** Nombres JSON de los campos del DTO (respetando `@SerialName` si lo hubiera). */
private fun Class<*>.jsonFields(): Set<String> =
    declaredFields
        .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { field -> field.getAnnotation(SerialName::class.java)?.value ?: field.name }
        .toSet()
