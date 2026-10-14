package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.BalancesResponse;
import app.cuentasclaras.api.dto.SettlementPlanResponse;
import app.cuentasclaras.application.balance.BalanceService;
import app.cuentasclaras.application.balance.GroupPlan;
import app.cuentasclaras.application.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /** Balances netos por miembro y deudas minimas por pares. */
    @GetMapping("/balances")
    public BalancesResponse balances(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @PathVariable UUID groupId) {
        return BalancesResponse.from(balanceService.balances(principal.id(), groupId));
    }

    /** Plan de liquidacion: minimo de transferencias que deja el grupo a cero. */
    @GetMapping("/settlement-plan")
    public SettlementPlanResponse settlementPlan(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID groupId) {
        GroupPlan plan = balanceService.settlementPlan(principal.id(), groupId);
        return SettlementPlanResponse.from(plan.currency(), plan.plan());
    }
}
