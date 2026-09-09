package com.juxin.orin.app.device;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeDeviceSyncServiceTest {
    private JdbcTemplate appJdbc;
    private JdbcTemplate nodeJdbc;
    private NodeDeviceSyncService service;

    @BeforeEach
    void setUp() {
        appJdbc = h2("jdbc:h2:mem:app;MODE=MySQL;DB_CLOSE_DELAY=-1");
        nodeJdbc = h2("jdbc:h2:mem:node;MODE=MySQL;DB_CLOSE_DELAY=-1");
        appJdbc.execute("DROP TABLE IF EXISTS app_node");
        appJdbc.execute("DROP TABLE IF EXISTS app_user_account");
        nodeJdbc.execute("DROP TABLE IF EXISTS device");
        nodeJdbc.execute("DROP TABLE IF EXISTS app_user");
        appJdbc.execute("CREATE TABLE app_user_account (id BIGINT PRIMARY KEY, phone VARCHAR(20))");
        appJdbc.execute("""
                CREATE TABLE app_node (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  binding_code VARCHAR(64) NOT NULL UNIQUE,
                  owner_user_id BIGINT,
                  name VARCHAR(80),
                  status VARCHAR(16),
                  hashrate DECIMAL(18,3),
                  temperature DECIMAL(6,2),
                  daily_earnings DECIMAL(18,8),
                  total_earnings DECIMAL(18,8),
                  last_reported_at TIMESTAMP,
                  bound_at TIMESTAMP,
                  created_at TIMESTAMP,
                  updated_at TIMESTAMP
                )""");
        nodeJdbc.execute("CREATE TABLE app_user (id BIGINT PRIMARY KEY, phone VARCHAR(20), deleted TINYINT DEFAULT 0)");
        nodeJdbc.execute("""
                CREATE TABLE device (
                  id BIGINT PRIMARY KEY,
                  bind_code VARCHAR(64),
                  name VARCHAR(80),
                  status INT,
                  hashrate INT,
                  type INT,
                  user_id BIGINT,
                  last_heartbeat_time TIMESTAMP,
                  bind_time TIMESTAMP
                )""");
        service = new NodeDeviceSyncService(
                appJdbc, "jdbc:h2:mem:node;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", "org.h2.Driver");
    }

    @Test
    void syncByCodeMirrorsUnboundPhysicalDevice() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (1, '13800000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (10, 'JDPHYS01', NULL, 1, 100, 2, NULL)
                """);
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (101, '13800000000')");

        NodeDeviceSyncService.LegacyLookup lookup = service.syncByCode("jdphys01");

        assertNotNull(lookup);
        assertEquals(10L, lookup.device().id());
        assertEquals("JDPHYS01", lookup.device().bindingCode());
        assertNull(lookup.appOwnerUserId());

        Long owner = appJdbc.queryForObject(
                "SELECT owner_user_id FROM app_node WHERE binding_code = 'JDPHYS01'", Long.class);
        assertNull(owner);
        String name = appJdbc.queryForObject(
                "SELECT name FROM app_node WHERE binding_code = 'JDPHYS01'", String.class);
        assertEquals("聚芯节点", name);
    }

    @Test
    void recordBindBackWritesOwnerToLegacyDbAndResolvesOwnerOnNextSync() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (1, '13800000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (10, 'JDPHYS01', NULL, 1, 100, 2, NULL)
                """);
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (101, '13800000000')");

        NodeDeviceSyncService.LegacyLookup lookup = service.syncByCode("JDPHYS01");
        service.recordBindBack(lookup.device(), 101);

        Long legacyOwner = nodeJdbc.queryForObject(
                "SELECT user_id FROM device WHERE id = 10", Long.class);
        assertEquals(1L, legacyOwner);

        NodeDeviceSyncService.LegacyLookup after = service.syncByCode("JDPHYS01");
        assertEquals(101L, after.appOwnerUserId());
        Long appOwner = appJdbc.queryForObject(
                "SELECT owner_user_id FROM app_node WHERE binding_code = 'JDPHYS01'", Long.class);
        assertEquals(101L, appOwner);
    }

    @Test
    void syncForUserListsOwnedPhysicalDevices() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (1, '13800000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (20, 'JDPHYS02', '客厅节点', 1, 250, 2, 1)
                """);
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (101, '13800000000')");

        service.syncForUser(101);

        Long owner = appJdbc.queryForObject(
                "SELECT owner_user_id FROM app_node WHERE binding_code = 'JDPHYS02'", Long.class);
        assertEquals(101L, owner);
    }

    @Test
    void legacyBoundDeviceWithoutAppAccountKeepsOwnerNullButReportsLegacyUserId() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (2, '13900000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (30, 'JDPHYS03', NULL, 1, 50, 2, 2)
                """);

        NodeDeviceSyncService.LegacyLookup lookup = service.syncByCode("JDPHYS03");

        assertNotNull(lookup);
        assertEquals(2L, lookup.device().legacyUserId());
        assertNull(lookup.appOwnerUserId());
    }

    @Test
    void syncByCodeReturnsNullForUnknownCode() {
        assertNull(service.syncByCode("UNKNOWN"));
    }

    @Test
    void virtualDeviceFallsBackToAffiliatedLabel() {
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (50, 'JDVIRT01', NULL, 1, 100, 1, NULL)
                """);

        service.syncByCode("JDVIRT01");

        String name = appJdbc.queryForObject(
                "SELECT name FROM app_node WHERE binding_code = 'JDVIRT01'", String.class);
        assertEquals("挂靠设备", name);
    }

    @Test
    void rebindReassignsAppNodeFromOldOwnerToNewOwner() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (1, '13800000000')");
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (2, '13900000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (60, 'JDREBIND', NULL, 1, 100, 2, 1)
                """);
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (101, '13800000000')");
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (102, '13900000000')");
        appJdbc.update("""
                INSERT INTO app_node (binding_code, owner_user_id, name, status, hashrate)
                VALUES ('JDREBIND', 101, '聚芯节点', 'online', 100)
                """);

        // The 二开后台 rebinds the device to legacy user 2 (APP account 102).
        nodeJdbc.update("UPDATE device SET user_id = 2 WHERE id = 60");

        // The old owner's refresh drops the stale mirror.
        service.syncForUser(101);
        assertNull(ownerOf("JDREBIND"));

        // The new owner's refresh reassigns it.
        service.syncForUser(102);
        assertEquals(102L, ownerOf("JDREBIND"));
    }

    @Test
    void unboundLegacyDeviceIsReleasedFromAppOwner() {
        nodeJdbc.update("INSERT INTO app_user (id, phone) VALUES (1, '13800000000')");
        nodeJdbc.update("""
                INSERT INTO device (id, bind_code, name, status, hashrate, type, user_id)
                VALUES (70, 'JDUNBIND', NULL, 1, 100, 2, 1)
                """);
        appJdbc.update("INSERT INTO app_user_account (id, phone) VALUES (101, '13800000000')");
        appJdbc.update("""
                INSERT INTO app_node (binding_code, owner_user_id, name, status, hashrate)
                VALUES ('JDUNBIND', 101, '聚芯节点', 'online', 100)
                """);

        // The 二开后台 unbinds the device (user_id -> NULL).
        nodeJdbc.update("UPDATE device SET user_id = NULL WHERE id = 70");

        service.syncForUser(101);

        assertNull(ownerOf("JDUNBIND"));
    }

    private Long ownerOf(String code) {
        return appJdbc.queryForObject(
                "SELECT owner_user_id FROM app_node WHERE binding_code = ?", Long.class, code);
    }

    private static JdbcTemplate h2(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        return new JdbcTemplate(dataSource);
    }
}
