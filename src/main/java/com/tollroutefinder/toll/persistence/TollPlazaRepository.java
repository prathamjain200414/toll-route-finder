package com.tollroutefinder.toll.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TollPlazaRepository extends JpaRepository<TollPlazaEntity, Long>, TollPlazaUpsertOperations {

    Optional<TollPlazaEntity> findByPlazaKey(String plazaKey);
}
