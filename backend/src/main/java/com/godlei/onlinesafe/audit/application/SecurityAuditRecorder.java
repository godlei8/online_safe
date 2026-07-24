package com.godlei.onlinesafe.audit.application;

import com.godlei.onlinesafe.audit.domain.SecurityAuditEvent;

public interface SecurityAuditRecorder {

    /** 加入当前业务事务；失败则回滚业务。 */
    void record(SecurityAuditEvent event);

    /** 独立事务；写入失败不得把认证失败变成 500。 */
    void recordIndependent(SecurityAuditEvent event);
}
