package com.glifery.photoimport.adapter.telegram;

import com.glifery.photoimport.application.usecase.ImportMediaToStorage;
import com.glifery.photoimport.domain.model.MediaData;
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
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId().toString());

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
                message.setText("Unable to parse message");
            }

            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
