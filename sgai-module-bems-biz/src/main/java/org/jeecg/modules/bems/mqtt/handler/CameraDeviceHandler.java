package org.jeecg.modules.bems.mqtt.handler;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mqtt.dto.ImageResultDTO;
import org.jeecg.modules.bems.mqtt.dto.ResultReportDTO;
import org.jeecg.modules.bems.mqtt.entity.CameraMessageLogEntity;
import org.jeecg.modules.bems.mqtt.entity.VehicleRecordEntity;
import org.jeecg.modules.bems.mqtt.enums.CmdEnum;
import org.jeecg.modules.bems.mqtt.enums.RspStatusEnum;
import org.jeecg.modules.bems.mqtt.service.CameraMessageLogService;
import org.jeecg.modules.bems.mqtt.service.VehicleRecordService;
import org.jeecg.modules.bems.mqtt.vo.HandleResult;
import org.jeecg.modules.bems.mqtt.vo.ResultRspVO;
import org.springframework.stereotype.Component;

/**
 * 相机设备消息处理器
 */
@Slf4j
@Component
public class CameraDeviceHandler implements DeviceMessageHandler {

    private final VehicleRecordService vehicleRecordService;
    private final CameraMessageLogService cameraMessageLogService;

    public CameraDeviceHandler(VehicleRecordService vehicleRecordService,
                               CameraMessageLogService cameraMessageLogService) {
        this.vehicleRecordService = vehicleRecordService;
        this.cameraMessageLogService = cameraMessageLogService;
    }

    @Override
    public String supportDeviceType() {
        return "camera";
    }

    @Override
    public HandleResult handle(String topic, String payload,
                               String deviceType, String deviceId, String action) {
        // 应答类消息不再回应答
        if (action != null && action.endsWith("_rsp")) {
            log.debug("忽略应答类消息 action={}", action);
            return HandleResult.of(null, deviceType, deviceId, action);
        }

        CmdEnum cmdEnum = CmdEnum.of(action);
        if (cmdEnum == null) {
            log.warn("camera 不支持的 action: {}", action);
            return HandleResult.of(ResultRspVO.fail("", "unsupported_action"),
                    deviceType, deviceId, action);
        }

        switch (cmdEnum) {
            case RESULT:
                return handleResult(topic, payload, deviceType, deviceId, action);
            case IMAGE_RESULT:
                return handleImageResult(topic, payload, deviceType, deviceId, action);
            default:
                return HandleResult.of(ResultRspVO.fail("", "unsupported_action"),
                        deviceType, deviceId, action);
        }
    }

    private HandleResult handleResult(String topic, String payload,
                                      String deviceType, String deviceId, String action) {
        ResultReportDTO dto;
        try {
            dto = JSON.parseObject(payload, ResultReportDTO.class);
        } catch (Exception e) {
            log.error("ResultReportDTO解析失败", e);
            saveLog(topic, payload, "", null, RspStatusEnum.PARAM_ERROR.getCode());
            return HandleResult.of(ResultRspVO.fail("", RspStatusEnum.PARAM_ERROR.getCode()),
                    deviceType, deviceId, action);
        }

        String msgId = dto.getMsgId();
        String sn = firstNonBlank(deviceId, dto.getSn());


        log.info("收到结果上报: msgId={}, type={}, plateNum={}, sn={}, 是否带图片={}",
                msgId, dto.getType(), dto.getPlateNum(), sn, hasInlineImage(dto));

        try {
            // 完整转换：图片字段、imageUploaded 均在 fromDTO 内处理
            VehicleRecordEntity entity = VehicleRecordEntity.fromDTO(dto);
            entity.setCmd(CmdEnum.RESULT.getCode());
            entity.setDeviceType(deviceType);
            if (sn != null) {
                entity.setSn(sn);
            }

            if (Boolean.TRUE.equals(entity.getImageUploaded())) {
                log.info("一起上传模式：full_pic_len={}, plate_pic_len={}",
                        dto.getFullPicLen(), dto.getPlatePicLen());
            } else {
                log.info("分开上传模式：full_pic_path={}, plate_pic_path={}",
                        dto.getFullPicPath(), dto.getPlatePicPath());
            }

            vehicleRecordService.saveOrUpdateByMsgId(entity);
            saveLog(topic, payload, msgId, entity.getId(), RspStatusEnum.OK.getCode());

            return HandleResult.of(ResultRspVO.ok(msgId), deviceType, sn, action);
        } catch (Exception e) {
            log.error("处理结果上报失败 msgId={}", msgId, e);
            saveLog(topic, payload, msgId, null, RspStatusEnum.SERVER_ERROR.getCode());
            return HandleResult.of(ResultRspVO.fail(msgId, RspStatusEnum.SERVER_ERROR.getCode()),
                    deviceType, sn, action);
        }
    }

    private HandleResult handleImageResult(String topic, String payload,
                                           String deviceType, String deviceId, String action) {
        ImageResultDTO dto;
        try {
            dto = JSON.parseObject(payload, ImageResultDTO.class);
        } catch (Exception e) {
            log.error("ImageResultDTO解析失败", e);
            saveLog(topic, payload, "", null, RspStatusEnum.PARAM_ERROR.getCode());
            return HandleResult.of(ResultRspVO.fail("", RspStatusEnum.PARAM_ERROR.getCode()),
                    deviceType, deviceId, action);
        }

        String msgId = dto.getMsgId();
        log.info("收到图片消息: msgId={}, utcTs={}, fullPicLen={}, platePicLen={}",
                msgId, dto.getUtcTs(), dto.getFullPicLen(), dto.getPlatePicLen());

        try {
            VehicleRecordEntity record = vehicleRecordService.findLatestByUtcTs(dto.getUtcTs());
            if (record == null) {
                log.warn("未找到匹配的 result 记录，utcTs={}，无法确定 sn，不回应答", dto.getUtcTs());
                saveLog(topic, payload, msgId, null, "no_match_record");
                return HandleResult.of(ResultRspVO.fail(msgId, "no_match_record"),
                        deviceType, deviceId, action);
            }

            record.setCmd(CmdEnum.IMAGE_RESULT.getCode());
            record.setFullPic(dto.getFullPic());
            record.setPlatePic(dto.getPlatePic());
            record.setFullPicLen(dto.getFullPicLen());
            record.setPlatePicLen(dto.getPlatePicLen());
            record.setImageUploaded(true);
            record.setImageMsgId(msgId);
            vehicleRecordService.updateById(record);

            // 优先用 topic 里的 deviceId，否则用关联记录里的 sn
            String sn = firstNonBlank(deviceId, record.getSn());
            log.info("图片已关联到记录 id={}, plateNum={}, sn={}",
                    record.getId(), record.getPlateNum(), sn);

            saveLog(topic, payload, msgId, record.getId(), RspStatusEnum.OK.getCode());
            return HandleResult.of(ResultRspVO.ok(msgId), deviceType, sn, action);
        } catch (Exception e) {
            log.error("处理图片消息失败 msgId={}", msgId, e);
            saveLog(topic, payload, msgId, null, RspStatusEnum.SERVER_ERROR.getCode());
            return HandleResult.of(ResultRspVO.fail(msgId, RspStatusEnum.SERVER_ERROR.getCode()),
                    deviceType, deviceId, action);
        }
    }

    private boolean hasInlineImage(ResultReportDTO dto) {
        return (dto.getFullPic() != null && !dto.getFullPic().isEmpty())
                || (dto.getPlatePic() != null && !dto.getPlatePic().isEmpty());
    }

    private void saveLog(String topic, String payload, String msgId, Long recordId, String status) {
        try {
            CameraMessageLogEntity logEntity = new CameraMessageLogEntity();
            logEntity.setTopic(topic);
            logEntity.setMsgId(msgId);
            logEntity.setPayload(payload);
            logEntity.setRecordId(recordId);
            logEntity.setStatus(status);
            logEntity.setDirection("up");
            cameraMessageLogService.save(logEntity);
        } catch (Exception e) {
            log.error("保存消息日志失败", e);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }
}