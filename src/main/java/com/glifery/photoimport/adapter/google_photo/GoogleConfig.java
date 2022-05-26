package com.glifery.photoimport.adapter.google_photo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "google")
public class GoogleConfig {
    private String clientId;
    private String clientSecret;
}
