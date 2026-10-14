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

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feed de actividad del grupo (UC-08) sobre H2 en memoria (perfil dev).
 * Comprueba que los eventos se registran, el orden y la paginacion por cursor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ActivityApiH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void historialReflejaEventosYPagina() throws Exception {
        String ana = register("ana.act@example.com", "Ana");
        String beto = register("beto.act@example.com", "Beto");
        String anaId = tokenToId(ana);
        String betoId = tokenToId(beto);

        String groupId = createGroup(ana, "Actividad");
        addMember(ana, groupId, "beto.act@example.com");
        createExpense(ana, groupId, equalExpense("Cena", 1000, anaId, "2026-04-01", anaId, betoId));

        mockMvc.perform(post(settlementsUrl(groupId))
                        .header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlementJson(betoId, anaId, 500, "2026-04-02")))
                .andExpect(status().isCreated());

        // Ana quita a Beto del grupo -> MEMBER_LEFT
        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + betoId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNoContent());

        JsonNode page = readJson(mockMvc.perform(get(activityUrl(groupId))
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andReturn().getResponse().getContentAsString());
        JsonNode items = page.get("items");

        // orden descendente por occurredAt
        for (int i = 1; i < items.size(); i++) {
            Instant previous = Instant.parse(items.get(i - 1).get("occurredAt").asText());
            Instant current = Instant.parse(items.get(i).get("occurredAt").asText());
            assertThat(previous).isAfterOrEqualTo(current);
        }

        JsonNode expenseEvent = firstOfType(items, "EXPENSE_CREATED");
        assertThat(expenseEvent).isNotNull();
        assertThat(expenseEvent.get("actorUserId").asText()).isEqualTo(anaId);
        assertThat(expenseEvent.get("amountMinor").asLong()).isEqualTo(1000);

        JsonNode settlementEvent = firstOfType(items, "SETTLEMENT_CREATED");
        assertThat(settlementEvent).isNotNull();
        assertThat(settlementEvent.get("actorUserId").asText()).isEqualTo(betoId);
        assertThat(settlementEvent.get("amountMinor").asLong()).isEqualTo(500);

        assertThat(countOfType(items, "MEMBER_JOINED")).isEqualTo(2);
        assertThat(countOfType(items, "MEMBER_LEFT")).isEqualTo(1);

        // paginacion por cursor: 2 + 2 + 1
        JsonNode page1 = readJson(mockMvc.perform(get(activityUrl(groupId))
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString());
        String cursor1 = page1.get("nextCursor").asText();

        JsonNode page2 = readJson(mockMvc.perform(get(activityUrl(groupId))
                        .param("limit", "2")
                        .param("cursor", cursor1)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString());
        String cursor2 = page2.get("nextCursor").asText();

        JsonNode page3 = readJson(mockMvc.perform(get(activityUrl(groupId))
                        .param("limit", "2")
                        .param("cursor", cursor2)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString());

        Set<String> ids = new HashSet<>();
        ids.addAll(idsOf(page1));
        ids.addAll(idsOf(page2));
        ids.addAll(idsOf(page3));
        assertThat(ids).hasSize(5);
    }

    @Test
    void validacionesDeActividad() throws Exception {
        String ana = register("ana.act2@example.com", "Ana");
        String dora = register("dora.act2@example.com", "Dora");
        String groupId = createGroup(ana, "Actividad 2");

        // sin token -> 401
        mockMvc.perform(get(activityUrl(groupId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // no miembro -> 403
        mockMvc.perform(get(activityUrl(groupId)).header("Authorization", "Bearer " + dora))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // cursor invalido -> 400
        mockMvc.perform(get(activityUrl(groupId))
                        .param("cursor", "no-es-base64!!")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static JsonNode firstOfType(JsonNode items, String type) {
        for (JsonNode item : items) {
            if (type.equals(item.get("type").asText())) {
                return item;
            }
        }
        return null;
    }

    private static long countOfType(JsonNode items, String type) {
        long count = 0;
        for (JsonNode item : items) {
            if (type.equals(item.get("type").asText())) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> idsOf(JsonNode page) {
        Set<String> ids = new HashSet<>();
        for (JsonNode item : page.get("items")) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String activityUrl(String groupId) {
        return "/api/v1/groups/" + groupId + "/activity";
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

    private static String settlementJson(String fromUserId, String toUserId, long amountMinor, String date) {
        return "{\"fromUserId\":\"" + fromUserId + "\",\"toUserId\":\"" + toUserId + "\","
                + "\"amountMinor\":" + amountMinor + ",\"settledOn\":\"" + date + "\"}";
    }
}
