package app.cuentasclaras.application.settlement;

import app.cuentasclaras.api.dto.CreateSettlementRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.common.CursorCodec;
import app.cuentasclaras.application.group.GroupAccessGuard;
import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.model.Page;
import app.cuentasclaras.domain.model.Settlement;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de pagos entre miembros: registro (idempotente en el
 * controlador) y listado paginado por cursor.
 */
@Service
public class SettlementService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SettlementRepository settlements;
    private final GroupMemberRepository members;
    private final GroupAccessGuard guard;

    public SettlementService(SettlementRepository settlements, GroupMemberRepository members,
                             GroupAccessGuard guard) {
        this.settlements = settlements;
        this.members = members;
        this.guard = guard;
    }

    @Transactional
    public Settlement create(UUID userId, UUID groupId, CreateSettlementRequest request) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);

        if (request.fromUserId().equals(request.toUserId())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "El pagador y el receptor deben ser usuarios distintos");
        }
        requireActiveMember(groupId, request.fromUserId());
        requireActiveMember(groupId, request.toUserId());

        String currency = resolveCurrency(request.currency(), group.currency());
        Instant now = Instant.now();
        Settlement settlement = new Settlement(
                UUID.randomUUID(), groupId, request.fromUserId(), request.toUserId(), request.amountMinor(),
                currency, request.settledOn(), trimToNull(request.note()), userId, now, null);
        return settlements.save(settlement);
    }

    @Transactional(readOnly = true)
    public Page<Settlement> list(UUID userId, UUID groupId, Integer limit, String cursor) {
        guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);

        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = (cursor == null || cursor.isBlank()) ? null : CursorCodec.decode(cursor);
        LocalDate cursorDate = decoded == null ? null : LocalDate.parse(decoded.key());
        UUID cursorId = decoded == null ? null : decoded.id();

        List<Settlement> found = settlements.findPage(groupId, cursorDate, cursorId, size + 1);
        boolean hasMore = found.size() > size;
        List<Settlement> page = hasMore ? found.subList(0, size) : found;

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Settlement last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.settledOn().toString(), last.id());
        }
        return new Page<>(page, nextCursor, hasMore);
    }

    private void requireActiveMember(UUID groupId, UUID userId) {
        boolean active = members.findByGroupAndUser(groupId, userId)
                .filter(member -> member.status() == MemberStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw new ApiException(ErrorCode.INVALID_PARTICIPANT,
                    "El usuario " + userId + " no es un miembro activo del grupo");
        }
    }

    private static String resolveCurrency(String requested, String groupCurrency) {
        if (requested == null) {
            return groupCurrency;
        }
        String normalized = requested.toUpperCase(Locale.ROOT);
        if (!normalized.equals(groupCurrency)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "La moneda del pago debe coincidir con la del grupo (" + groupCurrency + ")");
        }
        return normalized;
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
