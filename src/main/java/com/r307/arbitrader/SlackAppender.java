package com.r307.arbitrader;

import ch.qos.logback.core.AppenderBase;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.request.chat.ChatPostMessageRequest;
import com.r307.arbitrader.config.NotificationConfiguration;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Collections;

public class SlackAppender<T> extends AppenderBase<T> {
    @Override
    protected void append(T eventObject) {
        ApplicationContext applicationContext = SpringContextSingleton.getInstance().getApplicationContext();

        if (applicationContext == null) {
            return;
        }

        NotificationConfiguration notificationConfiguration = (NotificationConfiguration) applicationContext.getBean("notificationConfiguration");

        try {
            Slack.getInstance().methods().chatPostMessage(ChatPostMessageRequest.builder()
                    .token(notificationConfiguration.getSlack().getAccessToken())
                    .asUser(false)
                    .channel(notificationConfiguration.getSlack().getChannel())
                    .text(eventObject.toString())
                    .attachments(Collections.emptyList())
                    .build());
        } catch (IOException | SlackApiException e) {
            // can't log here or we'll cause an endless loop...
        }
    }
}
