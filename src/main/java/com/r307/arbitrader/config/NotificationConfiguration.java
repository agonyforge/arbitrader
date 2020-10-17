package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("notifications")
@Configuration
public class NotificationConfiguration {
    private Slack slack = new Slack();
    private Logs logs = new Logs();
    private Email email = new Email();

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

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
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

    public class Email {
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
}
