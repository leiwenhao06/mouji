package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.dto.PlanCreateRequest;
import com.eyefollowup.ledger.entity.FollowupPlan;
import com.eyefollowup.ledger.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/followup")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping("/plans")
    public ApiResponse<List<FollowupPlan>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(planService.list(status, keyword));
    }

    @PostMapping("/plans")
    public ApiResponse<FollowupPlan> create(@Valid @RequestBody PlanCreateRequest request) {
        return ApiResponse.ok(planService.createManual(request.getPatientId(),
                request.getFrequencyMonths() == null ? 6 : request.getFrequencyMonths(),
                request.getNextDueDate(), request.getBasis()));
    }

    @PutMapping("/plans/{planId}/close")
    public ApiResponse<Void> close(@PathVariable int planId) {
        planService.close(planId);
        return ApiResponse.ok();
    }

    @PutMapping("/plans/{planId}/done")
    public ApiResponse<Void> done(@PathVariable int planId) {
        planService.done(planId);
        return ApiResponse.ok();
    }

    @GetMapping("/overdue-count")
    public ApiResponse<Map<String, Object>> overdueCount() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", planService.overdueCount());
        return ApiResponse.ok(map);
    }
}
