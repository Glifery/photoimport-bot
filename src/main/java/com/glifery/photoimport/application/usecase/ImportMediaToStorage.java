package com.glifery.photoimport.application.usecase;

import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.domain.model.MediaData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportMediaToStorage {
//    private final MediaStorageInterface mediaStorage;

    public void execute(MediaData mediaData) {

    }
}
