package com.vonage.nonhmac.repos;

import com.vonage.nonhmac.pojo.StringContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StringContainerRepository extends JpaRepository<StringContainer, Long> {

}
