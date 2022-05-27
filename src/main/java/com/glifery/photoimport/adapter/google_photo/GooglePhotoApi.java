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
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.DateFilter;
import com.google.photos.library.v1.proto.Filters;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.MediaItem;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

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
        try {
            TokenResponse tokenResponse = request.execute();
            flow.createAndStoreCredential(tokenResponse, user.getId());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public void storeMediaData(MediaData mediaData, User user) throws IOException {
        Credential credential = flow.loadCredential(user.getId());

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(googleConfig.getClientId())
                .setClientSecret(googleConfig.getClientSecret())
                .setAccessToken(
                        new AccessToken(credential.getAccessToken(),
                        new Date(credential.getExpiresInSeconds()))
                )
                .setRefreshToken(credential.getRefreshToken())
                .build();

        PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials))
                .build();

        try (PhotosLibraryClient photosLibraryClient =
                     PhotosLibraryClient.initialize(settings)) {

            com.google.type.Date date = com.google.type.Date.newBuilder()
                    .setDay(mediaData.getDate().getDate())
                    .setMonth(mediaData.getDate().getMonth() + 1)
                    .setYear(mediaData.getDate().getYear() + 1900)
                    .build();
            DateFilter dateFilter = DateFilter.newBuilder()
                    .addDates(date)
                    .build();
            Filters filters = Filters.newBuilder()
                     .setDateFilter(dateFilter)
                    .build();

            InternalPhotosLibraryClient.SearchMediaItemsPagedResponse response = photosLibraryClient.searchMediaItems(filters);
            for (MediaItem item : response.iterateAll()) {
                String filename = item.getFilename();
                if (mediaData.getName().equals(filename)) {
                    return;
                }
            }

            RandomAccessFile file = new RandomAccessFile(mediaData.getFile(), "r");

            UploadMediaItemRequest uploadRequest = UploadMediaItemRequest.newBuilder()
                .setMimeType(mediaData.getMimeType())
                .setDataFile(file)
                .build();

            UploadMediaItemResponse uploadResponse = photosLibraryClient.uploadMediaItem(uploadRequest);
            if (uploadResponse.getError().isPresent()) {
                UploadMediaItemResponse.Error error = uploadResponse.getError().get();
                error.toString();

                return;
            }

            String uploadToken = uploadResponse.getUploadToken().get();

            NewMediaItem newMediaItem = NewMediaItemFactory
                    .createNewMediaItem(uploadToken, mediaData.getName(), "photoimport_bot");
            List<NewMediaItem> newItems = Arrays.asList(newMediaItem);

            BatchCreateMediaItemsResponse createResponse = photosLibraryClient.batchCreateMediaItems(newItems);
//            for (NewMediaItemResult itemsResponse : createResponse.getNewMediaItemResultsList()) {
//                Status status = itemsResponse.getStatus();
//                if (status.getCode() == Code.OK_VALUE) {
//                    MediaItem createdItem = itemsResponse.getMediaItem();
//                } else {
//                }
//            }

        } catch (ApiException e) {
            e.printStackTrace();
        }
    }
}
