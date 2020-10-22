package com.r307.arbitrader.config;

import com.r307.arbitrader.service.NotificationService;
import com.r307.arbitrader.service.NotificationServiceImpl;
import com.r307.arbitrader.service.model.Spread;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;

@Configuration
public class MailConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring", value = "mail")
    public NotificationService notificationService(JavaMailSender javaMailSender, NotificationConfiguration config) {
        return new NotificationServiceImpl(javaMailSender, config);
    }

    @Bean
    @ConditionalOnMissingBean(value = NotificationService.class)
    public NotificationService notificationService() {
        return new NotificationService() {
            @Override
            public void sendEmailNotification(String subject, String body) {
            }

            @Override
            public void sendEmailNotificationBodyForEntryTrade(Spread spread, BigDecimal exitTarget, BigDecimal longVolume, BigDecimal longLimitPrice, BigDecimal shortVolume, BigDecimal shortLimitPrice) {
            }

            @Override
            public void sendEmailNotificationBodyForExitTrade(Spread spread, BigDecimal longVolume, BigDecimal longLimitPrice, BigDecimal shortVolume, BigDecimal shortLimitPrice, BigDecimal entryBalance, BigDecimal updatedBalance) {
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
