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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** APP-facing pieces used by the management modules: notices, wallet, feedback and exchange. */
@RestController
@RequestMapping("/api/app")
public class AppUserFeatureController {
    private final JdbcTemplate jdbc;
    public AppUserFeatureController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/notices")
    public ApiResponse<List<Map<String,Object>>> notices() {
        return ApiResponse.success(jdbc.query("SELECT id,title,content,pinned,published_at FROM app_notice WHERE status='published' ORDER BY pinned DESC,published_at DESC LIMIT 50", (rs,row)->map(rs,"id","title","content","pinned","published_at")));
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody Feedback body, HttpServletRequest request) {
        jdbc.update("INSERT INTO app_feedback(user_id,category,content) VALUES(?,?,?)", userId(request), body.category(), body.content());
        return ApiResponse.success();
    }

    @GetMapping("/feedback")
    public ApiResponse<List<Map<String,Object>>> myFeedback(HttpServletRequest request) {
        return ApiResponse.success(jdbc.query("SELECT id,category,content,status,reply,created_at,handled_at FROM app_feedback WHERE user_id=? ORDER BY created_at DESC", (rs,row)->map(rs,"id","category","content","status","reply","created_at","handled_at"), userId(request)));
    }

    @GetMapping("/wallet")
    public ApiResponse<Map<String,Object>> wallet(HttpServletRequest request) {
        long uid = userId(request); Map<String,Object> result = new LinkedHashMap<>();
        result.put("balance", balance(uid));
        result.put("ledger", jdbc.query("SELECT id,amount,balance_after,direction,entry_type,description,created_at FROM app_wallet_ledger WHERE user_id=? ORDER BY created_at DESC LIMIT 100", (rs,row)->map(rs,"id","amount","balance_after","direction","entry_type","description","created_at"), uid));
        return ApiResponse.success(result);
    }

    @PostMapping("/withdrawals")
    @Transactional
    public ApiResponse<Map<String,Object>> withdraw(@Valid @RequestBody Withdraw body, HttpServletRequest request) {
        long uid = userId(request); BigDecimal current = balance(uid);
        if (body.amount().compareTo(BigDecimal.ZERO) <= 0 || current.compareTo(body.amount()) < 0) throw new ApiException(409,"可用余额不足");
        String key = "withdraw:" + UUID.randomUUID(); BigDecimal after = current.subtract(body.amount());
        jdbc.update("INSERT INTO app_wallet_ledger(user_id,amount,balance_after,direction,entry_type,idempotency_key,description) VALUES(?,?,?,'debit','withdraw',?,?)",uid,body.amount(),after,key,"提现申请");
        var id = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM app_wallet_ledger WHERE idempotency_key=?",Long.class,key);
        jdbc.update("INSERT INTO app_withdrawal(user_id,amount,method,account_name,account_no) VALUES(?,?,?,?,?)",uid,body.amount(),body.method(),body.accountName(),body.accountNo());
        return ApiResponse.success(Map.of("ledgerId",id,"balance",after,"status","pending"));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<List<Map<String,Object>>> withdrawals(HttpServletRequest request) { return ApiResponse.success(jdbc.query("SELECT id,amount,method,account_name,account_no,status,review_note,created_at,reviewed_at FROM app_withdrawal WHERE user_id=? ORDER BY created_at DESC",(rs,row)->map(rs,"id","amount","method","account_name","account_no","status","review_note","created_at","reviewed_at"),userId(request))); }

    @PostMapping("/payment-applies")
    public ApiResponse<Void> paymentApply(@Valid @RequestBody Payment body,HttpServletRequest request){jdbc.update("INSERT INTO app_payment_apply(user_id,method,account_name,account_no) VALUES(?,?,?,?)",userId(request),body.method(),body.accountName(),body.accountNo());return ApiResponse.success();}
    @GetMapping("/payment-applies")
    public ApiResponse<List<Map<String,Object>>> myPayments(HttpServletRequest request){return ApiResponse.success(jdbc.query("SELECT id,method,account_name,account_no,status,review_note,created_at,reviewed_at FROM app_payment_apply WHERE user_id=? ORDER BY created_at DESC",(rs,row)->map(rs,"id","method","account_name","account_no","status","review_note","created_at","reviewed_at"),userId(request)));}

    @GetMapping("/invites")
    public ApiResponse<Map<String,Object>> invites(HttpServletRequest request){long uid=userId(request);Map<String,Object> out=new LinkedHashMap<>();out.put("inviteCode","JX"+String.format("%08d",uid));out.put("members",jdbc.query("SELECT r.invitee_user_id,u.phone,u.nickname,r.created_at FROM app_invite_relation r JOIN app_user_account u ON u.id=r.invitee_user_id WHERE r.inviter_user_id=? ORDER BY r.created_at DESC",(rs,row)->map(rs,"invitee_user_id","phone","nickname","created_at"),uid));out.put("rewards",jdbc.query("SELECT id,amount,description,created_at FROM app_reward_record WHERE user_id=? ORDER BY created_at DESC",(rs,row)->map(rs,"id","amount","description","created_at"),uid));return ApiResponse.success(out);}

    @GetMapping("/exchange/products")
    public ApiResponse<List<Map<String,Object>>> products(){return ApiResponse.success(jdbc.query("SELECT id,name,description,price,stock,status,image_url FROM app_exchange_product WHERE status='active' ORDER BY created_at DESC",(rs,row)->map(rs,"id","name","description","price","stock","status","image_url")));}
    @GetMapping("/exchange/orders")
    public ApiResponse<List<Map<String,Object>>> myOrders(HttpServletRequest request){return ApiResponse.success(jdbc.query("SELECT o.id,o.order_no,o.product_id,p.name product_name,o.quantity,o.amount,o.status,o.address_snapshot,o.created_at,o.updated_at FROM app_exchange_order o JOIN app_exchange_product p ON p.id=o.product_id WHERE o.user_id=? ORDER BY o.created_at DESC",(rs,row)->map(rs,"id","order_no","product_id","product_name","quantity","amount","status","address_snapshot","created_at","updated_at"),userId(request)));}

    @PostMapping("/exchange/orders")
    @Transactional
    public ApiResponse<Map<String,Object>> createOrder(@Valid @RequestBody Order body,HttpServletRequest request){long uid=userId(request);Map<String,Object> product=one("SELECT * FROM app_exchange_product WHERE id=? AND status='active'",body.productId());if(product.isEmpty())throw new ApiException(404,"商品不存在");int stock=((Number)product.get("stock")).intValue();if(stock<body.quantity())throw new ApiException(409,"库存不足");BigDecimal amount=new BigDecimal(product.get("price").toString()).multiply(BigDecimal.valueOf(body.quantity()));BigDecimal current=balance(uid);if(current.compareTo(amount)<0)throw new ApiException(409,"余额不足");String orderNo="JX"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase();String key="order:"+orderNo;BigDecimal after=current.subtract(amount);int stockChanged=jdbc.update("UPDATE app_exchange_product SET stock=stock-? WHERE id=? AND stock>=?",body.quantity(),body.productId(),body.quantity());if(stockChanged==0)throw new ApiException(409,"库存不足，请重试");jdbc.update("INSERT INTO app_wallet_ledger(user_id,amount,balance_after,direction,entry_type,idempotency_key,description) VALUES(?,?,?,'debit','exchange',?,?)",uid,amount,after,key,"兑换商品 "+product.get("name"));jdbc.update("INSERT INTO app_exchange_order(order_no,user_id,product_id,quantity,amount,address_snapshot) VALUES(?,?,?,?,?,?)",orderNo,uid,body.productId(),body.quantity(),amount,body.addressSnapshot());return ApiResponse.success(Map.of("orderNo",orderNo,"amount",amount,"balance",after,"status","pending"));}

    private long userId(HttpServletRequest request){Object v=request.getAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE);if(!(v instanceof Number n)||n.longValue()<=0)throw new ApiException(401,"登录已过期，请重新登录");return n.longValue();}
    private BigDecimal balance(long id){Object v=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='credit' THEN amount ELSE -amount END),0) FROM app_wallet_ledger WHERE user_id=?",Object.class,id);return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
    private Map<String,Object> one(String sql,Object...a){List<Map<String,Object>> l=jdbc.queryForList(sql,a);return l.isEmpty()?Map.of():l.get(0);}
    private static Map<String,Object> map(java.sql.ResultSet rs,String... cols)throws java.sql.SQLException{Map<String,Object> m=new LinkedHashMap<>();for(String c:cols){Object v=rs.getObject(c);if(v instanceof java.sql.Clob clob)v=clob.getSubString(1,(int)Math.min(clob.length(),32768));m.put(c,v);}return m;}
    public record Feedback(@Size(max=40) String category,@NotBlank @Size(max=4000) String content){}
    public record Withdraw(@NotNull @Positive BigDecimal amount,@NotBlank String method,@Size(max=80) String accountName,@NotBlank @Size(max=128) String accountNo){}
    public record Payment(@NotBlank String method,@NotBlank @Size(max=80) String accountName,@NotBlank @Size(max=128) String accountNo){}
    public record Order(@NotNull Long productId,@NotNull @Positive Integer quantity,@Size(max=2000) String addressSnapshot){}
}
