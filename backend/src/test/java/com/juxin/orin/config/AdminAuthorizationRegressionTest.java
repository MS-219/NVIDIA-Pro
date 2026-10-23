package com.juxin.orin.config;

import com.juxin.orin.controller.AdminComputeJobController;
import com.juxin.orin.controller.AdminOrinDeviceController;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.ISysUserService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAuthorizationRegressionTest {
    private ISysUserService users;
    private IDeviceService devices;
    private IComputeJobService jobs;
    private AdminOperationLogFilter filter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(ISysUserService.class);
        devices = mock(IDeviceService.class);
        jobs = mock(IComputeJobService.class);
        filter = new AdminOperationLogFilter(new AdminAuthValidator(users));
        AdminOrinDeviceController deviceController = new AdminOrinDeviceController();
        ReflectionTestUtils.setField(deviceController, "deviceService", devices);
        AdminComputeJobController jobController = new AdminComputeJobController();
        ReflectionTestUtils.setField(jobController, "computeJobService", jobs);
        mvc = MockMvcBuilders.standaloneSetup(deviceController, jobController)
                .addFilters(filter).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/sl/devices/list", "/api/%61dmin/sl/devices/list",
            "/api/%61%64%6din/sl/devices/list", "/api/admin/sl/devices/stats",
            "/api/%61dmin/sl/devices/stats", "/%61pi/%61dmin/sl/devices/list"
    })
    void anonymousDeviceReadsNeverReachServices(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isUnauthorized());
        verifyNoInteractions(devices, jobs, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/device-tasks/dispatch", "/api/%61dmin/device-tasks/dispatch",
            "/api/%61%64%6d%69%6e/device-tasks/dispatch"
    })
    void anonymousDispatchCannotCreateAJob(String path) throws Exception {
        mvc.perform(post(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskType\":\"python\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs, devices, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin;ignored/sl/devices/list", "/api//admin/sl/devices/list",
            "/api/public/../admin/sl/devices/list", "/api/%2561dmin/sl/devices/list",
            "/api/%zz/admin/sl/devices/list", "/api/admin%3Bignored/sl/devices/list",
            "/api/%2e%2e/admin/sl/devices/list", "/api/admin\\sl/devices/list"
    })
    void ambiguousPathsFailClosedBeforeRouting(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertEquals(400, response.getStatus());
        assertFalse(called.get());
        verifyNoInteractions(devices, jobs, users);
    }

    @Test
    void contextPathDoesNotHideAdminRoute() throws Exception {
        mvc.perform(get(URI.create("/console/api/%61dmin/sl/devices/list"))
                .contextPath("/console")).andExpect(status().isUnauthorized());
        verifyNoInteractions(devices, jobs, users);
    }

    @Test
    void invalidAndAppTokensCannotDispatch() throws Exception {
        for (String token : new String[]{"invalid", JwtUtil.generateToken(1L, "app-user", "app")}) {
            mvc.perform(post(URI.create("/api/%61dmin/device-tasks/dispatch"))
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"taskType\":\"python\"}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(jobs, devices, users);
    }

    @Test
    void factoryCannotDispatchThroughEncodedPath() throws Exception {
        String token = adminToken("factory");
        mvc.perform(post(URI.create("/api/%61dmin/device-tasks/dispatch"))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskType\":\"python\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs, devices);
    }

    @Test
    void authenticatedAdminCanReadStatisticsThroughEncodedPath() throws Exception {
        String token = adminToken("admin");
        when(jobs.getStatistics()).thenReturn(Map.of("total", 0));
        mvc.perform(get(URI.create("/api/%61dmin/device-tasks/statistics"))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(jobs).getStatistics();
    }

    @Test
    void authenticatedAdminCanStillDispatch() throws Exception {
        String token = adminToken("admin");
        when(jobs.save(any(ComputeJob.class))).thenReturn(true);
        mvc.perform(post("/api/admin/device-tasks/dispatch")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskType\":\"python\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(jobs).save(any(ComputeJob.class));
    }

    @Test
    void deletedAdminCannotReadDevices() throws Exception {
        String token = JwtUtil.generateToken(99L, "deleted-admin", "admin", "admin");
        mvc.perform(get(URI.create("/api/%61dmin/sl/devices/list"))
                .header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs, devices);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/device/%65xport-sn", "/api/%75ser/updateAsset",
            "/api/%73ettings/earnings", "/api/notice/%61dmin/list"})
    void encodedAdminRoutesOutsideAdminPrefixAlsoRequireAuthentication(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertEquals(401, response.getStatus());
        assertFalse(called.get());
        verifyNoInteractions(users, devices, jobs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/info", "/api/device/export-sn", "/api/device/%65xport-sn"})
    void factoryKeepsItsAllowedRoutes(String path) throws Exception {
        String token = adminToken("factory");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertTrue(called.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/login", "/api/health", "/api/edge/report", "/api/app/devices"})
    void publicAndDevicePathsRemainOutsideAdminAuthentication(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertTrue(called.get());
        verifyNoInteractions(users);
    }

    private String adminToken(String role) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("test-admin");
        user.setRole(role);
        when(users.getById(1L)).thenReturn(user);
        return JwtUtil.generateToken(1L, "test-admin", "admin", role);
    }
}
