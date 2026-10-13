package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.GroupRole;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de rol de un miembro.
 */
public record UpdateMemberRequest(
        @NotNull
        GroupRole role
) {
}
