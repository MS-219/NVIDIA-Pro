package com.juxin.orin.service;

import java.util.Map;

/**
 * AI 创作服务接口
 */
public interface IAiCreationService {

    /**
     * 文生视频 (Vidu)
     * 
     * @param prompt      文本描述
     * @param duration    视频时长（秒）
     * @param aspectRatio 宽高比 (16:9, 9:16, 1:1)
     * @param resolution  分辨率 (720p, 1080p)
     * @return 任务信息 {taskId, status}
     */
    Map<String, Object> textToVideo(String prompt, Integer duration, String aspectRatio, String resolution);

    /**
     * 图生视频 (Vidu)
     * 
     * @param imageUrl    图片URL
     * @param prompt      文本描述
     * @param duration    视频时长（秒）
     * @param aspectRatio 宽高比 (16:9, 9:16, 1:1)
     * @param resolution  分辨率 (720p, 1080p)
     * @return 任务信息
     */
    Map<String, Object> imageToVideo(String imageUrl, String prompt, Integer duration, String aspectRatio,
            String resolution);

    /**
     * 查询视频生成状态
     * 
     * @param taskId 任务ID
     * @return 任务状态和结果
     */
    Map<String, Object> getVideoStatus(String taskId);

    /**
     * 文生图片 (GPT-Image-2)
     * 
     * @param prompt 文本描述
     * @param size   图片尺寸 (如 "1024x1024")
     * @return 生成结果 {imageUrl}
     */
    Map<String, Object> textToImage(String prompt, String size);

    /**
     * 以图生图 (GPT-Image-2)
     * 
     * @param imageUrl 原图URL
     * @param prompt   描述
     * @return 生成结果
     */
    Map<String, Object> imageToImage(String imageUrl, String prompt, String size);

    /**
     * AI 文案生成 (Gemini)
     * 
     * @param prompt       用户的文案需求描述
     * @param copyType     文案类型 (marketing/social/article/slogan/email)
     * @param style        风格 (professional/casual/creative/formal)
     * @param systemPrompt 系统指令（可选）
     * @return 生成结果 {content, thinking}
     */
    Map<String, Object> generateCopywriting(String prompt, String copyType, String style, String systemPrompt);
}
