package com.juxin.orin.app.admin;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Management surface for the independent mobile APP data set. */
@RestController
@RequestMapping("/api/admin")
public class AppAdminManagementController {
    private final JdbcTemplate jdbc;

    public AppAdminManagementController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/overview/summary")
    public ApiResponse<Map<String, Object>> overview(HttpServletRequest request) {
        requireAdmin(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", scalar("SELECT COUNT(*) FROM app_user_account WHERE status = 1"));
        out.put("nodes", scalar("SELECT COUNT(*) FROM app_node"));
        out.put("onlineNodes", scalar("SELECT COUNT(*) FROM app_node WHERE status = 'online'"));
        out.put("pendingWithdrawals", scalar("SELECT COUNT(*) FROM app_withdrawal WHERE status = 'pending'"));
        out.put("pendingPaymentApplies", scalar("SELECT COUNT(*) FROM app_payment_apply WHERE status = 'pending'"));
        out.put("openFeedback", scalar("SELECT COUNT(*) FROM app_feedback WHERE status = 'open'"));
        out.put("pendingCommands", scalar("SELECT COUNT(*) FROM app_edge_command WHERE status IN ('pending','delivered')"));
        return ApiResponse.success(out);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(defaultValue = "") String keyword,
                                                         @RequestParam(defaultValue = "") String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "50") int size,
                                                         HttpServletRequest request) {
        requireAdmin(request);
        int offset = Math.max(0, page - 1) * Math.min(Math.max(size, 1), 100);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String state = status == null ? "" : status.trim();
        return ApiResponse.success(jdbc.query("""
                SELECT u.id, u.phone, u.nickname, u.status, u.created_at, u.updated_at,
                       COALESCE(n.node_count, 0) AS node_count,
                       COALESCE(w.balance, 0) AS balance
                  FROM app_user_account u
                  LEFT JOIN (SELECT owner_user_id, COUNT(*) node_count FROM app_node GROUP BY owner_user_id) n ON n.owner_user_id = u.id
                  LEFT JOIN (SELECT user_id, COALESCE(SUM(CASE WHEN direction='credit' THEN amount ELSE -amount END),0) balance FROM app_wallet_ledger GROUP BY user_id) w ON w.user_id = u.id
                 WHERE (? = '' OR u.phone LIKE ? OR u.nickname LIKE ? OR CAST(u.id AS CHAR) LIKE ?)
                   AND (? = '' OR CAST(u.status AS CHAR) = ?)
                 ORDER BY u.created_at DESC LIMIT ? OFFSET ?
                """, (rs, row) -> row(rs, "id", "phone", "nickname", "status", "created_at", "updated_at", "node_count", "balance"),
                state, like, like, like, state, state, Math.min(Math.max(size, 1), 100), offset));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable long id, @Valid @RequestBody UserUpdate body,
                                                        HttpServletRequest request) {
        String admin = requireAdmin(request);
        int changed = jdbc.update("UPDATE app_user_account SET nickname = COALESCE(?, nickname), status = COALESCE(?, status), updated_at = CURRENT_TIMESTAMP WHERE id = ?", blankToNull(body.nickname()), body.status(), id);
        if (changed == 0) throw new ApiException(404, "用户不存在");
        audit(admin, "update_user", "user", Long.toString(id), body.toString());
        return ApiResponse.success(user(id));
    }

    @GetMapping("/earnings")
    public ApiResponse<List<Map<String, Object>>> earnings(@RequestParam(required = false) Long userId, HttpServletRequest request) {
        requireAdmin(request);
        String sql = """
                SELECT e.id, e.user_id, u.phone, u.nickname, e.node_id, e.amount, e.earning_date, e.source, e.description, e.created_at
                  FROM app_earning_record e JOIN app_user_account u ON u.id=e.user_id
                 WHERE (? IS NULL OR e.user_id = ?) ORDER BY e.created_at DESC LIMIT 500
                """;
        return ApiResponse.success(jdbc.query(sql, (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "node_id", "amount", "earning_date", "source", "description", "created_at"), userId, userId));
    }

    @PostMapping("/wallet/adjust")
    @Transactional
    public ApiResponse<Map<String, Object>> adjustWallet(@Valid @RequestBody WalletAdjust body, HttpServletRequest request) {
        String admin = requireAdmin(request);
        ensureUser(body.userId());
        if (body.amount().signum() <= 0) throw new ApiException(400, "金额必须大于 0");
        String key = "admin:" + UUID.randomUUID();
        BigDecimal current = balance(body.userId());
        BigDecimal after = current.add("credit".equals(body.direction()) ? body.amount() : body.amount().negate());
        if (after.signum() < 0) throw new ApiException(409, "余额不足");
        jdbc.update("INSERT INTO app_wallet_ledger (user_id, amount, balance_after, direction, entry_type, idempotency_key, description) VALUES (?, ?, ?, ?, 'admin_adjust', ?, ?)", body.userId(), body.amount(), after, body.direction(), key, body.description());
        audit(admin, "adjust_wallet", "user", Long.toString(body.userId()), body.direction() + " " + body.amount());
        return ApiResponse.success(Map.of("userId", body.userId(), "balance", after, "idempotencyKey", key));
    }

    @GetMapping("/wallet/ledger")
    public ApiResponse<List<Map<String, Object>>> ledger(@RequestParam(required = false) Long userId, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("SELECT l.*, u.phone, u.nickname FROM app_wallet_ledger l JOIN app_user_account u ON u.id=l.user_id WHERE (? IS NULL OR l.user_id=?) ORDER BY l.created_at DESC LIMIT 500", (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "amount", "balance_after", "direction", "entry_type", "idempotency_key", "description", "created_at"), userId, userId));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<List<Map<String, Object>>> withdrawals(@RequestParam(defaultValue = "") String status, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("SELECT w.*, u.phone, u.nickname FROM app_withdrawal w JOIN app_user_account u ON u.id=w.user_id WHERE (?='' OR w.status=?) ORDER BY w.created_at DESC LIMIT 500", (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "amount", "method", "account_name", "account_no", "status", "review_note", "reviewed_by", "reviewed_at", "created_at"), status == null ? "" : status, status == null ? "" : status));
    }

    @PostMapping("/withdrawals/{id}/{action}")
    @Transactional
    public ApiResponse<Void> reviewWithdrawal(@PathVariable long id, @PathVariable String action, @RequestBody(required = false) Review body, HttpServletRequest request) {
        String admin = requireAdmin(request);
        String next = switch (action) { case "approve" -> "approved"; case "reject" -> "rejected"; default -> throw new ApiException(400, "审核动作不正确"); };
        Map<String, Object> w = one("SELECT * FROM app_withdrawal WHERE id=?", id);
        if (w.isEmpty()) throw new ApiException(404, "提现申请不存在");
        if (!"pending".equals(w.get("status"))) throw new ApiException(409, "申请已审核");
        jdbc.update("UPDATE app_withdrawal SET status=?, review_note=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", next, body == null ? null : body.note(), admin, id);
        audit(admin, "review_withdrawal", "withdrawal", Long.toString(id), next);
        return ApiResponse.success();
    }

    @GetMapping("/payment-applies")
    public ApiResponse<List<Map<String, Object>>> paymentApplies(@RequestParam(defaultValue = "") String status, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("SELECT p.*, u.phone, u.nickname FROM app_payment_apply p JOIN app_user_account u ON u.id=p.user_id WHERE (?='' OR p.status=?) ORDER BY p.created_at DESC LIMIT 500", (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "method", "account_name", "account_no", "status", "review_note", "reviewed_by", "reviewed_at", "created_at"), status == null ? "" : status, status == null ? "" : status));
    }

    @PostMapping("/payment-applies/{id}/{action}")
    public ApiResponse<Void> reviewPayment(@PathVariable long id, @PathVariable String action, @RequestBody(required = false) Review body, HttpServletRequest request) {
        String admin = requireAdmin(request);
        String next = switch (action) { case "approve" -> "approved"; case "reject" -> "rejected"; default -> throw new ApiException(400, "审核动作不正确"); };
        int changed = jdbc.update("UPDATE app_payment_apply SET status=?, review_note=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", next, body == null ? null : body.note(), admin, id);
        if (changed == 0) throw new ApiException(404, "申请不存在或已审核");
        audit(admin, "review_payment", "payment_apply", Long.toString(id), next);
        return ApiResponse.success();
    }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String, Object>>> notices(HttpServletRequest request) { requireAdmin(request); return ApiResponse.success(jdbc.query("SELECT * FROM app_notice ORDER BY pinned DESC, created_at DESC LIMIT 500", (rs, row) -> row(rs, "id", "title", "content", "status", "pinned", "published_at", "created_by", "created_at", "updated_at"))); }

    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> createNotice(@Valid @RequestBody Notice body, HttpServletRequest request) {
        String admin = requireAdmin(request); jdbc.update("INSERT INTO app_notice (title, content, status, pinned, published_at, created_by) VALUES (?, ?, ?, ?, CASE WHEN ?='published' THEN CURRENT_TIMESTAMP ELSE NULL END, ?)", body.title(), body.content(), body.status(), body.pinned(), body.status(), admin); audit(admin, "create_notice", "notice", null, body.title()); return ApiResponse.success(jdbc.queryForMap("SELECT * FROM app_notice ORDER BY id DESC LIMIT 1"));
    }

    @PatchMapping("/notices/{id}")
    public ApiResponse<Void> updateNotice(@PathVariable long id, @Valid @RequestBody Notice body, HttpServletRequest request) { String admin=requireAdmin(request); int n=jdbc.update("UPDATE app_notice SET title=?, content=?, status=?, pinned=?, published_at=CASE WHEN ?='published' THEN COALESCE(published_at,CURRENT_TIMESTAMP) ELSE published_at END, updated_at=CURRENT_TIMESTAMP WHERE id=?", body.title(),body.content(),body.status(),body.pinned(),body.status(),id); if(n==0)throw new ApiException(404,"公告不存在"); audit(admin,"update_notice","notice",Long.toString(id),body.status()); return ApiResponse.success(); }

    @GetMapping("/feedback")
    public ApiResponse<List<Map<String, Object>>> feedback(@RequestParam(defaultValue = "") String status, HttpServletRequest request) { requireAdmin(request); return ApiResponse.success(jdbc.query("SELECT f.*, u.phone, u.nickname FROM app_feedback f JOIN app_user_account u ON u.id=f.user_id WHERE (?='' OR f.status=?) ORDER BY f.created_at DESC LIMIT 500", (rs,row)->row(rs,"id","user_id","phone","nickname","category","content","status","reply","handled_by","handled_at","created_at"),status==null?"":status,status==null?"":status)); }

    @PostMapping("/feedback/{id}/reply")
    public ApiResponse<Void> replyFeedback(@PathVariable long id, @Valid @RequestBody Reply body, HttpServletRequest request) { String admin=requireAdmin(request); int n=jdbc.update("UPDATE app_feedback SET reply=?, status='replied', handled_by=?, handled_at=CURRENT_TIMESTAMP WHERE id=?",body.reply(),admin,id); if(n==0)throw new ApiException(404,"反馈不存在"); audit(admin,"reply_feedback","feedback",Long.toString(id),null); return ApiResponse.success(); }

    @GetMapping("/teams")
    public ApiResponse<List<Map<String, Object>>> teams(HttpServletRequest request) { requireAdmin(request); return ApiResponse.success(jdbc.query("SELECT r.inviter_user_id, u.phone, u.nickname, COUNT(*) member_count, MIN(r.created_at) created_at FROM app_invite_relation r JOIN app_user_account u ON u.id=r.inviter_user_id GROUP BY r.inviter_user_id,u.phone,u.nickname ORDER BY member_count DESC", (rs,row)->row(rs,"inviter_user_id","phone","nickname","member_count","created_at"))); }
    @GetMapping("/rewards")
    public ApiResponse<List<Map<String, Object>>> rewards(HttpServletRequest request) { requireAdmin(request); return ApiResponse.success(jdbc.query("SELECT r.*, u.phone, u.nickname FROM app_reward_record r JOIN app_user_account u ON u.id=r.user_id ORDER BY r.created_at DESC LIMIT 500", (rs,row)->row(rs,"id","user_id","phone","nickname","source_user_id","amount","status","description","created_at"))); }

    @GetMapping("/exchange/products")
    public ApiResponse<List<Map<String, Object>>> products(HttpServletRequest request) { requireAdmin(request); return ApiResponse.success(jdbc.query("SELECT * FROM app_exchange_product ORDER BY created_at DESC", (rs,row)->row(rs,"id","name","description","price","stock","status","image_url","created_at","updated_at"))); }
    @PostMapping("/exchange/products")
    public ApiResponse<Void> createProduct(@Valid @RequestBody Product body, HttpServletRequest request) { String admin=requireAdmin(request); jdbc.update("INSERT INTO app_exchange_product (name,description,price,stock,status,image_url) VALUES (?,?,?,?,?,?)",body.name(),body.description(),body.price(),body.stock(),body.status(),body.imageUrl()); audit(admin,"create_product","exchange_product",null,body.name()); return ApiResponse.success(); }
    @PatchMapping("/exchange/products/{id}")
    public ApiResponse<Void> updateProduct(@PathVariable long id,@Valid @RequestBody Product body,HttpServletRequest request){String admin=requireAdmin(request);int n=jdbc.update("UPDATE app_exchange_product SET name=?,description=?,price=?,stock=?,status=?,image_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",body.name(),body.description(),body.price(),body.stock(),body.status(),body.imageUrl(),id);if(n==0)throw new ApiException(404,"商品不存在");audit(admin,"update_product","exchange_product",Long.toString(id),body.status());return ApiResponse.success();}
    @GetMapping("/exchange/orders")
    public ApiResponse<List<Map<String,Object>>> orders(HttpServletRequest request){requireAdmin(request);return ApiResponse.success(jdbc.query("SELECT o.*,u.phone,u.nickname,p.name product_name FROM app_exchange_order o JOIN app_user_account u ON u.id=o.user_id JOIN app_exchange_product p ON p.id=o.product_id ORDER BY o.created_at DESC LIMIT 500",(rs,row)->row(rs,"id","order_no","user_id","phone","nickname","product_id","product_name","quantity","amount","status","address_snapshot","created_at","updated_at")));}
    @PostMapping("/exchange/orders/{id}/{action}")
    public ApiResponse<Void> orderAction(@PathVariable long id,@PathVariable String action,@RequestBody(required=false) Shipping body,HttpServletRequest request){String admin=requireAdmin(request);if("ship".equals(action)){if(body==null||blank(body.carrier())||blank(body.trackingNo()))throw new ApiException(400,"物流信息不能为空");jdbc.update("INSERT INTO app_exchange_logistics(order_id,carrier,tracking_no) VALUES(?,?,?)",id,body.carrier(),body.trackingNo());}String next=switch(action){case"ship"->"shipped";case"cancel"->"cancelled";default->throw new ApiException(400,"订单动作不正确");};int n=jdbc.update("UPDATE app_exchange_order SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status NOT IN ('completed','cancelled')",next,id);if(n==0)throw new ApiException(404,"订单不存在或已完成");audit(admin,"order_"+action,"exchange_order",Long.toString(id),next);return ApiResponse.success();}

    @GetMapping("/tasks") public ApiResponse<List<Map<String,Object>>> tasks(HttpServletRequest request){requireAdmin(request);return ApiResponse.success(jdbc.query("SELECT * FROM app_device_task ORDER BY created_at DESC LIMIT 500",(rs,row)->row(rs,"id","task_no","device_sn","task_type","payload","status","result_text","created_by","created_at","completed_at")));}
    @PostMapping("/tasks") public ApiResponse<Void> createTask(@Valid @RequestBody Task body,HttpServletRequest request){String admin=requireAdmin(request);jdbc.update("INSERT INTO app_device_task(task_no,device_sn,task_type,payload,created_by) VALUES(?,?,?,?,?)","TASK-"+UUID.randomUUID().toString().replace("-","").substring(0,20),blankToNull(body.deviceSn()),body.taskType(),body.payload(),admin);audit(admin,"create_task","device_task",null,body.taskType());return ApiResponse.success();}
    @GetMapping("/upgrades/packages") public ApiResponse<List<Map<String,Object>>> packages(HttpServletRequest request){requireAdmin(request);return ApiResponse.success(jdbc.query("SELECT * FROM app_device_upgrade_package ORDER BY created_at DESC",(rs,row)->row(rs,"id","version","file_name","download_url","sha256","release_note","status","created_at")));}
    @PostMapping("/upgrades/packages") public ApiResponse<Void> createPackage(@Valid @RequestBody Upgrade body,HttpServletRequest request){String admin=requireAdmin(request);jdbc.update("INSERT INTO app_device_upgrade_package(version,file_name,download_url,sha256,release_note) VALUES(?,?,?,?,?)",body.version(),body.fileName(),body.downloadUrl(),body.sha256(),body.releaseNote());audit(admin,"create_upgrade_package","upgrade_package",null,body.version());return ApiResponse.success();}
    @GetMapping("/settings") public ApiResponse<List<Map<String,Object>>> settings(HttpServletRequest request){requireAdmin(request);return ApiResponse.success(jdbc.query("SELECT * FROM app_system_setting ORDER BY setting_key",(rs,row)->row(rs,"setting_key","setting_value","updated_by","updated_at")));}
    @PutMapping("/settings/{key}") public ApiResponse<Void> setting(@PathVariable String key,@Valid @RequestBody Setting body,HttpServletRequest request){String admin=requireAdmin(request);int changed=jdbc.update("UPDATE app_system_setting SET setting_value=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE setting_key=?",body.value(),admin,key);if(changed==0)jdbc.update("INSERT INTO app_system_setting(setting_key,setting_value,updated_by) VALUES(?,?,?)",key,body.value(),admin);audit(admin,"update_setting","system_setting",key,null);return ApiResponse.success();}

    private Map<String,Object> user(long id){return one("SELECT id,phone,nickname,status,created_at,updated_at FROM app_user_account WHERE id=?",id);}
    private Map<String,Object> one(String sql,Object... args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?Map.of():rows.get(0);}
    private Object scalar(String sql){return jdbc.queryForObject(sql,Object.class);}
    private BigDecimal balance(long id){Object v=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='credit' THEN amount ELSE -amount END),0) FROM app_wallet_ledger WHERE user_id=?",Object.class,id);return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
    private void ensureUser(long id){if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_account WHERE id=?",Integer.class,id)==0)throw new ApiException(404,"用户不存在");}
    private void audit(String admin,String action,String type,String id,String detail){jdbc.update("INSERT INTO app_admin_audit_log(admin_username,action,resource_type,resource_id,detail) VALUES(?,?,?,?,?)",admin,action,type,id,detail);}
    private static String requireAdmin(HttpServletRequest r){if(!"app-admin".equals(r.getAttribute(BearerTokenFilter.USER_TYPE_ATTRIBUTE)))throw new ApiException(403,"需要管理员权限");Object v=r.getAttribute("juxin.app.adminUsername");return v==null?"admin":v.toString();}
    private static boolean blank(String v){return v==null||v.isBlank();} private static String blankToNull(String v){return blank(v)?null:v.trim();}
    private static Map<String,Object> row(java.sql.ResultSet rs,String... columns)throws java.sql.SQLException{Map<String,Object> m=new LinkedHashMap<>();for(String c:columns){Object v=rs.getObject(c);if(v instanceof java.sql.Clob clob)v=clob.getSubString(1,(int)Math.min(clob.length(),32768));m.put(c,v);}return m;}

    public record UserUpdate(@Size(max=40) String nickname, Integer status){}
    public record WalletAdjust(@NotNull Long userId,@NotNull BigDecimal amount,@NotBlank String direction,@Size(max=255) String description){}
    public record Review(@Size(max=255) String note){}
    public record Notice(@NotBlank @Size(max=160) String title,@NotBlank String content,@NotBlank String status,boolean pinned){}
    public record Reply(@NotBlank String reply){}
    public record Product(@NotBlank @Size(max=160) String name,String description,@NotNull @Positive BigDecimal price,@NotNull Integer stock,@NotBlank String status,String imageUrl){}
    public record Shipping(@NotBlank String carrier,@NotBlank String trackingNo){}
    public record Task(@NotBlank String taskType,String deviceSn,String payload){}
    public record Upgrade(@NotBlank String version,@NotBlank String fileName,@NotBlank String downloadUrl,@NotBlank @Size(min=64,max=64) String sha256,String releaseNote){}
    public record Setting(@NotBlank String value){}
}
