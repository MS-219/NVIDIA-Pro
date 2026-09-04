package com.juxin.orin.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.juxin.orin.config.BossKgConfig;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.mapper.WithdrawMapper;
import com.juxin.orin.util.DESUtil;
import com.juxin.orin.util.RSAUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossKgServiceImplProtocolTest {

    private static final String DES_KEY = "12345678";

    @Mock
    private UserContractMapper userContractMapper;

    @Mock
    private WithdrawMapper withdrawMapper;

    @Mock
    private AppUserMapper appUserMapper;

    private HttpServer server;
    private BossKgServiceImpl service;
    private BossKgConfig config;
    private KeyPair merchantKeys;
    private KeyPair platformKeys;

    @BeforeEach
    void setUp() throws Exception {
        merchantKeys = RSAUtil.generateKeyPair();
        platformKeys = RSAUtil.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        config = new BossKgConfig();
        config.setEnabled(true);
        config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/gateway");
        config.setMerId("MERCHANT-1");
        config.setProviderId("10001");
        config.setTaskId("20001");
        config.setDesKey(DES_KEY);
        config.setMerchantPrivateKey(RSAUtil.getPrivateKeyString(merchantKeys.getPrivate()));
        config.setPlatformPublicKey(RSAUtil.getPublicKeyString(platformKeys.getPublic()));
        config.setMiniAppId("wx-test-app");

        service = new BossKgServiceImpl();
        ReflectionTestUtils.setField(service, "bossKgConfig", config);
        ReflectionTestUtils.setField(service, "userContractMapper", userContractMapper);
        ReflectionTestUtils.setField(service, "withdrawMapper", withdrawMapper);
        ReflectionTestUtils.setField(service, "appUserMapper", appUserMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void signsEncryptedRequestDataAndVerifiesSignedResponseData() throws Exception {
        AtomicBoolean requestVerified = new AtomicBoolean(false);
        server.createContext("/gateway", exchange -> {
            try {
                JSONObject request = readJson(exchange);
                requestVerified.set(RSAUtil.verify(
                        request.getStr("reqData"),
                        request.getStr("sign"),
                        RSAUtil.getPublicKeyString(merchantKeys.getPublic())));
                assertEquals("6003", request.getStr("funCode"));

                JSONObject response = signedResponse(request, new JSONObject().set("balance", 12345L));
                writeJson(exchange, response);
            } catch (Exception e) {
                throw new IOException(e);
            }
        });

        Long balance = service.queryBalance();

        assertEquals(12345L, balance);
        assertTrue(requestVerified.get());
    }

    @Test
    void rejectsTamperedSynchronousResponseBeforeDecryptingIt() throws Exception {
        server.createContext("/gateway", exchange -> {
            try {
                JSONObject request = readJson(exchange);
                JSONObject response = signedResponse(request, new JSONObject().set("balance", 12345L));
                response.set("sign", RSAUtil.sign("tampered", RSAUtil.getPrivateKeyString(platformKeys.getPrivate())));
                writeJson(exchange, response);
            } catch (Exception e) {
                throw new IOException(e);
            }
        });

        assertNull(service.queryBalance());
    }

    @Test
    void h5ContractRequestContainsRequiredMiniProgramIdentityFields() throws Exception {
        AtomicReference<JSONObject> captured = new AtomicReference<>();
        server.createContext("/gateway", exchange -> {
            try {
                JSONObject request = readJson(exchange);
                captured.set(JSONUtil.parseObj(DESUtil.decrypt(request.getStr("reqData"), DES_KEY)));
                writeJson(exchange, signedResponse(request,
                        new JSONObject().set("resData", "https://contract.example/sign")));
            } catch (Exception e) {
                throw new IOException(e);
            }
        });

        String url = service.getH5ContractUrl(
                7L, "测试用户", "110101199001011234", "13800138000",
                "6222020000000000", 0, "AQID", "BAUG");

        assertEquals("https://contract.example/sign", url);
        assertEquals("010203", captured.get().getStr("idCardFrontPic"));
        assertEquals("040506", captured.get().getStr("idCardBackPic"));
        assertEquals("wx-test-app", captured.get().getStr("appid"));
        assertEquals("RE_LAUNCH", captured.get().getStr("redirectType"));
    }

    @Test
    void verifiesPaymentCallbackAgainstResDataOnly() throws Exception {
        Withdraw withdraw = new Withdraw();
        withdraw.setId(42L);
        withdraw.setPaymentFailCount(0);
        when(withdrawMapper.selectById(42L)).thenReturn(withdraw);

        JSONObject callbackData = new JSONObject()
                .set("merOrderId", "W42_1234567890")
                .set("orderNo", "PLATFORM-42")
                .set("state", 3)
                .set("amt", 1000L)
                .set("userDueAmt", 990L);

        String result = service.handlePaymentNotify(callback(callbackData).toString());

        assertEquals("SUCCESS", result);
        ArgumentCaptor<Withdraw> captor = ArgumentCaptor.forClass(Withdraw.class);
        verify(withdrawMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
        assertEquals("PLATFORM-42", captor.getValue().getBossKgOrderNo());
    }

    @Test
    void repeatedFailureCallbackDoesNotIncreaseFailureCountAgain() throws Exception {
        Withdraw withdraw = new Withdraw();
        withdraw.setId(42L);
        withdraw.setStatus(4);
        withdraw.setBossKgState(4);
        withdraw.setBossKgOrderNo("PLATFORM-42");
        withdraw.setPaymentFailCount(1);
        when(withdrawMapper.selectById(42L)).thenReturn(withdraw);

        JSONObject callbackData = new JSONObject()
                .set("merOrderId", "W42_1234567890")
                .set("orderNo", "PLATFORM-42")
                .set("state", 4)
                .set("amt", 1000L)
                .set("resMsg", "测试失败");

        assertEquals("SUCCESS", service.handlePaymentNotify(callback(callbackData).toString()));

        ArgumentCaptor<Withdraw> captor = ArgumentCaptor.forClass(Withdraw.class);
        verify(withdrawMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getPaymentFailCount());
    }

    private JSONObject callback(JSONObject data) throws Exception {
        String encrypted = DESUtil.encrypt(data.toString(), DES_KEY);
        return new JSONObject()
                .set("reqId", "notify-1")
                .set("funCode", "6001")
                .set("merId", config.getMerId())
                .set("version", "V1.0")
                .set("resCode", "0000")
                .set("resMsg", "success")
                .set("resData", encrypted)
                .set("sign", RSAUtil.sign(encrypted, RSAUtil.getPrivateKeyString(platformKeys.getPrivate())));
    }

    private JSONObject signedResponse(JSONObject request, JSONObject data) throws Exception {
        String encrypted = DESUtil.encrypt(data.toString(), DES_KEY);
        return new JSONObject()
                .set("reqId", request.getStr("reqId"))
                .set("funCode", request.getStr("funCode"))
                .set("merId", request.getStr("merId"))
                .set("version", request.getStr("version"))
                .set("resCode", "0000")
                .set("resMsg", "success")
                .set("resData", encrypted)
                .set("sign", RSAUtil.sign(encrypted, RSAUtil.getPrivateKeyString(platformKeys.getPrivate())));
    }

    private JSONObject readJson(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        return JSONUtil.parseObj(new String(body, StandardCharsets.UTF_8));
    }

    private void writeJson(HttpExchange exchange, JSONObject body) throws IOException {
        byte[] response = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
