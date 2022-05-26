package com.glifery.photoimport.adapter.telegram;

import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.application.usecase.ImportMediaToStorage;
import com.glifery.photoimport.domain.model.MediaData;
import com.glifery.photoimport.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PhotoimportBot extends TelegramLongPollingBot {
    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    private final MediaOperator mediaOperator;
    private final ImportMediaToStorage importMediaToStorage;
    private final MediaStorageInterface mediaStorage;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            User user = retrieveUserFromUpdate(update);
            SendMessage message = createSendMessage(update);

            if (textEquals(update,"/start auth_completed")) {
                message.setText("Google Photo account successfully connected");
                execute(message);

                return;
            }

            if (!mediaStorage.isAuthorized(user)) {
                message.setParseMode("markdown");
                message.setText(String.format("[Authorize in Google Photo](%s)", mediaStorage.getAuthUrl(user)));
                execute(message);

                return;
            }

            Optional<MediaData> optionalMediaData = mediaOperator.extractMediaData(update, this);
            if (optionalMediaData.isPresent()) {
                importMediaToStorage.execute(optionalMediaData.get());

                message.setReplyToMessageId(update.getMessage().getMessageId());
                message.setText(String.format(
                        "File %s from %s sent at %s",
                        optionalMediaData.get().getName(),
                        optionalMediaData.get().getSender(),
                        optionalMediaData.get().getDate().toString()
                ));
            } else {
                message.setText("Unable to parse request");
            }

            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private User retrieveUserFromUpdate(Update update) {
        return new User(update.getMessage().getFrom().getId().toString());
    }

    private SendMessage createSendMessage(Update update) {
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());

        return message;
    }

    private boolean textEquals(Update update, String expected) {
        return Optional.ofNullable(update.getMessage().getText())
                .map(text -> text.equals(expected))
                .orElse(false);
    }
}
