package com.fc.v2.common.sms;

/**
 * 手机短信出口（传承人本人那一路）。
 *
 * <p>送到就是送到，没送到就是没送到：送到与否由实现回话，
 * 手机那一路送到了也不算替站内那一路交了差，两路各记各的。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface IchSmsSender {

    /**
     * 发一条手机短信。
     *
     * @param phone 联系人手机
     * @param text  照办的事由文本
     * @return true 送到；false 未送到（本轮不记，留待下一轮补发）
     */
    boolean send(String phone, String text);
}
