package app.cuentasclaras.domain.repository;

import app.cuentasclaras.domain.model.Settlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de pagos entre miembros.
 */
public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(UUID id);

    /**
     * Pagos activos (no borrados) de un grupo ordenados por
     * {@code settled_on DESC, id DESC}. Si {@code cursorDate} no es nulo
     * devuelve solo los posteriores al cursor.
     */
    List<Settlement> findPage(UUID groupId, LocalDate cursorDate, UUID cursorId, int limit);

    /** Todos los pagos activos de un grupo (para el calculo de balances). */
    List<Settlement> findActiveByGroup(UUID groupId);
}
