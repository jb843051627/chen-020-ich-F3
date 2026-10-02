package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.custom.cycle.CycleRoundOutcome;
import com.fc.v2.model.custom.cycle.CycleRowView;

/**
 * 履约考核到期单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）。
 *
 * <p>两条路数分开：{@link #selectTIchCycleTaskById(Long)}、{@link #listDue(Date)}、
 * {@link #runOnce(Date)} 是扛这一步的老方法，参数与返回一律不碰，原样维持；
 * 新一轮按日子自己走的捞法单起方法名——查单走 {@link #listBoard(Date)}，
 * 回写走 {@link #runRound(Date)}，两把活共用同一份剩余天数算法。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIchCycleTaskService {

    /** 按主键回查条目 */
    TIchCycleTask selectTIchCycleTaskById(Long id);

    /** 该时刻可处理的条目（执行窗口内 + 到期 + 尚未处理） */
    List<TIchCycleTask> listDue(Date at);

    /** 执行一次，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 查单：此刻到期单一览（催不动的行子不藏，照样列着）。
     *
     * <p>每一行带上按唯一一把尺点出的剩余天数与窗口状态；只读不动库，
     * 页面既添不上一单也剔除不掉一单。
     *
     * @param at 此刻
     * @return 逐行视图（按该出手那日、单号先后排定）
     */
    List<CycleRowView> listBoard(Date at);

    /**
     * 回写：本轮周期任务——捞出、办理、落库、点算，全在这一回里。
     *
     * <p>规矩：压着的那一段一行不动；挑单只问该出手那天到没到；
     * 办妥落两个痕迹（去向翻办妥 + 办妥那一刻，同一次落库）；
     * 催办两头递（县文旅局站内一条、传承人手机一条，各记各的）；
     * 项目销号或联系人空着的行子就地改判催不动，缘由写进同一行；
     * 越过该出手那日还没送出去的，把话撤回另记一笔，不盖旧字；
     * 一行受阻不牵连其余，剩下的仍要一行一行走到底；
     * 三个数（还欠看的、这一轮办妥的、催不动的）同源于这一回点算。
     *
     * @param at 本轮下手的时刻
     * @return 本轮点算结局（一个都没挑着时寄语"本轮无到期"，正常收尾）
     */
    CycleRoundOutcome runRound(Date at);
}
