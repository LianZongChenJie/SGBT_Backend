-- 三张主数据表加 master_id（可空 + 唯一索引）
ALTER TABLE device ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE device ADD UNIQUE INDEX uk_device_master_id (master_id);

ALTER TABLE equipment_category ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE equipment_category ADD UNIQUE INDEX uk_category_master_id (master_id);

ALTER TABLE space ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE space ADD UNIQUE INDEX uk_space_master_id (master_id);

-- 推送日志表
CREATE TABLE integration_push_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  batch_id    VARCHAR(64)  NOT NULL COMMENT '推送批次 uuid',
  op          VARCHAR(16)  NULL COMMENT 'UPSERT/DELETE/SNAPSHOT',
  type        VARCHAR(16)  NULL COMMENT '目前固定 DEVICE',
  data_count  INT          NULL COMMENT '推送条数',
  data_ids    VARCHAR(2000) NULL COMMENT '推送设备 master_id 列表',
  status      VARCHAR(16)  NULL COMMENT 'SUCCESS/FAIL',
  http_status INT          NULL COMMENT 'master 返回 HTTP 状态码',
  response_msg VARCHAR(500) NULL COMMENT 'master 返回 message 或异常',
  create_time DATETIME     NULL COMMENT '推送时间',
  PRIMARY KEY (id),
  KEY idx_push_batch (batch_id)
) COMMENT='主数据推送日志';
