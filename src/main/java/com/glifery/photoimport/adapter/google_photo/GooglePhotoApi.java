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
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.proto.NewMediaItemResult;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import lombok.Getter;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Component;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
        File newfile = new File("result_file.jpg");

        try {
            changeExifMetadata(mediaData.getFile(), mediaData.getDate(), newfile);
        } catch (ImageReadException e) {
            e.printStackTrace();
        } catch (ImageWriteException e) {
            e.printStackTrace();
        }


        Credential credential = flow.loadCredential(user.getId());

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(googleConfig.getClientId())
                .setClientSecret(googleConfig.getClientSecret())
                .setAccessToken(new AccessToken(credential.getAccessToken(), new Date(credential.getExpiresInSeconds())))
                .setRefreshToken(credential.getRefreshToken())
                .build();

        PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials))
                .build();

        try (PhotosLibraryClient photosLibraryClient =
                     PhotosLibraryClient.initialize(settings)) {

            RandomAccessFile file = new RandomAccessFile(newfile, "r");

            UploadMediaItemRequest uploadRequest = UploadMediaItemRequest.newBuilder()
                // The media type (e.g. "image/png")
                .setMimeType("image/jpeg")
                // The file to upload
                .setDataFile(file)
                .build();

            UploadMediaItemResponse uploadResponse = photosLibraryClient.uploadMediaItem(uploadRequest);
            if (uploadResponse.getError().isPresent()) {
                // If the upload results in an error, handle it
                UploadMediaItemResponse.Error error = uploadResponse.getError().get();
                error.toString();

                return;
            }

            // If the upload is successful, get the uploadToken
            String uploadToken = uploadResponse.getUploadToken().get();
            // Use this upload token to create a media item



            NewMediaItem newMediaItem = NewMediaItemFactory
                    .createNewMediaItem(uploadToken, mediaData.getName(), "itemDescription");
            List<NewMediaItem> newItems = Arrays.asList(newMediaItem);

            BatchCreateMediaItemsResponse response = photosLibraryClient.batchCreateMediaItems(newItems);
            for (NewMediaItemResult itemsResponse : response.getNewMediaItemResultsList()) {
                Status status = itemsResponse.getStatus();
                if (status.getCode() == Code.OK_VALUE) {
                    // The item is successfully created in the user's library
                    MediaItem createdItem = itemsResponse.getMediaItem();
                } else {
                    // The item could not be created. Check the status and try again
                }
            }

        } catch (ApiException e) {
            e.printStackTrace();
        }
    }


    public void changeExifMetadata(final File jpegImageFile, Date date, final File dst)
              throws IOException, ImageReadException, ImageWriteException {
                  try (FileOutputStream fos = new FileOutputStream(dst);
                       OutputStream os = new BufferedOutputStream(fos)) {

                          TiffOutputSet outputSet = null;

                          // note that metadata might be null if no metadata is found.
                          final ImageMetadata metadata = Imaging.getMetadata(jpegImageFile);
                          final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                          if (null != jpegMetadata) {
                                  // note that exif might be null if no Exif metadata is found.
                                  final TiffImageMetadata exif = jpegMetadata.getExif();

                                  if (null != exif) {
                                          // TiffImageMetadata class is immutable (read-only).
                                          // TiffOutputSet class represents the Exif data to write.
                                          //
                                          // Usually, we want to update existing Exif metadata by
                                          // changing
                                          // the values of a few fields, or adding a field.
                                          // In these cases, it is easiest to use getOutputSet() to
                                          // start with a "copy" of the fields read from the image.
                                          outputSet = exif.getOutputSet();
                                      }
                              }

                          // if file does not contain any exif metadata, we create an empty
                          // set of exif metadata. Otherwise, we keep all of the other
                          // existing tags.
                          if (null == outputSet) {
                                  outputSet = new TiffOutputSet();
                              }

                                  // Example of how to add a field/tag to the output set.
                                  //
                                  // Note that you should first remove the field/tag if it already
                                  // exists in this directory, or you may end up with duplicate
                                  // tags. See above.
                                  //
                                 // Certain fields/tags are expected in certain Exif directories;
                                 // Others can occur in more than one directory (and often have a
                                 // different meaning in different directories).
                                 //
                                 // TagInfo constants often contain a description of what
                                 // directories are associated with a given tag.
                                 //
                                 final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
                                 // make sure to remove old value if present (this method will
                                 // not fail if the tag does not exist).
                                 exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);

                              DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                              String strDate = dateFormat.format(date);
                              exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, strDate);



                         // printTagValue(jpegMetadata, TiffConstants.TIFF_TAG_DATE_TIME);

                         new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os,
                                         outputSet);
                     }
             }
}
