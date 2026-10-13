package app.cuentasclaras.application.group;

import java.time.Instant;

/**
 * Invitacion de grupo: codigo y enlace publico para unirse.
 */
public record Invite(String code, String url, Instant expiresAt) {
}
