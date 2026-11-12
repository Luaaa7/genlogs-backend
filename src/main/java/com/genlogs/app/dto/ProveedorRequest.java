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

    /** Obligatoria con RUC. Con DNI o pasaporte se arma con los nombres y apellidos. */
    @Size(max = 200)
    private String razonSocial;

    @Size(max = 120)
    private String nombres;

    @Size(max = 60)
    private String apellidoPaterno;

    @Size(max = 60)
    private String apellidoMaterno;

    /** Obligatorio solo si el país es Perú (D-15); lo valida UbigeoService.aplicarUbicacion. */
    private Integer idDistrito;

    /** País del tercero; si no se indica, Perú. */
    private Integer idPais;

    /** Solo si el país no es Perú: ubicación en texto libre (RF-81). */
    @Size(max = 100)
    private String regionExtranjero;

    @Size(max = 100)
    private String ciudadExtranjero;

    @Size(max = 20)
    private String codigoPostalExtranjero;

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
        if (t.getNombres() != null) nombres = t.getNombres().trim();
        if (t.getApellidoPaterno() != null) apellidoPaterno = t.getApellidoPaterno().trim();
        if (t.getApellidoMaterno() != null) apellidoMaterno = t.getApellidoMaterno().trim();
        if (t.getIdDistrito() != null) idDistrito = t.getIdDistrito();
        if (t.getDireccion() != null) direccion = vacioANulo(t.getDireccion());
        if (t.getTelefono() != null) telefono = vacioANulo(t.getTelefono());
        if (t.getCorreo() != null) correo = vacioANulo(t.getCorreo());
        if (t.getIdPais() != null) idPais = t.getIdPais();
        if (t.getRegionExtranjero() != null) regionExtranjero = vacioANulo(t.getRegionExtranjero());
        if (t.getCiudadExtranjero() != null) ciudadExtranjero = vacioANulo(t.getCiudadExtranjero());
        if (t.getCodigoPostalExtranjero() != null) codigoPostalExtranjero = vacioANulo(t.getCodigoPostalExtranjero());
    }

    private static String vacioANulo(String v) {
        return v.isBlank() ? null : v.trim();
    }
}