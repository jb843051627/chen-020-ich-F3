package com.fc.v2.model.custom.cycle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * 剩余天数的**唯一一把尺**（纯算，不碰库；歇业日由服务层从歇业日历读进注入）。
 *
 * <p>规矩全在这里，查单与回写共用同一份算法，各算各的年末两张表必然对不齐：
 * <ul>
 *   <li>工作日：周一到周五，且不在歇业日历（法定节假日、省里统一停办公）里。</li>
 *   <li>开口时段：工作日 09:00 起 17:00 止；之外即"夜里那一段"，整段不算在手里的数。</li>
 *   <li>剩余天数：从今日数到该出手那日，只点工作日；今日还在开口时段才算在手里，
 *       今日已越过该出手那日的止点则一日不剩。</li>
 *   <li>最早开口日：从该出手那日按工作日倒推"可提前几日"，该出手那一日本身不许挪动。</li>
 *   <li>压着的那一段：此刻不开口（夜里、周末、法定节假日、省里统一停办公），
 *       手里就算有已到点的也不动，等下一轮开口再说。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class CycleCalendar {

    /** 开口时段起点（含）：09:00 */
    public static final LocalTime WORK_START = LocalTime.of(9, 0);

    /** 开口时段止点（不含）：17:00，一到即收口 */
    public static final LocalTime WORK_END = LocalTime.of(17, 0);

    /** 歇业日（法定节假日 + 省里统一停办公），整段不算在手里的数 */
    private final Set<LocalDate> closedDays;

    public CycleCalendar(Collection<LocalDate> closedDays) {
        this.closedDays = closedDays == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(closedDays));
    }

    /** Date 转墙钟日（与库里 datetime 同一本墙钟，不经 JDBC 参数来回倒） */
    public static LocalDate dayOf(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Date 转墙钟时刻 */
    public static LocalTime timeOf(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    }

    /** 这一日歇不歇业（法定节假日或省里统一停办公） */
    public boolean isClosedDay(LocalDate day) {
        return closedDays.contains(day);
    }

    /** 这一日算不算工作日：周一到周五且不歇业 */
    public boolean isWorkday(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !isClosedDay(day);
    }

    /**
     * 此刻开不开口：工作日且落在开口时段 [09:00, 17:00)。
     * 不开口的那一段就是"压着的那一段"——手里有已到点的也不动。
     */
    public boolean isOpen(Date at) {
        LocalTime t = timeOf(at);
        return isWorkday(dayOf(at)) && !t.isBefore(WORK_START) && t.isBefore(WORK_END);
    }

    /**
     * 数到该出手那日还剩几日：从今日数到该出手那日，只点工作日。
     * 今日还在开口时段才算在手里（夜里那一段不算）；该出手那日是工作日才算一日；
     * 已越过该出手那日的止点，一日不剩，返回 0。
     */
    public int remainingDays(Date at, Date dueAt) {
        if (at == null || dueAt == null) {
            return 0;
        }
        LocalDate today = dayOf(at);
        LocalDate dueDay = dayOf(dueAt);
        if (dueDay.isBefore(today)) {
            return 0;
        }
        int n = 0;
        for (LocalDate d = today; !d.isAfter(dueDay); d = d.plusDays(1)) {
            if (!isWorkday(d)) {
                continue;
            }
            // 今日：夜里那一段不算在手里；已越过该出手那日的止点也不算
            if (d.equals(today) && (!isOpen(at) || at.after(dueAt))) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * 最早开口日：从该出手那日按工作日倒推"可提前几日"。
     * 该出手那一日不许挪动，可商量的只剩提前几日这一格。
     */
    public LocalDate earliestOpenDay(Date dueAt, int advanceDays) {
        LocalDate d = dayOf(dueAt);
        int left = Math.max(0, advanceDays);
        while (left > 0) {
            d = d.minusDays(1);
            if (isWorkday(d)) {
                left--;
            }
        }
        return d;
    }

    /** 一日开工那一刻（该日 00:00 起即够上日子，开口与否另看开口时段） */
    public Date startOfDay(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
