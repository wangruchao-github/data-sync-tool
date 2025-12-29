package com.datasync.tool.repository;

import com.datasync.tool.entity.ApiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, String> {
    Optional<ApiDefinition> findByPathAndMethod(String path, String method);
}
