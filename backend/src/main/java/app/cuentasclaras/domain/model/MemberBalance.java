package app.cuentasclaras.domain.model;

import java.util.UUID;

/**
 * Saldo neto de un miembro del grupo.
 *
 * @param netMinor positivo = le deben; negativo = debe
 */
public record MemberBalance(UUID userId, String displayName, long netMinor, String currency) {
}
