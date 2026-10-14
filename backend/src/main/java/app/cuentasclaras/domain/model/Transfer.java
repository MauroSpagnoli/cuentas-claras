package app.cuentasclaras.domain.model;

import java.util.UUID;

/**
 * Movimiento sugerido o derivado: {@code fromUserId} paga {@code amountMinor} a
 * {@code toUserId}. Se usa tanto para las deudas por pares como para los pasos
 * del plan de liquidacion.
 */
public record Transfer(UUID fromUserId, UUID toUserId, long amountMinor) {
}
