package app.cuentasclaras.domain.model;

/**
 * Tipos de evento del historial de un grupo. Coinciden con el enum
 * {@code ActivityItem.type} del contrato OpenAPI.
 */
public enum ActivityType {
    EXPENSE_CREATED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    SETTLEMENT_CREATED,
    MEMBER_JOINED,
    MEMBER_LEFT
}
