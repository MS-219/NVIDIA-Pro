package com.juxin.orin.controller;

import com.juxin.orin.entity.Notice;
import com.juxin.orin.service.INoticeService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoticeControllerAccessTest {

    @Test
    void noticesRequireAppLoginBeforeEitherQuery() {
        NoticeController controller = new NoticeController();
        INoticeService notices = mock(INoticeService.class);
        ReflectionTestUtils.setField(controller, "noticeService", notices);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String admin = "Bearer " + JwtUtil.generateToken(1L, "admin", "admin");
        for (String anonymous : new String[]{null, ""}) {
            assertEquals(java.util.List.of(), controller.list(5, anonymous, response).getData());
            assertEquals(403, controller.detail(1L, anonymous, response).getCode());
            assertNull(controller.detail(1L, anonymous, response).getData());
        }
        for (String token : new String[]{"Bearer invalid", admin}) {
            assertEquals(401, controller.list(5, token, response).getCode());
            assertEquals(401, controller.detail(1L, token, response).getCode());
        }
        verifyNoInteractions(notices);
        String token = "Bearer " + JwtUtil.generateToken(2L, "openid", "app");
        Notice notice = new Notice();
        notice.setStatus(1);
        when(notices.getById(1L)).thenReturn(notice);
        assertEquals(200, controller.list(5, token, response).getCode());
        assertSame(notice, controller.detail(1L, token, response).getData());
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        notice.setStatus(0);
        assertNull(controller.detail(1L, token, response).getData());
    }

}
