package com.juxin.orin.app.admin;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import com.juxin.orin.app.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stores APP-uploaded images (collection QR codes) and serves them publicly. */
@RestController
public class AppFileController {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_QR_BYTES = 5L * 1024 * 1024;

    private final AppProperties properties;

    public AppFileController(AppProperties properties) {
        this.properties = properties;
    }

    /** APP uploads a WeChat/Alipay collection QR code; returns its public URL. */
    @PostMapping(value = "/api/app/upload/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadQr(@RequestPart("file") MultipartFile file,
                                                     HttpServletRequest request) throws IOException {
        long uid = userId(request);
        if (file.isEmpty()) throw new ApiException(400, "收款码图片不能为空");
        if (file.getSize() > MAX_QR_BYTES) throw new ApiException(413, "收款码图片不能超过 5MB");
        String original = file.getOriginalFilename() == null ? "qr.png" : file.getOriginalFilename();
        String ext = extension(original);
        if (!ALLOWED.contains(ext)) throw new ApiException(400, "仅支持 JPG/PNG/WebP 图片");
        Path root = qrRoot();
        Files.createDirectories(root);
        String filename = uid + "-" + System.currentTimeMillis() + "." + ext;
        Path target = root.resolve(filename).normalize();
        file.transferTo(target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", absoluteUrl("/api/app/files/" + filename));
        out.put("path", "/api/app/files/" + filename);
        return ApiResponse.success(out);
    }

    /** Public image endpoint (no auth) so admin browsers can render collection codes. */
    @GetMapping("/api/app/files/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        if (!filename.matches("^[A-Za-z0-9._-]+$")) throw new ApiException(404, "文件不存在");
        Path file = qrRoot().resolve(filename).normalize();
        if (!file.startsWith(qrRoot().normalize()) || !Files.isRegularFile(file)) throw new ApiException(404, "文件不存在");
        String ext = extension(filename);
        String contentType = switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(new FileSystemResource(file));
    }

    private Path qrRoot() {
        // Sibling of ./uploads/app-releases, both persisted by the compose volume ./uploads:/app/uploads.
        Path parent = Path.of(properties.getUpdate().getStorageDir()).toAbsolutePath().normalize().getParent();
        return (parent == null ? Path.of("uploads") : parent).resolve("app-qr").toAbsolutePath().normalize();
    }

    private String absoluteUrl(String path) { return properties.getPublicBaseUrl().replaceAll("/$", "") + path; }
    private static String extension(String name) { int i = name.lastIndexOf('.'); return i < 0 || i == name.length() - 1 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT); }
    private static long userId(HttpServletRequest request) {
        Object v = request.getAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE);
        if (!(v instanceof Number n) || n.longValue() <= 0) throw new ApiException(401, "登录已过期，请重新登录");
        return n.longValue();
    }
}
