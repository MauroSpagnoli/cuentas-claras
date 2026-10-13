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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de grupos y miembros sobre H2 en memoria (perfil dev).
 * Cubre autorizacion por recurso, roles e invitaciones. No requiere Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GroupApiH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flujoCompletoDeGruposYMiembros() throws Exception {
        String ana = register("ana.grupos@example.com", "Ana");
        String beto = register("beto.grupos@example.com", "Beto");
        String carla = register("carla.grupos@example.com", "Carla");

        // sin token -> 401
        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // Ana crea el grupo: queda como OWNER
        String groupBody = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pisos\",\"description\":\"Gastos de casa\",\"currency\":\"eur\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pisos"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.inviteCode").isNotEmpty())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode group = objectMapper.readTree(groupBody);
        String groupId = group.get("id").asText();
        String inviteCode = group.get("inviteCode").asText();

        // Ana ve el grupo; Beto no es miembro -> 403 NOT_MEMBER
        mockMvc.perform(get("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(get("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + beto))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // grupo inexistente -> 404
        mockMvc.perform(get("/api/v1/groups/11111111-1111-1111-1111-111111111111")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));

        // "mis grupos" de Ana
        mockMvc.perform(get("/api/v1/groups")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(groupId))
                .andExpect(jsonPath("$.hasMore").value(false));

        // Ana anade a Beto por email -> MEMBER
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"beto.grupos@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // anadirlo de nuevo -> 409 ALREADY_MEMBER
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"beto.grupos@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        // email inexistente -> 404 USER_NOT_FOUND
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        // Beto ya ve el grupo y figura en la lista de miembros
        mockMvc.perform(get("/api/v1/groups")
                        .header("Authorization", "Bearer " + beto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Beto es MEMBER: no puede editar el grupo -> 403 INSUFFICIENT_ROLE
        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otro\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));

        // Ana edita el grupo
        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Piso compartido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Piso compartido"))
                .andExpect(jsonPath("$.version").value(1));

        // Ana asciende a Beto a ADMIN y ahora si puede editar
        JsonNode betoUser = objectMapper.readTree(mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + beto))
                .andReturn().getResponse().getContentAsString());
        String betoId = betoUser.get("id").asText();

        mockMvc.perform(patch("/api/v1/groups/" + groupId + "/members/" + betoId)
                        .header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + beto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Editado por un admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Editado por un admin"));

        // No se puede quitar al ultimo OWNER -> 409 LAST_OWNER
        JsonNode anaUser = objectMapper.readTree(mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + ana))
                .andReturn().getResponse().getContentAsString());
        String anaId = anaUser.get("id").asText();

        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + anaId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER"));

        // Ana expulsa a Beto -> 204; Beto deja de verlo
        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/members/" + betoId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + beto))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        // Invitaciones: un no-miembro no puede generarlas
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites")
                        .header("Authorization", "Bearer " + carla))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites")
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(inviteCode))
                .andExpect(jsonPath("$.url").isNotEmpty());

        // Carla se une con el codigo
        mockMvc.perform(post("/api/v1/invites/" + inviteCode + "/accept")
                        .header("Authorization", "Bearer " + carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // aceptar de nuevo -> 409 ALREADY_MEMBER
        mockMvc.perform(post("/api/v1/invites/" + inviteCode + "/accept")
                        .header("Authorization", "Bearer " + carla))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        // codigo invalido -> 404
        mockMvc.perform(post("/api/v1/invites/NOEXISTE12/accept")
                        .header("Authorization", "Bearer " + carla))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));

        // Ana borra el grupo (OWNER) -> 204 y desaparece
        mockMvc.perform(delete("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void paginacionPorCursorDeMisGrupos() throws Exception {
        String dora = register("dora.grupos@example.com", "Dora");

        createGroup(dora, "Grupo 1");
        createGroup(dora, "Grupo 2");
        createGroup(dora, "Grupo 3");

        String firstPage = mockMvc.perform(get("/api/v1/groups")
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + dora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String nextCursor = objectMapper.readTree(firstPage).get("nextCursor").asText();

        String secondPage = mockMvc.perform(get("/api/v1/groups")
                        .param("limit", "2")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + dora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();

        // El orden es por createdAt desc: la primera pagina trae los mas recientes.
        JsonNode firstItems = objectMapper.readTree(firstPage).get("items");
        JsonNode secondItems = objectMapper.readTree(secondPage).get("items");
        assertThat(firstItems.get(0).get("name").asText()).isEqualTo("Grupo 3");
        assertThat(secondItems.get(0).get("name").asText()).isEqualTo("Grupo 1");

        // cursor invalido -> 400
        mockMvc.perform(get("/api/v1/groups")
                        .param("cursor", "no-es-base64!!")
                        .header("Authorization", "Bearer " + dora))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    private String createGroup(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currency\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
