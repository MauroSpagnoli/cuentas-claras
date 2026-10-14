package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.SettlementPlan;

import java.util.List;

public record SettlementPlanResponse(
        String currency,
        String algorithm,
        int transactionCount,
        List<SettlementStepResponse> steps
) {

    public static SettlementPlanResponse from(String currency, SettlementPlan plan) {
        return new SettlementPlanResponse(
                currency,
                plan.algorithm().name(),
                plan.transactionCount(),
                plan.transfers().stream().map(SettlementStepResponse::from).toList()
        );
    }
}
