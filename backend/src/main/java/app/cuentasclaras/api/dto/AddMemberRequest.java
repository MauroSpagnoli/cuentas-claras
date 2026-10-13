package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.GroupRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Alta directa de un miembro por email. Si no se indica rol, es MEMBER.
 */
public record AddMemberRequest(
        @NotBlank
        @Email
        String email,

        GroupRole role
) {
}
