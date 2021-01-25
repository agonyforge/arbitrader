package com.r307.arbitrader.service;

import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.service.model.Spread;
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
 * Send email notifications.
 */
@Service
@Async
public class NotificationServiceImpl implements NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);
    public static final String EMAIL_SUBJECT_NEW_ENTRY = "Arbitrader - New Entry Trade";
    public static final String EMAIL_SUBJECT_NEW_EXIT = "Arbitrader - New Exit Trade";

    private final JavaMailSender javaMailSender;
    private final NotificationConfiguration notificationConfiguration;

    @Inject
    public NotificationServiceImpl(JavaMailSender javaMailSender, NotificationConfiguration notificationConfiguration) {
        this.javaMailSender = javaMailSender;
        this.notificationConfiguration = notificationConfiguration;
    }

    /**
     * Send an email notification, if email is configured.
     *
     * @param subject The subject line of the email.
     * @param body The body of the email.
     */
    @Override
    public void sendEmailNotification(String subject, String body) {
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
     * Formats and sends an email when a trade is entered.
     *
     * @param spread The Spread.
     * @param exitTarget The exit target.
     * @param longVolume The long exchange volume.
     * @param longLimitPrice The long exchange limit price.
     * @param shortVolume The short exchange volume.
     * @param shortLimitPrice The short exchange limit price.
     */
    @Override
    public void sendEmailNotificationBodyForEntryTrade(Spread spread, BigDecimal exitTarget, BigDecimal longVolume,
                                                           BigDecimal longLimitPrice, BigDecimal shortVolume,
                                                           BigDecimal shortLimitPrice) {

        final String longEntryString = String.format("Long entry: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getLongExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            longVolume.toPlainString(),
            longLimitPrice.toPlainString(),
            spread.getLongTicker().getAsk().toPlainString(),
            Currency.USD.getSymbol(),
            longVolume.multiply(longLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            longVolume.multiply(spread.getLongTicker().getAsk()).toPlainString());

        final String shortEntryString = String.format("Short entry: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getShortExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            shortVolume.toPlainString(),
            shortLimitPrice.toPlainString(),
            spread.getShortTicker().getBid().toPlainString(),
            Currency.USD.getSymbol(),
            shortVolume.multiply(shortLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            shortVolume.multiply(spread.getShortTicker().getBid()).toPlainString());

        final String emailBody = "***** ENTRY *****\n" +
            String.format("Entry spread: %s\n", spread.getIn().toPlainString()) +
            String.format("Exit spread target: %s\n", exitTarget.toPlainString()) +
            longEntryString +
            shortEntryString;

        sendEmailNotification(EMAIL_SUBJECT_NEW_ENTRY, emailBody);
    }

    /**
     * Format and send an email when a trade exits.
     *
     * @param spread The Spread.
     * @param longVolume The long exchange volume.
     * @param longLimitPrice The long exchange limit price.
     * @param shortVolume The short exchange volume.
     * @param shortLimitPrice The short exchange limit price.
     * @param entryBalance The combined account balances when the trades were first entered.
     * @param updatedBalance The new account balances after exiting the trades.
     */
    @Override
    public void sendEmailNotificationBodyForExitTrade(Spread spread, BigDecimal longVolume, BigDecimal longLimitPrice,
                                                          BigDecimal shortVolume, BigDecimal shortLimitPrice,
                                                          BigDecimal entryBalance, BigDecimal updatedBalance) {

        final String longCloseString = String.format("Long close: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getLongExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            longVolume.toPlainString(),
            longLimitPrice.toPlainString(),
            spread.getLongTicker().getBid().toPlainString(),
            Currency.USD.getSymbol(),
            longVolume.multiply(longLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            longVolume.multiply(spread.getLongTicker().getBid()).toPlainString());

        final String shortCloseString = String.format("Short close: %s %s %s @ %s (slipped from %s) = %s%s (slipped from %s%s)\n",
            spread.getShortExchange().getExchangeSpecification().getExchangeName(),
            spread.getCurrencyPair(),
            shortVolume.toPlainString(),
            shortLimitPrice.toPlainString(),
            spread.getShortTicker().getAsk().toPlainString(),
            Currency.USD.getSymbol(),
            shortVolume.multiply(shortLimitPrice).toPlainString(),
            Currency.USD.getSymbol(),
            shortVolume.multiply(spread.getShortTicker().getAsk()).toPlainString());

        final BigDecimal profit = updatedBalance.subtract(entryBalance);

        final String emailBody = "***** EXIT *****\n" +
            longCloseString +
            shortCloseString +
            String.format("Combined account balances on entry: $%s\n", entryBalance.toPlainString()) +
            String.format("Profit calculation: $%s - $%s = $%s\n", updatedBalance.toPlainString(), entryBalance.toPlainString(), profit.toPlainString());

        sendEmailNotification(EMAIL_SUBJECT_NEW_EXIT, emailBody);
    }
}
