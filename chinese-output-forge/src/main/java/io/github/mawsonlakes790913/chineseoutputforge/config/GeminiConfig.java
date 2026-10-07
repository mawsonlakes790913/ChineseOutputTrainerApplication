package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

@Configuration
public class GeminiConfig {

    /**
     * Gemini APIとの通信に使用するClientを生成する。
     *
     * @param apiKey Gemini APIキー
     * @return Gemini API用のClient
     */
    @Bean
    Client geminiClient(
            @Value("${GOOGLE_API_KEY}") String apiKey) {

        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}