package com.glifery.photoimport.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.MimeType;

import java.io.File;
import java.util.Date;

@Data
@AllArgsConstructor
public class MediaData {
    private File file;
    private String mimeType;
    private String name;
    private Date date;
    private String sender;
}
