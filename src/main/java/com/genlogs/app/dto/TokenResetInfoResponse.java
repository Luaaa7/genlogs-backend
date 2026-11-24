package com.genlogs.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResetInfoResponse {
    private long segundosRestantes;
    /**
     * v3.4: true si la cuenta es de un cliente del portal, para que «Ir al login»
     * lleve a /portal/ingreso y no al login del personal.
     */
    private boolean portal;
}
