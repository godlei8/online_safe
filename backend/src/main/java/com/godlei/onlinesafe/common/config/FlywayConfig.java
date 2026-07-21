package com.godlei.onlinesafe.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MySQL 的 DDL 无法放入事务回滚。迁移若在「DDL 已提交、历史表尚未标记成功」之间中断，
 * Flyway 会留下 success=0 并阻断启动。
 * <p>
 * 本策略在 migrate 前执行 repair：清理失败记录、对齐校验和；再配合幂等迁移脚本，从根上避免卡死。
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.repair-before-migrate:true}") boolean repairBeforeMigrate
    ) {
        return flyway -> {
            if (repairBeforeMigrate) {
                log.info("执行 Flyway repair（对齐校验和并清理失败迁移记录），随后 migrate");
                flyway.repair();
            }
            var result = flyway.migrate();
            log.info(
                    "Flyway migrate 完成：migrationsExecuted={}, success={}",
                    result.migrationsExecuted,
                    result.success
            );
        };
    }
}
