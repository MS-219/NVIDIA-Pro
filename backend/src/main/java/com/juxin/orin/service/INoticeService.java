package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.Notice;

import java.util.List;

public interface INoticeService extends IService<Notice> {

    /**
     * 获取已发布的公告列表（小程序用）
     */
    List<Notice> getPublishedList(Integer limit);
}
