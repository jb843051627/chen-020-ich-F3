package com.fc.v2.model.custom.cycle;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 一轮周期任务的点算结局（三个数同源于这一回点算，分成两次去取就视为错）。
 *
 * <p>一轮跑完，屏上落的三个数——还欠看的、这一轮办妥的、催不动的——
 * 全部由服务层当轮从档里一条一条点出来点齐，页面只照这份结局摆，
 * 不许从屏上另数一遍。一个都没挑着时寄语"本轮无到期"，是正常收尾不是故障。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleRoundOutcome {

    /** 本轮下手的时刻 */
    private Date roundAt;

    /** 是否正压着的那一段：是则本轮一行不动，候办留待下一轮开口 */
    private boolean paused;

    /** 本轮捞出的行数（够上日子的；够不上日子的这一轮不碰） */
    private int pickedCount;

    /** 还欠看的（本轮跑完仍候办的行数） */
    private int pendingCount;

    /** 这一轮办妥的行数 */
    private int doneCount;

    /** 本轮改判催不动的行数 */
    private int failedCount;

    /** 本轮寄语（"本轮无到期"为正常收尾，别为这句话半夜打电话） */
    private String message;

    /** 逐行结局（本轮捞出的每一行各归各，既不合并也不漏） */
    private List<RowResult> rows = new ArrayList<>();

    /** 一行这一轮的结局 */
    public static final class RowResult {
        /** 到期单id */
        private Long taskId;

        /** 到期单号 */
        private String itemNo;

        /** 结局：办妥 / 催不动 / 受阻留待下轮 */
        private String outcome;

        /** 催不动卡在哪一处缘由（或受阻的异常一句话） */
        private String detail;

        /** 站内那一路送到时刻 */
        private Date siteSentAt;

        /** 手机那一路送到时刻 */
        private Date smsSentAt;

        /** 办妥的那一刻 */
        private Date doneAt;

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getItemNo() {
            return itemNo;
        }

        public void setItemNo(String itemNo) {
            this.itemNo = itemNo;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public Date getSiteSentAt() {
            return siteSentAt;
        }

        public void setSiteSentAt(Date siteSentAt) {
            this.siteSentAt = siteSentAt;
        }

        public Date getSmsSentAt() {
            return smsSentAt;
        }

        public void setSmsSentAt(Date smsSentAt) {
            this.smsSentAt = smsSentAt;
        }

        public Date getDoneAt() {
            return doneAt;
        }

        public void setDoneAt(Date doneAt) {
            this.doneAt = doneAt;
        }
    }

    public Date getRoundAt() {
        return roundAt;
    }

    public void setRoundAt(Date roundAt) {
        this.roundAt = roundAt;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getPickedCount() {
        return pickedCount;
    }

    public void setPickedCount(int pickedCount) {
        this.pickedCount = pickedCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(int doneCount) {
        this.doneCount = doneCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<RowResult> getRows() {
        return rows;
    }

    public void setRows(List<RowResult> rows) {
        this.rows = rows;
    }

    /** 添一行结局并顺手按结局归类计数（办妥/催不动各归各的数） */
    public void addRow(RowResult r) {
        this.rows.add(r);
        if (CycleDict.ROW_DONE.equals(r.getOutcome())) {
            this.doneCount++;
        } else if (CycleDict.ROW_FAILED.equals(r.getOutcome())) {
            this.failedCount++;
        }
    }
}
