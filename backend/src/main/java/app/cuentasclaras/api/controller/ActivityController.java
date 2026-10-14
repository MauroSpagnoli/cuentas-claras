package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.ActivityItemResponse;
import app.cuentasclaras.api.dto.PageResponse;
import app.cuentasclaras.application.activity.ActivityService;
import app.cuentasclaras.application.security.AuthenticatedUser;
import app.cuentasclaras.domain.model.ActivityEvent;
import app.cuentasclaras.domain.model.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/activity")
    public PageResponse<ActivityItemResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable UUID groupId,
                                                   @RequestParam(required = false) Integer limit,
                                                   @RequestParam(required = false) String cursor) {
        Page<ActivityEvent> page = activityService.list(principal.id(), groupId, limit, cursor);
        return PageResponse.of(page, ActivityItemResponse::from);
    }
}
