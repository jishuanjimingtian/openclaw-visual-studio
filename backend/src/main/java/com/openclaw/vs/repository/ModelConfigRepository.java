package com.openclaw.vs.repository;

import com.openclaw.vs.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {

    List<ModelConfig> findByEnabledTrue();
    List<ModelConfig> findByProvider(String provider);
}
