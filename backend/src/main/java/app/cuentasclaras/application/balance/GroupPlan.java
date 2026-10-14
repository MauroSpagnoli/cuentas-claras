package app.cuentasclaras.application.balance;

import app.cuentasclaras.domain.model.SettlementPlan;

/**
 * Plan de liquidacion junto a la moneda del grupo.
 */
public record GroupPlan(String currency, SettlementPlan plan) {
}
