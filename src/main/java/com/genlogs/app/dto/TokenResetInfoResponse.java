package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResetInfoResponse {
    private long segundosRestantes;
}