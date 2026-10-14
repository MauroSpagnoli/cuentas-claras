package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.CreateSettlementRequest;
import app.cuentasclaras.api.dto.PageResponse;
import app.cuentasclaras.api.dto.SettlementResponse;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.idempotency.IdempotencyService;
import app.cuentasclaras.application.security.AuthenticatedUser;
import app.cuentasclaras.application.settlement.SettlementService;
import app.cuentasclaras.domain.model.Page;
import app.cuentasclaras.domain.model.Settlement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SettlementController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final SettlementService settlementService;
    private final IdempotencyService idempotency;

    public SettlementController(SettlementService settlementService, IdempotencyService idempotency) {
        this.settlementService = settlementService;
        this.idempotency = idempotency;
    }

    @GetMapping("/groups/{groupId}/settlements")
    public PageResponse<SettlementResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID groupId,
                                                 @RequestParam(required = false) Integer limit,
                                                 @RequestParam(required = false) String cursor) {
        Page<Settlement> page = settlementService.list(principal.id(), groupId, limit, cursor);
        return PageResponse.of(page, SettlementResponse::from);
    }

    @PostMapping("/groups/{groupId}/settlements")
    public ResponseEntity<SettlementResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID groupId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateSettlementRequest request) {
        boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();
        if (idempotent && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "La cabecera " + IDEMPOTENCY_HEADER + " no puede superar "
                            + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        }

        String hash = null;
        if (idempotent) {
            hash = idempotency.hash(request);
            Optional<IdempotencyService.StoredResponse> replay =
                    idempotency.findCompleted(principal.id(), idempotencyKey, hash);
            if (replay.isPresent()) {
                SettlementResponse stored = idempotency.deserialize(replay.get().body(), SettlementResponse.class);
                return ResponseEntity.status(replay.get().status()).body(stored);
            }
            idempotency.reserve(principal.id(), idempotencyKey,
                    "POST /groups/" + groupId + "/settlements", hash);
        }

        try {
            Settlement settlement = settlementService.create(principal.id(), groupId, request);
            SettlementResponse response = SettlementResponse.from(settlement);
            if (idempotent) {
                idempotency.complete(principal.id(), idempotencyKey, HttpStatus.CREATED.value(),
                        idempotency.serialize(response));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            if (idempotent) {
                idempotency.release(principal.id(), idempotencyKey);
            }
            throw ex;
        }
    }
}
