package org.jeecg.modules.bems.fire.service;

/**
 * 消防报警采集服务：
 * 每 2 分钟请求 pSpace 历史数据接口 /HistData（tagids 5282-5292，区间=当前时间前2分钟~当前），
 * 依据 5292(二次码) 匹配消防设备属性，结合 5282/5283/5284 生成告警记录 alarm_record。
 */
public interface IFireAlarmService {

    /**
     * 采集一次消防历史数据并生成告警记录
     *
     * @return 本次新增的告警记录数
     */
    int collectFireAlarm();
}
