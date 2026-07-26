package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.Notice;
import com.juxin.orin.mapper.NoticeMapper;
import com.juxin.orin.service.INoticeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    @Override
    public List<Notice> getPublishedList(Integer limit) {
        return this.lambdaQuery()
                .eq(Notice::getStatus, 1) // 已发布
                .orderByDesc(Notice::getSort)
                .orderByDesc(Notice::getPublishTime)
                .last(limit != null ? "LIMIT " + limit : "")
                .list();
    }
}
