package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.entity.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户反馈 Mapper
 */
@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
