package com.godlei.onlinesafe.settings.infrastructure;

import com.godlei.onlinesafe.settings.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    List<SystemSetting> findBySettingKeyIn(Collection<String> keys);
}
