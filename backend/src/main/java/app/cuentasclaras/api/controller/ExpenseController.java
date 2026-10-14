package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.CreateExpenseRequest;
import app.cuentasclaras.api.dto.ExpenseResponse;
import app.cuentasclaras.api.dto.PageResponse;
import app.cuentasclaras.api.dto.UpdateExpenseRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.expense.ExpenseService;
import app.cuentasclaras.application.idempotency.IdempotencyService;
import app.cuentasclaras.application.security.AuthenticatedUser;
import app.cuentasclaras.domain.model.ExpenseDetail;
import app.cuentasclaras.domain.model.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExpenseController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final ExpenseService expenseService;
    private final IdempotencyService idempotency;

    public ExpenseController(ExpenseService expenseService, IdempotencyService idempotency) {
        this.expenseService = expenseService;
        this.idempotency = idempotency;
    }

    @GetMapping("/groups/{groupId}/expenses")
    public PageResponse<ExpenseResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID groupId,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) UUID paidByUserId,
                                              @RequestParam(required = false) String category) {
        Page<ExpenseDetail> page = expenseService.list(principal.id(), groupId, limit, cursor, from, to,
                paidByUserId, category);
        return PageResponse.of(page, ExpenseResponse::from);
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID groupId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateExpenseRequest request) {
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
                ExpenseResponse stored = idempotency.deserialize(replay.get().body(), ExpenseResponse.class);
                return ResponseEntity.status(replay.get().status()).body(stored);
            }
            idempotency.reserve(principal.id(), idempotencyKey,
                    "POST /groups/" + groupId + "/expenses", hash);
        }

        try {
            ExpenseDetail detail = expenseService.create(principal.id(), groupId, request);
            ExpenseResponse response = ExpenseResponse.from(detail);
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

    @GetMapping("/expenses/{expenseId}")
    public ExpenseResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable UUID expenseId) {
        return ExpenseResponse.from(expenseService.get(principal.id(), expenseId));
    }

    @PatchMapping("/expenses/{expenseId}")
    public ExpenseResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @PathVariable UUID expenseId,
                                  @Valid @RequestBody UpdateExpenseRequest request) {
        return ExpenseResponse.from(expenseService.update(principal.id(), expenseId, request));
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal,
                       @PathVariable UUID expenseId) {
        expenseService.delete(principal.id(), expenseId);
    }
}
