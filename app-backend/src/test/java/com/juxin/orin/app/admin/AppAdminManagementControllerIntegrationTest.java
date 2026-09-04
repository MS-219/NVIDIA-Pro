package com.juxin.orin.app.admin;

import com.juxin.orin.app.auth.JwtService;
import com.juxin.orin.app.auth.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppAdminManagementControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM app_admin_audit_log");
        jdbc.update("DELETE FROM app_notice");
        jdbc.update("DELETE FROM app_wallet_ledger");
        jdbc.update("DELETE FROM app_user_account");
    }

    @Test
    void adminCanListUsersPublishNoticeAndAdjustWallet() throws Exception {
        jdbc.update("INSERT INTO app_user_account(phone,nickname,status) VALUES('13800138000','测试用户',1)");
        long userId = jdbc.queryForObject("SELECT id FROM app_user_account WHERE phone='13800138000'", Long.class);
        String token = jwt.issueAdmin("admin");
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].phone").value("13800138000"));
        mockMvc.perform(post("/api/admin/wallet/adjust").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + ",\"amount\":\"12.50\",\"direction\":\"credit\",\"description\":\"测试入账\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(12.5));
        mockMvc.perform(post("/api/admin/notices").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"维护公告\",\"content\":\"节点将在今晚维护\",\"status\":\"published\",\"pinned\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/app/notices").header("Authorization", "Bearer " + jwt.issue(new UserAccount(userId,"13800138000","测试用户", Instant.now()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("维护公告"));
        org.junit.jupiter.api.Assertions.assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM app_admin_audit_log", Long.class));
    }

    @Test
    void appTokenCannotUseAdminEndpoints() throws Exception {
        String token = jwt.issue(new UserAccount(9, "13900139000", "APP", Instant.now()));
        mockMvc.perform(get("/api/admin/overview/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
}
