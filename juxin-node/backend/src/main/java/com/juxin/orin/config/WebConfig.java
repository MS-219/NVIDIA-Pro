package com.juxin.orin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@org.springframework.lang.NonNull ResourceHandlerRegistry registry) {
        // 映射预览内部上传路径
        String uploadPath = System.getProperty("user.dir") + "/uploads/";
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 将 /uploads/** 映射到本地物理路径
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);

        String upgradePackagePath = System.getProperty("user.dir") + "/uploads/agent-upgrades/";
        registry.addResourceHandler("/api/upgrade-packages/**")
                .addResourceLocations("file:" + upgradePackagePath);

        // 设备侧远程自升级包：走 /api 前缀，确保 Nginx 会代理到后端。
        String agentPath = System.getenv().getOrDefault(
                "ORIN_AGENT_ASSET_PATH",
                System.getProperty("user.dir") + "/../image/agent/");
        if (!agentPath.endsWith("/")) {
            agentPath += "/";
        }
        registry.addResourceHandler("/api/agent/**")
                .addResourceLocations("file:" + agentPath);
    }
}
