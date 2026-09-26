package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

@Configuration
public class GeminiConfig {

    /**
     * Gemini APIとの通信に使用するClientを生成する。
     *
     * @return Gemini API用のClient
     */
    @Bean
    Client geminiClient() {
        return new Client();
    }
}
