package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.entity.FollowupSuggestion;
import com.eyefollowup.ledger.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/followup")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    /** 提交 AI 随访建议生成任务（异步）。 */
    @PostMapping("/suggestions")
    public ApiResponse<FollowupSuggestion> submit(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String patientId = body.get("patientId");
        return ApiResponse.ok(suggestionService.submit(patientId,
                String.valueOf(request.getAttribute("username")),
                request.getRemoteAddr()));
    }

    @GetMapping("/suggestions/recent")
    public ApiResponse<List<FollowupSuggestion>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(suggestionService.recent(limit));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<FollowupSuggestion>> list(@RequestParam String patientId,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(suggestionService.listByPatient(patientId, limit));
    }

    @GetMapping("/suggestions/{requestId}")
    public ApiResponse<FollowupSuggestion> get(@PathVariable String requestId) {
        return ApiResponse.ok(suggestionService.get(requestId));
    }

    @PutMapping("/suggestions/{suggestionId}/adoption")
    public ApiResponse<Void> adopt(@PathVariable int suggestionId,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest request) {
        boolean adopted = Boolean.TRUE.equals(body.get("adopted"));
        suggestionService.adopt(suggestionId, adopted,
                String.valueOf(request.getAttribute("username")));
        return ApiResponse.ok();
    }

    @GetMapping("/agent-status")
    public ApiResponse<Map<String, Object>> agentStatus() {
        return ApiResponse.ok(suggestionService.status());
    }
}
