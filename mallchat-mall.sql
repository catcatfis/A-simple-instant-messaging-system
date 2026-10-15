-- ============================================================
-- MallChat 商城模块（商户 / 优惠券 / 秒杀）
-- 参考 D:\heimadianping 的 tb_* 表设计，字段保持完全一致
-- 表名统一加 tb_ 前缀以标识商城域，避免与聊天域表混淆
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 商户类型
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_shop_type` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商户类型' ROW_FORMAT = Compact;

-- ------------------------------------------------------------
-- 商户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_shop` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '商铺类型的id',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商铺图片，多个图片以,隔开',
  `area` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商圈',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地址',
  `x` double NOT NULL COMMENT '经度',
  `y` double NOT NULL COMMENT '纬度',
  `avg_price` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '均价，取整数',
  `sold` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '销量',
  `comments` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量',
  `score` int(2) UNSIGNED NOT NULL DEFAULT 0 COMMENT '评分，1~5分，乘10保存',
  `open_hours` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '营业时间',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `foreign_key_type`(`type_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商户' ROW_FORMAT = Compact;

-- ------------------------------------------------------------
-- 优惠券（普通券 / 秒杀券）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_voucher` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '商铺id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '代金券标题',
  `sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT '支付金额，单位是分',
  `actual_value` bigint(10) NOT NULL COMMENT '抵扣金额，单位是分',
  `type` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0,普通券；1,秒杀券',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1,上架; 2,下架; 3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券' ROW_FORMAT = Compact;

-- ------------------------------------------------------------
-- 秒杀优惠券（与 tb_voucher 一对一）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_seckill_voucher` (
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的优惠券的id',
  `stock` int(8) NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `end_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀优惠券表' ROW_FORMAT = Compact;

-- ------------------------------------------------------------
-- 优惠券订单
-- 关键：uniq_user_voucher 唯一索引兜底「一人一单」，
-- 即使 Lua 脚本与 Redisson 锁都被绕过，数据库仍能拦住重复下单
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_voucher_order` (
  `id` bigint(20) NOT NULL COMMENT '主键（Redis 全局唯一ID）',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '下单的用户id',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '购买的代金券id',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uniq_user_voucher` (`user_id`, `voucher_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券订单' ROW_FORMAT = Compact;

-- ============================================================
-- 示例数据
-- ============================================================

INSERT INTO `tb_shop_type` (`id`, `name`, `icon`, `sort`) VALUES
 (1, '美食', '/types/food.png', 1),
 (2, '休闲娱乐', '/types/entertainment.png', 2),
 (3, '酒店', '/types/hotel.png', 3),
 (4, '丽人/美发', '/types/beauty.png', 4),
 (5, '运动健身', '/types/sports.png', 5),
 (6, '看电影', '/types/movie.png', 6),
 (7, '亲子游乐', '/types/kids.png', 7),
 (8, 'KTV', '/types/ktv.png', 8),
 (9, '按摩足疗', '/types/massage.png', 9),
 (10, '咖啡/饮品', '/types/coffee.png', 10)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `tb_shop` (`id`, `name`, `type_id`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`) VALUES
 (1, '海底捞火锅(人民广场店)', 1, '/imgs/shop/haidilao1.jpg,/imgs/shop/haidilao2.jpg', '人民广场', '上海市黄浦区南京西路100号', 121.4737, 31.2304, 128, 3521, 1024, 48, '10:00-23:00'),
 (2, '星巴克(南京东路店)', 10, '/imgs/shop/starbucks1.jpg', '南京东路', '上海市黄浦区南京东路300号', 121.4805, 31.2360, 42, 8762, 2130, 46, '07:00-22:00'),
 (3, '外婆家(静安寺店)', 1, '/imgs/shop/waipojia1.jpg', '静安寺', '上海市静安区南京西路1618号', 121.4450, 31.2246, 76, 2143, 687, 45, '11:00-21:30'),
 (4, '万达影城(五角场店)', 6, '/imgs/shop/wanda1.jpg', '五角场', '上海市杨浦区淞沪路77号', 121.5150, 31.3060, 55, 5432, 1567, 47, '09:00-24:00'),
 (5, '一兆韦德健身(徐家汇店)', 5, '/imgs/shop/weide1.jpg', '徐家汇', '上海市徐汇区肇嘉浜路1065号', 121.4380, 31.1950, 199, 892, 321, 44, '06:00-23:00'),
 (6, '全季酒店(陆家嘴店)', 3, '/imgs/shop/jijihotel1.jpg', '陆家嘴', '上海市浦东新区世纪大道88号', 121.5060, 31.2400, 480, 1265, 430, 45, '全天'),
 (7, '纯K量贩KTV(中山公园店)', 8, '/imgs/shop/chunk1.jpg', '中山公园', '上海市长宁区长宁路1018号', 121.4200, 31.2200, 156, 1876, 542, 43, '12:00-次日02:00'),
 (8, '良子足道(徐汇店)', 9, '/imgs/shop/liangzi1.jpg', '徐家汇', '上海市徐汇区衡山路811号', 121.4430, 31.2010, 168, 654, 213, 44, '11:00-次日01:00'),
 (9, '汤姆熊欢乐世界(大宁店)', 7, '/imgs/shop/tom1.jpg', '大宁', '上海市静安区共和新路1898号', 121.4590, 31.2760, 88, 2341, 765, 46, '10:00-22:00'),
 (10, 'SEVEN造型(打浦桥店)', 4, '/imgs/shop/seven1.jpg', '打浦桥', '上海市黄浦区徐家汇路618号', 121.4720, 31.2050, 320, 432, 156, 45, '10:00-21:00'),
 (11, '喜茶(新天地店)', 10, '/imgs/shop/heytea1.jpg', '新天地', '上海市黄浦区马当路245号', 121.4760, 31.2180, 28, 9876, 2456, 47, '10:00-22:00'),
 (12, '西贝莜面村(环球港店)', 1, '/imgs/shop/xibei1.jpg', '曹杨', '上海市普陀区金沙江路330号', 121.4180, 31.2360, 92, 1654, 498, 46, '10:30-21:30')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 普通券（type=0）
INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`) VALUES
 (1, 1, '海底捞 100 元代金券', '全场通用', '仅限堂食，每桌限用一张', 8000, 10000, 0, 1),
 (2, 2, '星巴克 30 元代金券', '中杯及以上可用', '不与其他优惠同享', 2500, 3000, 0, 1),
 (3, 4, '万达影城 50 元观影券', '2D/3D 通用', '节假日不可用，需提前选座', 3900, 5000, 0, 1),
 (4, 11, '喜茶 20 元代金券', '全场饮品通用', '每单限用一张', 1500, 2000, 0, 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- 秒杀券（type=1），需要与 tb_seckill_voucher 配套
INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`) VALUES
 (5, 3, '外婆家 双人套餐秒杀', '限量 100 份', '需提前预约，仅限工作日', 9900, 18800, 1, 1),
 (6, 5, '一兆韦德 月卡秒杀', '限量 50 份', '开卡后 30 天内有效', 19900, 39900, 1, 1),
 (7, 7, '纯K 欢唱 3 小时秒杀', '限量 200 份', '需提前一天预约', 4900, 12800, 1, 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- 秒杀券库存与时间窗（now 前后各留 1 天，保证测试时处于可秒杀状态）
INSERT INTO `tb_seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
 (5, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY)),
 (6, 50,  DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY)),
 (7, 200, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE `stock` = VALUES(`stock`), `begin_time` = VALUES(`begin_time`), `end_time` = VALUES(`end_time`);
