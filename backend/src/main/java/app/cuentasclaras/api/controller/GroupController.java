package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.AddMemberRequest;
import app.cuentasclaras.api.dto.CreateGroupRequest;
import app.cuentasclaras.api.dto.GroupMemberResponse;
import app.cuentasclaras.api.dto.GroupResponse;
import app.cuentasclaras.api.dto.InviteResponse;
import app.cuentasclaras.api.dto.PageResponse;
import app.cuentasclaras.api.dto.UpdateGroupRequest;
import app.cuentasclaras.api.dto.UpdateMemberRequest;
import app.cuentasclaras.application.group.GroupMemberService;
import app.cuentasclaras.application.group.GroupService;
import app.cuentasclaras.application.group.GroupWithCount;
import app.cuentasclaras.application.group.Invite;
import app.cuentasclaras.application.security.AuthenticatedUser;
import app.cuentasclaras.domain.model.GroupMemberView;
import app.cuentasclaras.domain.model.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService memberService;

    public GroupController(GroupService groupService, GroupMemberService memberService) {
        this.groupService = groupService;
        this.memberService = memberService;
    }

    @GetMapping
    public PageResponse<GroupResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) String cursor) {
        Page<GroupWithCount> page = groupService.list(principal.id(), limit, cursor);
        return PageResponse.of(page, item -> GroupResponse.from(item.group(), item.memberCount()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                @Valid @RequestBody CreateGroupRequest request) {
        GroupWithCount created = groupService.create(principal.id(), request);
        return GroupResponse.from(created.group(), created.memberCount());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@AuthenticationPrincipal AuthenticatedUser principal,
                             @PathVariable UUID groupId) {
        GroupWithCount group = groupService.get(groupId, principal.id());
        return GroupResponse.from(group.group(), group.memberCount());
    }

    @PatchMapping("/{groupId}")
    public GroupResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                                @PathVariable UUID groupId,
                                @Valid @RequestBody UpdateGroupRequest request) {
        GroupWithCount group = groupService.update(groupId, principal.id(), request);
        return GroupResponse.from(group.group(), group.memberCount());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal,
                       @PathVariable UUID groupId) {
        groupService.delete(groupId, principal.id());
    }

    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponse> listMembers(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID groupId) {
        List<GroupMemberView> views = memberService.list(groupId, principal.id());
        return views.stream().map(GroupMemberResponse::from).toList();
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupMemberResponse addMember(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable UUID groupId,
                                         @Valid @RequestBody AddMemberRequest request) {
        return GroupMemberResponse.from(memberService.add(groupId, principal.id(), request));
    }

    @PatchMapping("/{groupId}/members/{userId}")
    public GroupMemberResponse changeMemberRole(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID groupId,
                                                @PathVariable UUID userId,
                                                @Valid @RequestBody UpdateMemberRequest request) {
        return GroupMemberResponse.from(
                memberService.changeRole(groupId, principal.id(), userId, request.role()));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser principal,
                             @PathVariable UUID groupId,
                             @PathVariable UUID userId) {
        memberService.remove(groupId, principal.id(), userId);
    }

    @PostMapping("/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse createInvite(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID groupId) {
        Invite invite = groupService.invite(groupId, principal.id());
        return new InviteResponse(invite.code(), invite.url(), invite.expiresAt());
    }
}
