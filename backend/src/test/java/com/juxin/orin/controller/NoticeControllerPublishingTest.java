package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Notice;
import com.juxin.orin.service.INoticeService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeControllerPublishingTest {

    @Mock
    private INoticeService noticeService;

    @InjectMocks
    private NoticeController controller;

    private final String adminToken = "Bearer " + JwtUtil.generateToken(1L, "admin", "admin", "admin");

    @Test
    void addPublishedNoticeSetsPublishTime() {
        Notice notice = new Notice();
        notice.setStatus(1);
        when(noticeService.save(any(Notice.class))).thenReturn(true);

        Result<String> result = controller.add(notice, adminToken);

        assertEquals(200, result.getCode());
        assertNotNull(notice.getPublishTime());
    }

    @Test
    void publishingDraftThroughUpdateSetsFirstPublishTime() {
        Notice existing = new Notice();
        existing.setId(7L);
        existing.setStatus(0);
        Notice update = new Notice();
        update.setId(7L);
        update.setStatus(1);
        when(noticeService.getById(7L)).thenReturn(existing);
        when(noticeService.updateById(any(Notice.class))).thenReturn(true);

        Result<String> result = controller.update(update, adminToken);

        assertEquals(200, result.getCode());
        assertNotNull(update.getPublishTime());
    }

    @Test
    void editingPublishedNoticeKeepsOriginalPublishTime() {
        LocalDateTime originalPublishTime = LocalDateTime.of(2026, 7, 1, 8, 30);
        Notice existing = new Notice();
        existing.setId(8L);
        existing.setStatus(1);
        existing.setPublishTime(originalPublishTime);
        Notice update = new Notice();
        update.setId(8L);
        update.setStatus(1);
        when(noticeService.getById(8L)).thenReturn(existing);
        when(noticeService.updateById(any(Notice.class))).thenReturn(true);

        Result<String> result = controller.update(update, adminToken);

        assertEquals(200, result.getCode());
        assertEquals(originalPublishTime, update.getPublishTime());
    }

    @Test
    void republishingNoticeKeepsOriginalPublishTime() {
        LocalDateTime originalPublishTime = LocalDateTime.of(2026, 7, 2, 9, 45);
        Notice existing = new Notice();
        existing.setId(9L);
        existing.setStatus(0);
        existing.setPublishTime(originalPublishTime);
        when(noticeService.getById(9L)).thenReturn(existing);
        when(noticeService.updateById(any(Notice.class))).thenReturn(true);

        Result<String> result = controller.publish(9L, adminToken);

        assertEquals(200, result.getCode());
        assertEquals(originalPublishTime, existing.getPublishTime());
    }
}
