package com.juxin.orin.app.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    SmsGateway smsGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sendsCodeLogsInAndReadsProtectedProfile() throws Exception {
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MockSmsGateway mock = (MockSmsGateway) smsGateway;
        String loginBody = objectMapper.createObjectNode()
                .put("phone", mock.lastPhone())
                .put("code", mock.lastCode())
                .toString();
        String response = mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String token = root.path("data").path("token").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.token").value(""));
    }

    @Test
    void rejectsProtectedRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void persistsFailedCodeAttemptAfterLoginTransactionRollsBack() throws Exception {
        String phone = "13900139000";
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("phone", phone).toString()))
                .andExpect(status().isOk());

        MockSmsGateway mock = (MockSmsGateway) smsGateway;
        String wrongCode = "000000".equals(mock.lastCode()) ? "000001" : "000000";
        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("phone", phone)
                                .put("code", wrongCode)
                                .toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Integer attempts = jdbc.queryForObject(
                "SELECT attempts FROM sms_login_challenge WHERE phone = ? ORDER BY id DESC LIMIT 1",
                Integer.class, phone);
        assertEquals(1, attempts);
    }

    @Test
    void appliesTheSamePhoneNormalizationToSendAndLogin() throws Exception {
        String formattedPhone = "+86 139-0013-9111";
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("phone", formattedPhone).toString()))
                .andExpect(status().isOk());

        MockSmsGateway mock = (MockSmsGateway) smsGateway;
        String response = mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("phone", formattedPhone)
                                .put("code", mock.lastCode())
                                .toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals("13900139111", objectMapper.readTree(response).path("data").path("phone").asText());
    }

    @Test
    void updatesNicknameOnlyForTheAuthenticatedAccount() throws Exception {
        String phone = "13700137000";
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("phone", phone).toString()))
                .andExpect(status().isOk());
        MockSmsGateway mock = (MockSmsGateway) smsGateway;
        String loginResponse = mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("phone", phone).put("code", mock.lastCode()).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).path("data").path("token").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"userId\":9999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }
}
