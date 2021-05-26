package com.agonyforge.arbitrader.logging;

import ch.qos.logback.core.AppenderBase;
import com.agonyforge.arbitrader.config.NotificationConfiguration;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;

import java.io.IOException;

/**
 * Sends slf4j log messages to Discord.
 *
 * @param <T> the log message.
 */
public class DiscordAppender<T> extends AppenderBase<T> {
    public static final MediaType MEDIA_TYPE_JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void append(T eventObject) {
        final ApplicationContext appContext = SpringContextSingleton.getInstance().getApplicationContext();

        if (appContext == null) {
            return;
        }

        final NotificationConfiguration notificationConfig = (NotificationConfiguration) appContext.getBean("notificationConfiguration");
        final String url = "https://discord.com/api/webhooks/" + notificationConfig.getDiscord().getWebhookId() + "/" +
            notificationConfig.getDiscord().getWebhookToken();

        final String bodyContent = "{\"content\": \"" + eventObject.toString() + "\" }";
        final RequestBody body = RequestBody.create(bodyContent, MEDIA_TYPE_JSON);
        final Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        // Send the log message asynchronously to Discord via webhook
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // Cancel the connection in case it is still active. There is no problem calling cancel() on an
                // already canceled connection
                call.cancel();
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                // We need to close the response in order to avoid leaking it
                response.close();
            }
        });
    }
}
