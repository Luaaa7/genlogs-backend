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
    @Size(min = 1, max = 20, message = "El número de documento debe tener entre 1 y 20 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9\\-]{1,20}$", message = "Número de documento inválido (solo letras, números y guiones)")
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

    /**
     * El frontend envía los datos del tercero anidados:
     * { "tercero": { idTipoDocumento, idDistrito, numeroDocumento, razonSocial, ... }, ... }
     * pero este DTO es plano. Este setter copia los datos anidados a los campos planos,
     * de modo que se aceptan los dos formatos. Las validaciones (@NotNull, etc.) se
     * aplican después sobre los campos planos ya rellenados.
     */
    public void setTercero(TerceroDatos tercero) {
        if (tercero == null) {
            return;
        }
        if (tercero.getIdTipoDocumento() != null) this.idTipoDocumento = tercero.getIdTipoDocumento();
        if (tercero.getNumeroDocumento() != null) this.numeroDocumento = tercero.getNumeroDocumento();
        if (tercero.getRazonSocial() != null) this.razonSocial = tercero.getRazonSocial();
        if (tercero.getIdDistrito() != null) this.idDistrito = tercero.getIdDistrito();
        if (tercero.getDireccion() != null) this.direccion = tercero.getDireccion();
        if (tercero.getTelefono() != null) this.telefono = tercero.getTelefono();
        if (tercero.getCorreo() != null) this.correo = tercero.getCorreo();
    }
}
