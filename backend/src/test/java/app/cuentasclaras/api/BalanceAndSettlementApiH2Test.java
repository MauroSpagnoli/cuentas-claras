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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de balances, plan de liquidacion y pagos sobre H2 en memoria (perfil
 * dev). No requiere Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BalanceAndSettlementApiH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void balancesPlanYPagos() throws Exception {
        String ana = register("ana.balances@example.com", "Ana");
        String beto = register("beto.balances@example.com", "Beto");
        String carla = register("carla.balances@example.com", "Carla");
        String dora = register("dora.balances@example.com", "Dora");

        String groupId = createGroup(ana, "Piso balances");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        String carlaId = tokenToId(carla);
        addMember(ana, groupId, "beto.balances@example.com");
        addMember(ana, groupId, "carla.balances@example.com");

        // Ana paga 3000 a partes iguales -> Ana +2000, Beto -1000, Carla -1000.
        createExpense(ana, groupId, equalExpense("Cena", 3000, anaId, "2026-04-01", anaId, betoId, carlaId));

        // sin token -> 401
        mockMvc.perform(get(balancesUrl(groupId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // no miembro -> 403
        mockMvc.perform(get(balancesUrl(groupId)).header("Authorization", "Bearer " + dora))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // balances iniciales
        JsonNode balances = readJson(mockMvc.perform(get(balancesUrl(groupId))
                        .header("Authorization", "Bearer " + beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.pairs.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        Map<String, Long> nets = netsByUser(balances);
        assertThat(nets.get(anaId)).isEqualTo(2000);
        assertThat(nets.get(betoId)).isEqualTo(-1000);
        assertThat(nets.get(carlaId)).isEqualTo(-1000);
        assertThat(nets.values().stream().mapToLong(Long::longValue).sum()).isZero();
        assertThat(pairs(balances)).containsExactlyInAnyOrder(
                betoId + "->" + anaId + ":1000",
                carlaId + "->" + anaId + ":1000");

        // plan de liquidacion
        JsonNode plan = readJson(mockMvc.perform(get(planUrl(groupId))
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.algorithm").value("EXACT"))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(planApplicationClears(nets, plan.get("steps"))).isTrue();

        // Beto le devuelve 1000 a Ana (con idempotencia)
        String body = settlementJson(betoId, anaId, 1000, "2026-04-02", null);
        String first = mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + beto)
                        .header("Idempotency-Key", "pago-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromUserId").value(betoId))
                .andExpect(jsonPath("$.toUserId").value(anaId))
                .andExpect(jsonPath("$.amountMinor").value(1000))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn().getResponse().getContentAsString();
        String settlementId = readJson(first).get("id").asText();

        // reintento con la misma clave -> misma respuesta y sin duplicar
        String second = mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + beto)
                        .header("Idempotency-Key", "pago-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(readJson(second).get("id").asText()).isEqualTo(settlementId);
        mockMvc.perform(get(settlementsUrl(groupId)).header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.items.length()").value(1));

        // los balances reflejan el pago
        JsonNode after = readJson(mockMvc.perform(get(balancesUrl(groupId))
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        Map<String, Long> afterNets = netsByUser(after);
        assertThat(afterNets.get(anaId)).isEqualTo(1000);
        assertThat(afterNets.get(betoId)).isZero();
        assertThat(afterNets.get(carlaId)).isEqualTo(-1000);
        assertThat(pairs(after)).containsExactly(carlaId + "->" + anaId + ":1000");
    }

    @Test
    void validacionesDePagos() throws Exception {
        String ana = register("ana.pagos@example.com", "Ana");
        String beto = register("beto.pagos@example.com", "Beto");
        String dora = register("dora.pagos@example.com", "Dora");

        String groupId = createGroup(ana, "Pagos");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);
        String doraId = tokenToId(dora);
        addMember(ana, groupId, "beto.pagos@example.com");

        // pagador y receptor iguales -> 400
        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(anaId, anaId, 500, "2026-04-02", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // pagador que no es miembro -> 422 INVALID_PARTICIPANT
        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(doraId, anaId, 500, "2026-04-02", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PARTICIPANT"));

        // importe no positivo -> 400
        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(betoId, anaId, 0, "2026-04-02", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // moneda distinta a la del grupo -> 400
        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(betoId, anaId, 500, "2026-04-02", "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // un no miembro no puede registrar pagos -> 403
        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + dora)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(betoId, anaId, 500, "2026-04-02", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // cursor invalido -> 400
        mockMvc.perform(get(settlementsUrl(groupId))
                        .param("cursor", "no-es-base64!!")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static boolean planApplicationClears(Map<String, Long> nets, JsonNode steps) {
        Map<String, Long> remaining = new HashMap<>(nets);
        for (JsonNode step : steps) {
            long amount = step.get("amountMinor").asLong();
            remaining.merge(step.get("fromUserId").asText(), amount, Long::sum);
            remaining.merge(step.get("toUserId").asText(), -amount, Long::sum);
        }
        return remaining.values().stream().allMatch(value -> value == 0);
    }

    private static Map<String, Long> netsByUser(JsonNode balances) {
        Map<String, Long> nets = new HashMap<>();
        for (JsonNode member : balances.get("members")) {
            nets.put(member.get("userId").asText(), member.get("netMinor").asLong());
        }
        return nets;
    }

    private static Set<String> pairs(JsonNode balances) {
        Set<String> pairs = new HashSet<>();
        for (JsonNode pair : balances.get("pairs")) {
            pairs.add(pair.get("fromUserId").asText() + "->" + pair.get("toUserId").asText()
                    + ":" + pair.get("amountMinor").asLong());
        }
        return pairs;
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String balancesUrl(String groupId) {
        return "/api/v1/groups/" + groupId + "/balances";
    }

    private String planUrl(String groupId) {
        return "/api/v1/groups/" + groupId + "/settlement-plan";
    }

    private String settlementsUrl(String groupId) {
        return "/api/v1/groups/" + groupId + "/settlements";
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
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private static String equalExpense(String description, long amount, String paidBy, String date,
                                       String... userIds) {
        String shares = Arrays.stream(userIds)
                .map(userId -> "{\"userId\":\"" + userId + "\"}")
                .collect(Collectors.joining(","));
        return "{\"description\":\"" + description + "\",\"amountMinor\":" + amount + ","
                + "\"paidByUserId\":\"" + paidBy + "\",\"splitType\":\"EQUAL\","
                + "\"expenseDate\":\"" + date + "\",\"shares\":[" + shares + "]}";
    }

    private static String settlementJson(String fromUserId, String toUserId, long amountMinor, String date,
                                         String currency) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"fromUserId\":\"").append(fromUserId).append("\",");
        json.append("\"toUserId\":\"").append(toUserId).append("\",");
        json.append("\"amountMinor\":").append(amountMinor).append(",");
        if (currency != null) {
            json.append("\"currency\":\"").append(currency).append("\",");
        }
        json.append("\"settledOn\":\"").append(date).append("\"}");
        return json.toString();
    }
}
