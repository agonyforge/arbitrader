package com.r307.arbitrader.service;

import com.r307.arbitrader.service.model.EntryTradeVolume;
import com.r307.arbitrader.service.model.ExitTradeVolume;
import com.r307.arbitrader.service.model.Spread;

import java.math.BigDecimal;

/**
 * An email notification service.
 */
public interface NotificationService {
    void sendNotification(String subject, String message);
    void sendEntryTradeNotification(Spread spread, BigDecimal exitTarget, EntryTradeVolume tradeVolume,
                                    BigDecimal longLimitPrice, BigDecimal shortLimitPrice, boolean isForceEntryPosition);
    void sendExitTradeNotification(Spread spread, ExitTradeVolume tradeVolume, BigDecimal longLimitPrice,
                                   BigDecimal shortLimitPrice, BigDecimal entryBalance, BigDecimal updatedBalance, BigDecimal exitTarget,
                                   boolean isForceCloseCondition, boolean isActivePositionExpired);
}
