package app.cuentasclaras.application.group;

import app.cuentasclaras.api.dto.CreateGroupRequest;
import app.cuentasclaras.api.dto.UpdateGroupRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.application.common.CursorCodec;
import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.model.Page;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.repository.GroupRepository;
import app.cuentasclaras.infrastructure.config.InviteProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso de grupos: creacion, listado paginado, detalle, edicion,
 * borrado logico e invitaciones.
 */
@Service
public class GroupService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_INVITE_ATTEMPTS = 5;

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupAccessGuard guard;
    private final InviteCodeGenerator inviteCodes;
    private final InviteProperties inviteProperties;

    public GroupService(GroupRepository groups, GroupMemberRepository members, GroupAccessGuard guard,
                        InviteCodeGenerator inviteCodes, InviteProperties inviteProperties) {
        this.groups = groups;
        this.members = members;
        this.guard = guard;
        this.inviteCodes = inviteCodes;
        this.inviteProperties = inviteProperties;
    }

    @Transactional
    public GroupWithCount create(UUID userId, CreateGroupRequest request) {
        Instant now = Instant.now();
        Group group = new Group(
                UUID.randomUUID(),
                request.name().trim(),
                trimToNull(request.description()),
                request.currency().toUpperCase(Locale.ROOT),
                userId,
                uniqueInviteCode(),
                null,
                null,
                now,
                now,
                0L
        );
        Group saved = groups.save(group);
        members.save(new GroupMember(UUID.randomUUID(), saved.id(), userId, GroupRole.OWNER,
                MemberStatus.ACTIVE, now, now));
        return new GroupWithCount(saved, 1);
    }

    @Transactional(readOnly = true)
    public Page<GroupWithCount> list(UUID userId, Integer limit, String cursor) {
        int size = normalizeLimit(limit);
        CursorCodec.Cursor decoded = (cursor == null || cursor.isBlank())
                ? null
                : CursorCodec.decode(cursor);

        List<Group> found = groups.findForUser(
                userId,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.id(),
                size + 1);

        boolean hasMore = found.size() > size;
        List<Group> page = hasMore ? found.subList(0, size) : found;

        Map<UUID, Long> counts = members.countMembersByGroups(page.stream().map(Group::id).toList());
        List<GroupWithCount> items = page.stream()
                .map(group -> new GroupWithCount(group, counts.getOrDefault(group.id(), 0L)))
                .toList();

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Group last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(new CursorCodec.Cursor(last.createdAt(), last.id()));
        }
        return new Page<>(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public GroupWithCount get(UUID groupId, UUID userId) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);
        return new GroupWithCount(group, members.countMembers(groupId));
    }

    @Transactional
    public GroupWithCount update(UUID groupId, UUID userId, UpdateGroupRequest request) {
        Group current = guard.requireGroup(groupId);
        guard.requireManager(groupId, userId);

        String name = request.name() != null ? request.name().trim() : current.name();
        String description = request.description() != null
                ? trimToNull(request.description())
                : current.description();

        Instant archivedAt = current.archivedAt();
        if (request.archived() != null) {
            archivedAt = request.archived()
                    ? (current.archivedAt() != null ? current.archivedAt() : Instant.now())
                    : null;
        }

        Group updated = new Group(current.id(), name, description, current.currency(), current.createdBy(),
                current.inviteCode(), archivedAt, current.deletedAt(), current.createdAt(), Instant.now(),
                current.version());
        Group saved = groups.save(updated);
        return new GroupWithCount(saved, members.countMembers(groupId));
    }

    @Transactional
    public void delete(UUID groupId, UUID userId) {
        Group current = guard.requireGroup(groupId);
        guard.requireOwner(groupId, userId);

        Instant now = Instant.now();
        Group deleted = new Group(current.id(), current.name(), current.description(), current.currency(),
                current.createdBy(), current.inviteCode(), current.archivedAt(), now, current.createdAt(),
                now, current.version());
        groups.save(deleted);
    }

    @Transactional(readOnly = true)
    public Invite invite(UUID groupId, UUID userId) {
        Group group = guard.requireGroup(groupId);
        guard.requireMembership(groupId, userId);
        String url = inviteProperties.baseUrl() + "/" + group.inviteCode();
        return new Invite(group.inviteCode(), url, null);
    }

    @Transactional
    public GroupWithCount accept(String code, UUID userId) {
        Group group = groups.findByInviteCode(code)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INVITE_CODE,
                        "Código de invitación no válido"));

        Instant now = Instant.now();
        GroupMember existing = members.findByGroupAndUser(group.id(), userId).orElse(null);
        if (existing != null && existing.status() != MemberStatus.LEFT) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER, "Ya perteneces a este grupo");
        }
        if (existing != null) {
            members.save(new GroupMember(existing.id(), group.id(), userId, existing.role(),
                    MemberStatus.ACTIVE, now, existing.createdAt()));
        } else {
            members.save(new GroupMember(UUID.randomUUID(), group.id(), userId, GroupRole.MEMBER,
                    MemberStatus.ACTIVE, now, now));
        }
        return new GroupWithCount(group, members.countMembers(group.id()));
    }

    private String uniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_ATTEMPTS; attempt++) {
            String candidate = inviteCodes.generate();
            if (!groups.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "No se pudo generar el código de invitación");
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
