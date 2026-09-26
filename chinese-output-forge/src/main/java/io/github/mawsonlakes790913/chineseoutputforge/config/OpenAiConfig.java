package io.github.mawsonlakes790913.chineseoutputforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

@Configuration
public class OpenAiConfig {

    /**
     * OpenAI APIとの通信に使用するClientを生成する。
     * 接続に必要な設定は環境変数から取得する。
     *
     * @return OpenAI API用のClient
     */
    @Bean
    OpenAIClient openAIClient() {

        return OpenAIOkHttpClient.fromEnv();
    }
}
