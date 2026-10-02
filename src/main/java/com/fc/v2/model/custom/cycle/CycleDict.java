package com.fc.v2.model.custom.cycle;

/**
 * 履约考核到期单的口径常量（纯字典，不碰库）。
 *
 * <p>去向只分三种（候办/办妥/催不动），事由两种（履约催办/年度考核），
 * 开不成单的缘由与窗口叫法都收在这里，全系统不许另起一套叫法。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class CycleDict {

    private CycleDict() {
    }

    /** 条目情形：候办 */
    public static final int STATUS_WAIT = 0;

    /** 条目情形：已办妥 */
    public static final int STATUS_DONE = 1;

    /** 条目情形：催不动 */
    public static final int STATUS_FAIL = 2;

    /** 事由：履约催办 */
    public static final int BIZ_URGE = 0;

    /** 事由：年度考核 */
    public static final int BIZ_ASSESS = 1;

    /** 拴在哪：名录项目 */
    public static final int TARGET_PROJECT = 0;

    /** 拴在哪：传承人 */
    public static final int TARGET_PERSON = 1;

    /** 歇业名目：法定节假日 */
    public static final int CLOSED_HOLIDAY = 0;

    /** 歇业名目：省里统一停办公 */
    public static final int CLOSED_PROVINCE = 1;

    /** 名录项目底册情形：已注销（销号） */
    public static final int PROJECT_DEREGISTERED = 1;

    /** 催不动缘由：项目早已从名录上销号 */
    public static final String FAIL_PROJECT_DEREGISTERED = "项目已从名录销号";

    /** 催不动缘由：联系人那一栏空着没人补 */
    public static final String FAIL_NO_CONTACT = "联系人一栏空着未补";

    /** 催不动缘由：到了该出手那日还没送出去，话已撤回 */
    public static final String FAIL_OVERDUE_REVOKED = "越过应出手日未送出，话已撤回";

    /** 窗口叫法：压着的那一段（手里有已到点的也不动，等下一轮开口） */
    public static final String WIN_PAUSED = "压着的那一段";

    /** 窗口叫法：够不上日子，这一轮不碰 */
    public static final String WIN_NOT_DUE = "未到开口日";

    /** 窗口叫法：够上日子，候办待出手 */
    public static final String WIN_ACTIONABLE = "可开口候办";

    /** 窗口叫法：已越过该出手那日，等本轮撤回 */
    public static final String WIN_OVERDUE = "越过应出手日待撤回";

    /** 窗口叫法：已办妥 */
    public static final String WIN_DONE = "已办妥";

    /** 窗口叫法：催不动 */
    public static final String WIN_FAILED = "催不动";

    /** 本轮寄语：一个都没挑着（正常收尾，不是故障） */
    public static final String MSG_NONE_DUE = "本轮无到期";

    /** 逐行结局：办妥 */
    public static final String ROW_DONE = "办妥";

    /** 逐行结局：催不动 */
    public static final String ROW_FAILED = "催不动";

    /** 逐行结局：本行受阻，留待下一轮（不牵连其余行） */
    public static final String ROW_ERROR = "受阻留待下轮";

    /** 逐行结局：只送到一路，行仍候办，留待下一轮补送另一路 */
    public static final String ROW_PARTIAL = "一路已送仍候办";

    /** 去向叫法 */
    public static String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case STATUS_WAIT:
                return "候办";
            case STATUS_DONE:
                return "已办妥";
            case STATUS_FAIL:
                return "催不动";
            default:
                return "未知";
        }
    }

    /** 事由叫法 */
    public static String bizName(Integer bizType) {
        if (bizType == null) {
            return "未知事由";
        }
        return bizType == BIZ_URGE ? "履约催办" : bizType == BIZ_ASSESS ? "年度考核" : "未知事由";
    }

    /** 拴挂对象叫法 */
    public static String targetName(Integer targetType) {
        if (targetType == null) {
            return "未知对象";
        }
        return targetType == TARGET_PROJECT ? "名录项目" : targetType == TARGET_PERSON ? "传承人" : "未知对象";
    }
}
