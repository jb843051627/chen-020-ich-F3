package com.fc.v2.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import com.fc.v2.model.custom.cycle.CycleDict;
import com.fc.v2.model.custom.cycle.CycleRoundOutcome;
import com.fc.v2.model.custom.cycle.CycleRowView;
import com.fc.v2.service.impl.TIchCycleTaskServiceImpl;

/**
 * 履约考核到期单周期任务 TIchCycleTaskServiceImpl 的规矩测试
 * （Mapper 与短信出口全打桩，不起 Spring、不碰库）。
 *
 * <p>2026-10-09 是周五；10-12 周一、10-13 周二、10-14 周三、10-20 下周二。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchCycleTaskServiceImplTest {

    @Mock
    private TIchCycleTaskMapper taskMapper;

    @Mock
    private TIchCycleRevokeMapper revokeMapper;

    @Mock
    private TIchCalendarClosedMapper calendarMapper;

    @Mock
    private TIchProjectMapper projectMapper;

    @Mock
    private TSysNoticeMapper noticeMapper;

    @Mock
    private IchSmsSender smsSender;

    @InjectMocks
    private TIchCycleTaskServiceImpl service;

    /** 行档（库里那一份的镜像） */
    private final List<TIchCycleTask> rows = new ArrayList<>();

    /** 撤回留痕 */
    private final List<TIchCycleRevoke> revokes = new ArrayList<>();

    /** 歇业日历 */
    private final List<TIchCalendarClosed> closedRows = new ArrayList<>();

    /** 名录项目底册 */
    private final List<TIchProject> projects = new ArrayList<>();

    /** 站内发出去的那一条一条 */
    private final List<TSysNotice> notices = new ArrayList<>();

    /** 手机发出去的那一条一条：[手机, 文本] */
    private final List<String[]> smsLog = new ArrayList<>();

    /** 每一回落库的补丁（按行归拢，验"两个痕迹同一次落"） */
    private final Map<Long, List<TIchCycleTask>> patches = new HashMap<>();

    /** 落笔瞬间已被别人推动的行（模拟并发：更新落空，回查给新样子） */
    private final Set<Long> movedIds = new HashSet<>();

    private final AtomicInteger idSeq = new AtomicInteger(1);

    /** 手机网关此刻通不通 */
    private boolean smsOk = true;

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private TIchCycleTask addRow(long id, String itemNo, int biz, int targetType, String targetNo,
                                 String due, double advance, String phone) {
        TIchCycleTask r = new TIchCycleTask();
        r.setId(id);
        r.setItemNo(itemNo);
        r.setBizType(biz);
        r.setTargetType(targetType);
        r.setTargetNo(targetNo);
        r.setTargetName("称呼·" + itemNo);
        r.setDueAt(due == null ? null : d(due));
        r.setAmount(BigDecimal.valueOf(advance));
        r.setTplText("事由文本·" + itemNo);
        r.setContactPhone(phone);
        r.setContent("记要·" + itemNo);
        r.setStatus(CycleDict.STATUS_WAIT);
        r.setDelFlag(0);
        rows.add(r);
        return r;
    }

    private void addProject(String siteNo, int status) {
        TIchProject p = new TIchProject();
        p.setId((long) idSeq.getAndIncrement());
        p.setSiteNo(siteNo);
        p.setSiteName("项目·" + siteNo);
        p.setStatus(status);
        p.setDelFlag(0);
        projects.add(p);
    }

    private void addClosed(String day, int type) {
        TIchCalendarClosed c = new TIchCalendarClosed();
        c.setId((long) idSeq.getAndIncrement());
        c.setClosedDate(d(day + " 00:00:00"));
        c.setClosedType(type);
        c.setDelFlag(0);
        closedRows.add(c);
    }

    @SuppressWarnings("rawtypes")
    private static Long wrapperId(Wrapper<?> w) {
        // MP 的条件值是惰性渲染的：先触发 SQL 段渲染，参数才落进 paramNameValuePairs
        ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper) w).getCustomSqlSegment();
        for (Object v : ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper) w)
                .getParamNameValuePairs().values()) {
            if (v instanceof Long) {
                return (Long) v;
            }
        }
        return null;
    }

    private TIchCycleTask findRow(long id) {
        for (TIchCycleTask r : rows) {
            if (r.getId() != null && r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    @BeforeEach
    public void setUp() {
        rows.clear();
        revokes.clear();
        closedRows.clear();
        projects.clear();
        notices.clear();
        smsLog.clear();
        patches.clear();
        movedIds.clear();
        smsOk = true;

        // 行档查询：空条件给全部（老方法的形状），带条件即 del_flag=0（新捞法的形状）
        when(taskMapper.selectList(any())).thenAnswer(inv -> {
            Wrapper<?> w = inv.getArgument(0);
            @SuppressWarnings("rawtypes")
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper aw =
                    (com.baomidou.mybatisplus.core.conditions.AbstractWrapper) w;
            aw.getCustomSqlSegment(); // 惰性参数先渲染出来
            boolean noFilter = aw.getParamNameValuePairs().isEmpty();
            List<TIchCycleTask> out = new ArrayList<>();
            for (TIchCycleTask r : rows) {
                if (noFilter || r.getDelFlag() == null || r.getDelFlag() == 0) {
                    out.add(r);
                }
            }
            return out;
        });
        when(taskMapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (movedIds.contains(id)) {
                // 落笔瞬间已被别人推动：库里那一份已是办妥
                TIchCycleTask fresh = new TIchCycleTask();
                fresh.setId(id);
                fresh.setStatus(CycleDict.STATUS_DONE);
                fresh.setDoneAt(d("2026-10-09 10:00:00"));
                return fresh;
            }
            return findRow(id);
        });
        when(taskMapper.update(any(), any())).thenAnswer(inv -> {
            TIchCycleTask patch = inv.getArgument(0);
            Long id = wrapperId(inv.getArgument(1));
            if (id == null || movedIds.contains(id)) {
                return 0;
            }
            TIchCycleTask row = findRow(id);
            if (row == null || row.getStatus() == null || row.getStatus() != CycleDict.STATUS_WAIT) {
                return 0; // 只许翻动候办行
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
            patches.computeIfAbsent(id, k -> new ArrayList<>()).add(patch);
            return 1;
        });

        when(calendarMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(closedRows));
        when(projectMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(projects));

        when(revokeMapper.selectCount(any())).thenAnswer(inv -> {
            Long taskId = wrapperId(inv.getArgument(0));
            int n = 0;
            for (TIchCycleRevoke r : revokes) {
                if (r.getTaskId() != null && r.getTaskId().equals(taskId)
                        && (r.getDelFlag() == null || r.getDelFlag() == 0)) {
                    n++;
                }
            }
            return n;
        });
        when(revokeMapper.insert(any())).thenAnswer(inv -> {
            TIchCycleRevoke r = inv.getArgument(0);
            r.setId((long) idSeq.getAndIncrement());
            revokes.add(r);
            return 1;
        });

        when(noticeMapper.insert(any())).thenAnswer(inv -> {
            TSysNotice n = inv.getArgument(0);
            if (n.getContent() != null && n.getContent().contains("炸")) {
                throw new RuntimeException("站内网关受阻");
            }
            notices.add(n);
            return 1;
        });
        when(smsSender.send(any(), any())).thenAnswer(inv -> {
            smsLog.add(new String[]{inv.getArgument(0), inv.getArgument(1)});
            return smsOk;
        });
    }

    // ------------------------------------------------------------------
    // 老方法：参数与返回一律不碰，原样维持
    // ------------------------------------------------------------------

    @Test
    public void 老方法原样不动() {
        TIchCycleTask past = addRow(1L, "DQ-1", 0, 0, "XM00", "2026-10-08 17:00:00", 2, "139");
        addRow(2L, "DQ-2", 0, 0, "XM00", "2026-10-20 17:00:00", 2, "139");

        assertSame(past, service.selectTIchCycleTaskById(1L));

        // 老 listDue：墙钟串比较，due_at 早于此刻的都给（不看去向、不看删除标记之外的事）
        List<TIchCycleTask> due = service.listDue(d("2026-10-09 10:00:00"));
        assertEquals(1, due.size());
        assertSame(past, due.get(0));

        // 老 runOnce：非空时按 amount>=0 点成功条数
        assertEquals(1, service.runOnce(d("2026-10-09 10:00:00")));
    }

    // ------------------------------------------------------------------
    // 挑单与窗口
    // ------------------------------------------------------------------

    @Test
    public void 挑单只问该出手那天到没到() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139****0001");
        TIchCycleTask b = addRow(2L, "DQ-B", 1, 1, "CR01", "2026-10-20 17:00:00", 2, "139****0002");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        // 够上日子的 A 办妥；够不上日子的 B 这一轮不碰
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
        assertEquals(CycleDict.STATUS_WAIT, b.getStatus());
        assertNull(b.getDoneAt());
        assertEquals(1, notices.size());
        assertEquals(1, smsLog.size());
        assertEquals(1, o.getDoneCount());
        assertEquals(1, o.getPendingCount());
    }

    @Test
    public void 可商量的只剩提前几日这一格() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");

        // 最早开口日是 10-09（周三到期往前数 3 个工作日）：10-08 还不够日子
        CycleRoundOutcome early = service.runRound(d("2026-10-08 10:00:00"));
        assertEquals(CycleDict.MSG_NONE_DUE, early.getMessage());
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());
        assertTrue(notices.isEmpty());

        // 到了 10-09 才许开口
        CycleRoundOutcome onDay = service.runRound(d("2026-10-09 10:00:00"));
        assertEquals(1, onDay.getDoneCount());
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
    }

    @Test
    public void 压着的那一段手里有已到点的也不动() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");

        // 夜里那一段：已到点的也不动，等下一轮开口
        CycleRoundOutcome night = service.runRound(d("2026-10-09 20:00:00"));
        assertTrue(night.isPaused());
        assertEquals(0, night.getPickedCount());
        assertEquals(1, night.getPendingCount());
        assertEquals(0, night.getDoneCount());
        assertTrue(night.getMessage().contains("压着"));
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());
        verify(taskMapper, never()).update(any(), any());
        verify(noticeMapper, never()).insert(any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    public void 省里统一停办公的那几日也压着() {
        addClosed("2026-10-12", CycleDict.CLOSED_PROVINCE);
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");

        CycleRoundOutcome closed = service.runRound(d("2026-10-12 10:00:00"));
        assertTrue(closed.isPaused());
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());

        // 翌日开口，留待的这一行照常办妥
        CycleRoundOutcome next = service.runRound(d("2026-10-13 10:00:00"));
        assertFalse(next.isPaused());
        assertEquals(1, next.getDoneCount());
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
    }

    @Test
    public void 本轮办妥过的下一轮不许再当候办捞出来() {
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        assertEquals(1, service.runRound(d("2026-10-09 10:00:00")).getDoneCount());

        CycleRoundOutcome again = service.runRound(d("2026-10-13 10:00:00"));
        assertEquals(0, again.getPickedCount());
        assertEquals(CycleDict.MSG_NONE_DUE, again.getMessage());
        assertEquals(1, notices.size());
        assertEquals(1, smsLog.size());
    }

    @Test
    public void 同一瞬间两条一起到各归各处理() {
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139****0001");
        addRow(2L, "DQ-B", 1, 1, "CR01", "2026-10-14 17:00:00", 3, "139****0002");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        // 既不合成一条也不许漏掉一条
        assertEquals(2, o.getPickedCount());
        assertEquals(2, o.getDoneCount());
        assertEquals(2, notices.size());
        assertEquals(2, smsLog.size());
        assertEquals("139****0001", smsLog.get(0)[0]);
        assertEquals("139****0002", smsLog.get(1)[0]);
    }

    // ------------------------------------------------------------------
    // 办妥与两路递送
    // ------------------------------------------------------------------

    @Test
    public void 办妥两个痕迹同一次落库() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        Date at = d("2026-10-09 10:00:00");
        service.runRound(at);

        // 去向翻办妥 + 办妥那一刻：同一回 UPDATE 里两个痕迹齐全，只落其一不算完事
        List<TIchCycleTask> ps = patches.get(1L);
        assertEquals(1, ps.size());
        assertEquals(Integer.valueOf(CycleDict.STATUS_DONE), ps.get(0).getStatus());
        assertEquals(at, ps.get(0).getDoneAt());
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
        assertEquals(at, a.getDoneAt());
    }

    @Test
    public void 催办两头递哪一路送到就单独记哪一路() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139****0001");
        Date at = d("2026-10-09 10:00:00");
        service.runRound(at);

        // 县文旅局收站内那一条，传承人本人收手机那一条，各记各的时刻
        assertEquals(at, a.getSiteSentAt());
        assertEquals(at, a.getSmsSentAt());
        assertEquals(1, notices.size());
        assertTrue(notices.get(0).getContent().contains("事由文本·DQ-A"));
        assertTrue(notices.get(0).getTitle().contains("履约催办"));
        assertEquals(1, smsLog.size());
        assertEquals("139****0001", smsLog.get(0)[0]);
        assertEquals("事由文本·DQ-A", smsLog.get(0)[1]);
    }

    @Test
    public void 手机那一路不算替站内那一路交差() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        Date at = d("2026-10-09 10:00:00");

        // 站内送到了、手机网关不通：只记站内一路，行仍候办
        smsOk = false;
        CycleRoundOutcome o1 = service.runRound(at);
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());
        assertEquals(at, a.getSiteSentAt());
        assertNull(a.getSmsSentAt());
        assertNull(a.getDoneAt());
        assertEquals(0, o1.getDoneCount());
        assertEquals(1, o1.getPendingCount());
        assertEquals(CycleDict.ROW_PARTIAL, o1.getRows().get(0).getOutcome());

        // 下一轮手机通了补送那一头：站内不重复递，两路齐才翻办妥
        smsOk = true;
        CycleRoundOutcome o2 = service.runRound(d("2026-10-09 11:00:00"));
        assertEquals(1, o2.getDoneCount());
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
        assertEquals(1, notices.size());
        assertEquals(2, smsLog.size());
    }

    @Test
    public void 站内受阻手机再顺也不算办妥() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        a.setTplText("炸一炸站内网关");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        // 站内那一条没递出去，手机那一路再顺也不替它交差
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());
        assertNull(a.getSiteSentAt());
        assertNull(a.getDoneAt());
        assertEquals(0, o.getDoneCount());
        assertEquals(CycleDict.ROW_ERROR, o.getRows().get(0).getOutcome());
    }

    // ------------------------------------------------------------------
    // 开不成单的两种行子
    // ------------------------------------------------------------------

    @Test
    public void 项目早已销号的行子就地改判催不动() {
        addProject("XM00", 0);
        addProject("XM01", CycleDict.PROJECT_DEREGISTERED);
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM01", "2026-10-14 17:00:00", 3, "139");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        assertEquals(CycleDict.STATUS_FAIL, a.getStatus());
        assertEquals(CycleDict.FAIL_PROJECT_DEREGISTERED, a.getFailReason());
        assertEquals(1, o.getFailedCount());
        assertTrue(notices.isEmpty());
        assertTrue(smsLog.isEmpty());

        // 这类行子不藏：页面上照旧列着，只是不再对它动手
        List<CycleRowView> board = service.listBoard(d("2026-10-09 10:00:00"));
        assertEquals(1, board.size());
        assertEquals(CycleDict.WIN_FAILED, board.get(0).getWindowState());
    }

    @Test
    public void 联系人空着的行子就地改判催不动() {
        addRow(1L, "DQ-A", 1, 1, "CR01", "2026-10-14 17:00:00", 3, null);
        addRow(2L, "DQ-B", 1, 1, "CR02", "2026-10-14 17:00:00", 3, "   ");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        assertEquals(2, o.getFailedCount());
        assertEquals(CycleDict.FAIL_NO_CONTACT, findRow(1L).getFailReason());
        assertEquals(CycleDict.FAIL_NO_CONTACT, findRow(2L).getFailReason());
        assertTrue(notices.isEmpty());
        assertTrue(smsLog.isEmpty());
    }

    // ------------------------------------------------------------------
    // 越过该出手那日：撤回另记一笔，不盖旧字
    // ------------------------------------------------------------------

    @Test
    public void 越过该出手那日没送出去的把话撤回改判催不动() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-08 17:00:00", 3, "139");
        Date firstDue = a.getDueAt();
        Date at = d("2026-10-09 10:00:00");

        CycleRoundOutcome o = service.runRound(at);

        // 这一行改判催不动，缘由顺着写进同一行
        assertEquals(CycleDict.STATUS_FAIL, a.getStatus());
        assertEquals(CycleDict.FAIL_OVERDUE_REVOKED, a.getFailReason());
        assertEquals(1, o.getFailedCount());
        assertTrue(notices.isEmpty());
        assertTrue(smsLog.isEmpty());

        // 撤回另记一笔，事后查得到它的存在
        assertEquals(1, revokes.size());
        TIchCycleRevoke revoke = revokes.get(0);
        assertEquals(Long.valueOf(1L), revoke.getTaskId());
        assertEquals("DQ-A", revoke.getItemNo());
        assertEquals(at, revoke.getRevokeAt());
        assertEquals(CycleDict.FAIL_OVERDUE_REVOKED, revoke.getReason());
        assertEquals(firstDue, revoke.getDueAtSnapshot());
        assertNull(revoke.getLastDoneAtSnapshot());

        // 盖不住两处旧字：头回定下的那一日没挪，补丁里也不许带这两格
        assertEquals(firstDue, a.getDueAt());
        List<TIchCycleTask> ps = patches.get(1L);
        assertEquals(1, ps.size());
        assertNull(ps.get(0).getDueAt());
        assertNull(ps.get(0).getDoneAt());
    }

    @Test
    public void 撤回留痕已有一笔就不再重记() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-08 17:00:00", 3, "139");
        TIchCycleRevoke old = new TIchCycleRevoke();
        old.setId(99L);
        old.setTaskId(1L);
        old.setItemNo("DQ-A");
        old.setRevokeAt(d("2026-10-09 09:00:00"));
        old.setReason(CycleDict.FAIL_OVERDUE_REVOKED);
        old.setDueAtSnapshot(a.getDueAt());
        old.setDelFlag(0);
        revokes.add(old);

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        assertEquals(1, revokes.size());
        assertEquals(CycleDict.STATUS_FAIL, a.getStatus());
        assertEquals(1, o.getFailedCount());
    }

    // ------------------------------------------------------------------
    // 一行受阻不牵连其余
    // ------------------------------------------------------------------

    @Test
    public void 同一轮里别条行子受阻也不牵连其余() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139****0001");
        a.setTplText("炸一炸站内网关");
        TIchCycleTask b = addRow(2L, "DQ-B", 1, 1, "CR01", "2026-10-14 17:00:00", 3, "139****0002");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        // A 受阻留候办，B 照样办妥，一行一行走到底
        assertEquals(CycleDict.STATUS_WAIT, a.getStatus());
        assertEquals(CycleDict.STATUS_DONE, b.getStatus());
        assertEquals(1, o.getDoneCount());
        assertEquals(1, o.getPendingCount());
        assertEquals(0, o.getFailedCount());
        assertEquals(2, o.getRows().size());
        assertEquals(CycleDict.ROW_ERROR, o.getRows().get(0).getOutcome());
        assertEquals(CycleDict.ROW_DONE, o.getRows().get(1).getOutcome());
    }

    // ------------------------------------------------------------------
    // 三个数同源一次点算
    // ------------------------------------------------------------------

    @Test
    public void 三个数同源于一次点算() {
        addProject("XM01", CycleDict.PROJECT_DEREGISTERED);
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");   // 办妥
        addRow(2L, "DQ-B", 0, 0, "XM01", "2026-10-14 17:00:00", 3, "139");   // 销号→催不动
        addRow(3L, "DQ-C", 1, 1, "CR01", "2026-10-20 17:00:00", 0, "139");   // 够不上日子→还欠看
        addRow(4L, "DQ-D", 0, 0, "XM00", "2026-10-08 17:00:00", 3, "139");   // 越过→撤回催不动

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        assertEquals(3, o.getPickedCount());
        assertEquals(1, o.getDoneCount());
        assertEquals(2, o.getFailedCount());
        assertEquals(1, o.getPendingCount());
        assertEquals("本轮到期 3 行：办妥 1、催不动 2、还欠看 1", o.getMessage());

        // 三个数与行档一条一条点出来的对齐；行档只取过一遍，不分两回取
        int waiting = 0;
        for (TIchCycleTask r : rows) {
            if (r.getStatus() == CycleDict.STATUS_WAIT) {
                waiting++;
            }
        }
        assertEquals(waiting, o.getPendingCount());
        verify(taskMapper, times(1)).selectList(any());
    }

    @Test
    public void 本轮无到期是正常收尾不是故障() {
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-20 17:00:00", 0, "139");

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        assertFalse(o.isPaused());
        assertEquals(0, o.getPickedCount());
        assertEquals(0, o.getDoneCount());
        assertEquals(0, o.getFailedCount());
        assertEquals(1, o.getPendingCount());
        assertEquals(CycleDict.MSG_NONE_DUE, o.getMessage());
        assertTrue(notices.isEmpty());
        assertTrue(smsLog.isEmpty());
    }

    // ------------------------------------------------------------------
    // 查单与回写同用一把尺；查单只读不动库
    // ------------------------------------------------------------------

    @Test
    public void 查单与回写共用同一份算法() {
        addClosed("2026-10-12", CycleDict.CLOSED_PROVINCE);
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");

        // 查单：周五、周二、周三 3 日在手里（周一省里停办公、周末都不算）
        List<CycleRowView> board = service.listBoard(d("2026-10-09 10:00:00"));
        assertEquals(1, board.size());
        assertEquals(Integer.valueOf(3), board.get(0).getRemainingDays());
        assertEquals(CycleDict.WIN_ACTIONABLE, board.get(0).getWindowState());

        // 回写：同一本歇业日历，周一那一日压着不动
        CycleRoundOutcome closed = service.runRound(d("2026-10-12 10:00:00"));
        assertTrue(closed.isPaused());

        // 查单只读：不动库、不递话
        verify(taskMapper, never()).update(any(), any());
        verify(noticeMapper, never()).insert(any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    public void 查单窗口状态逐行分明() {
        addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");  // 可开口
        addRow(2L, "DQ-B", 0, 0, "XM00", "2026-10-20 17:00:00", 0, "139");  // 未到开口日
        addRow(3L, "DQ-C", 0, 0, "XM00", "2026-10-08 17:00:00", 3, "139");  // 越过待撤回
        TIchCycleTask done = addRow(4L, "DQ-D", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        done.setStatus(CycleDict.STATUS_DONE);
        done.setDoneAt(d("2026-10-09 09:30:00"));

        // 查单按该出手那日先后排定：DQ-C(10-08)、DQ-A(10-14)、DQ-D(10-14)、DQ-B(10-20)
        List<CycleRowView> board = service.listBoard(d("2026-10-09 10:00:00"));
        assertEquals(4, board.size());
        assertEquals(CycleDict.WIN_OVERDUE, board.get(0).getWindowState());
        assertEquals(Integer.valueOf(0), board.get(0).getRemainingDays());
        assertEquals(CycleDict.WIN_ACTIONABLE, board.get(1).getWindowState());
        assertEquals(Integer.valueOf(4), board.get(1).getRemainingDays());
        assertEquals(CycleDict.WIN_DONE, board.get(2).getWindowState());
        assertNull(board.get(2).getRemainingDays());
        assertEquals(CycleDict.WIN_NOT_DUE, board.get(3).getWindowState());
        assertEquals(Integer.valueOf(8), board.get(3).getRemainingDays());
    }

    // ------------------------------------------------------------------
    // 屏上与库里有出入，按库里那一份走
    // ------------------------------------------------------------------

    @Test
    public void 落笔时行已被推动按库里那一份走() {
        TIchCycleTask a = addRow(1L, "DQ-A", 0, 0, "XM00", "2026-10-14 17:00:00", 3, "139");
        movedIds.add(1L);

        CycleRoundOutcome o = service.runRound(d("2026-10-09 10:00:00"));

        // 更新落空：行档按库里那一份同步成办妥，本轮不算自己办妥的
        assertEquals(CycleDict.STATUS_DONE, a.getStatus());
        assertEquals(0, o.getDoneCount());
        assertEquals(0, o.getPendingCount());
        assertEquals(1, o.getRows().size());
        assertEquals(CycleDict.ROW_ERROR, o.getRows().get(0).getOutcome());
        assertTrue(o.getRows().get(0).getDetail().contains("按库里那一份"));
    }
}
