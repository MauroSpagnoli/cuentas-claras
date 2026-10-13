package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.GroupResponse;
import app.cuentasclaras.application.group.GroupService;
import app.cuentasclaras.application.group.GroupWithCount;
import app.cuentasclaras.application.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final GroupService groupService;

    public InviteController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping("/{code}/accept")
    public GroupResponse accept(@AuthenticationPrincipal AuthenticatedUser principal,
                                @PathVariable String code) {
        GroupWithCount group = groupService.accept(code, principal.id());
        return GroupResponse.from(group.group(), group.memberCount());
    }
}
