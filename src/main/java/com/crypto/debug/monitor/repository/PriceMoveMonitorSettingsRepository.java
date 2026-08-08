package com.crypto.debug.monitor.repository;

import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceMoveMonitorSettingsRepository extends JpaRepository<PriceMoveMonitorSettings, Long> {
}
