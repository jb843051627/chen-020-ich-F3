package com.fc.v2.model.custom.cycle;

import com.fc.v2.model.auto.TIchCycleTask;

/**
 * 查单一行：到期单原样 + 按唯一一把尺点出的剩余天数与窗口状态。
 *
 * <p>页面照列就行——催不动的行子不藏，照样列着；页面既添不上一单也剔除不掉一单。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleRowView {

    /** 到期单原行（库里的那一份） */
    private TIchCycleTask row;

    /** 还剩几日（候办行才有数；已办妥/催不动为 null） */
    private Integer remainingDays;

    /** 窗口状态（压着的那一段/未到开口日/可开口候办/越过应出手日待撤回/已办妥/催不动） */
    private String windowState;

    public CycleRowView() {
    }

    public CycleRowView(TIchCycleTask row, Integer remainingDays, String windowState) {
        this.row = row;
        this.remainingDays = remainingDays;
        this.windowState = windowState;
    }

    public TIchCycleTask getRow() {
        return row;
    }

    public void setRow(TIchCycleTask row) {
        this.row = row;
    }

    public Integer getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(Integer remainingDays) {
        this.remainingDays = remainingDays;
    }

    public String getWindowState() {
        return windowState;
    }

    public void setWindowState(String windowState) {
        this.windowState = windowState;
    }
}
