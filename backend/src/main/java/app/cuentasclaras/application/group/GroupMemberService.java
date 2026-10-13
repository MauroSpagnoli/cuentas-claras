package app.cuentasclaras.application.group;

import app.cuentasclaras.api.dto.AddMemberRequest;
import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.model.User;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Casos de uso de miembros: listado, alta por email, cambio de rol y baja
 * (expulsion o abandono). Protege la invariante "al menos un OWNER".
 */
@Service
public class GroupMemberService {

    private final GroupMemberRepository members;
    private final UserRepository users;
    private final GroupAccessGuard guard;

    public GroupMemberService(GroupMemberRepository members, UserRepository users, GroupAccessGuard guard) {
        this.members = members;
        this.users = users;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<GroupMemberView> list(UUID groupId, UUID requesterId) {
        guard.requireGroup(groupId);
        guard.requireMembership(groupId, requesterId);
        return members.findViewsByGroup(groupId);
    }

    @Transactional
    public GroupMemberView add(UUID groupId, UUID requesterId, AddMemberRequest request) {
        guard.requireGroup(groupId);
        GroupMember requester = guard.requireManager(groupId, requesterId);

        User target = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "No existe un usuario registrado con ese email"));

        GroupRole role = request.role() == null ? GroupRole.MEMBER : request.role();
        if (role == GroupRole.OWNER && requester.role() != GroupRole.OWNER) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Solo un OWNER puede asignar el rol OWNER");
        }

        GroupMember existing = members.findByGroupAndUser(groupId, target.id()).orElse(null);
        if (existing != null && existing.status() != MemberStatus.LEFT) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER, "El usuario ya pertenece al grupo");
        }

        Instant now = Instant.now();
        GroupMember saved;
        if (existing != null) {
            saved = members.save(new GroupMember(existing.id(), groupId, target.id(), role,
                    MemberStatus.ACTIVE, now, existing.createdAt()));
        } else {
            saved = members.save(new GroupMember(UUID.randomUUID(), groupId, target.id(), role,
                    MemberStatus.ACTIVE, now, now));
        }

        return new GroupMemberView(target.id(), target.displayName(), target.email(), saved.role(),
                saved.status(), saved.joinedAt());
    }

    @Transactional
    public GroupMemberView changeRole(UUID groupId, UUID requesterId, UUID targetUserId, GroupRole newRole) {
        guard.requireGroup(groupId);
        GroupMember requester = guard.requireManager(groupId, requesterId);
        GroupMember target = requireMembership(groupId, targetUserId);

        if (target.role() == newRole) {
            return view(target);
        }
        if ((target.role() == GroupRole.OWNER || newRole == GroupRole.OWNER)
                && requester.role() != GroupRole.OWNER) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Solo un OWNER puede gestionar el rol OWNER");
        }
        if (target.role() == GroupRole.OWNER && newRole != GroupRole.OWNER
                && members.countActiveOwners(groupId) <= 1) {
            throw new ApiException(ErrorCode.LAST_OWNER, "No se puede degradar al último OWNER del grupo");
        }

        GroupMember updated = members.save(new GroupMember(target.id(), groupId, targetUserId, newRole,
                target.status(), target.joinedAt(), target.createdAt()));
        return view(updated);
    }

    @Transactional
    public void remove(UUID groupId, UUID requesterId, UUID targetUserId) {
        guard.requireGroup(groupId);
        GroupMember requester = guard.requireMembership(groupId, requesterId);

        boolean self = requesterId.equals(targetUserId);
        if (!self && !requester.isManager()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Se requiere rol OWNER o ADMIN");
        }

        GroupMember target = requireMembership(groupId, targetUserId);
        if (!self && target.role() == GroupRole.OWNER && requester.role() != GroupRole.OWNER) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Solo un OWNER puede expulsar a un OWNER");
        }
        if (target.role() == GroupRole.OWNER && members.countActiveOwners(groupId) <= 1) {
            throw new ApiException(ErrorCode.LAST_OWNER, "El grupo debe conservar al menos un OWNER");
        }

        members.save(new GroupMember(target.id(), groupId, targetUserId, target.role(), MemberStatus.LEFT,
                target.joinedAt(), target.createdAt()));
    }

    private GroupMember requireMembership(UUID groupId, UUID userId) {
        return members.findByGroupAndUser(groupId, userId)
                .filter(member -> member.status() != MemberStatus.LEFT)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_MEMBER, "El usuario no pertenece al grupo"));
    }

    private GroupMemberView view(GroupMember member) {
        User user = users.findById(member.userId()).orElse(null);
        String displayName = user != null ? user.displayName() : null;
        String email = user != null ? user.email() : null;
        return new GroupMemberView(member.userId(), displayName, email, member.role(), member.status(),
                member.joinedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
