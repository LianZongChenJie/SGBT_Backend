-- 监测采集点表：第三方实时库(pSpace风格/SCADA)采集点静态信息 + 最近一次采集值
-- 参照 fwbz device_attribute(点+实时值) 结构；category 为设备分类(点表sheet名)，pid 即第三方点ID(tagid)
CREATE TABLE IF NOT EXISTS `monitor_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pid` BIGINT NOT NULL COMMENT '第三方点ID(tagid)',
  `category` VARCHAR(64) NOT NULL COMMENT '设备类别',
  `tag_type` VARCHAR(32) DEFAULT NULL COMMENT '点类型(psDigital/psAnalog/psNode)',
  `long_name` VARCHAR(255) DEFAULT NULL COMMENT '点位长名',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
  `data_type` VARCHAR(32) DEFAULT NULL COMMENT '数据类型(psDataType_Bool/psDataType_Double等)',
  `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位(预留)',
  `value` VARCHAR(64) DEFAULT NULL COMMENT '最新采集值',
  `gather_time` DATETIME DEFAULT NULL COMMENT '最新采集时间',
  `online` TINYINT NOT NULL DEFAULT 1 COMMENT '在线状态 1在线 0离线',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_pid` (`pid`),
  KEY `idx_point_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监测采集点表';

-- 监测点历史表：15分钟对齐槽位(每点每槽一条，upsert)
CREATE TABLE IF NOT EXISTS `monitor_point_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pid` BIGINT NOT NULL COMMENT '第三方点ID',
  `category` VARCHAR(64) NOT NULL COMMENT '设备类别',
  `value` VARCHAR(64) DEFAULT NULL COMMENT '采集值',
  `collection_time` DATETIME NOT NULL COMMENT '采集时间(15分钟对齐槽位)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hist_pid_time` (`pid`,`collection_time`),
  KEY `idx_hist_category_time` (`category`,`collection_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监测点15分钟历史表';
