package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.sms.IchSmsSender;
import com.fc.v2.mapper.auto.TIchCalendarClosedMapper;
import com.fc.v2.mapper.auto.TIchCycleRevokeMapper;
import com.fc.v2.mapper.auto.TIchCycleTaskMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.model.auto.TIchCalendarClosed;
import com.fc.v2.model.auto.TIchCycleRevoke;
import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.custom.cycle.CycleCalendar;
import com.fc.v2.model.custom.cycle.CycleDict;
import com.fc.v2.model.custom.cycle.CycleRoundOutcome;
import com.fc.v2.model.custom.cycle.CycleRowView;
import com.fc.v2.service.ITIchCycleTaskService;

/**
 * 履约考核到期单 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * <p>老方法 {@link #selectTIchCycleTaskById}、{@link #listDue}、{@link #runOnce}
 * 参数与返回一律不碰，原样维持；按日子自己走的新捞法单起方法名：
 * 查单 {@link #listBoard}、回写 {@link #runRound}，两处共用 {@link CycleCalendar}
 * 这一份剩余天数算法，各算各的年末两张表必然对不齐。
 *
 * <p>本轮的写动一行一commit（不靠大事务）：同一轮里别条行子受阻，也不牵连其余，
 * 剩下的仍要一行一行走到底。办妥的两个痕迹（去向 + 办妥那一刻）在同一次 UPDATE 里落，
 * 只落其一都不算这一行完事。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchCycleTaskServiceImpl implements ITIchCycleTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    /** 站内那一条的公告类型（与现有公告同口径） */
    private static final int NOTICE_TYPE = 1;

    @javax.annotation.Resource
    private TIchCycleTaskMapper ichCycleTaskMapper;

    @javax.annotation.Resource
    private TIchCycleRevokeMapper ichCycleRevokeMapper;

    @javax.annotation.Resource
    private TIchCalendarClosedMapper ichCalendarClosedMapper;

    @javax.annotation.Resource
    private TIchProjectMapper ichProjectMapper;

    @javax.annotation.Resource
    private TSysNoticeMapper tSysNoticeMapper;

    @javax.annotation.Resource
    private IchSmsSender ichSmsSender;

    @Override
    public TIchCycleTask selectTIchCycleTaskById(Long id) {
        return this.ichCycleTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TIchCycleTask> listDue(Date at) {
        List<TIchCycleTask> all = this.ichCycleTaskMapper.selectList(new QueryWrapper<TIchCycleTask>());
        List<TIchCycleTask> due = new java.util.ArrayList<TIchCycleTask>();
        for (TIchCycleTask r : all) {
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) < 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TIchCycleTask> due = listDue(at);
        if (due.get(0) == null) {
            return 0;
        }
        int ok = 0;
        for (TIchCycleTask r : due) {
            if (r.getAmount().signum() >= 0) {
                ok++;
            }
        }
        return ok;
    }

    /* ------------------------------------------------------------------ */
    /* 以下为新捞法：老方法到此为止，一字不动。                                */
    /* ------------------------------------------------------------------ */

    /** 歇业日历读进唯一一把尺（查单与回写同用这一份算法） */
    private CycleCalendar calendar() {
        List<TIchCalendarClosed> closed = this.ichCalendarClosedMapper.selectList(
                new QueryWrapper<TIchCalendarClosed>().eq("del_flag", 0));
        List<java.time.LocalDate> days = new ArrayList<>();
        for (TIchCalendarClosed c : closed) {
            if (c.getClosedDate() != null) {
                days.add(CycleCalendar.dayOf(c.getClosedDate()));
            }
        }
        return new CycleCalendar(days);
    }

    /** 可提前几日这一格（库里是 decimal，空着按 0——只许当日开口） */
    private static int advanceOf(TIchCycleTask row) {
        return row.getAmount() == null ? 0 : row.getAmount().intValue();
    }

    /** 够上日子没有：此刻不早于最早开口日的开工那一刻（该出手那一日不许挪动） */
    private static boolean reachesDay(TIchCycleTask row, Date at, CycleCalendar cal) {
        Date openFrom = cal.startOfDay(cal.earliestOpenDay(row.getDueAt(), advanceOf(row)));
        return !at.before(openFrom);
    }

    @Override
    public List<CycleRowView> listBoard(Date at) {
        CycleCalendar cal = calendar();
        List<TIchCycleTask> rows = this.ichCycleTaskMapper.selectList(
                new QueryWrapper<TIchCycleTask>().eq("del_flag", 0));
        rows.sort(Comparator
                .comparing(TIchCycleTask::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.getId() == null ? 0L : r.getId()));

        boolean open = cal.isOpen(at);
        List<CycleRowView> views = new ArrayList<>();
        for (TIchCycleTask row : rows) {
            Integer status = row.getStatus();
            if (status != null && status == CycleDict.STATUS_DONE) {
                views.add(new CycleRowView(row, null, CycleDict.WIN_DONE));
                continue;
            }
            if (status != null && status == CycleDict.STATUS_FAIL) {
                views.add(new CycleRowView(row, null, CycleDict.WIN_FAILED));
                continue;
            }
            Integer remaining = row.getDueAt() == null ? null : cal.remainingDays(at, row.getDueAt());
            String state;
            if (!open) {
                // 压着的那一段：手里就算有已到点的也不动，等下一轮开口再说
                state = CycleDict.WIN_PAUSED;
            } else if (row.getDueAt() == null || !reachesDay(row, at, cal)) {
                state = CycleDict.WIN_NOT_DUE;
            } else if (at.after(row.getDueAt())) {
                state = CycleDict.WIN_OVERDUE;
            } else {
                state = CycleDict.WIN_ACTIONABLE;
            }
            views.add(new CycleRowView(row, remaining, state));
        }
        return views;
    }

    @Override
    public CycleRoundOutcome runRound(Date at) {
        CycleCalendar cal = calendar();
        // 三个数同源于这一回点算：行档只取这一遍，办妥/催不动/还欠看都从它身上点出来
        List<TIchCycleTask> rows = this.ichCycleTaskMapper.selectList(
                new QueryWrapper<TIchCycleTask>().eq("del_flag", 0));

        CycleRoundOutcome outcome = new CycleRoundOutcome();
        outcome.setRoundAt(at);

        // 压着的那一段：手里就算有已到点的也不动，等下一轮开口再说
        if (!cal.isOpen(at)) {
            outcome.setPaused(true);
            outcome.setPendingCount(countWaiting(rows));
            outcome.setMessage(CycleDict.WIN_PAUSED + "：候办 " + outcome.getPendingCount()
                    + " 行留待下一轮开口");
            return outcome;
        }

        // 名录项目底册（销号判定只认库里那一份）
        Map<String, Integer> projects = new HashMap<>();
        for (TIchProject p : this.ichProjectMapper.selectList(
                new QueryWrapper<TIchProject>().eq("del_flag", 0))) {
            if (p.getSiteNo() != null) {
                projects.put(p.getSiteNo(), p.getStatus() == null ? 0 : p.getStatus());
            }
        }

        // 挑单只问一条：该出手那天到没到。够不上日子的这一轮不碰
        List<TIchCycleTask> picked = new ArrayList<>();
        for (TIchCycleTask row : rows) {
            if (row.getStatus() != null && row.getStatus() == CycleDict.STATUS_WAIT
                    && row.getDueAt() != null && reachesDay(row, at, cal)) {
                picked.add(row);
            }
        }
        // 同一瞬间两条一起到：各归各处理，既不合成一条也不许漏掉一条
        picked.sort(Comparator.comparing(TIchCycleTask::getDueAt)
                .thenComparing(r -> r.getId() == null ? 0L : r.getId()));
        outcome.setPickedCount(picked.size());

        for (TIchCycleTask row : picked) {
            try {
                processRow(row, at, projects, outcome);
            } catch (Exception e) {
                // 本行受阻不牵连其余：记下一笔，剩下的仍要一行一行走到底
                CycleRoundOutcome.RowResult r = new CycleRoundOutcome.RowResult();
                r.setTaskId(row.getId());
                r.setItemNo(row.getItemNo());
                r.setOutcome(CycleDict.ROW_ERROR);
                r.setDetail("本行受阻：" + e.getMessage());
                outcome.addRow(r);
            }
        }

        // 一轮跑完，三个数从这一回点算里出：还欠看的顺着行档一条一条点齐
        outcome.setPendingCount(countWaiting(rows));
        if (picked.isEmpty()) {
            // 一个都没挑着：正常收尾不是故障，别为这句话半夜打电话
            outcome.setMessage(CycleDict.MSG_NONE_DUE);
        } else {
            outcome.setMessage("本轮到期 " + picked.size() + " 行：办妥 " + outcome.getDoneCount()
                    + "、催不动 " + outcome.getFailedCount() + "、还欠看 " + outcome.getPendingCount());
        }
        return outcome;
    }

    /** 仍候办的行数（还欠看的） */
    private static int countWaiting(List<TIchCycleTask> rows) {
        int n = 0;
        for (TIchCycleTask r : rows) {
            if (r.getStatus() == null || r.getStatus() == CycleDict.STATUS_WAIT) {
                n++;
            }
        }
        return n;
    }

    /**
     * 一行这一轮怎么走：越过该出手那日的先撤回；开不成单的两种就地改判催不动；
     * 其余两头递，两路都送到才算办妥。
     */
    private void processRow(TIchCycleTask row, Date at, Map<String, Integer> projects,
                            CycleRoundOutcome outcome) {
        if (at.after(row.getDueAt())) {
            revokeOverdue(row, at, outcome);
            return;
        }
        // 开不成单的两种：项目早已从名录上销号，或联系人那一栏空着没人补
        if (isDeregistered(row, projects)) {
            failRow(row, CycleDict.FAIL_PROJECT_DEREGISTERED, outcome);
            return;
        }
        if (isBlank(row.getContactPhone())) {
            failRow(row, CycleDict.FAIL_NO_CONTACT, outcome);
            return;
        }
        deliver(row, at, outcome);
    }

    /** 项目早已从名录上销号（只对名录项目行；底册查无此号不妄断） */
    private static boolean isDeregistered(TIchCycleTask row, Map<String, Integer> projects) {
        return row.getTargetType() != null && row.getTargetType() == CycleDict.TARGET_PROJECT
                && row.getTargetNo() != null
                && projects.getOrDefault(row.getTargetNo(), 0) == CycleDict.PROJECT_DEREGISTERED;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 越过该出手那日还没送出去：把话撤回、这一行改判催不动。
     * 撤回另记一笔（事后查得到它的存在），但盖不住两处旧字——
     * 头回定下的那一日 due_at 与上一回真办成的那一刻 done_at，补丁里一概不碰。
     */
    private void revokeOverdue(TIchCycleTask row, Date at, CycleRoundOutcome outcome) {
        Integer exists = this.ichCycleRevokeMapper.selectCount(new QueryWrapper<TIchCycleRevoke>()
                .eq("task_id", row.getId()).eq("del_flag", 0));
        if (exists == null || exists == 0) {
            TIchCycleRevoke revoke = new TIchCycleRevoke();
            revoke.setTaskId(row.getId());
            revoke.setItemNo(row.getItemNo());
            revoke.setRevokeAt(at);
            revoke.setReason(CycleDict.FAIL_OVERDUE_REVOKED);
            revoke.setDueAtSnapshot(row.getDueAt());
            revoke.setLastDoneAtSnapshot(row.getDoneAt());
            revoke.setDelFlag(0);
            this.ichCycleRevokeMapper.insert(revoke);
        }
        failRow(row, CycleDict.FAIL_OVERDUE_REVOKED, outcome);
    }

    /** 就地改判催不动：缘由顺着写进同一行，事后按缘由挑得出来 */
    private void failRow(TIchCycleTask row, String reason, CycleRoundOutcome outcome) {
        TIchCycleTask patch = new TIchCycleTask();
        patch.setStatus(CycleDict.STATUS_FAIL);
        patch.setFailReason(reason);
        if (!applyWaiting(row, patch, outcome)) {
            return;
        }
        CycleRoundOutcome.RowResult r = new CycleRoundOutcome.RowResult();
        r.setTaskId(row.getId());
        r.setItemNo(row.getItemNo());
        r.setOutcome(CycleDict.ROW_FAILED);
        r.setDetail(reason);
        outcome.addRow(r);
    }

    /**
     * 催办两头递：县文旅局收站内那一条，传承人本人收手机短信息那一条。
     * 哪一路送到就单独记哪一路；两路都送到，去向翻办妥、办妥那一刻记上，
     * 两个痕迹同一次落库——只落其一都不算这一行完事。
     */
    private void deliver(TIchCycleTask row, Date at, CycleRoundOutcome outcome) {
        Date siteAt = row.getSiteSentAt();
        Date smsAt = row.getSmsSentAt();
        String text = isBlank(row.getTplText()) ? row.getContent() : row.getTplText();

        if (siteAt == null) {
            sendSiteNotice(row, text);
            siteAt = at;
        }
        if (smsAt == null && this.ichSmsSender.send(row.getContactPhone(), text)) {
            smsAt = at;
        }

        TIchCycleTask patch = new TIchCycleTask();
        patch.setSiteSentAt(siteAt);
        patch.setSmsSentAt(smsAt);
        boolean done = siteAt != null && smsAt != null;
        if (done) {
            // 手机那一路不算替站内那一路交差：两路齐才许翻办妥
            patch.setStatus(CycleDict.STATUS_DONE);
            patch.setDoneAt(at);
        }
        if (!applyWaiting(row, patch, outcome)) {
            return;
        }
        CycleRoundOutcome.RowResult r = new CycleRoundOutcome.RowResult();
        r.setTaskId(row.getId());
        r.setItemNo(row.getItemNo());
        r.setSiteSentAt(siteAt);
        r.setSmsSentAt(smsAt);
        if (done) {
            r.setDoneAt(at);
            r.setOutcome(CycleDict.ROW_DONE);
            r.setDetail(CycleDict.bizName(row.getBizType()) + "两路俱送到");
        } else {
            // 站内那一路先走，能走到这里它必已送到；手机那一路未送到，留待下一轮补送
            r.setOutcome(CycleDict.ROW_PARTIAL);
            r.setDetail("站内一路已送到，手机那一路未送到，行仍候办留待下一轮补送");
        }
        outcome.addRow(r);
    }

    /** 站内那一条：县文旅局收（公告栏里的一条） */
    private void sendSiteNotice(TIchCycleTask row, String text) {
        TSysNotice notice = new TSysNotice();
        notice.setTitle("【" + CycleDict.bizName(row.getBizType()) + "】"
                + (row.getTargetName() == null ? row.getItemNo() : row.getTargetName()));
        notice.setContent(text + "（单号：" + row.getItemNo() + "）");
        notice.setType(NOTICE_TYPE);
        this.tSysNoticeMapper.insert(notice);
    }

    /**
     * 只许翻动候办行的落库：补丁与行档同一次更新， affected==1 才把行档同步成新样子；
     * 落笔瞬间该行已被别人推动（屏上时刻与库里有出入），按库里那一份走，本轮不再动它，
     * 并在结局里留一笔，事后查得到这一行是怎么收口的。
     */
    private boolean applyWaiting(TIchCycleTask row, TIchCycleTask patch, CycleRoundOutcome outcome) {
        UpdateWrapper<TIchCycleTask> uw = new UpdateWrapper<>();
        uw.eq("id", row.getId()).eq("status", CycleDict.STATUS_WAIT);
        if (this.ichCycleTaskMapper.update(patch, uw) == 0) {
            TIchCycleTask fresh = this.ichCycleTaskMapper.selectById(row.getId());
            if (fresh != null) {
                row.setStatus(fresh.getStatus());
                row.setDoneAt(fresh.getDoneAt());
                row.setSiteSentAt(fresh.getSiteSentAt());
                row.setSmsSentAt(fresh.getSmsSentAt());
                row.setFailReason(fresh.getFailReason());
            }
            CycleRoundOutcome.RowResult r = new CycleRoundOutcome.RowResult();
            r.setTaskId(row.getId());
            r.setItemNo(row.getItemNo());
            r.setOutcome(CycleDict.ROW_ERROR);
            r.setDetail("落笔时该行已被推动，按库里那一份走，本轮不再动它");
            outcome.addRow(r);
            return false;
        }
        if (patch.getStatus() != null) {
            row.setStatus(patch.getStatus());
        }
        if (patch.getDoneAt() != null) {
            row.setDoneAt(patch.getDoneAt());
        }
        if (patch.getSiteSentAt() != null) {
            row.setSiteSentAt(patch.getSiteSentAt());
        }
        if (patch.getSmsSentAt() != null) {
            row.setSmsSentAt(patch.getSmsSentAt());
        }
        if (patch.getFailReason() != null) {
            row.setFailReason(patch.getFailReason());
        }
        return true;
    }
}
