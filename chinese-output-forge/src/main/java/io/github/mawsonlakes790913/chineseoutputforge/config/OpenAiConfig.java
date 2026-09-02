package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

@Configuration
public class OpenAiConfig {

    @Bean
    OpenAIClient openAIClient() {

        return OpenAIOkHttpClient.fromEnv();
    }
}
