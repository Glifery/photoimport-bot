package com.glifery.photoimport.application.port;

import com.glifery.photoimport.domain.model.MediaData;

public interface MediaStorageInterface {
    void storeMediaData(MediaData mediaData);
}
