package com.tollroutefinder.toll.persistence;

import com.tollroutefinder.toll.TollPlaza;

import java.util.Collection;

/**
 * Custom repository fragment for bulk upserts, which Spring Data JPA cannot express efficiently.
 */
public interface TollPlazaUpsertOperations {

    /**
     * Inserts new plazas and updates changed ones in batched statements, keyed on {@link TollPlaza#key()}.
     * Unchanged rows are not written at all.
     *
     * @return number of rows actually inserted or updated
     */
    int upsertAll(Collection<TollPlaza> plazas);
}
