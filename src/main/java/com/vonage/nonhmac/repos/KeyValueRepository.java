package com.vonage.nonhmac.repos;

import com.vonage.nonhmac.pojo.KeyValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KeyValueRepository extends JpaRepository<KeyValue, Long> {

    Optional<KeyValue> findByKey(String key);
}
