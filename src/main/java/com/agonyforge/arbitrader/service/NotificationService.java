package com.agonyforge.arbitrader.service;

import com.agonyforge.arbitrader.service.model.Spread;
import com.agonyforge.arbitrader.service.model.EntryTradeVolume;
import com.agonyforge.arbitrader.service.model.ExitTradeVolume;

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
