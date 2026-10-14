package app.cuentasclaras.application.activity;

import app.cuentasclaras.application.common.CursorCodec;
import app.cuentasclaras.application.group.GroupAccessGuard;
import app.cuentasclaras.domain.model.ActivityEvent;
import app.cuentasclaras.domain.model.Page;
import app.cuentasclaras.domain.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de lectura del historial de actividad de un grupo, paginado por
 * cursor ordenado por {@code occurredAt DESC}.
 */
@Service
public class ActivityService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ActivityRepository activities;
    private final GroupAccessGuard guard;

    public ActivityService(ActivityRepository activities, GroupAccessGuard guard) {
        this.activities = activities;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Page<ActivityEvent> list(UUID userId, UUID groupId, Integer limit, String cursor) {
        guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);

        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = (cursor == null || cursor.isBlank()) ? null : CursorCodec.decode(cursor);
        Instant cursorOccurredAt = decoded == null ? null : Instant.parse(decoded.key());
        UUID cursorId = decoded == null ? null : decoded.id();

        List<ActivityEvent> found = activities.findPage(groupId, cursorOccurredAt, cursorId, size + 1);
        boolean hasMore = found.size() > size;
        List<ActivityEvent> page = hasMore ? found.subList(0, size) : found;

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            ActivityEvent last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.occurredAt().toString(), last.id());
        }
        return new Page<>(page, nextCursor, hasMore);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }
}
