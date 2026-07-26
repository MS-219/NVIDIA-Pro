package com.juxin.orin.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.juxin.orin.service.IAiCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 创作服务实现
 */
@Service
public class AiCreationServiceImpl implements IAiCreationService {

    private static final Logger log = LoggerFactory.getLogger(AiCreationServiceImpl.class);

    // Vidu 视频生成配置
    @Value("${ai.vidu.api-key}")
    private String viduApiKey;

    @Value("${ai.vidu.base-url}")
    private String viduBaseUrl;

    @Value("${ai.vidu.model}")
    private String viduModel;

    // GPT-Image-2 图片生成配置
    @Value("${ai.gpt-image.api-key}")
    private String gptImageApiKey;

    @Value("${ai.gpt-image.base-url}")
    private String gptImageBaseUrl;

    @Value("${ai.gpt-image.model}")
    private String gptImageModel;

    // ==================== Vidu 视频生成 ====================

    @Override
    public Map<String, Object> textToVideo(String prompt, Integer duration, String aspectRatio, String resolution) {
        Map<String, Object> result = new HashMap<>();

        try {
            int finalDuration = normalizeVideoDuration(duration);
            String finalAspectRatio = normalizeVideoAspectRatio(aspectRatio);
            String finalResolution = normalizeVideoResolution(resolution);

            log.info("调用 Vidu 文生视频 API: model={}, prompt={}, duration={}, resolution={}, aspect_ratio={}",
                    viduModel, prompt, finalDuration, finalResolution, finalAspectRatio);

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", viduModel);
            requestBody.set("prompt", prompt);
            requestBody.set("duration", finalDuration);
            requestBody.set("resolution", finalResolution);
            requestBody.set("aspect_ratio", finalAspectRatio);

            HttpResponse response = HttpRequest.post(normalizeBaseUrl(viduBaseUrl) + "/videos")
                    .header("Authorization", "Bearer " + viduApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(60000)
                    .execute();

            String responseBody = response.body();
            log.info("Vidu 文生视频响应状态: {}, 响应体: {}", response.getStatus(), responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                String taskId = json.getStr("id");
                if (taskId == null || taskId.isBlank()) {
                    result.put("success", false);
                    result.put("error", "Vidu 未返回任务ID: " + responseBody);
                    return result;
                }
                result.put("success", true);
                result.put("taskId", taskId);
                result.put("status", normalizeVideoStatus(json.getStr("status", "queued")));
                log.info("Vidu 文生视频任务已创建: taskId={}", taskId);
            } else {
                result.put("success", false);
                result.put("error", "视频生成请求失败(HTTP " + response.getStatus() + "): " + responseBody);
                log.error("Vidu 文生视频 API 调用失败: status={}, body={}", response.getStatus(), responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "视频生成异常: " + e.getMessage());
            log.error("Vidu 文生视频异常", e);
        }

        return result;
    }

    @Override
    public Map<String, Object> imageToVideo(String imageUrl, String prompt, Integer duration, String aspectRatio,
            String resolution) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<String> images = parseImageUrls(imageUrl, 1);
            if (images.isEmpty()) {
                result.put("success", false);
                result.put("error", "未提供参考图片");
                return result;
            }

            int finalDuration = normalizeVideoDuration(duration);
            String finalResolution = normalizeVideoResolution(resolution);

            log.info("调用 Vidu 图生视频 API: model={}, prompt={}, duration={}, resolution={}, image={}",
                    viduModel, prompt, finalDuration, finalResolution, images.get(0));

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", viduModel);
            if (prompt != null && !prompt.isBlank()) {
                requestBody.set("prompt", prompt);
            }
            requestBody.set("duration", finalDuration);
            requestBody.set("resolution", finalResolution);
            JSONArray imagesArray = new JSONArray();
            imagesArray.add(images.get(0));
            requestBody.set("images", imagesArray);

            HttpResponse response = HttpRequest.post(normalizeBaseUrl(viduBaseUrl) + "/videos")
                    .header("Authorization", "Bearer " + viduApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(60000)
                    .execute();

            String responseBody = response.body();
            log.info("Vidu 图生视频响应状态: {}, 响应体: {}", response.getStatus(), responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                String taskId = json.getStr("id");
                if (taskId == null || taskId.isBlank()) {
                    result.put("success", false);
                    result.put("error", "Vidu 未返回任务ID: " + responseBody);
                    return result;
                }
                result.put("success", true);
                result.put("taskId", taskId);
                result.put("status", normalizeVideoStatus(json.getStr("status", "queued")));
                log.info("Vidu 图生视频任务已创建: taskId={}", taskId);
            } else {
                result.put("success", false);
                result.put("error", "图生视频请求失败(HTTP " + response.getStatus() + "): " + responseBody);
                log.error("Vidu 图生视频 API 调用失败: status={}, body={}", response.getStatus(), responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "图生视频异常: " + e.getMessage());
            log.error("Vidu 图生视频异常", e);
        }

        return result;
    }

    @Override
    public Map<String, Object> getVideoStatus(String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            HttpResponse response = HttpRequest.get(normalizeBaseUrl(viduBaseUrl) + "/videos/" + taskId)
                    .header("Authorization", "Bearer " + viduApiKey)
                    .timeout(10000)
                    .execute();

            String responseBody = response.body();
            log.info("查询 Vidu 视频任务状态: taskId={}, response={}", taskId, responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                String status = normalizeVideoStatus(json.getStr("status"));
                result.put("success", true);
                result.put("taskId", taskId);
                result.put("status", status);
                result.put("progress", json.getStr("progress"));

                if ("completed".equals(status)) {
                    String videoUrl = json.getStr("video_url");
                    String finalVideoUrl = downloadAndSave(videoUrl, ".mp4");
                    result.put("videoUrl", finalVideoUrl);
                }

                if ("failed".equals(status)) {
                    result.put("failReason", extractErrorMessage(json, "视频生成失败"));
                }
            } else {
                result.put("success", false);
                result.put("error", "查询失败(HTTP " + response.getStatus() + "): " + responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ==================== GPT-Image-2 图片生成 ====================

    @Override
    public Map<String, Object> textToImage(String prompt, String size) {
        Map<String, Object> result = new HashMap<>();

        try {
            String aspectRatio = mapImageSizeToAspectRatio(size, "1:1");

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", gptImageModel);
            requestBody.set("prompt", prompt);
            requestBody.set("aspect_ratio", aspectRatio);
            requestBody.set("response_format", "url");
            requestBody.set("resolution", "1K");

            log.info("调用 GPT-Image-2 文生图 API: model={}, prompt={}, aspect_ratio={}",
                    gptImageModel, prompt, aspectRatio);

            HttpResponse response = HttpRequest.post(normalizeBaseUrl(gptImageBaseUrl) + "/images/generations")
                    .header("Authorization", "Bearer " + gptImageApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(120000)
                    .execute();

            String responseBody = response.body();
            log.info("GPT-Image-2 文生图响应: status={}, body={}", response.getStatus(), responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                String finalImageUrl = extractAndSaveGeneratedImage(json);
                if (finalImageUrl != null && !finalImageUrl.isBlank()) {
                    result.put("success", true);
                    result.put("imageUrl", finalImageUrl);
                    log.info("GPT-Image-2 文生图成功: url={}", finalImageUrl);
                } else {
                    result.put("success", false);
                    result.put("error", extractErrorMessage(json, "未返回图片数据，响应: " + responseBody));
                    log.error("GPT-Image-2 未返回图片数据: {}", responseBody);
                }
            } else {
                result.put("success", false);
                result.put("error", "图片生成请求失败(HTTP " + response.getStatus() + "): " + responseBody);
                log.error("GPT-Image-2 文生图 API 调用失败: status={}, body={}", response.getStatus(), responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "图片生成异常: " + e.getMessage());
            log.error("GPT-Image-2 文生图异常", e);
        }

        return result;
    }

    @Override
    public Map<String, Object> imageToImage(String imageUrl, String prompt, String size) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<String> images = parseImageUrls(imageUrl, 16);
            if (images.isEmpty()) {
                result.put("success", false);
                result.put("error", "未提供参考图片");
                return result;
            }

            String aspectRatio = mapImageSizeToAspectRatio(size, "auto");

            log.info("调用 GPT-Image-2 图生图 API: model={}, prompt={}, aspect_ratio={}, image_count={}",
                    gptImageModel, prompt, aspectRatio, images.size());

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", gptImageModel);
            requestBody.set("prompt", prompt);
            requestBody.set("aspect_ratio", aspectRatio);
            requestBody.set("response_format", "url");
            requestBody.set("resolution", "1K");
            JSONArray imageArray = new JSONArray();
            for (String image : images) {
                imageArray.add(image);
            }
            requestBody.set("image", imageArray);

            HttpResponse response = HttpRequest.post(normalizeBaseUrl(gptImageBaseUrl) + "/images/generations")
                    .header("Authorization", "Bearer " + gptImageApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(120000)
                    .execute();

            String responseBody = response.body();
            log.info("GPT-Image-2 图生图响应状态: {}, 响应体: {}", response.getStatus(), responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                String finalImageUrl = extractAndSaveGeneratedImage(json);
                if (finalImageUrl != null && !finalImageUrl.isBlank()) {
                    result.put("success", true);
                    result.put("imageUrl", finalImageUrl);
                    log.info("GPT-Image-2 图生图成功: url={}", finalImageUrl);
                } else {
                    result.put("success", false);
                    result.put("error", extractErrorMessage(json, "未返回图片数据，响应内容: " + responseBody));
                }
            } else {
                result.put("success", false);
                result.put("error", "图生图请求失败(HTTP " + response.getStatus() + "): " + responseBody);
                log.error("GPT-Image-2 图生图 API 调用失败: status={}, body={}", response.getStatus(), responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "图生图异常: " + e.getMessage());
            log.error("GPT-Image-2 图生图异常", e);
        }

        return result;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int normalizeVideoDuration(Integer duration) {
        if (duration == null) {
            return 5;
        }
        return Math.max(1, Math.min(duration, 16));
    }

    private String normalizeVideoResolution(String resolution) {
        if ("540p".equals(resolution) || "720p".equals(resolution) || "1080p".equals(resolution)) {
            return resolution;
        }
        return "720p";
    }

    private String normalizeVideoAspectRatio(String aspectRatio) {
        if ("9:16".equals(aspectRatio) || "1:1".equals(aspectRatio)
                || "3:4".equals(aspectRatio) || "4:3".equals(aspectRatio)) {
            return aspectRatio;
        }
        return "16:9";
    }

    private String normalizeVideoStatus(String status) {
        if (status == null || status.isBlank()) {
            return "processing";
        }
        if ("success".equals(status)) {
            return "completed";
        }
        if ("pending".equals(status)) {
            return "queued";
        }
        return status;
    }

    private String mapImageSizeToAspectRatio(String size, String defaultValue) {
        if (size == null || size.isBlank()) {
            return defaultValue;
        }
        switch (size.trim()) {
            case "1024x1024":
            case "1:1":
                return "1:1";
            case "1792x1024":
            case "16:9":
                return "16:9";
            case "1024x1792":
            case "9:16":
                return "9:16";
            case "4:3":
            case "3:4":
            case "auto":
                return size.trim();
            default:
                return defaultValue;
        }
    }

    private List<String> parseImageUrls(String imageUrl, int maxCount) {
        List<String> result = new ArrayList<>();
        if (imageUrl == null || imageUrl.isBlank()) {
            return result;
        }
        String[] urls = imageUrl.split(",");
        for (String rawUrl : urls) {
            if (result.size() >= maxCount) {
                break;
            }
            String url = rawUrl == null ? "" : rawUrl.trim();
            if (!url.isEmpty()) {
                result.add(toPublicImageUrl(url));
            }
        }
        return result;
    }

    private String toPublicImageUrl(String url) {
        if (url.startsWith("/uploads/")) {
            return System.getenv().getOrDefault("ORIN_PUBLIC_BASE_URL", "https://orin-api.example.invalid") + url;
        }
        return url;
    }

    private String extractAndSaveGeneratedImage(JSONObject json) {
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return null;
        }
        JSONObject first = data.getJSONObject(0);
        String imageUrl = first.getStr("url");
        if (imageUrl != null && !imageUrl.isBlank()) {
            return downloadAndSave(imageUrl, ".png");
        }
        String b64 = first.getStr("b64_json");
        if (b64 != null && !b64.isBlank()) {
            return saveBase64Image(b64);
        }
        return null;
    }

    private String saveBase64Image(String b64) {
        try {
            String raw = b64.trim();
            int commaIndex = raw.indexOf(',');
            if (raw.startsWith("data:") && commaIndex >= 0) {
                raw = raw.substring(commaIndex + 1);
            }
            byte[] imageBytes = Base64.getDecoder().decode(raw);
            String uploadPath = System.getProperty("user.dir") + "/uploads/";
            java.io.File uploadDir = new java.io.File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            String fileName = java.util.UUID.randomUUID().toString() + ".png";
            java.io.File destFile = new java.io.File(uploadDir, fileName);
            java.nio.file.Files.write(destFile.toPath(), imageBytes);
            return "/uploads/" + fileName;
        } catch (Exception e) {
            log.error("保存 base64 图片失败", e);
            return null;
        }
    }

    private String extractErrorMessage(JSONObject json, String fallback) {
        if (json == null) {
            return fallback;
        }
        String message = json.getStr("message");
        if (message != null && !message.isBlank()) {
            return message;
        }
        String failReason = json.getStr("fail_reason");
        if (failReason != null && !failReason.isBlank()) {
            return failReason;
        }
        JSONObject error = json.getJSONObject("error");
        if (error != null) {
            String errorMessage = error.getStr("message");
            if (errorMessage != null && !errorMessage.isBlank()) {
                return errorMessage;
            }
            String errorCode = error.getStr("code");
            if (errorCode != null && !errorCode.isBlank()) {
                return errorCode;
            }
        }
        return fallback;
    }

    // ==================== Gemini AI 文案生成 ====================

    @Value("${ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${ai.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.model}")
    private String geminiModel;

    @Override
    public Map<String, Object> generateCopywriting(String prompt, String copyType, String style, String systemPrompt) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 构建系统指令
            String finalSystemPrompt = buildSystemPrompt(copyType, style, systemPrompt);

            // 构建请求体
            JSONObject requestBody = new JSONObject();

            // 系统指令 - parts 必须是数组
            if (finalSystemPrompt != null && !finalSystemPrompt.isEmpty()) {
                JSONObject systemInstruction = new JSONObject();
                JSONArray systemParts = new JSONArray();
                JSONObject systemTextPart = new JSONObject();
                systemTextPart.set("text", finalSystemPrompt);
                systemParts.add(systemTextPart);
                systemInstruction.set("parts", systemParts);
                requestBody.set("system_instruction", systemInstruction);
            }

            // 用户内容
            JSONArray contents = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.set("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.set("text", prompt);
            parts.add(textPart);
            userContent.set("parts", parts);
            contents.add(userContent);
            requestBody.set("contents", contents);

            // 生成配置
            JSONObject generationConfig = new JSONObject();
            generationConfig.set("temperature", 0.9);
            generationConfig.set("topP", 0.95);
            generationConfig.set("maxOutputTokens", 8192);
            requestBody.set("generationConfig", generationConfig);

            String apiUrl = geminiBaseUrl + "/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

            log.info("调用 Gemini API: model={}, copyType={}, style={}", geminiModel, copyType, style);
            log.debug("Gemini 请求体: {}", requestBody.toString());

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(120000)
                    .execute();

            String responseBody = response.body();
            log.info("Gemini API 响应状态: {}", response.getStatus());
            // 打印完整响应以便调试
            log.info("Gemini API 响应体: {}", responseBody);

            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(responseBody);
                JSONArray candidates = json.getJSONArray("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    JSONObject content = firstCandidate.getJSONObject("content");

                    if (content != null) {
                        JSONArray responseParts = content.getJSONArray("parts");
                        if (responseParts != null && !responseParts.isEmpty()) {
                            StringBuilder fullContent = new StringBuilder();
                            StringBuilder thinkingContent = new StringBuilder();

                            for (int i = 0; i < responseParts.size(); i++) {
                                JSONObject part = responseParts.getJSONObject(i);
                                String text = part.getStr("text");
                                if (text != null) {
                                    // 检查是否是思考过程（thinking model 特性）
                                    if (text.startsWith("<thinking>") || part.containsKey("thought")) {
                                        thinkingContent.append(text);
                                    } else {
                                        fullContent.append(text);
                                    }
                                }
                            }

                            result.put("success", true);
                            result.put("content", fullContent.toString().trim());
                            if (thinkingContent.length() > 0) {
                                result.put("thinking", thinkingContent.toString().trim());
                            }

                            // 提取 token 使用情况
                            JSONObject usageMetadata = json.getJSONObject("usageMetadata");
                            if (usageMetadata != null) {
                                result.put("promptTokens", usageMetadata.getInt("promptTokenCount", 0));
                                result.put("outputTokens", usageMetadata.getInt("candidatesTokenCount", 0));
                            }

                            log.info("Gemini 文案生成成功，内容长度: {}", fullContent.length());
                        } else {
                            result.put("success", false);
                            result.put("error", "未返回文案内容");
                        }
                    } else {
                        result.put("success", false);
                        result.put("error", "响应格式异常，无 content");
                    }
                } else {
                    // 检查是否被安全过滤
                    JSONObject promptFeedback = json.getJSONObject("promptFeedback");
                    if (promptFeedback != null) {
                        String blockReason = promptFeedback.getStr("blockReason");
                        result.put("success", false);
                        result.put("error", "内容被过滤: " + (blockReason != null ? blockReason : "安全限制"));
                    } else {
                        result.put("success", false);
                        result.put("error", "未返回候选内容");
                    }
                }
            } else {
                result.put("success", false);
                result.put("error", "AI 文案生成请求失败(HTTP " + response.getStatus() + "): " + responseBody);
                log.error("Gemini API 调用失败: status={}, body={}", response.getStatus(), responseBody);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "AI 文案生成异常: " + e.getMessage());
            log.error("Gemini 文案生成异常", e);
        }

        return result;
    }

    /**
     * 构建系统指令
     */
    private String buildSystemPrompt(String copyType, String style, String customPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的 AI 文案创作专家，擅长各类营销文案、社交媒体文案、文章撰写等。");
        sb.append("你需要根据用户的需求，创作出高质量、吸引人的文案内容。\n\n");

        // 根据文案类型添加指令
        if (copyType != null) {
            switch (copyType) {
                case "marketing":
                    sb.append("【文案类型】营销推广文案\n");
                    sb.append("- 突出产品/服务的核心卖点\n");
                    sb.append("- 使用具有吸引力的标题和开头\n");
                    sb.append("- 包含清晰的行动号召（CTA）\n");
                    break;
                case "social":
                    sb.append("【文案类型】社交媒体文案\n");
                    sb.append("- 内容简洁有力，适合快速阅读\n");
                    sb.append("- 增加互动性，鼓励评论和分享\n");
                    sb.append("- 可适当使用表情符号增加趣味性\n");
                    break;
                case "article":
                    sb.append("【文案类型】文章/博客\n");
                    sb.append("- 结构清晰，有明确的开头、正文、结尾\n");
                    sb.append("- 内容详实，有深度和价值\n");
                    sb.append("- 适当使用小标题分段\n");
                    break;
                case "slogan":
                    sb.append("【文案类型】广告语/口号\n");
                    sb.append("- 简洁有力，朗朗上口\n");
                    sb.append("- 突出品牌特色和核心价值\n");
                    sb.append("- 具有记忆点和传播性\n");
                    break;
                case "email":
                    sb.append("【文案类型】邮件文案\n");
                    sb.append("- 主题行吸引人，提高打开率\n");
                    sb.append("- 内容清晰，重点突出\n");
                    sb.append("- 包含明确的行动呼吁\n");
                    break;
                default:
                    sb.append("【文案类型】通用文案\n");
            }
        }

        // 根据风格添加指令
        if (style != null) {
            sb.append("\n【文案风格】");
            switch (style) {
                case "professional":
                    sb.append("专业正式 - 用词专业、语气严谨、内容权威\n");
                    break;
                case "casual":
                    sb.append("轻松随意 - 语气亲切、用词通俗、接地气\n");
                    break;
                case "creative":
                    sb.append("创意新颖 - 角度独特、表达有创意、令人眼前一亮\n");
                    break;
                case "formal":
                    sb.append("正式严肃 - 适合官方场合、用词规范、格式标准\n");
                    break;
                case "humorous":
                    sb.append("幽默风趣 - 轻松诙谐、带有趣味性、令人会心一笑\n");
                    break;
                default:
                    sb.append("自然流畅\n");
            }
        }

        // 添加自定义指令
        if (customPrompt != null && !customPrompt.isEmpty()) {
            sb.append("\n【特殊要求】\n");
            sb.append(customPrompt);
        }

        sb.append("\n\n请直接输出文案内容，不需要额外的解释说明。");

        return sb.toString();
    }

    /**
     * 下载并保存文件到本地 uploads 目录
     */
    private String downloadAndSave(String url, String defaultSuffix) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            // 定义保存目录
            String uploadPath = System.getProperty("user.dir") + "/uploads/";
            java.io.File uploadDir = new java.io.File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 获取文件后缀
            String suffix = defaultSuffix;
            if (url.contains(".")) {
                String potentialSuffix = url.substring(url.lastIndexOf("."));
                // 简单的后缀校验，避免获取到如 .com, .cn 等域名后缀或 url 参数
                if (potentialSuffix.length() <= 5 && potentialSuffix.matches("\\.[a-zA-Z0-9]+")) {
                    suffix = potentialSuffix;
                }
            }
            // 剔除后缀中的 url 参数 (例如 .png?v=123)
            if (suffix.contains("?")) {
                suffix = suffix.substring(0, suffix.indexOf("?"));
            }

            // 生成文件名
            String fileName = java.util.UUID.randomUUID().toString() + suffix;
            java.io.File destFile = new java.io.File(uploadDir, fileName);

            log.info("开始下载文件: url={}, dest={}", url, destFile.getAbsolutePath());
            long size = cn.hutool.http.HttpUtil.downloadFile(url, destFile);

            if (size > 0) {
                log.info("文件下载成功, 大小: {}, 相对路径: /uploads/{}", size, fileName);
                return "/uploads/" + fileName;
            } else {
                log.warn("文件下载大小为0: {}", url);
            }
        } catch (Exception e) {
            log.error("文件下载保存失败: url={}", url, e);
        }
        // 如果下载失败，返回原始 URL，至少让用户尝试访问（虽然可能访问不了）
        return url;
    }
}
