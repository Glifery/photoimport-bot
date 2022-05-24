package com.glifery.photoimport.adapter.telegram;

import com.glifery.photoimport.domain.model.MediaData;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MediaOperator {
    public Optional<MediaData> extractMediaData(Update update, DefaultAbsSender sender) throws TelegramApiException {
        return Optional.ofNullable(getPhoto(update))
                .map(photoSize -> {
                    java.io.File file;
                    try {
                        file = sender.downloadFile(getFilePath(photoSize, sender));
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        return null;
                    }

                    if (Objects.isNull(file)) {
                        return null;
                    }
                    return new MediaData(file, photoSize.getFileUniqueId(), getOriginalDate(update), getOriginalSender(update));
                });
    }

    public Date getOriginalDate(Update update) {
        Integer timestamp = Optional.ofNullable(update.getMessage().getForwardDate())
                .orElse(update.getMessage().getDate());

        return new java.util.Date((long)timestamp*1000);
    }

    public String getOriginalSender(Update update) {
        return Optional.ofNullable(update.getMessage().getForwardFromChat())
                .map(chat -> chat.getTitle())
                .orElse(
                        Optional.ofNullable(update.getMessage().getForwardFrom())
                                .map(this::getUserName)
                                .orElse(update.getMessage().getForwardSenderName())
                );
    }

    public PhotoSize getPhoto(Update update) {
        // Check that the update contains a message and the message has a photo
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            // When receiving a photo, you usually get different sizes of it
            List<PhotoSize> photos = update.getMessage().getPhoto();

            // We fetch the bigger photo
            return photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(null);
        }

        // Return null if not found
        return null;
    }

    public String getFilePath(PhotoSize photo, DefaultAbsSender sender) {
        Objects.requireNonNull(photo);

        if (Objects.nonNull(photo.getFilePath())) { // If the file_path is already present, we are done!
            return photo.getFilePath();
        } else { // If not, let find it
            // We create a GetFile method and set the file_id from the photo
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            try {
                // We execute the method using AbsSender::execute method.
                File file = sender.execute(getFileMethod);
                // We now have the file_path
                return file.getFilePath();
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        return null; // Just in case
    }

    public String getUserName(User user) {
        if (Objects.nonNull(user.getUserName())) {
           return user.getUserName();
        }
        return Arrays.asList(user.getFirstName(), user.getLastName()).stream()
                .collect(Collectors.joining(" "));
    }
}
