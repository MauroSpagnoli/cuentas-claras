package app.cuentasclaras.application.balance;

import app.cuentasclaras.domain.model.MemberBalance;
import app.cuentasclaras.domain.model.SettlementPlan;

import java.util.List;

/**
 * Balance de un grupo: saldo neto por miembro y plan de liquidacion derivado
 * (cuyas transferencias son tambien las deudas por pares).
 */
public record GroupBalances(String currency, List<MemberBalance> members, SettlementPlan plan) {
}
