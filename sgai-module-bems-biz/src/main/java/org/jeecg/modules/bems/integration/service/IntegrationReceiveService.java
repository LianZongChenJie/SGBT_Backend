package org.jeecg.modules.bems.integration.service;

import org.jeecg.modules.bems.integration.dto.IntegrationPayload;
import org.jeecg.modules.bems.integration.dto.ReceiveResult;

public interface IntegrationReceiveService {
    /**
     * @param deviceType "1" 仪表 / "2" 设备（由端点决定，仅 DEVICE/CATEGORY 落库赋值用；SPACE 忽略）
     */
    ReceiveResult receive(IntegrationPayload<?> payload, String deviceType);
}
