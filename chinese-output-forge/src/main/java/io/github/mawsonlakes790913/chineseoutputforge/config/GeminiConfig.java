package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

@Configuration
public class GeminiConfig {

    @Bean
    Client geminiClient() {
        return new Client();
    }
}
