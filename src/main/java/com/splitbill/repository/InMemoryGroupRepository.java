package com.splitbill.repository;

import com.splitbill.domain.BillGroup;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryGroupRepository implements GroupRepository {
    private final Map<String, BillGroup> store = new ConcurrentHashMap<>();

    @Override
    public BillGroup save(BillGroup group) {
        store.put(group.getId(), group);
        return group;
    }

    @Override
    public Optional<BillGroup> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
