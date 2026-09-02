package com.juxin.orin.app.device;

import com.juxin.orin.app.auth.JwtService;
import com.juxin.orin.app.auth.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppNodeControllerIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwtService;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clearNodes() {
        jdbc.update("DELETE FROM app_node");
    }

    @Test
    void bindsOnlyAProvisionedNodeAndDerivesOwnerFromBearerToken() throws Exception {
        provision("NODE-001", null, "预置节点", "pending", "120.500", null, "1.25", "8.50");
        String token = tokenFor(101);

        // userId in the payload is deliberately ignored; the verified token
        // remains the only source of ownership.
        mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" node-001 \",\"name\":\"客厅节点\",\"userId\":9999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("NODE-001"))
                .andExpect(jsonPath("$.data.name").value("客厅节点"))
                .andExpect(jsonPath("$.data.status").value("pending"));

        Long owner = jdbc.queryForObject("SELECT owner_user_id FROM app_node WHERE binding_code = 'NODE-001'",
                Long.class);
        assertEquals(101L, owner);

        mockMvc.perform(get("/api/app/devices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hashrate").value(120.5))
                .andExpect(jsonPath("$.data[0].dailyEarnings").value(1.25))
                .andExpect(jsonPath("$.data[0].totalEarnings").value(8.5));

        mockMvc.perform(get("/api/app/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.online").value(0))
                .andExpect(jsonPath("$.data.totalHashrate").value(120.5))
                .andExpect(jsonPath("$.data.todayEarnings").value(1.25))
                .andExpect(jsonPath("$.data.totalEarnings").value(8.5));
    }

    @Test
    void returns404ForUnknownCodeAnd409ForAlreadyClaimedCode() throws Exception {
        String firstToken = tokenFor(201);
        mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MISSING-001\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        provision("NODE-002", 201L, "已绑定", "online", "1", "55.20", "2", "3");
        String secondToken = tokenFor(202);
        mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NODE-002\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void rejectsUnsafeBindingCodeBeforeDatabaseLookup() throws Exception {
        mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + tokenFor(250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NODE;DROP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteReleasesOnlyTheCurrentUsersNode() throws Exception {
        provision("NODE-003", null, "可释放", "online", "10", "42", "0.5", "4");
        String ownerToken = tokenFor(301);
        String otherToken = tokenFor(302);

        String bindResponse = mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NODE-003\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode bindRoot = objectMapper.readTree(bindResponse);
        long nodeId = bindRoot.path("data").path("id").asLong();

        mockMvc.perform(delete("/api/app/devices/" + nodeId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/app/devices/" + nodeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM app_node WHERE owner_user_id = 301", Long.class));

        // The provisioned row remains available for a new APP account.
        mockMvc.perform(post("/api/app/devices/bind")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NODE-003\",\"name\":\"新账户节点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新账户节点"));
    }

    @Test
    void protectedDeviceEndpointsRequireBearerToken() throws Exception {
        mockMvc.perform(get("/api/app/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/app/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/app/earnings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void earningsReturnsSnapshotListAndSummaryForOwnedNodes() throws Exception {
        provision("NODE-004", 401L, "收益节点", "online", "2", "40", "3.50", "12.75");
        mockMvc.perform(get("/api/app/earnings")
                        .header("Authorization", "Bearer " + tokenFor(401)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].deviceId").isNumber())
                .andExpect(jsonPath("$.data.todayEarnings").value(3.5))
                .andExpect(jsonPath("$.data.totalEarnings").value(12.75));
    }

    @Test
    void earningsCanBeEmptyBeforeAnyNodeReportsCounters() throws Exception {
        provision("NODE-005", 402L, "等待上报", "pending", "0", null, "0", "0");
        mockMvc.perform(get("/api/app/earnings")
                        .header("Authorization", "Bearer " + tokenFor(402)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.todayEarnings").value(0))
                .andExpect(jsonPath("$.data.totalEarnings").value(0));
    }

    private String tokenFor(long userId) {
        return jwtService.issue(new UserAccount(userId, "13800000000", "测试用户", Instant.now()));
    }

    private void provision(String code, Long owner, String name, String status,
                           String hashrate, String temperature, String daily, String total) {
        jdbc.update("""
                INSERT INTO app_node
                    (binding_code, owner_user_id, name, status, hashrate, temperature,
                     daily_earnings, total_earnings)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, code, owner, name, status, hashrate,
                temperature == null ? null : new java.math.BigDecimal(temperature),
                new java.math.BigDecimal(daily), new java.math.BigDecimal(total));
    }
}
