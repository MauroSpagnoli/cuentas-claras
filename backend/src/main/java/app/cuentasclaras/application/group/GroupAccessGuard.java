package app.cuentasclaras.application.group;

import app.cuentasclaras.api.error.ApiException;
import app.cuentasclaras.api.error.ErrorCode;
import app.cuentasclaras.domain.model.Group;
import app.cuentasclaras.domain.model.GroupMember;
import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;
import app.cuentasclaras.domain.repository.GroupMemberRepository;
import app.cuentasclaras.domain.repository.GroupRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Autorizacion por recurso: comprueba la pertenencia y el rol de un usuario
 * sobre un grupo antes de dejar continuar un caso de uso.
 */
@Component
public class GroupAccessGuard {

    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public GroupAccessGuard(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    /** Devuelve el grupo o lanza 404 si no existe (o esta borrado). */
    public Group requireGroup(UUID groupId) {
        return groups.findById(groupId)
                .filter(group -> !group.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND, "Grupo no encontrado"));
    }

    /** Exige que el usuario sea miembro activo (no LEFT). */
    public GroupMember requireMembership(UUID groupId, UUID userId) {
        return members.findByGroupAndUser(groupId, userId)
                .filter(member -> member.status() != MemberStatus.LEFT)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_MEMBER, "No perteneces a este grupo"));
    }

    /** Exige rol OWNER o ADMIN. */
    public GroupMember requireManager(UUID groupId, UUID userId) {
        GroupMember member = requireMembership(groupId, userId);
        if (!member.isManager()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Se requiere rol OWNER o ADMIN");
        }
        return member;
    }

    /** Exige rol OWNER. */
    public GroupMember requireOwner(UUID groupId, UUID userId) {
        GroupMember member = requireMembership(groupId, userId);
        if (member.role() != GroupRole.OWNER) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE, "Se requiere rol OWNER");
        }
        return member;
    }
}
