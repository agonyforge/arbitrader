package com.r307.arbitrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("notifications")
@Configuration
public class NotificationConfiguration {
    private Slack slack = new Slack();

    public Slack getSlack() {
        return slack;
    }

    public void setSlack(Slack slack) {
        this.slack = slack;
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
}
