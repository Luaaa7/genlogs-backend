package com.genlogs.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.ContactoTerceroRequest;
import com.genlogs.app.dto.ContactoTerceroResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.ContactoTercero;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.ContactoTerceroRepository;
import com.genlogs.app.repository.TerceroRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactoTerceroService {

    private final ContactoTerceroRepository contactoTerceroRepository;
    private final TerceroRepository terceroRepository;

    @Transactional(readOnly = true)
    public List<ContactoTerceroResponse> listarPorTercero(Long idTercero) {
        return contactoTerceroRepository.findByTercero_IdTerceroAndStatus(idTercero, "A").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ContactoTerceroResponse crear(Long idTercero, ContactoTerceroRequest request) {
        Tercero tercero = terceroRepository.findById(idTercero)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero no encontrado"));

        ContactoTercero contacto = ContactoTercero.builder()
                .tercero(tercero)
                .nombres(request.getNombres())
                .cargo(request.getCargo())
                .correo(request.getCorreo())
                .telefono(request.getTelefono())
                .esPrincipal(request.isEsPrincipal())
                .build();
        contacto.setUserCreate(usuarioActual());
        contacto.setProcessCreate("ALTA_CONTACTO");

        return toResponse(contactoTerceroRepository.save(contacto));
    }

    @Transactional
    public ContactoTerceroResponse actualizar(Long idContacto, ContactoTerceroRequest request) {
        ContactoTercero contacto = contactoTerceroRepository.findById(idContacto)
                .orElseThrow(() -> new ResourceNotFoundException("Contacto no encontrado"));

        contacto.setNombres(request.getNombres());
        contacto.setCargo(request.getCargo());
        contacto.setCorreo(request.getCorreo());
        contacto.setTelefono(request.getTelefono());
        contacto.setEsPrincipal(request.isEsPrincipal());
        contacto.setUserUpdate(usuarioActual());
        contacto.setProcessUpdate("EDITAR_CONTACTO");
        contacto.setDateUpdate(LocalDateTime.now());

        return toResponse(contactoTerceroRepository.save(contacto));
    }

    @Transactional
    public void eliminar(Long idContacto) {
        ContactoTercero contacto = contactoTerceroRepository.findById(idContacto)
                .orElseThrow(() -> new ResourceNotFoundException("Contacto no encontrado"));
        contacto.setStatus("I");
        contacto.setUserUpdate(usuarioActual());
        contacto.setProcessUpdate("BAJA_CONTACTO");
        contacto.setDateUpdate(LocalDateTime.now());
        contactoTerceroRepository.save(contacto);
    }

    private ContactoTerceroResponse toResponse(ContactoTercero c) {
        return ContactoTerceroResponse.builder()
                .idContacto(c.getIdContacto())
                .idTercero(c.getTercero().getIdTercero())
                .nombres(c.getNombres())
                .cargo(c.getCargo())
                .correo(c.getCorreo())
                .telefono(c.getTelefono())
                .esPrincipal(c.getEsPrincipal())
                .build();
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}