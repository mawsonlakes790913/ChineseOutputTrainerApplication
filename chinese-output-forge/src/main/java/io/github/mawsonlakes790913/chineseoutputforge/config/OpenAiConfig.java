package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

@Configuration
public class OpenAiConfig {

    /**
     * OpenAI APIとの通信に使用するClientを生成する。
     *
     * @param apiKey OpenAI APIキー
     * @return OpenAI API用のClient
     */
    @Bean
    OpenAIClient openAIClient(
            @Value("${OPENAI_API_KEY}") String apiKey) {

        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}