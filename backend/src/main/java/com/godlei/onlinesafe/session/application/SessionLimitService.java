package com.godlei.onlinesafe.session.application;

import com.godlei.onlinesafe.settings.application.SystemSettingService;
import org.springframework.stereotype.Service;

@Service
public class SessionLimitService {

    public static final int DEFAULT_MAX_ACTIVE_USER_SESSIONS = 2;
    public static final int ADMIN_MAX_SESSIONS = 1;

    private final SystemSettingService systemSettingService;

    public SessionLimitService(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    public int maxActiveUserSessions() {
        return systemSettingService.maxActiveUserSessions();
    }

    public int adminMaxSessions() {
        return ADMIN_MAX_SESSIONS;
    }
}
