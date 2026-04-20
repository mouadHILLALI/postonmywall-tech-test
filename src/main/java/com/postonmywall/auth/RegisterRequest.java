package com.postonmywall.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {
    @NotBlank @Size(min = 3, max = 64)
    private String username;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, max = 128)
    private String password;
}
