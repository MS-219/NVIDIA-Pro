package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器 - 仅允许安全的图片文件
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final String PUBLIC_FILE_BASE = System.getenv().getOrDefault("ORIN_PUBLIC_BASE_URL", "https://orin-api.example.invalid");
    private final String uploadPath = System.getProperty("user.dir") + "/uploads/";

    /** 允许的图片扩展名白名单 */
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    /** 允许的 MIME 类型白名单 */
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    /** 最大文件大小: 5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {

        // 1. 空文件检查
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        // 2. 文件大小检查
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过 5MB");
        }

        // 3. 扩展名白名单检查
        String originalFilename = file.getOriginalFilename();
        String suffix = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(suffix.toLowerCase())) {
            return Result.error("不支持的文件类型，仅允许: jpg, jpeg, png, gif, webp, bmp");
        }

        // 4. MIME 类型检查
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            return Result.error("文件类型不合法，仅允许图片文件");
        }

        // 5. 文件头（Magic Bytes）校验 - 防止伪造扩展名的木马文件
        if (!isValidImageMagicBytes(file)) {
            return Result.error("文件内容不是有效的图片格式");
        }

        // 6. 文件名安全处理：使用UUID替换原始文件名，防止路径穿越
        String safeFileName = UUID.randomUUID().toString().replace("-", "") + suffix.toLowerCase();

        // 确保目录存在
        File destDir = new File(uploadPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        if (!destDir.canWrite()) {
            return Result.error("服务器存储目录无写入权限");
        }

        try {
            File destFile = new File(uploadPath + safeFileName);
            // 防止路径穿越：确认目标文件确实在 uploads 目录下
            if (!destFile.getCanonicalPath().startsWith(new File(uploadPath).getCanonicalPath())) {
                return Result.error("非法的文件路径");
            }

            file.transferTo(destFile);
            String fileUrl = PUBLIC_FILE_BASE + "/uploads/" + safeFileName;

            return Result.success(Map.of("url", fileUrl));
        } catch (IOException e) {
            return Result.error("文件保存失败");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        // 只取最后一个点之后的部分，防止双扩展名攻击如 shell.php.jpg
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 通过文件头 Magic Bytes 验证是否为真实图片
     * - JPEG: FF D8 FF
     * - PNG:  89 50 4E 47
     * - GIF:  47 49 46 38
     * - WEBP: 52 49 46 46 ... 57 45 42 50
     * - BMP:  42 4D
     */
    private boolean isValidImageMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                return false;
            }

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return true;
            }
            // PNG: 89 50 4E 47 (‰PNG)
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50
                    && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                return true;
            }
            // GIF: 47 49 46 38 (GIF8)
            if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49
                    && header[2] == (byte) 0x46 && header[3] == (byte) 0x38) {
                return true;
            }
            // BMP: 42 4D (BM)
            if (header[0] == (byte) 0x42 && header[1] == (byte) 0x4D) {
                return true;
            }
            // WEBP: 52 49 46 46 ... 57 45 42 50 (RIFF....WEBP)
            if (bytesRead >= 12
                    && header[0] == (byte) 0x52 && header[1] == (byte) 0x49
                    && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                    && header[8] == (byte) 0x57 && header[9] == (byte) 0x45
                    && header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
                return true;
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
