package com.costco.gb.controller;

import com.costco.gb.dto.request.UserDeliveryProfileRequest;
import com.costco.gb.dto.response.UserDeliveryProfileResponse;
import com.costco.gb.service.UserDeliveryProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-delivery-profiles")
@RequiredArgsConstructor
public class UserDeliveryProfileController {

    private final UserDeliveryProfileService profileService;

    @GetMapping
    public ResponseEntity<List<UserDeliveryProfileResponse>> getMyProfiles(
            Authentication authentication,
            @RequestParam(required = false) String profileType
    ) {
        return ResponseEntity.ok(profileService.getMyProfiles(currentUserId(authentication), profileType));
    }

    @PostMapping
    public ResponseEntity<UserDeliveryProfileResponse> create(
            Authentication authentication,
            @Valid @RequestBody UserDeliveryProfileRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.create(currentUserId(authentication), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDeliveryProfileResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UserDeliveryProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.update(currentUserId(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        profileService.delete(currentUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getPrincipal().toString());
    }
}
