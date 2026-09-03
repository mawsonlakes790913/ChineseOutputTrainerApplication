package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiPromptService {

    private static final String COMMON_PROMPT_PATH =
            "prompts/ai-question-generation-common.txt";
//    private static final String COMMON_PROMPT_PATH =
//            "prompts/ai-question-generation-common-test.txt";

    private static final String MAINLAND_PROFILE_PATH =
            "prompts/language-profile-mainland.txt";

    private static final String TAIWAN_PROFILE_PATH =
            "prompts/language-profile-taiwan.txt";
    
    private final MessageSource messageSource;
    
    // AI問題生成の共通プロンプトを取得する
    public String getCommonPrompt(Locale locale) {

        return readPromptFile(COMMON_PROMPT_PATH, locale);
    }


    // LanguageVariantに対応するLanguage Profileを取得する
    public String getLanguageProfile(
    		LanguageVariant languageVariant,
    		Locale locale) {

        if (languageVariant == LanguageVariant.MAINLAND) {
            return readPromptFile(MAINLAND_PROFILE_PATH, locale);
        }

        if (languageVariant == LanguageVariant.TAIWAN) {
            return readPromptFile(TAIWAN_PROFILE_PATH, locale);
        }

        throw new IllegalArgumentException(
                "Unsupported language variant: " + languageVariant);
    }
    
    // classpath上のプロンプトファイルを読み込む
    private String readPromptFile(
    		String path,
    		Locale locale) {

        ClassPathResource resource =
                new ClassPathResource(path);

        try (InputStream inputStream =
                resource.getInputStream()) {

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8);

        } catch (IOException e) {

        	throw new IllegalStateException(
        	        messageSource.getMessage(
        	                "ai.prompt.error.read",
        	                new Object[] { path },
        	                locale),
        	        e);
        }
    }
}
