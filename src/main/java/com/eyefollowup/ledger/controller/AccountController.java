package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.common.ApiResponse;
import com.eyefollowup.ledger.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("username") String username) {
        return ApiResponse.ok(accountService.profile(username));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestAttribute("username") String username,
                                                          @RequestBody Map<String, String> body) {
        return ApiResponse.ok(accountService.updateDisplayName(username, body.get("displayName")));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestAttribute("username") String username,
                                                         @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(accountService.uploadAvatar(username, file));
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar(@RequestAttribute("username") String username) {
        AccountService.AvatarFile avatar = accountService.loadAvatar(username);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(avatar.data());
    }
}
