package com.postonmywall.account;

import com.postonmywall.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Social Accounts", description = "Link and manage social media accounts")
public class SocialAccountController {

    private final SocialAccountService socialAccountService;

    public SocialAccountController(SocialAccountService socialAccountService) {
        this.socialAccountService = socialAccountService;
    }

    @PostMapping
    @Operation(summary = "Link a social media account")
    public ResponseEntity<ApiResponse<SocialAccountResponse>> linkAccount(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody LinkAccountRequest request) {

        SocialAccountResponse response = socialAccountService.linkAccount(userId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Account linked successfully"));
    }

    @GetMapping
    @Operation(summary = "List all linked social accounts")
    public ResponseEntity<ApiResponse<List<SocialAccountResponse>>> listAccounts(
            @AuthenticationPrincipal UserDetails principal) {

        List<SocialAccountResponse> accounts = socialAccountService.listAccounts(userId(principal));
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Unlink a social media account")
    public ResponseEntity<ApiResponse<Void>> unlinkAccount(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID accountId) {

        socialAccountService.unlinkAccount(userId(principal), accountId);
        return ResponseEntity.ok(ApiResponse.ok("Account unlinked successfully"));
    }

    private UUID userId(UserDetails principal) {
        return UUID.fromString(principal.getUsername());
    }
}
