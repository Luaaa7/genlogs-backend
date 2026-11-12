package com.genlogs.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Datos del tercero tal como los manda el frontend, anidados:
 * { "tercero": { "idTipoDocumento": 1, "numeroDocumento": "...", ... }, "idSectorEconomico": 3 }.
 * ClienteRequest y ProveedorRequest los copian a sus campos planos.
 * Getters y setters escritos a mano (sin Lombok) para que el editor no marque errores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TerceroDatosRequest {
    private Integer idTipoDocumento;
    private String numeroDocumento;
    private String razonSocial;
    private String nombres;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;
    /** D-15 / RF-81: país (null = Perú) y ubicación en texto libre si no es Perú. */
    private Integer idPais;
    private String regionExtranjero;
    private String ciudadExtranjero;
    private String codigoPostalExtranjero;

    public Integer getIdPais() { return idPais; }
    public void setIdPais(Integer idPais) { this.idPais = idPais; }

    public String getRegionExtranjero() { return regionExtranjero; }
    public void setRegionExtranjero(String regionExtranjero) { this.regionExtranjero = regionExtranjero; }

    public String getCiudadExtranjero() { return ciudadExtranjero; }
    public void setCiudadExtranjero(String ciudadExtranjero) { this.ciudadExtranjero = ciudadExtranjero; }

    public String getCodigoPostalExtranjero() { return codigoPostalExtranjero; }
    public void setCodigoPostalExtranjero(String codigoPostalExtranjero) { this.codigoPostalExtranjero = codigoPostalExtranjero; }

    public Integer getIdTipoDocumento() { return idTipoDocumento; }
    public void setIdTipoDocumento(Integer idTipoDocumento) { this.idTipoDocumento = idTipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidoPaterno() { return apellidoPaterno; }
    public void setApellidoPaterno(String apellidoPaterno) { this.apellidoPaterno = apellidoPaterno; }

    public String getApellidoMaterno() { return apellidoMaterno; }
    public void setApellidoMaterno(String apellidoMaterno) { this.apellidoMaterno = apellidoMaterno; }

    public Integer getIdDistrito() { return idDistrito; }
    public void setIdDistrito(Integer idDistrito) { this.idDistrito = idDistrito; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}