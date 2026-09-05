package com.juxin.orin.app.admin;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import com.juxin.orin.app.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

/** Publishes mobile APP releases and provides a public update manifest/download. */
@RestController
public class AppUpdateController {
    private final JdbcTemplate jdbc;
    private final AppProperties properties;

    public AppUpdateController(JdbcTemplate jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** Public endpoint polled by the mobile APP on launch. */
    @GetMapping({"/api/mobile-app/update", "/api/app/update/check"})
    public ApiResponse<Map<String, Object>> check(
            @RequestParam(defaultValue = "0") int versionCode,
            @RequestParam(defaultValue = "android") String platform) {
        Map<String, Object> latest = latest(platform);
        if (latest.isEmpty()) {
            return ApiResponse.success(Map.of("updateAvailable", false));
        }
        int latestCode = ((Number) latest.get("version_code")).intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updateAvailable", latestCode > versionCode);
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("id", latest.get("id"));
        release.put("versionName", latest.get("version"));
        release.put("versionCode", latestCode);
        release.put("packageName", "cn.juxin.orin.app");
        release.put("downloadUrl", absoluteUrl("/api/app/update/download/" + latest.get("id")));
        release.put("sha256", latest.get("sha256"));
        release.put("fileSize", latest.get("file_size"));
        release.put("releaseNote", latest.get("release_note"));
        release.put("forceUpdate", latest.get("force_update"));
        release.put("publishedAt", latest.get("published_at"));
        result.put("release", release);
        return ApiResponse.success(result);
    }

    @GetMapping("/api/app/update/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Map<String, Object> row = one("SELECT file_name, storage_path, status FROM app_mobile_release WHERE id=?", id);
        if (row.isEmpty() || !"active".equals(row.get("status"))) {
            throw new ApiException(404, "更新包不存在");
        }
        Path file = Path.of(row.get("storage_path").toString()).normalize();
        Path root = storageRoot().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new ApiException(404, "更新包不存在");
        Resource resource = new FileSystemResource(file);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename(row.get("file_name").toString()) + "\"")
                .body(resource);
    }

    @GetMapping("/api/admin/app-updates")
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("SELECT id,platform,version,version_code,file_name,file_size,sha256,release_note,force_update,status,published_at,created_at FROM app_mobile_release ORDER BY version_code DESC, id DESC", (rs, row) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String c : new String[]{"id", "platform", "version", "version_code", "file_name", "file_size", "sha256", "release_note", "force_update", "status", "published_at", "created_at"}) m.put(c, rs.getObject(c));
            return m;
        }));
    }

    @PostMapping(value = "/api/admin/app-updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("version") @NotBlank @Size(max = 64) String version,
            @RequestParam("versionCode") int versionCode,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "releaseNote", required = false) String releaseNote,
            @RequestParam(value = "forceUpdate", required = false) Boolean forceUpdate,
            HttpServletRequest request) throws IOException {
        String admin = requireAdmin(request);
        if (file.isEmpty()) throw new ApiException(400, "更新包不能为空");
        if (versionCode < 1) throw new ApiException(400, "版本号必须大于 0");
        String p = StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "android";
        if (!"android".equals(p)) throw new ApiException(400, "目前仅支持 Android 更新包");
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "app.apk" : file.getOriginalFilename());
        if (!original.toLowerCase().endsWith(".apk")) throw new ApiException(400, "请上传 APK 文件");
        long max = properties.getUpdate().getMaxFileBytes();
        if (file.getSize() > max) throw new ApiException(413, "更新包超过大小限制");
        Path root = storageRoot();
        Files.createDirectories(root);
        String stored = versionCode + "-" + System.currentTimeMillis() + ".apk";
        Path target = root.resolve(stored).normalize();
        file.transferTo(target);
        String sha = sha256(target);
        String path = "/api/app/update/download/";
        jdbc.update("UPDATE app_mobile_release SET status='disabled' WHERE platform=? AND version_code <= ? AND status='active'", p, versionCode);
        jdbc.update("INSERT INTO app_mobile_release(platform,version,version_code,file_name,file_size,sha256,release_note,force_update,status,storage_path,download_path,published_at,created_by) VALUES(?,?,?,?,?,?,?,?, 'active', ?, ?, CURRENT_TIMESTAMP, ?)", p, version.trim(), versionCode, original, file.getSize(), sha, releaseNote, Boolean.TRUE.equals(forceUpdate), target.toString(), path, admin);
        Map<String, Object> created = one("SELECT id,version,version_code,file_name,file_size,sha256,release_note,force_update,status,published_at FROM app_mobile_release WHERE storage_path=?", target.toString());
        if (!created.isEmpty()) created.put("downloadUrl", absoluteUrl(path + created.get("id")));
        return ApiResponse.success(created);
    }

    @PostMapping("/api/admin/app-updates/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable long id, HttpServletRequest request) {
        String admin = requireAdmin(request);
        if (jdbc.update("UPDATE app_mobile_release SET status='disabled' WHERE id=?", id) == 0) throw new ApiException(404, "更新包不存在");
        jdbc.update("INSERT INTO app_admin_audit_log(admin_username,action,resource_type,resource_id,detail) VALUES(?,?,?,?,?)", admin, "disable_app_update", "app_mobile_release", Long.toString(id), null);
        return ApiResponse.success();
    }

    private Map<String, Object> latest(String platform) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM app_mobile_release WHERE platform=? AND status='active' ORDER BY version_code DESC, id DESC LIMIT 1", StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "android");
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
    private Path storageRoot() { return Path.of(properties.getUpdate().getStorageDir()).toAbsolutePath().normalize(); }
    private String absoluteUrl(String path) { return properties.getPublicBaseUrl().replaceAll("/$", "") + path; }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(rows.get(0)); }
    private static String safeFilename(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String sha256(Path file) throws IOException { try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (var in = Files.newInputStream(file)) { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0;) digest.update(b, 0, n); } return HexFormat.of().formatHex(digest.digest()); } catch (Exception e) { throw new IOException("无法计算更新包校验值", e); } }
    private String requireAdmin(HttpServletRequest request) {
        if ("app-admin".equals(request.getAttribute(BearerTokenFilter.USER_TYPE_ATTRIBUTE))) {
            Object v = request.getAttribute("juxin.app.adminUsername");
            return v == null ? "admin" : v.toString();
        }
        String token = request.getHeader("Authorization");
        String secret = properties.getNodeAdminJwtSecret();
        try {
            if (token == null || secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException();
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build()
                    .parseSignedClaims(token.replaceFirst("^Bearer\\s+", "")).getPayload();
            if (!"admin".equals(claims.get("userType", String.class))) throw new IllegalArgumentException();
            Object username = claims.get("username");
            return username == null ? "admin" : username.toString();
        } catch (Exception error) {
            throw new ApiException(403, "需要管理员权限");
        }
    }
}
