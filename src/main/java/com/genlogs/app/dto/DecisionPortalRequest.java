package com.genlogs.app.dto;

import lombok.Getter;
import lombok.Setter;

/** Motivo opcional al aprobar o rechazar desde el portal. */
@Getter
@Setter
public class DecisionPortalRequest {

    private String motivo;
}
