package com.glifery.photoimport.application.usecase;

import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.domain.model.MediaData;
import com.glifery.photoimport.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ImportMediaToStorage {
    private final MediaStorageInterface mediaStorage;

    public void execute(MediaData mediaData, User user) {
        try {
            mediaStorage.storeMediaData(mediaData, user);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
