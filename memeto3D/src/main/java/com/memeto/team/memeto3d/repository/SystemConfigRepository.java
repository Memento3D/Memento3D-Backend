package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    List<SystemConfig> findAllByOrderByConfigKeyAsc();

    Optional<SystemConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
