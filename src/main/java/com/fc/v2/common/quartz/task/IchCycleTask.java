package com.fc.v2.common.quartz.task;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.cycle.CycleDict;
import com.fc.v2.model.custom.cycle.CycleRoundOutcome;
import com.fc.v2.service.ITIchCycleTaskService;

/**
 * 履约考核到期单周期任务入口（quartz 调用目标：ichCycleTask.runRoundNow()）。
 *
 * <p>名录项目的保护履约催办、传承人的年度考核，两桩按日子走的差事都从这里过：
 * 本轮捞出哪几单、捞出来办没办到位，全由服务层当轮算过再落库，这里只递一句话。
 * "本轮无到期"是正常收尾不是故障——别为这句话半夜打电话。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Component("ichCycleTask")
public class IchCycleTask {

    private static final Logger log = LoggerFactory.getLogger(IchCycleTask.class);

    @Autowired
    private ITIchCycleTaskService ichCycleTaskService;

    /**
     * 无参的任务：按此刻开口一轮。压着的那一段它自己不动；
     * 一个都没挑着时只落一句"本轮无到期"，不告警不叫人。
     */
    public void runRoundNow() {
        CycleRoundOutcome outcome = ichCycleTaskService.runRound(new Date());
        if (outcome.isPaused()) {
            log.info("履约考核到期单本轮压着不动：{}", outcome.getMessage());
        } else if (CycleDict.MSG_NONE_DUE.equals(outcome.getMessage())) {
            log.info("履约考核到期单本轮正常收尾：{}", outcome.getMessage());
        } else {
            log.info("履约考核到期单本轮收官：{}", outcome.getMessage());
        }
    }
}
