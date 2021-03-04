package com.r307.arbitrader.service;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.model.EntryTradeVolume;
import com.r307.arbitrader.service.model.ExitTradeVolume;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.telegram.TelegramClient;
import org.knowm.xchange.currency.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.math.BigDecimal;

/**
 * Send notifications.
 * This class should be a central point where all outbound notifications and created and processed.
 */
@Service
@Async
public class NotificationServiceImpl implements NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);
    public static final String EMAIL_SUBJECT_NEW_ENTRY = "Arbitrader - New Entry Trade";
    public static final String EMAIL_SUBJECT_NEW_EXIT = "Arbitrader - New Exit Trade";

    private final JavaMailSender javaMailSender;
    private final NotificationConfiguration notificationConfiguration;
    private final TelegramClient telegramClient;

    @Inject
    public NotificationServiceImpl(JavaMailSender javaMailSender, NotificationConfiguration notificationConfiguration, TelegramClient telegramClient) {
        this.javaMailSender = javaMailSender;
        this.notificationConfiguration = notificationConfiguration;
        this.telegramClient = telegramClient;
    }

    /**
     * Formats and sends a notification when we trade an entry position.
     *
     * @param spread The Spread.
     * @param exitTarget The exit target.
     * @param tradeVolume The traded volumes.
     * @param longLimitPrice The long exchange limit price.
     * @param shortLimitPrice The short exchange limit price.
     * @param isForceEntryPosition Flag to indicate if this entry trade is forced or not.
     */
    @Override
    public void sendEntryTradeNotification(Spread spread, BigDecimal exitTarget, EntryTradeVolume tradeVolume, BigDecimal longLimitPrice,
                                           BigDecimal shortLimitPrice, boolean isForceEntryPosition) {

        final String longEntryString = String.format("Long entry: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getLongExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            tradeVolume.getLongVolume().toPlainString(),
            longLimitPrice.toPlainString(),
            spread.getLongTicker().getAsk().toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getLongVolume().multiply(longLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getLongVolume().multiply(spread.getLongTicker().getAsk()).toPlainString());

        final String shortEntryString = String.format("Short entry: %s %s %s @ %s (slipped %s) = %s%s (slipped from %s%s)\n",
            spread.getShortExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            tradeVolume.getShortVolume().toPlainString(),
            shortLimitPrice.toPlainString(),
            spread.getShortTicker().getBid().toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getShortVolume().multiply(shortLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getShortVolume().multiply(spread.getShortTicker().getBid()).toPlainString());

        final String message = isForceEntryPosition ? "***** FORCED ENTRY *****\n" : "***** ENTRY *****\n" +
            String.format("Entry spread: %s\n", spread.getIn().toPlainString()) +
            String.format("Exit spread target: %s\n", exitTarget.toPlainString()) +
            String.format("Market neutrality rating: %s\n", tradeVolume.getMarketNeutralityRating()) +
            String.format("Minimum profit estimation: %s%s\n", Currency.USD.getSymbol(), tradeVolume.getMinimumProfit(longLimitPrice, shortLimitPrice))+
            longEntryString +
            shortEntryString;

        sendNotification(EMAIL_SUBJECT_NEW_ENTRY, message);
    }

    /**
     * Format and send a notification when a trade exits.
     *
     * @param spread The Spread.
     * @param tradeVolume The traded volumes
     * @param longLimitPrice The long exchange limit price.
     * @param shortLimitPrice The short exchange limit price.
     * @param entryBalance The combined account balances when the trades were first entered.
     * @param updatedBalance The new account balances after exiting the trades.
     * @param exitTarget The spread exit target.
     * @param isForceExitPosition Flag to indicate if this exit trade is forced or not.
     * @param isActivePositionExpired Flag to indicate if this exit trade is due to a timeout (position time expired).
     */
    @Override
    public void sendExitTradeNotification(Spread spread, ExitTradeVolume tradeVolume, BigDecimal longLimitPrice,
                                          BigDecimal shortLimitPrice, BigDecimal entryBalance, BigDecimal updatedBalance, BigDecimal exitTarget,
                                          boolean isForceExitPosition, boolean isActivePositionExpired) {

        final String exitSpreadString = String.format("Exit spread: %s\nExit spread target %s\n", spread.getOut(), exitTarget);

        final String longCloseString = String.format("Long close: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getLongExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            tradeVolume.getLongVolume().toPlainString(),
            longLimitPrice.toPlainString(),
            spread.getLongTicker().getBid().toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getLongVolume().multiply(longLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getLongVolume().multiply(spread.getLongTicker().getBid()).toPlainString());

        final String shortCloseString = String.format("Short close: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getShortExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            tradeVolume.getShortVolume().toPlainString(),
            shortLimitPrice.toPlainString(),
            spread.getShortTicker().getAsk().toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getShortVolume().multiply(shortLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            tradeVolume.getShortVolume().multiply(spread.getShortTicker().getAsk()).toPlainString());

        final BigDecimal profit = updatedBalance.subtract(entryBalance);

        // This is equivalent of if(isActivePositionExpired) {timeout} else if(isForceExitPosition) {forced} else {exit}
        final String message = isActivePositionExpired ? "***** TIMEOUT EXIT *****\n" : (isForceExitPosition ? "***** FORCED EXIT *****\n" : "***** EXIT *****\n") +
            exitSpreadString +
            longCloseString +
            shortCloseString +
            String.format("Combined account balances on entry: $%s\n", entryBalance.toPlainString()) +
            String.format("Profit calculation: $%s - $%s = $%s\n", updatedBalance.toPlainString(), entryBalance.toPlainString(), profit.toPlainString());

        sendNotification(EMAIL_SUBJECT_NEW_EXIT, message);
    }

    /**
     * Send the notification via email and/or Telegram.
     * @param subject the notification title (in case of email)
     * @param message the notification body/message
     */
    @Override
    public void sendNotification(String subject, String message) {
        sendInstantMessage(message);
        sendEmail(subject, message);
    }

    /**
     * Send an email notification, if email is configured.
     *
     * @param subject The subject line of the email.
     * @param body The body of the email.
     */
    private void sendEmail(String subject, String body) {
        if (notificationConfiguration.getMail() == null || notificationConfiguration.getMail().getActive() == null ||
            !notificationConfiguration.getMail().getActive()) {

            LOGGER.info("Email notification is disabled");
            return;
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(notificationConfiguration.getMail().getTo());
        mail.setFrom(notificationConfiguration.getMail().getFrom());
        mail.setSubject(subject);
        mail.setText(body);

        try {
            javaMailSender.send(mail);
        }
        catch (Exception e) {
            LOGGER.error("Could not send email notification to {}. Reason: {}", notificationConfiguration.getMail().getTo(), e.getMessage());
        }
    }

    /**
     * Send an instant message notification. Currently only supports instant messages via Telegram.
     * Check the wiki page for more details on how to receive instant messages via Telegram.
     * @param message the message to send
     * @see <a href="https://github.com/agonyforge/arbitrader/wiki/Arbitrade-Configurations#telegram">Wiki Page</a>
     */
    private void sendInstantMessage(String message) {
        if (notificationConfiguration.getTelegram() == null || notificationConfiguration.getTelegram().getActive() == null ||
            !notificationConfiguration.getTelegram().getActive()) {

            LOGGER.info("Instant messaging notification is disabled");
            return;
        }

        final String groupId = notificationConfiguration.getTelegram().getGroupId();

        if (groupId.isEmpty()) {
            LOGGER.error("Missing groupId in the telegram configuation. Set it in application.yml file");
            return;
        }

        try {
            telegramClient.sendMessage(message, groupId);
        }
        catch (Exception e) {
            LOGGER.error("Could not instant message notification to groupId {}. Reason: {}", groupId, e.getMessage());
        }
    }
}
