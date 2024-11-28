package com.vonage.nonhmac.repos;

import com.vonage.nonhmac.pojo.NonHmacData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NonHmacDataRepository extends JpaRepository<NonHmacData, Long> {
}
