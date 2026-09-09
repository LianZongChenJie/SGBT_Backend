-- 楼控监测设备基础数据录入(第二步)
USE `bems`;
INSERT INTO device(device_code, device_name, device_type, sort)
SELECT t.code, t.code, '2', t.sort FROM (
  SELECT 'YLRQBJQ' AS code, 0 AS sort UNION ALL SELECT 'RLRQBJQ' AS code, 0 AS sort UNION ALL SELECT 'ELDB' AS code, 0 AS sort UNION ALL SELECT 'YLQQBJQ' AS code, 0 AS sort UNION ALL SELECT 'YSCLQ' AS code, 0 AS sort UNION ALL SELECT 'GL1' AS code, 0 AS sort UNION ALL SELECT 'GL2' AS code, 0 AS sort UNION ALL SELECT 'GL3' AS code, 0 AS sort UNION ALL SELECT 'GLFJ' AS code, 0 AS sort UNION ALL SELECT 'LD' AS code, 0 AS sort UNION ALL SELECT 'BFXTSCL' AS code, 0 AS sort UNION ALL SELECT 'CQ_HQ' AS code, 0 AS sort UNION ALL SELECT 'CQ_QQJYQ' AS code, 0 AS sort UNION ALL SELECT 'GRXT' AS code, 0 AS sort UNION ALL SELECT 'GF' AS code, 0 AS sort UNION ALL SELECT 'NYZJC' AS code, 0 AS sort
) t
WHERE NOT EXISTS (SELECT 1 FROM device d WHERE d.device_code = t.code);
