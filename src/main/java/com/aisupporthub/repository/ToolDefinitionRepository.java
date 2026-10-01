package com.aisupporthub.repository;

import com.aisupporthub.model.entity.ToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {
    List<ToolDefinition> findByClientIdAndActiveTrue(Long clientId);
}
