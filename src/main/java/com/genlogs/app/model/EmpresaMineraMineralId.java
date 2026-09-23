package com.genlogs.app.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Llave compuesta de empresa_minera_mineral (id_empresa_minera, id_mineral).
 * Los nombres de campo deben coincidir con los campos @Id de
 * EmpresaMineraMineral.java (empresaMinera, mineral), con el mismo tipo
 * que el ID de la entidad referenciada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EmpresaMineraMineralId implements Serializable {

    private Long empresaMinera;
    private Integer mineral;
}
