package com.genlogs.app.sourcing.dto;

import jakarta.validation.constraints.NotBlank;

public record LeerUrlRequest(@NotBlank(message = "Pega la URL del producto") String url) {
}
