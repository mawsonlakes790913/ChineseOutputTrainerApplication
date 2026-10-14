package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
public class SesConfig {

    @Bean
    SesV2Client sesV2Client() {
        return SesV2Client.builder()
                .region(Region.AP_NORTHEAST_1)
                .build();
    }
}
