package com.fc.v2.common.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认短信出口：真实网关未接前，先落日志当送到。
 * 接上真网关时另立实现顶替本 bean 即可，周期任务一行不用动。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Component
public class LoggingIchSmsSender implements IchSmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingIchSmsSender.class);

    @Override
    public boolean send(String phone, String text) {
        log.info("手机短信（默认落日志出口）发往 {}：{}", phone, text);
        return true;
    }
}
