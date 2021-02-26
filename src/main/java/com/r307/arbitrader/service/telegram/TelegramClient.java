package com.r307.arbitrader.service.telegram;

import com.r307.arbitrader.config.NotificationConfiguration;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TelegramClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramClient.class);

    private final OkHttpClient client;
    private final String token;

    public TelegramClient(NotificationConfiguration notificationConfiguration) {
        this.client = new OkHttpClient();
        this.token = notificationConfiguration.getTelegram().getToken();
    }

    public void sendMessage(String message, String receiverUserName) {
        final HttpUrl url = new HttpUrl.Builder()
            .scheme("https")
            .host("api.telegram.org")
            .addPathSegment("bot" + token)
            .addPathSegment("sendMessage")
            .addQueryParameter("chat_id", receiverUserName)
            .addQueryParameter("text", message)
            .build();

        final Request request = new Request.Builder()
            .url(url)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                LOGGER.error("Failed to send message to telegram: ", e);
                // Cancel the connection in case it is still active. There is no problem calling cancel() on an
                // already canceled connection
                call.cancel();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                LOGGER.debug("Message sent to telegram. Response: {}", response);
                response.close();
            }
        });
    }
}

