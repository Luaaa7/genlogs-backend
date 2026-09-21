package com.genlogs.app.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para dar de alta (o actualizar) un cliente.
 * Si el tercero (mismo tipo + número de documento) ya existe, el
 * service solo le agrega el rol de cliente; si no existe, lo crea.
 */
@Getter
@Setter
public class ClienteRequest {

    @NotNull(message = "Debe indicar el tipo de documento (RUC, DNI, CE, PAS)")
    private Integer idTipoDocumento;

    @NotBlank(message = "El número de documento es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9]{1,15}$", message = "Número de documento inválido")
    private String numeroDocumento;

    @NotBlank(message = "La razón social / nombre es obligatorio")
    @Size(max = 200)
    private String razonSocial;

    @NotNull(message = "Debe indicar el distrito")
    private Integer idDistrito;

    @Size(max = 255)
    private String direccion;

    @Size(max = 30)
    private String telefono;

    @Email(message = "Correo inválido")
    @Size(max = 150)
    private String correo;

    @NotNull(message = "Debe indicar el sector económico")
    private Integer idSectorEconomico;
}
