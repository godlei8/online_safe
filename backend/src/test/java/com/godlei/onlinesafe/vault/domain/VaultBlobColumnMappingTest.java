package com.godlei.onlinesafe.vault.domain;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用 MySQLDialect 构建元数据，断言大字段映射为 LONGBLOB，
 * 避免 Hibernate 7 默认 BLOB+length=255 退化成 TINYBLOB 导致 validate 失败。
 */
class VaultBlobColumnMappingTest {

    private static StandardServiceRegistry registry;
    private static Metadata metadata;

    @BeforeAll
    static void setUpMetadata() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, MySQLDialect.class.getName())
                // 仅校验映射元数据，不连库
                .applySetting(AvailableSettings.ALLOW_METADATA_ON_BOOT, false)
                .build();
        metadata = new MetadataSources(registry)
                .addAnnotatedClass(PrivateTemplate.class)
                .addAnnotatedClass(VaultItem.class)
                .addAnnotatedClass(VaultKeyBundle.class)
                .buildMetadata();
    }

    @AfterAll
    static void tearDown() {
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void privateTemplateCiphertextMapsToLongblob() {
        assertColumnSqlType(PrivateTemplate.class, "ciphertext", "longblob");
    }

    @Test
    void vaultItemCiphertextMapsToLongblob() {
        assertColumnSqlType(VaultItem.class, "ciphertext", "longblob");
    }

    @Test
    void vaultKeyBundleWrappedDekColumnsMapToLongblob() {
        assertColumnSqlType(VaultKeyBundle.class, "wrapped_dek_master", "longblob");
        assertColumnSqlType(VaultKeyBundle.class, "wrapped_dek_recovery", "longblob");
    }

    private static void assertColumnSqlType(Class<?> entityClass, String columnName, String expectedSqlType) {
        PersistentClass persistentClass = metadata.getEntityBinding(entityClass.getName());
        assertThat(persistentClass).as("entity %s", entityClass.getSimpleName()).isNotNull();

        Column column = persistentClass.getTable().getColumn(metadata.getDatabase().toIdentifier(columnName));
        assertThat(column).as("column %s.%s", entityClass.getSimpleName(), columnName).isNotNull();

        String sqlType = column.getSqlType(metadata).toLowerCase();
        assertThat(sqlType)
                .as("%s.%s 在 MySQLDialect 下的 DDL 类型", entityClass.getSimpleName(), columnName)
                .isEqualTo(expectedSqlType);
        assertThat(sqlType).doesNotContain("tinyblob");
    }
}
