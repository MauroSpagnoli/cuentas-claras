package app.cuentasclaras.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Edicion parcial de grupo: los campos nulos se ignoran.
 */
public record UpdateGroupRequest(
        @Size(min = 1, max = 120)
        String name,

        @Size(max = 500)
        String description,

        Boolean archived
) {
}
