package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for notifications to other services like email, Slack and Discord.
 */
@ConfigurationProperties("notifications")
@Configuration
public class NotificationConfiguration {
    private Slack slack = new Slack();
    private Logs logs = new Logs();
    private Mail mail = new Mail();
    private Discord discord = new Discord();

    public Slack getSlack() {
        return slack;
    }

    public void setSlack(Slack slack) {
        this.slack = slack;
    }

    public Logs getLogs() {
        return logs;
    }

    public void setLogs(Logs logs) {
        this.logs = logs;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Discord getDiscord() {
        return discord;
    }

    public void setDiscord(Discord discord) {
        this.discord = discord;
    }

    public class Slack {
        private String accessToken;
        private String botAccessToken;
        private String channel;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getBotAccessToken() {
            return botAccessToken;
        }

        public void setBotAccessToken(String botAccessToken) {
            this.botAccessToken = botAccessToken;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }

    public class Logs {
        private Integer slowTickerWarning = 3000;

        public Integer getSlowTickerWarning() {
            return slowTickerWarning;
        }

        public void setSlowTickerWarning(Integer slowTickerWarning) {
            this.slowTickerWarning = slowTickerWarning;
        }
    }

    public class Mail {
        private Boolean active;
        private String from;
        private String to;

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }

    public class Discord {
        private String webhookId;
        private String webhookToken;

        public String getWebhookId() {
            return webhookId;
        }

        public void setWebhookId(String webhookId) {
            this.webhookId = webhookId;
        }

        public String getWebhookToken() {
            return webhookToken;
        }

        public void setWebhookToken(String webhookToken) {
            this.webhookToken = webhookToken;
        }
    }
}
