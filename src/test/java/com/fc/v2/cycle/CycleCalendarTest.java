package com.fc.v2.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.custom.cycle.CycleCalendar;

/**
 * 唯一一把尺 CycleCalendar 的规矩测试（纯算，不碰库）。
 *
 * <p>2026-10-09 是周五，10-10/10-11 是周末，10-12 周一、10-13 周二、10-14 周三。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleCalendarTest {

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static CycleCalendar cal(String... closedDays) {
        Set<LocalDate> set = new HashSet<>();
        for (String s : closedDays) {
            set.add(LocalDate.parse(s));
        }
        return new CycleCalendar(set);
    }

    @Test
    public void 工作日为周一到周五且不在歇业日历里() {
        CycleCalendar c = cal("2026-10-12");
        assertTrue(c.isWorkday(LocalDate.parse("2026-10-09")));   // 周五
        assertFalse(c.isWorkday(LocalDate.parse("2026-10-10")));  // 周六
        assertFalse(c.isWorkday(LocalDate.parse("2026-10-11")));  // 周日
        // 周一撞上省里统一停办公，整段不算
        assertFalse(c.isWorkday(LocalDate.parse("2026-10-12")));
        assertTrue(c.isWorkday(LocalDate.parse("2026-10-13")));
    }

    @Test
    public void 开口时段朝九晚五之外即夜里那一段() {
        CycleCalendar c = cal();
        assertFalse(c.isOpen(d("2026-10-09 08:59:59")));  // 还没开口
        assertTrue(c.isOpen(d("2026-10-09 09:00:00")));   // 开口那一刻
        assertTrue(c.isOpen(d("2026-10-09 16:59:59")));
        assertFalse(c.isOpen(d("2026-10-09 17:00:00")));  // 一到即收口
        assertFalse(c.isOpen(d("2026-10-09 20:00:00")));  // 夜里那一段
        assertFalse(c.isOpen(d("2026-10-10 10:00:00")));  // 周末不开口
    }

    @Test
    public void 法定节假日与省里停办公的那几日不开口() {
        CycleCalendar c = cal("2026-10-01", "2026-10-12");
        assertFalse(c.isOpen(d("2026-10-01 10:00:00")));  // 法定节假日
        assertFalse(c.isOpen(d("2026-10-12 10:00:00")));  // 省里统一停办公
        assertTrue(c.isOpen(d("2026-10-13 10:00:00")));   // 翌日照常开口
    }

    @Test
    public void 剩余天数只点工作日今日还在开口才算在手里() {
        CycleCalendar c = cal();
        // 周五白天 → 周五、下周一、周二、周三共 4 日（周末跳过）
        assertEquals(4, c.remainingDays(d("2026-10-09 10:00:00"), d("2026-10-14 17:00:00")));
        // 周五夜里 → 今日不算在手里，剩 3 日
        assertEquals(3, c.remainingDays(d("2026-10-09 20:00:00"), d("2026-10-14 17:00:00")));
        // 当日上午到当日止点 → 还剩 1 日
        assertEquals(1, c.remainingDays(d("2026-10-09 10:00:00"), d("2026-10-09 17:00:00")));
    }

    @Test
    public void 剩余天数跳过法定节假日与省里停办公() {
        CycleCalendar c = cal("2026-10-12");
        // 周一歇业：周五 + 周二 + 周三 = 3
        assertEquals(3, c.remainingDays(d("2026-10-09 10:00:00"), d("2026-10-14 17:00:00")));
        // 该出手那日本身歇业：那一天不算，只剩周五 1 日
        assertEquals(1, c.remainingDays(d("2026-10-09 10:00:00"), d("2026-10-12 17:00:00")));
    }

    @Test
    public void 越过该出手那日的止点一日不剩() {
        CycleCalendar c = cal();
        assertEquals(0, c.remainingDays(d("2026-10-09 17:30:00"), d("2026-10-09 17:00:00")));
        assertEquals(0, c.remainingDays(d("2026-10-10 10:00:00"), d("2026-10-09 17:00:00")));
    }

    @Test
    public void 最早开口日按工作日倒推该出手那日不许挪动() {
        CycleCalendar c = cal();
        // 周三到期提前 3 日：周二、周一、上周五 → 10-09
        assertEquals(LocalDate.parse("2026-10-09"),
                c.earliestOpenDay(d("2026-10-14 17:00:00"), 3));
        // 不提前：就是该出手那一日
        assertEquals(LocalDate.parse("2026-10-14"),
                c.earliestOpenDay(d("2026-10-14 17:00:00"), 0));
    }

    @Test
    public void 最早开口日倒推时跳过歇业日与周末() {
        CycleCalendar c = cal("2026-10-12");
        // 周三到期提前 3 日：周二(1)、周一歇业跳过、周末跳过、周五(2)、周四(3) → 10-08
        assertEquals(LocalDate.parse("2026-10-08"),
                c.earliestOpenDay(d("2026-10-14 17:00:00"), 3));
    }

    @Test
    public void 空日历照样转只是没有额外歇业日() {
        CycleCalendar c = new CycleCalendar(Collections.emptySet());
        assertTrue(c.isWorkday(LocalDate.parse("2026-10-12")));
        CycleCalendar c2 = new CycleCalendar(null);
        assertTrue(c2.isOpen(d("2026-10-09 10:00:00")));
        assertEquals(2, c2.remainingDays(d("2026-10-09 10:00:00"), d("2026-10-12 17:00:00")));
    }

    @Test
    public void 同一本歇业日历喂出同一份算法() {
        // 查单与回写共用一份算法的根：同一批歇业日、同一时刻，结论必须一致
        CycleCalendar a = cal("2026-10-12");
        CycleCalendar b = new CycleCalendar(new HashSet<>(Arrays.asList(LocalDate.parse("2026-10-12"))));
        Date at = d("2026-10-09 10:00:00");
        Date due = d("2026-10-14 17:00:00");
        assertEquals(a.remainingDays(at, due), b.remainingDays(at, due));
        assertEquals(a.earliestOpenDay(due, 3), b.earliestOpenDay(due, 3));
        assertEquals(a.isOpen(at), b.isOpen(at));
    }
}
