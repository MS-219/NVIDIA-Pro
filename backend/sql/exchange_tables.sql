-- ============================================
-- 设备兑换商城 - 数据库迁移脚本
-- ============================================

-- 1. 商品表
CREATE TABLE IF NOT EXISTS exchange_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    image_url VARCHAR(500) COMMENT '商品主图',
    images TEXT COMMENT '商品图片列表(JSON数组)',
    base_price DECIMAL(10,2) NOT NULL COMMENT '基础价格(元)',
    price_level1 DECIMAL(10,2) COMMENT '会员价(元)',
    price_level2 DECIMAL(10,2) COMMENT '社区价(元)',
    price_level3 DECIMAL(10,2) COMMENT '县级价(元)',
    price_level4 DECIMAL(10,2) COMMENT '市级价(元)',
    price_level5 DECIMAL(10,2) COMMENT '联创价(元)',
    stock INT DEFAULT 0 COMMENT '库存数量',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    sort_order INT DEFAULT 0 COMMENT '排序权重(越大越靠前)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '兑换商品表';

-- 2. 收货地址表
CREATE TABLE IF NOT EXISTS user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    receiver_name VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    phone VARCHAR(20) NOT NULL COMMENT '收货人电话',
    province VARCHAR(50) COMMENT '省',
    city VARCHAR(50) COMMENT '市',
    district VARCHAR(50) COMMENT '区',
    detail_address VARCHAR(200) NOT NULL COMMENT '详细地址',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认地址: 0-否, 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) COMMENT '用户收货地址表';

-- 3. 兑换订单表
CREATE TABLE IF NOT EXISTS exchange_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(100) COMMENT '商品名称快照',
    product_image VARCHAR(500) COMMENT '商品图片快照',
    quantity INT DEFAULT 1 COMMENT '数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价(元)',
    total_price DECIMAL(10,2) NOT NULL COMMENT '总价(元)',
    hashrate_cost BIGINT NOT NULL COMMENT '消耗算力值(展示用)',
    user_level INT COMMENT '下单时用户等级',
    receiver_name VARCHAR(50) COMMENT '收货人',
    receiver_phone VARCHAR(20) COMMENT '收货电话',
    receiver_address VARCHAR(300) COMMENT '完整收货地址',
    status TINYINT DEFAULT 0 COMMENT '订单状态: 0-待发货, 1-已发货, 2-运输中, 3-已到货, 4-已取消',
    express_company VARCHAR(50) COMMENT '快递公司',
    express_no VARCHAR(50) COMMENT '快递单号',
    ship_time DATETIME COMMENT '发货时间',
    receive_time DATETIME COMMENT '收货时间',
    remark VARCHAR(200) COMMENT '用户备注',
    admin_remark VARCHAR(200) COMMENT '管理员备注',
    inviter_id BIGINT COMMENT '邀请人ID',
    inviter_level INT COMMENT '邀请人等级',
    inviter_profit DECIMAL(10,2) DEFAULT 0 COMMENT '邀请人分润金额(元)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) COMMENT '设备兑换订单表';

-- 4. 物流跟踪表
CREATE TABLE IF NOT EXISTS exchange_logistics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    status TINYINT NOT NULL COMMENT '状态: 0-待发货, 1-已发货, 2-运输中, 3-已到货',
    description VARCHAR(200) COMMENT '物流描述',
    operator VARCHAR(50) COMMENT '操作人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
) COMMENT '物流跟踪记录表';
