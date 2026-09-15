-- 消防系统: 新增设备类别(type=2) + 设备 XFXT（自包含、可重复执行）
USE `bems`;

-- 1. 设备类别 消防系统
INSERT INTO equipment_category(pid, has_child, category_name, full_name, type, sort)
SELECT 0, '0', '消防系统', '消防系统', '2', 20
WHERE NOT EXISTS (SELECT 1 FROM equipment_category WHERE category_name = '消防系统' AND pid = 0);

-- 2. 设备 XFXT（category_id 取上面类别 id）
INSERT INTO device(device_code, device_name, device_type, category_id)
SELECT 'XFXT', '消防系统', '2',
       (SELECT id FROM equipment_category WHERE category_name = '消防系统' AND pid = 0 LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM device WHERE device_code = 'XFXT');
