package app.cuentasclaras.api.dto;

import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.GroupRole;
import app.cuentasclaras.domain.model.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String displayName,
        String email,
        GroupRole role,
        MemberStatus status,
        Instant joinedAt
) {

    public static GroupMemberResponse from(GroupMemberView view) {
        return new GroupMemberResponse(
                view.userId(),
                view.displayName(),
                view.email(),
                view.role(),
                view.status(),
                view.joinedAt()
        );
    }
}
