package com.agonyforge.arbitrader.config;

import com.agonyforge.arbitrader.service.NotificationService;
import com.agonyforge.arbitrader.service.NotificationServiceImpl;
import com.agonyforge.arbitrader.service.model.Spread;
import com.agonyforge.arbitrader.service.model.EntryTradeVolume;
import com.agonyforge.arbitrader.service.model.ExitTradeVolume;
import com.agonyforge.arbitrader.service.telegram.TelegramClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;

/**
 * Configuration for sending emails.
 */
@Configuration
public class MailConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring", value = "mail")
    public NotificationService notificationService(JavaMailSender javaMailSender, NotificationConfiguration config, TelegramClient telegramClient) {
        return new NotificationServiceImpl(javaMailSender, config, telegramClient);
    }

    @Bean
    @ConditionalOnMissingBean(value = NotificationService.class)
    public NotificationService notificationService() {
        return new NotificationService() {
            @Override
            public void sendNotification(String subject, String message) {
            }

            @Override
            public void sendEntryTradeNotification(Spread spread, BigDecimal exitTarget, EntryTradeVolume tradeVolume, BigDecimal longLimitPrice, BigDecimal shortLimitPrice, boolean isForceEntryPosition) {
            }

            @Override
            public void sendExitTradeNotification(Spread spread, ExitTradeVolume tradeVolume, BigDecimal longLimitPrice, BigDecimal shortLimitPrice, BigDecimal entryBalance, BigDecimal updatedBalance, BigDecimal exitTarget, boolean isForceCloseCondition, boolean isActivePositionExpired) {
            }
        };
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.mail")
    @ConditionalOnMissingBean(value = JavaMailSender.class)
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }


}
