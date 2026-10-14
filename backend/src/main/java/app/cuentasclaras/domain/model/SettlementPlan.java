package app.cuentasclaras.domain.model;

import java.util.List;

/**
 * Plan de liquidacion: transferencias sugeridas que dejan a todos los miembros
 * a cero, junto al algoritmo que las produjo.
 */
public record SettlementPlan(PlanAlgorithm algorithm, List<Transfer> transfers) {

    public int transactionCount() {
        return transfers.size();
    }
}
