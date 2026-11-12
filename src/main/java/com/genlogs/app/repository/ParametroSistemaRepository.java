package com.genlogs.app.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.genlogs.app.model.ParametroSistema;

import jakarta.persistence.LockModeType;

@Repository
public interface ParametroSistemaRepository extends JpaRepository<ParametroSistema, String> {

    /**
     * Lee y bloquea (SELECT ... FOR UPDATE) las filas de las claves dadas hasta que termine la
     * transacción. Siempre en orden de clave, para que dos ediciones simultáneas no se bloqueen
     * entre sí. Lo usa la edición de parámetros (BE-D) para validar MIN <= DEFAULT <= MAX
     * contra valores que nadie más está cambiando.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParametroSistema p where p.clave in :claves order by p.clave")
    List<ParametroSistema> bloquearPorClaves(@Param("claves") Collection<String> claves);
}
