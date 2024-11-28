package com.vonage.nonhmac.repos;

import com.vonage.nonhmac.pojo.RequestIDData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestIDDataRepository extends JpaRepository<RequestIDData, String> {
}
