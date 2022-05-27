package com.glifery.photoimport.adapter.telegram;

import com.glifery.photoimport.application.config.AppConfig;
import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.application.usecase.ImportMediaToStorage;
import com.glifery.photoimport.domain.model.MediaData;
import com.glifery.photoimport.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PhotoimportBot extends TelegramLongPollingBot {
    private final AppConfig appConfig;
    private final TelegramConfig telegramConfig;
    private final MediaOperator mediaOperator;
    private final ImportMediaToStorage importMediaToStorage;
    private final MediaStorageInterface mediaStorage;

    @Override
    public String getBotUsername() {
        return telegramConfig.getUsername();
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getToken();
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

            if (!mediaStorage.isAuthorized(user) && appConfig.getImportEnabled()) {
                message.setParseMode("markdown");
                message.setText(String.format("[Authorize in Google Photo](%s)", mediaStorage.getAuthUrl(user)));
                execute(message);

                return;
            }

            Optional<MediaData> optionalMediaData = mediaOperator.extractMediaData(update, this);
            if (optionalMediaData.isPresent()) {
                if (appConfig.getImportEnabled()) {
                    importMediaToStorage.execute(optionalMediaData.get(), user);
                }

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
