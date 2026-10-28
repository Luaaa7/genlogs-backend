package com.genlogs.app.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de entrada para dar de alta (o actualizar) un proveedor.
 * Igual que ClienteRequest: si el tercero ya existe (por tipo +
 * número de documento) solo se le agrega el rol de proveedor.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProveedorRequest {

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

    /**
     * El frontend manda los datos del tercero anidados ({ "tercero": {...} }), y
     * este DTO los esperaba planos: por eso crear clientes y proveedores fallaba
     * con "Debe indicar el tipo de documento". Se aceptan las dos formas.
     */
    @JsonProperty("tercero")
    public void setTercero(TerceroDatosRequest t) {
        if (t == null) {
            return;
        }
        if (t.getIdTipoDocumento() != null) idTipoDocumento = t.getIdTipoDocumento();
        if (t.getNumeroDocumento() != null) numeroDocumento = t.getNumeroDocumento().trim();
        if (t.getRazonSocial() != null) razonSocial = t.getRazonSocial().trim();
        if (t.getIdDistrito() != null) idDistrito = t.getIdDistrito();
        if (t.getDireccion() != null) direccion = vacioANulo(t.getDireccion());
        if (t.getTelefono() != null) telefono = vacioANulo(t.getTelefono());
        if (t.getCorreo() != null) correo = vacioANulo(t.getCorreo());
    }

    private static String vacioANulo(String v) {
        return v.isBlank() ? null : v.trim();
    }
}