package com.glifery.photoimport.adapter.google_photo;

import com.glifery.photoimport.application.config.AppConfig;
import com.glifery.photoimport.application.port.MediaStorageInterface;
import com.glifery.photoimport.domain.model.MediaData;
import com.glifery.photoimport.domain.model.User;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

@Component
public class GooglePhotoApi implements MediaStorageInterface {
    private GoogleConfig googleConfig;
    private AppConfig appConfig;

    @Getter
    private final AuthorizationCodeFlow flow;

    public GooglePhotoApi(GoogleConfig googleConfig, AppConfig appConfig) throws IOException {
        this.googleConfig = googleConfig;
        this.appConfig = appConfig;
        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                googleConfig.getClientId(),
                googleConfig.getClientSecret(),
                Collections
                        .singleton("https://www.googleapis.com/auth/photoslibrary")
        )
                .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
                .setAccessType("offline")
                .build();
    }

    @Override
    public boolean isAuthorized(User user) {
        try {
            Credential credential = flow.loadCredential(user.getId());
            return Objects.nonNull(credential);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String getAuthUrl(User user) {
        return flow.newAuthorizationUrl()
                .setState(user.getId())
                .setRedirectUri(getRedirectUrl()).build();
    }

    public String getRedirectUrl() {
        return String.format(
                "%s/auth",
                appConfig.getHost()
        );
    }

    @Override
    public boolean authByCode(String code, User user) {
        AuthorizationCodeTokenRequest request = flow.newTokenRequest(code).setRedirectUri(getRedirectUrl());
        Credential credential;
        try {
            TokenResponse tokenResponse = request.execute();
            credential = flow.createAndStoreCredential(tokenResponse, user.getId());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        credential = credential;

        return true;
    }

    public void storeMediaData(MediaData mediaData) {

    }


    private void auth() throws IOException {
        AuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                "[[ENTER YOUR CLIENT ID]]",
                "[[ENTER YOUR CLIENT SECRET]]",
                Collections
                        .singleton("https://www.googleapis.com/auth/photoslibrary")
        )
                .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
                .setAccessType("offline")
                .build();
        Credential credential = flow.loadCredential("user-id");
        if (Objects.isNull(credential)) {
            String url = flow.newAuthorizationUrl()
                    .setState("xyz")
                    .setRedirectUri("https://client.example.com/rd").build();
            AuthorizationCodeTokenRequest request = flow.newTokenRequest("code");
            credential = flow.createAndStoreCredential(request.execute(), "user-id");
        }

        GenericUrl url = new GenericUrl("https://t.me/photoimport_bot?start=");
        url.setRawPath("/oauth2callback");
//        return url.build();
    }

//    private void init() {
//
//        PhotosLibrarySettings settings =
//                PhotosLibrarySettings.newBuilder()
//                        .setCredentialsProvider(
//                                FixedCredentialsProvider.create())
//                        .build();
//
//        try (PhotosLibraryClient photosLibraryClient =
//                     PhotosLibraryClient.initialize(settings)) {
//
//            // Create a new Album  with at title
//            Album createdAlbum = photosLibraryClient.createAlbum("My Album");
//
//            // Get some properties from the album, such as its ID and product URL
//            String id = album.getId();
//            String url = album.getProductUrl();
//
//        } catch (ApiException e) {
//            // Error during album creation
//        }
//    }
}
