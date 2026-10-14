package app.cuentasclaras.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pago real registrado entre dos miembros de un grupo: {@code fromUserId} le
 * devuelve dinero a {@code toUserId}. El borrado es logico ({@code deletedAt}).
 */
public record Settlement(
        UUID id,
        UUID groupId,
        UUID fromUserId,
        UUID toUserId,
        long amountMinor,
        String currency,
        LocalDate settledOn,
        String note,
        UUID createdBy,
        Instant createdAt,
        Instant deletedAt
) {

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
