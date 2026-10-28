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
    private Integer idDistrito;
    private String direccion;
    private String telefono;
    private String correo;

    public Integer getIdTipoDocumento() { return idTipoDocumento; }
    public void setIdTipoDocumento(Integer idTipoDocumento) { this.idTipoDocumento = idTipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public Integer getIdDistrito() { return idDistrito; }
    public void setIdDistrito(Integer idDistrito) { this.idDistrito = idDistrito; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}