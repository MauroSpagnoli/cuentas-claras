package app.cuentasclaras.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de gastos sobre H2 en memoria (perfil dev): reparto, autorizacion,
 * idempotencia, paginacion y filtros. No requiere Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ExpenseApiH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flujoCompletoDeGastos() throws Exception {
        String ana = register("ana.gastos@example.com", "Ana");
        String beto = register("beto.gastos@example.com", "Beto");
        String carla = register("carla.gastos@example.com", "Carla");
        String dora = register("dora.gastos@example.com", "Dora");

        String groupId = createGroup(ana, "Piso gastos");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        String carlaId = tokenToId(carla);
        addMember(ana, groupId, "beto.gastos@example.com");
        addMember(ana, groupId, "carla.gastos@example.com");

        // sin token -> 401
        mockMvc.perform(get(expensesUrl(groupId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // no miembro -> 403
        mockMvc.perform(get(expensesUrl(groupId)).header("Authorization", "Bearer " + dora))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // Ana crea un gasto EQUAL de 3000 entre los tres
        String created = mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equalExpense("Cena", 3000, anaId, "2026-04-01", anaId, betoId, carlaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountMinor").value(3000))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.paidByUserId").value(anaId))
                .andExpect(jsonPath("$.shares.length()").value(3))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode expense = objectMapper.readTree(created);
        String expenseId = expense.get("id").asText();
        assertThat(sharesSum(expense)).isEqualTo(3000);
        assertThat(expense.get("shares").get(0).get("amountMinor").asLong()).isEqualTo(1000);

        // detalle visible para cualquier miembro
        mockMvc.perform(get("/api/v1/expenses/" + expenseId).header("Authorization", "Bearer " + beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares.length()").value(3));

        // listado del grupo
        mockMvc.perform(get(expensesUrl(groupId)).header("Authorization", "Bearer " + beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        // Beto es miembro pero ni creador ni ADMIN -> no puede editar
        mockMvc.perform(patch("/api/v1/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Otra\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));

        // la creadora edita y sube la version
        mockMvc.perform(patch("/api/v1/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Cena actualizada\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Cena actualizada"))
                .andExpect(jsonPath("$.version").value(1));

        // version obsoleta -> 409
        mockMvc.perform(patch("/api/v1/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Otra vez\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // un no miembro no ve el detalle
        mockMvc.perform(get("/api/v1/expenses/" + expenseId).header("Authorization", "Bearer " + dora))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // gasto inexistente -> 404
        mockMvc.perform(get("/api/v1/expenses/22222222-2222-2222-2222-222222222222")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPENSE_NOT_FOUND"));

        // borrado logico por la creadora
        mockMvc.perform(delete("/api/v1/expenses/" + expenseId).header("Authorization", "Bearer " + ana))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/expenses/" + expenseId).header("Authorization", "Bearer " + ana))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(expensesUrl(groupId)).header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void validacionesDeReparto() throws Exception {
        String ana = register("ana.reparto@example.com", "Ana");
        String beto = register("beto.reparto@example.com", "Beto");
        String dora = register("dora.reparto@example.com", "Dora");

        String groupId = createGroup(ana, "Repartos");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        String doraId = tokenToId(dora);
        addMember(ana, groupId, "beto.reparto@example.com");

        // EXACT cuya suma no cuadra -> 422 SPLIT_MISMATCH
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Exacto mal", 3000, anaId, "EXACT", "2026-04-02", null, null,
                                join(exactShare(anaId, 1000), exactShare(betoId, 1000)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPLIT_MISMATCH"));

        // PERCENTAGE que no suma 100 -> 422 PERCENTAGE_SUM
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Porcentaje mal", 3000, anaId, "PERCENTAGE", "2026-04-02", null, null,
                                join(percentShare(anaId, "60"), percentShare(betoId, "30")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERCENTAGE_SUM"));

        // SHARES 2:1 sobre 1000 -> 666/334 o 667/333 y suma correcta.
        // El centimo sobrante va al menor userId, que en cada ejecucion es
        // aleatorio, por eso solo se admite una de las dos repartos.
        String sharesBody = mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Pesos", 1000, anaId, "SHARES", "2026-04-02", null, null,
                                join(weightShare(anaId, "2"), weightShare(betoId, "1")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode sharesExpense = objectMapper.readTree(sharesBody);
        assertThat(sharesSum(sharesExpense)).isEqualTo(1000);
        assertThat(shareAmounts(sharesExpense)).isIn(List.of(334L, 666L), List.of(333L, 667L));

        // PERCENTAGE 50/50 sobre 3001 -> el resto va a un solo participante
        String percentBody = mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Porcentaje resto", 3001, anaId, "PERCENTAGE", "2026-04-02", null, null,
                                join(percentShare(anaId, "50"), percentShare(betoId, "50")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode percentExpense = objectMapper.readTree(percentBody);
        assertThat(sharesSum(percentExpense)).isEqualTo(3001);
        assertThat(maxShare(percentExpense)).isEqualTo(1501);

        // pagador que no es miembro -> 422 INVALID_PARTICIPANT
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Pagador ajeno", 1000, doraId, "EQUAL", "2026-04-02", null, null,
                                join(equalShare(anaId), equalShare(betoId)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PARTICIPANT"));

        // participante que no es miembro -> 422 INVALID_PARTICIPANT
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Parte ajena", 1000, anaId, "EQUAL", "2026-04-02", null, null,
                                join(equalShare(anaId), equalShare(doraId)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PARTICIPANT"));

        // moneda distinta a la del grupo -> 400 VALIDATION_ERROR
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("Dolares", 1000, anaId, "EQUAL", "2026-04-02", null, "USD",
                                join(equalShare(anaId), equalShare(betoId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // un no miembro no puede crear gastos -> 403
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + dora)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("De fuera", 1000, anaId, "EQUAL", "2026-04-02", null, null,
                                join(equalShare(anaId), equalShare(betoId)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));
    }

    @Test
    void idempotenciaEnCreacionDeGastos() throws Exception {
        String ana = register("ana.idem@example.com", "Ana");
        String beto = register("beto.idem@example.com", "Beto");

        String groupId = createGroup(ana, "Idempotencia");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        addMember(ana, groupId, "beto.idem@example.com");

        String body = equalExpense("Compra", 1500, anaId, "2026-04-03", anaId, betoId);

        String first = mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asText();

        // reintento con la misma clave y cuerpo -> misma respuesta, sin duplicar
        String second = mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second).get("id").asText()).isEqualTo(firstId);

        mockMvc.perform(get(expensesUrl(groupId)).header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(1));

        // misma clave, cuerpo distinto -> 409
        String other = equalExpense("Otra compra", 2000, anaId, "2026-04-03", anaId, betoId);
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .header("Idempotency-Key", "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(other))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // sin clave, dos peticiones iguales crean dos gastos
        mockMvc.perform(post(expensesUrl(groupId))
                .header("Authorization", "Bearer " + ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content(other));
        mockMvc.perform(post(expensesUrl(groupId))
                .header("Authorization", "Bearer " + ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content(other));
        mockMvc.perform(get(expensesUrl(groupId)).header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void paginacionYFiltrosDeGastos() throws Exception {
        String ana = register("ana.filtros@example.com", "Ana");
        String beto = register("beto.filtros@example.com", "Beto");

        String groupId = createGroup(ana, "Filtros");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        addMember(ana, groupId, "beto.filtros@example.com");

        createExpense(ana, groupId, expenseJson("Comida 1", 1000, anaId, "EQUAL", "2026-04-01", "COMIDA", null,
                join(equalShare(anaId), equalShare(betoId))));
        createExpense(ana, groupId, expenseJson("Bus", 500, betoId, "EQUAL", "2026-04-02", "TRANSPORTE", null,
                join(equalShare(anaId), equalShare(betoId))));
        createExpense(ana, groupId, expenseJson("Comida 2", 2000, anaId, "EQUAL", "2026-04-03", "COMIDA", null,
                join(equalShare(anaId), equalShare(betoId))));

        // orden por fecha desc: Comida 2, Bus, Comida 1
        String firstPage = mockMvc.perform(get(expensesUrl(groupId))
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstItems = objectMapper.readTree(firstPage).get("items");
        assertThat(firstItems.get(0).get("description").asText()).isEqualTo("Comida 2");
        assertThat(firstItems.get(1).get("description").asText()).isEqualTo("Bus");

        String nextCursor = objectMapper.readTree(firstPage).get("nextCursor").asText();
        String secondPage = mockMvc.perform(get(expensesUrl(groupId))
                        .param("limit", "2")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(secondPage).get("items").get(0).get("description").asText())
                .isEqualTo("Comida 1");

        // filtro por categoria
        mockMvc.perform(get(expensesUrl(groupId))
                        .param("category", "COMIDA")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(2));

        // filtro por pagador
        mockMvc.perform(get(expensesUrl(groupId))
                        .param("paidByUserId", betoId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Bus"));

        // filtro por rango de fechas
        mockMvc.perform(get(expensesUrl(groupId))
                        .param("from", "2026-04-02")
                        .param("to", "2026-04-02")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Bus"));

        // cursor invalido -> 400
        mockMvc.perform(get(expensesUrl(groupId))
                        .param("cursor", "no-es-base64!!")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String expensesUrl(String groupId) {
        return "/api/v1/groups/" + groupId + "/expenses";
    }

    private String register(String email, String displayName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\","
                                + "\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String tokenToId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createGroup(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currency\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void addMember(String token, String groupId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated());
    }

    private void createExpense(String token, String groupId, String body) throws Exception {
        mockMvc.perform(post(expensesUrl(groupId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private static String equalExpense(String description, long amount, String paidBy, String date,
                                       String... userIds) {
        String shares = Arrays.stream(userIds).map(ExpenseApiH2Test::equalShare).collect(Collectors.joining(","));
        return expenseJson(description, amount, paidBy, "EQUAL", date, null, null, shares);
    }

    private static String expenseJson(String description, long amount, String paidBy, String splitType,
                                      String date, String category, String currency, String shares) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"description\":\"").append(description).append("\",");
        json.append("\"amountMinor\":").append(amount).append(",");
        if (currency != null) {
            json.append("\"currency\":\"").append(currency).append("\",");
        }
        json.append("\"paidByUserId\":\"").append(paidBy).append("\",");
        json.append("\"splitType\":\"").append(splitType).append("\",");
        if (category != null) {
            json.append("\"category\":\"").append(category).append("\",");
        }
        json.append("\"expenseDate\":\"").append(date).append("\",");
        json.append("\"shares\":[").append(shares).append("]}");
        return json.toString();
    }

    private static String equalShare(String userId) {
        return "{\"userId\":\"" + userId + "\"}";
    }

    private static String exactShare(String userId, long amountMinor) {
        return "{\"userId\":\"" + userId + "\",\"amountMinor\":" + amountMinor + "}";
    }

    private static String percentShare(String userId, String percentage) {
        return "{\"userId\":\"" + userId + "\",\"percentage\":" + percentage + "}";
    }

    private static String weightShare(String userId, String weight) {
        return "{\"userId\":\"" + userId + "\",\"weight\":" + weight + "}";
    }

    private static String join(String... parts) {
        return String.join(",", parts);
    }

    private static long sharesSum(JsonNode expense) {
        long total = 0;
        for (JsonNode share : expense.get("shares")) {
            total += share.get("amountMinor").asLong();
        }
        return total;
    }

    private static long maxShare(JsonNode expense) {
        long max = 0;
        for (JsonNode share : expense.get("shares")) {
            max = Math.max(max, share.get("amountMinor").asLong());
        }
        return max;
    }

    /** Importes de las partes ordenados de menor a mayor. */
    private static List<Long> shareAmounts(JsonNode expense) {
        return expense.get("shares").findValues("amountMinor").stream()
                .map(JsonNode::asLong)
                .sorted()
                .toList();
    }
}
