package com.glifery.photoimport.adapter.telegram;

import com.glifery.photoimport.domain.model.MediaData;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MediaOperator {
    public Optional<MediaData> extractMediaData(Update update, DefaultAbsSender sender) throws TelegramApiException {
        return Optional.ofNullable(getPhoto(update))
                .map(photoSize -> {
                    String filePath = "";
                    java.io.File file;
                    try {
                        filePath = getFilePath(photoSize, sender);
                        file = sender.downloadFile(filePath);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        return null;
                    }

                    Date date = getOriginalDate(update);
                    try {
                        file = changeExifMetadata(file, date);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    } catch (ImageReadException e) {
                        e.printStackTrace();
                        return null;
                    } catch (ImageWriteException e) {
                        e.printStackTrace();
                        return null;
                    }

                    if (Objects.isNull(file)) {
                        return null;
                    }

                    String mimeType = URLConnection.guessContentTypeFromName(filePath);

                    return new MediaData(file, mimeType, photoSize.getFileUniqueId(), date, getOriginalSender(update));
                });
    }

    private Date getOriginalDate(Update update) {
        Integer timestamp = Optional.ofNullable(update.getMessage().getForwardDate())
                .orElse(update.getMessage().getDate());

        return new java.util.Date((long)timestamp*1000);
    }

    private String getOriginalSender(Update update) {
        return Optional.ofNullable(update.getMessage().getForwardFromChat())
                .map(chat -> chat.getTitle())
                .orElse(
                        Optional.ofNullable(update.getMessage().getForwardFrom())
                                .map(this::getUserName)
                                .orElse(update.getMessage().getForwardSenderName())
                );
    }

    private PhotoSize getPhoto(Update update) {
        return Optional.ofNullable(update.getMessage())
                .map(message -> message.getPhoto())
                .map(photoSizes -> photoSizes.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElse(null))
                .orElse(null);
    }

    private String getFilePath(PhotoSize photo, DefaultAbsSender sender) {
        if (Objects.nonNull(photo.getFilePath())) {
            return photo.getFilePath();
        }

        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(photo.getFileId());
        try {
            File file = sender.execute(getFileMethod);
            return file.getFilePath();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getUserName(User user) {
        if (Objects.nonNull(user.getUserName())) {
           return user.getUserName();
        }

        return Arrays.asList(user.getFirstName(), user.getLastName()).stream()
                .collect(Collectors.joining(" "));
    }

    public java.io.File changeExifMetadata(java.io.File original, Date date) throws IOException, ImageReadException, ImageWriteException {
        java.io.File tempFile = java.io.File.createTempFile("exif.", null);

        FileOutputStream fos = new FileOutputStream(tempFile);
        OutputStream os = new BufferedOutputStream(fos);

        TiffOutputSet outputSet = null;

        final ImageMetadata metadata = Imaging.getMetadata(original);
        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        if (null != jpegMetadata) {
            final TiffImageMetadata exif = jpegMetadata.getExif();

            if (null != exif) {
                outputSet = exif.getOutputSet();
            }
        }

        if (null == outputSet) {
            outputSet = new TiffOutputSet();
        }

        final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
        exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String strDate = dateFormat.format(date);
        exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, strDate);

        new ExifRewriter()
                .updateExifMetadataLossless(original, os, outputSet);

        return tempFile;
    }
}
