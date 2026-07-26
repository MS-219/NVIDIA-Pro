package com.juxin.orin.util;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 地址工具类 - 基于本地 ip2region.xdb 离线解析 IP 到省市信息
 */
@Component
public class IpUtil {

    private static final java.util.Map<String, String> CITY_PROVINCE_MAP = new java.util.HashMap<>();
    static {
        CITY_PROVINCE_MAP.put("石家庄市", "河北省");
        CITY_PROVINCE_MAP.put("唐山市", "河北省");
        CITY_PROVINCE_MAP.put("秦皇岛市", "河北省");
        CITY_PROVINCE_MAP.put("邯郸市", "河北省");
        CITY_PROVINCE_MAP.put("邢台市", "河北省");
        CITY_PROVINCE_MAP.put("保定市", "河北省");
        CITY_PROVINCE_MAP.put("张家口市", "河北省");
        CITY_PROVINCE_MAP.put("承德市", "河北省");
        CITY_PROVINCE_MAP.put("沧州市", "河北省");
        CITY_PROVINCE_MAP.put("廊坊市", "河北省");
        CITY_PROVINCE_MAP.put("衡水市", "河北省");
        CITY_PROVINCE_MAP.put("太原市", "山西省");
        CITY_PROVINCE_MAP.put("大同市", "山西省");
        CITY_PROVINCE_MAP.put("阳泉市", "山西省");
        CITY_PROVINCE_MAP.put("长治市", "山西省");
        CITY_PROVINCE_MAP.put("晋城市", "山西省");
        CITY_PROVINCE_MAP.put("朔州市", "山西省");
        CITY_PROVINCE_MAP.put("晋中市", "山西省");
        CITY_PROVINCE_MAP.put("运城市", "山西省");
        CITY_PROVINCE_MAP.put("忻州市", "山西省");
        CITY_PROVINCE_MAP.put("临汾市", "山西省");
        CITY_PROVINCE_MAP.put("吕梁市", "山西省");
        CITY_PROVINCE_MAP.put("呼和浩特市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("包头市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("乌海市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("赤峰市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("通辽市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("鄂尔多斯市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("呼伦贝尔市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("巴彦淖尔市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("乌兰察布市", "内蒙古自治区");
        CITY_PROVINCE_MAP.put("沈阳市", "辽宁省");
        CITY_PROVINCE_MAP.put("大连市", "辽宁省");
        CITY_PROVINCE_MAP.put("鞍山市", "辽宁省");
        CITY_PROVINCE_MAP.put("抚顺市", "辽宁省");
        CITY_PROVINCE_MAP.put("本溪市", "辽宁省");
        CITY_PROVINCE_MAP.put("丹东市", "辽宁省");
        CITY_PROVINCE_MAP.put("锦州市", "辽宁省");
        CITY_PROVINCE_MAP.put("营口市", "辽宁省");
        CITY_PROVINCE_MAP.put("阜新市", "辽宁省");
        CITY_PROVINCE_MAP.put("辽阳市", "辽宁省");
        CITY_PROVINCE_MAP.put("盘锦市", "辽宁省");
        CITY_PROVINCE_MAP.put("铁岭市", "辽宁省");
        CITY_PROVINCE_MAP.put("朝阳市", "辽宁省");
        CITY_PROVINCE_MAP.put("葫芦岛市", "辽宁省");
        CITY_PROVINCE_MAP.put("长春市", "吉林省");
        CITY_PROVINCE_MAP.put("吉林市", "吉林省");
        CITY_PROVINCE_MAP.put("四平市", "吉林省");
        CITY_PROVINCE_MAP.put("辽源市", "吉林省");
        CITY_PROVINCE_MAP.put("通化市", "吉林省");
        CITY_PROVINCE_MAP.put("白山市", "吉林省");
        CITY_PROVINCE_MAP.put("松原市", "吉林省");
        CITY_PROVINCE_MAP.put("白城市", "吉林省");
        CITY_PROVINCE_MAP.put("哈尔滨市", "黑龙江省");
        CITY_PROVINCE_MAP.put("齐齐哈尔市", "黑龙江省");
        CITY_PROVINCE_MAP.put("鸡西市", "黑龙江省");
        CITY_PROVINCE_MAP.put("鹤岗市", "黑龙江省");
        CITY_PROVINCE_MAP.put("双鸭山市", "黑龙江省");
        CITY_PROVINCE_MAP.put("大庆市", "黑龙江省");
        CITY_PROVINCE_MAP.put("伊春市", "黑龙江省");
        CITY_PROVINCE_MAP.put("佳木斯市", "黑龙江省");
        CITY_PROVINCE_MAP.put("七台河市", "黑龙江省");
        CITY_PROVINCE_MAP.put("牡丹江市", "黑龙江省");
        CITY_PROVINCE_MAP.put("黑河市", "黑龙江省");
        CITY_PROVINCE_MAP.put("绥化市", "黑龙江省");
        CITY_PROVINCE_MAP.put("南京市", "江苏省");
        CITY_PROVINCE_MAP.put("无锡市", "江苏省");
        CITY_PROVINCE_MAP.put("徐州市", "江苏省");
        CITY_PROVINCE_MAP.put("常州市", "江苏省");
        CITY_PROVINCE_MAP.put("苏州市", "江苏省");
        CITY_PROVINCE_MAP.put("南通市", "江苏省");
        CITY_PROVINCE_MAP.put("连云港市", "江苏省");
        CITY_PROVINCE_MAP.put("淮安市", "江苏省");
        CITY_PROVINCE_MAP.put("盐城市", "江苏省");
        CITY_PROVINCE_MAP.put("扬州市", "江苏省");
        CITY_PROVINCE_MAP.put("镇江市", "江苏省");
        CITY_PROVINCE_MAP.put("泰州市", "江苏省");
        CITY_PROVINCE_MAP.put("宿迁市", "江苏省");
        CITY_PROVINCE_MAP.put("杭州市", "浙江省");
        CITY_PROVINCE_MAP.put("宁波市", "浙江省");
        CITY_PROVINCE_MAP.put("温州市", "浙江省");
        CITY_PROVINCE_MAP.put("嘉兴市", "浙江省");
        CITY_PROVINCE_MAP.put("湖州市", "浙江省");
        CITY_PROVINCE_MAP.put("绍兴市", "浙江省");
        CITY_PROVINCE_MAP.put("金华市", "浙江省");
        CITY_PROVINCE_MAP.put("衢州市", "浙江省");
        CITY_PROVINCE_MAP.put("舟山市", "浙江省");
        CITY_PROVINCE_MAP.put("台州市", "浙江省");
        CITY_PROVINCE_MAP.put("丽水市", "浙江省");
        CITY_PROVINCE_MAP.put("合肥市", "安徽省");
        CITY_PROVINCE_MAP.put("芜湖市", "安徽省");
        CITY_PROVINCE_MAP.put("蚌埠市", "安徽省");
        CITY_PROVINCE_MAP.put("淮南市", "安徽省");
        CITY_PROVINCE_MAP.put("马鞍山市", "安徽省");
        CITY_PROVINCE_MAP.put("淮北市", "安徽省");
        CITY_PROVINCE_MAP.put("铜陵市", "安徽省");
        CITY_PROVINCE_MAP.put("安庆市", "安徽省");
        CITY_PROVINCE_MAP.put("黄山市", "安徽省");
        CITY_PROVINCE_MAP.put("滁州市", "安徽省");
        CITY_PROVINCE_MAP.put("阜阳市", "安徽省");
        CITY_PROVINCE_MAP.put("宿州市", "安徽省");
        CITY_PROVINCE_MAP.put("六安市", "安徽省");
        CITY_PROVINCE_MAP.put("亳州市", "安徽省");
        CITY_PROVINCE_MAP.put("池州市", "安徽省");
        CITY_PROVINCE_MAP.put("宣城市", "安徽省");
        CITY_PROVINCE_MAP.put("福州市", "福建省");
        CITY_PROVINCE_MAP.put("厦门市", "福建省");
        CITY_PROVINCE_MAP.put("莆田市", "福建省");
        CITY_PROVINCE_MAP.put("三明市", "福建省");
        CITY_PROVINCE_MAP.put("泉州市", "福建省");
        CITY_PROVINCE_MAP.put("漳州市", "福建省");
        CITY_PROVINCE_MAP.put("南平市", "福建省");
        CITY_PROVINCE_MAP.put("龙岩市", "福建省");
        CITY_PROVINCE_MAP.put("宁德市", "福建省");
        CITY_PROVINCE_MAP.put("南昌市", "江西省");
        CITY_PROVINCE_MAP.put("景德镇市", "江西省");
        CITY_PROVINCE_MAP.put("萍乡市", "江西省");
        CITY_PROVINCE_MAP.put("九江市", "江西省");
        CITY_PROVINCE_MAP.put("新余市", "江西省");
        CITY_PROVINCE_MAP.put("鹰潭市", "江西省");
        CITY_PROVINCE_MAP.put("赣州市", "江西省");
        CITY_PROVINCE_MAP.put("吉安市", "江西省");
        CITY_PROVINCE_MAP.put("宜春市", "江西省");
        CITY_PROVINCE_MAP.put("抚州市", "江西省");
        CITY_PROVINCE_MAP.put("上饶市", "江西省");
        CITY_PROVINCE_MAP.put("济南市", "山东省");
        CITY_PROVINCE_MAP.put("青岛市", "山东省");
        CITY_PROVINCE_MAP.put("淄博市", "山东省");
        CITY_PROVINCE_MAP.put("枣庄市", "山东省");
        CITY_PROVINCE_MAP.put("东营市", "山东省");
        CITY_PROVINCE_MAP.put("烟台市", "山东省");
        CITY_PROVINCE_MAP.put("潍坊市", "山东省");
        CITY_PROVINCE_MAP.put("济宁市", "山东省");
        CITY_PROVINCE_MAP.put("泰安市", "山东省");
        CITY_PROVINCE_MAP.put("威海市", "山东省");
        CITY_PROVINCE_MAP.put("日照市", "山东省");
        CITY_PROVINCE_MAP.put("临沂市", "山东省");
        CITY_PROVINCE_MAP.put("德州市", "山东省");
        CITY_PROVINCE_MAP.put("聊城市", "山东省");
        CITY_PROVINCE_MAP.put("滨州市", "山东省");
        CITY_PROVINCE_MAP.put("菏泽市", "山东省");
        CITY_PROVINCE_MAP.put("郑州市", "河南省");
        CITY_PROVINCE_MAP.put("开封市", "河南省");
        CITY_PROVINCE_MAP.put("洛阳市", "河南省");
        CITY_PROVINCE_MAP.put("平顶山市", "河南省");
        CITY_PROVINCE_MAP.put("安阳市", "河南省");
        CITY_PROVINCE_MAP.put("鹤壁市", "河南省");
        CITY_PROVINCE_MAP.put("新乡市", "河南省");
        CITY_PROVINCE_MAP.put("焦作市", "河南省");
        CITY_PROVINCE_MAP.put("濮阳市", "河南省");
        CITY_PROVINCE_MAP.put("许昌市", "河南省");
        CITY_PROVINCE_MAP.put("漯河市", "河南省");
        CITY_PROVINCE_MAP.put("三门峡市", "河南省");
        CITY_PROVINCE_MAP.put("南阳市", "河南省");
        CITY_PROVINCE_MAP.put("商丘市", "河南省");
        CITY_PROVINCE_MAP.put("信阳市", "河南省");
        CITY_PROVINCE_MAP.put("周口市", "河南省");
        CITY_PROVINCE_MAP.put("驻马店市", "河南省");
        CITY_PROVINCE_MAP.put("武汉市", "湖北省");
        CITY_PROVINCE_MAP.put("黄石市", "湖北省");
        CITY_PROVINCE_MAP.put("十堰市", "湖北省");
        CITY_PROVINCE_MAP.put("宜昌市", "湖北省");
        CITY_PROVINCE_MAP.put("襄阳市", "湖北省");
        CITY_PROVINCE_MAP.put("鄂州市", "湖北省");
        CITY_PROVINCE_MAP.put("荆门市", "湖北省");
        CITY_PROVINCE_MAP.put("孝感市", "湖北省");
        CITY_PROVINCE_MAP.put("荆州市", "湖北省");
        CITY_PROVINCE_MAP.put("黄冈市", "湖北省");
        CITY_PROVINCE_MAP.put("咸宁市", "湖北省");
        CITY_PROVINCE_MAP.put("随州市", "湖北省");
        CITY_PROVINCE_MAP.put("长沙市", "湖南省");
        CITY_PROVINCE_MAP.put("株洲市", "湖南省");
        CITY_PROVINCE_MAP.put("湘潭市", "湖南省");
        CITY_PROVINCE_MAP.put("衡阳市", "湖南省");
        CITY_PROVINCE_MAP.put("邵阳市", "湖南省");
        CITY_PROVINCE_MAP.put("岳阳市", "湖南省");
        CITY_PROVINCE_MAP.put("常德市", "湖南省");
        CITY_PROVINCE_MAP.put("张家界市", "湖南省");
        CITY_PROVINCE_MAP.put("益阳市", "湖南省");
        CITY_PROVINCE_MAP.put("郴州市", "湖南省");
        CITY_PROVINCE_MAP.put("永州市", "湖南省");
        CITY_PROVINCE_MAP.put("怀化市", "湖南省");
        CITY_PROVINCE_MAP.put("娄底市", "湖南省");
        CITY_PROVINCE_MAP.put("广州市", "广东省");
        CITY_PROVINCE_MAP.put("韶关市", "广东省");
        CITY_PROVINCE_MAP.put("深圳市", "广东省");
        CITY_PROVINCE_MAP.put("珠海市", "广东省");
        CITY_PROVINCE_MAP.put("汕头市", "广东省");
        CITY_PROVINCE_MAP.put("佛山市", "广东省");
        CITY_PROVINCE_MAP.put("江门市", "广东省");
        CITY_PROVINCE_MAP.put("湛江市", "广东省");
        CITY_PROVINCE_MAP.put("茂名市", "广东省");
        CITY_PROVINCE_MAP.put("肇庆市", "广东省");
        CITY_PROVINCE_MAP.put("惠州市", "广东省");
        CITY_PROVINCE_MAP.put("梅州市", "广东省");
        CITY_PROVINCE_MAP.put("汕尾市", "广东省");
        CITY_PROVINCE_MAP.put("河源市", "广东省");
        CITY_PROVINCE_MAP.put("阳江市", "广东省");
        CITY_PROVINCE_MAP.put("清远市", "广东省");
        CITY_PROVINCE_MAP.put("东莞市", "广东省");
        CITY_PROVINCE_MAP.put("中山市", "广东省");
        CITY_PROVINCE_MAP.put("潮州市", "广东省");
        CITY_PROVINCE_MAP.put("揭阳市", "广东省");
        CITY_PROVINCE_MAP.put("云浮市", "广东省");
        CITY_PROVINCE_MAP.put("南宁市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("柳州市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("桂林市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("梧州市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("北海市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("防城港市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("钦州市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("贵港市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("玉林市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("百色市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("贺州市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("河池市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("来宾市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("崇左市", "广西壮族自治区");
        CITY_PROVINCE_MAP.put("海口市", "海南省");
        CITY_PROVINCE_MAP.put("三亚市", "海南省");
        CITY_PROVINCE_MAP.put("三沙市", "海南省");
        CITY_PROVINCE_MAP.put("儋州市", "海南省");
        CITY_PROVINCE_MAP.put("成都市", "四川省");
        CITY_PROVINCE_MAP.put("自贡市", "四川省");
        CITY_PROVINCE_MAP.put("攀枝花市", "四川省");
        CITY_PROVINCE_MAP.put("泸州市", "四川省");
        CITY_PROVINCE_MAP.put("德阳市", "四川省");
        CITY_PROVINCE_MAP.put("绵阳市", "四川省");
        CITY_PROVINCE_MAP.put("广元市", "四川省");
        CITY_PROVINCE_MAP.put("遂宁市", "四川省");
        CITY_PROVINCE_MAP.put("内江市", "四川省");
        CITY_PROVINCE_MAP.put("乐山市", "四川省");
        CITY_PROVINCE_MAP.put("南充市", "四川省");
        CITY_PROVINCE_MAP.put("眉山市", "四川省");
        CITY_PROVINCE_MAP.put("宜宾市", "四川省");
        CITY_PROVINCE_MAP.put("广安市", "四川省");
        CITY_PROVINCE_MAP.put("达州市", "四川省");
        CITY_PROVINCE_MAP.put("雅安市", "四川省");
        CITY_PROVINCE_MAP.put("巴中市", "四川省");
        CITY_PROVINCE_MAP.put("资阳市", "四川省");
        CITY_PROVINCE_MAP.put("贵阳市", "贵州省");
        CITY_PROVINCE_MAP.put("六盘水市", "贵州省");
        CITY_PROVINCE_MAP.put("遵义市", "贵州省");
        CITY_PROVINCE_MAP.put("安顺市", "贵州省");
        CITY_PROVINCE_MAP.put("毕节市", "贵州省");
        CITY_PROVINCE_MAP.put("铜仁市", "贵州省");
        CITY_PROVINCE_MAP.put("昆明市", "云南省");
        CITY_PROVINCE_MAP.put("曲靖市", "云南省");
        CITY_PROVINCE_MAP.put("玉溪市", "云南省");
        CITY_PROVINCE_MAP.put("保山市", "云南省");
        CITY_PROVINCE_MAP.put("昭通市", "云南省");
        CITY_PROVINCE_MAP.put("丽江市", "云南省");
        CITY_PROVINCE_MAP.put("普洱市", "云南省");
        CITY_PROVINCE_MAP.put("临沧市", "云南省");
        CITY_PROVINCE_MAP.put("拉萨市", "西藏自治区");
        CITY_PROVINCE_MAP.put("日喀则市", "西藏自治区");
        CITY_PROVINCE_MAP.put("昌都市", "西藏自治区");
        CITY_PROVINCE_MAP.put("林芝市", "西藏自治区");
        CITY_PROVINCE_MAP.put("山南市", "西藏自治区");
        CITY_PROVINCE_MAP.put("那曲市", "西藏自治区");
        CITY_PROVINCE_MAP.put("西安市", "陕西省");
        CITY_PROVINCE_MAP.put("铜川市", "陕西省");
        CITY_PROVINCE_MAP.put("宝鸡市", "陕西省");
        CITY_PROVINCE_MAP.put("咸阳市", "陕西省");
        CITY_PROVINCE_MAP.put("渭南市", "陕西省");
        CITY_PROVINCE_MAP.put("延安市", "陕西省");
        CITY_PROVINCE_MAP.put("汉中市", "陕西省");
        CITY_PROVINCE_MAP.put("榆林市", "陕西省");
        CITY_PROVINCE_MAP.put("安康市", "陕西省");
        CITY_PROVINCE_MAP.put("商洛市", "陕西省");
        CITY_PROVINCE_MAP.put("兰州市", "甘肃省");
        CITY_PROVINCE_MAP.put("嘉峪关市", "甘肃省");
        CITY_PROVINCE_MAP.put("金昌市", "甘肃省");
        CITY_PROVINCE_MAP.put("白银市", "甘肃省");
        CITY_PROVINCE_MAP.put("天水市", "甘肃省");
        CITY_PROVINCE_MAP.put("武威市", "甘肃省");
        CITY_PROVINCE_MAP.put("张掖市", "甘肃省");
        CITY_PROVINCE_MAP.put("平凉市", "甘肃省");
        CITY_PROVINCE_MAP.put("酒泉市", "甘肃省");
        CITY_PROVINCE_MAP.put("庆阳市", "甘肃省");
        CITY_PROVINCE_MAP.put("定西市", "甘肃省");
        CITY_PROVINCE_MAP.put("陇南市", "甘肃省");
        CITY_PROVINCE_MAP.put("西宁市", "青海省");
        CITY_PROVINCE_MAP.put("海东市", "青海省");
        CITY_PROVINCE_MAP.put("银川市", "宁夏回族自治区");
        CITY_PROVINCE_MAP.put("石嘴山市", "宁夏回族自治区");
        CITY_PROVINCE_MAP.put("吴忠市", "宁夏回族自治区");
        CITY_PROVINCE_MAP.put("固原市", "宁夏回族自治区");
        CITY_PROVINCE_MAP.put("中卫市", "宁夏回族自治区");
        CITY_PROVINCE_MAP.put("乌鲁木齐市", "新疆维吾尔自治区");
        CITY_PROVINCE_MAP.put("克拉玛依市", "新疆维吾尔自治区");
        CITY_PROVINCE_MAP.put("吐鲁番市", "新疆维吾尔自治区");
        CITY_PROVINCE_MAP.put("哈密市", "新疆维吾尔自治区");
    }

    private static final Logger log = LoggerFactory.getLogger(IpUtil.class);
    private static final String UNKNOWN_LOCATION = "未知位置";
    private static final String LOCAL_LOCATION = "局域网/本地";
    private static final int MAX_CACHE_SIZE = 10000;

    @Value("${ip.location.xdb-path:}")
    private String xdbPath;

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            byte[] xdbBuffer = loadXdbBuffer();
            if (xdbBuffer == null || xdbBuffer.length == 0) {
                log.warn("IpUtil 未找到 ip2region.xdb，本地 IP 归属地解析未启用");
                return;
            }
            this.searcher = Searcher.newWithBuffer(xdbBuffer);
            log.info("IpUtil 初始化完成，使用本地 ip2region.xdb 解析 IP 地址");
        } catch (Exception e) {
            log.error("IpUtil 初始化失败，将返回未知位置: {}", e.getMessage());
            this.searcher = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher == null) {
            return;
        }
        try {
            searcher.close();
        } catch (Exception e) {
            log.debug("关闭 ip2region searcher 失败: {}", e.getMessage());
        }
    }

    /**
     * 根据 IP 地址获取地理位置
     *
     * @param ip IP 地址
     * @return 格式化的位置字符串，如 "北京市" 或 "广东省深圳市 电信"
     */
    public String getLocation(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN_LOCATION;
        }

        if (isInternalIp(ip)) {
            return LOCAL_LOCATION;
        }

        String cached = cache.get(ip);
        if (cached != null) {
            return cached;
        }

        String location = queryLocationFromLocalDb(ip);
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(ip, location);
        }
        return location;
    }

    private byte[] loadXdbBuffer() throws Exception {
        if (xdbPath != null && !xdbPath.isBlank()) {
            Path path = Path.of(xdbPath.trim());
            if (Files.exists(path)) {
                return Searcher.loadContentFromFile(path.toString());
            }
            log.warn("配置的 ip2region.xdb 不存在: {}", path);
        }

        ClassPathResource resource = new ClassPathResource("ip2region.xdb");
        if (resource.exists()) {
            try (InputStream inputStream = resource.getInputStream()) {
                return inputStream.readAllBytes();
            }
        }

        return null;
    }

    private String queryLocationFromLocalDb(String ip) {
        if (searcher == null) {
            return UNKNOWN_LOCATION;
        }

        try {
            String region = searcher.search(ip);
            return formatRegion(region);
        } catch (Exception e) {
            log.debug("本地 IP 查询失败: ip={}, error={}", ip, e.getMessage());
            return UNKNOWN_LOCATION;
        }
    }

    private String formatRegion(String region) {
        if (region == null || region.isBlank()) {
            return UNKNOWN_LOCATION;
        }

        String[] parts = region.split("\\|");
        String country = getPart(parts, 0);
        String province = getPart(parts, 2);
        String city = getPart(parts, 3);
        String isp = getPart(parts, 4);

        if ("内网IP".equals(country) || "内网IP".equals(province) || "内网IP".equals(city) || "内网IP".equals(isp)) {
            return LOCAL_LOCATION;
        }

        List<String> locationParts = new ArrayList<>();
        if (!country.isEmpty() && !"中国".equals(country)) {
            locationParts.add(country);
        }
        if (!province.isEmpty()) {
            locationParts.add(province);
        }
        if (!city.isEmpty() && !city.equals(province)) {
            locationParts.add(city);
        }

        String location = String.join("", locationParts);

        
        // 自动提取可能缺失的省份信息
        for (java.util.Map.Entry<String, String> entry : CITY_PROVINCE_MAP.entrySet()) {
            if (location.startsWith(entry.getKey())) {
                location = entry.getValue() + location;
                break;
            }
        }
        
if (!isp.isEmpty()) {
            return location.isEmpty() ? isp : location + " " + isp;
        }

        return location.isEmpty() ? UNKNOWN_LOCATION : location;
    }

    private String getPart(String[] parts, int index) {
        if (index >= parts.length) {
            return "";
        }
        String value = parts[index];
        if (value == null || value.isBlank() || "0".equals(value)) {
            return "";
        }
        return value.trim();
    }

    /**
     * 判断是否为内网 IP
     */
    private boolean isInternalIp(String ip) {
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("172.16.") ||
                ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") ||
                ip.startsWith("172.19.") ||
                ip.startsWith("172.20.") ||
                ip.startsWith("172.21.") ||
                ip.startsWith("172.22.") ||
                ip.startsWith("172.23.") ||
                ip.startsWith("172.24.") ||
                ip.startsWith("172.25.") ||
                ip.startsWith("172.26.") ||
                ip.startsWith("172.27.") ||
                ip.startsWith("172.28.") ||
                ip.startsWith("172.29.") ||
                ip.startsWith("172.30.") ||
                ip.startsWith("172.31.") ||
                ip.startsWith("127.") ||
                ip.equals("localhost");
    }
}
