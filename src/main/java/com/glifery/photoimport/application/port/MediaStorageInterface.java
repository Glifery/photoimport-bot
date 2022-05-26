package com.glifery.photoimport.application.port;

import com.glifery.photoimport.domain.model.MediaData;
import com.glifery.photoimport.domain.model.User;

public interface MediaStorageInterface {
    boolean isAuthorized(User user);
    String getAuthUrl(User user);
    boolean authByCode(String code, User user);
    void storeMediaData(MediaData mediaData);
}
