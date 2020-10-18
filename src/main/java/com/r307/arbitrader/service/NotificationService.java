package com.r307.arbitrader.service;

import com.r307.arbitrader.service.model.Spread;

import java.math.BigDecimal;

public interface NotificationService {
    void sendEmailNotification(String subject, String body);
    void sendEmailNotificationBodyForEntryTrade(Spread spread, BigDecimal exitTarget, BigDecimal longVolume,
                                                       BigDecimal longLimitPrice, BigDecimal shortVolume,
                                                       BigDecimal shortLimitPrice);
    void sendEmailNotificationBodyForExitTrade(Spread spread, BigDecimal longVolume, BigDecimal longLimitPrice,
                                               BigDecimal shortVolume, BigDecimal shortLimitPrice,
                                               BigDecimal entryBalance, BigDecimal updatedBalance);
}
