package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

/**
 * 按 master_id upsert 的统一返回结果。
 * 供 bems↔master 主数据接收服务使用：ok 返回本地 id；fail 返回原因（进 rejected 列表）。
 */
@Data
public class UpsertResult {
    private Long localId;
    private boolean ok;
    private String reason;

    public static UpsertResult ok(Long id) {
        UpsertResult r = new UpsertResult();
        r.ok = true;
        r.localId = id;
        return r;
    }

    public static UpsertResult fail(String reason) {
        UpsertResult r = new UpsertResult();
        r.ok = false;
        r.reason = reason;
        return r;
    }
}
