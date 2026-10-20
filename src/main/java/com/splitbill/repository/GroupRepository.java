package com.splitbill.repository;

import com.splitbill.domain.BillGroup;

import java.util.Optional;

public interface GroupRepository {
    BillGroup save(BillGroup group);

    Optional<BillGroup> findById(String id);
}
