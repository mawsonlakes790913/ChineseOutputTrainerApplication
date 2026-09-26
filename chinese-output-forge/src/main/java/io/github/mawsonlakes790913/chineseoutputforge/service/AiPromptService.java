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

/**
 * AI問題生成および発音表記生成で使用するプロンプトを管理するService。
 * classpath上のプロンプトファイルを読み込み、用途や学習対象言語に応じて提供する。
 */
@Service
@RequiredArgsConstructor
public class AiPromptService {

    private static final String COMMON_PROMPT_PATH =
            "prompts/ai-question-generation-common-test2.txt";

    private static final String MAINLAND_PROFILE_PATH =
            "prompts/language-profile-mainland.txt";

    private static final String TAIWAN_PROFILE_PATH =
            "prompts/language-profile-taiwan.txt";
    
    private static final String MAINLAND_PRONUNCIATION_PATH =
    		"prompts/ai-pronunciation-mainland.txt";
    
    private static final String TAIWAN_PRONUNCIATION_PATH =
    		"prompts/ai-pronunciation-taiwan.txt";
    
    private final MessageSource messageSource;
    
    /**
     * AI問題生成で使用する共通プロンプトを取得する。
     *
     * @param locale 現在の言語・地域情報
     * @return AI問題生成の共通プロンプト
     */
    public String getCommonPrompt(Locale locale) {

        return readPromptFile(COMMON_PROMPT_PATH, locale);
    }


    /**
     * 学習対象言語に対応するLanguage Profileを取得する。
     *
     * @param languageVariant 学習対象言語
     * @param locale 現在の言語・地域情報
     * @return 学習対象言語に対応するLanguage Profile
     */
    public String getLanguageProfile(
            LanguageVariant languageVariant,
            Locale locale) {

        // 中国大陸向けの言語別ルールを取得
        if (languageVariant == LanguageVariant.MAINLAND) {
            return readPromptFile(MAINLAND_PROFILE_PATH, locale);
        }

        // 台湾向けの言語別ルールを取得
        if (languageVariant == LanguageVariant.TAIWAN) {
            return readPromptFile(TAIWAN_PROFILE_PATH, locale);
        }

        // 未対応の言語区分の場合は例外をスロー
        throw new IllegalArgumentException(
                "Unsupported language variant: " + languageVariant);
    }
    
    /**
     * classpath上のプロンプトファイルをUTF-8で読み込む。
     *
     * @param path プロンプトファイルのパス
     * @param locale 現在の言語・地域情報
     * @return 読み込んだプロンプト
     */
    private String readPromptFile(
            String path,
            Locale locale) {

        // クラスパス上のプロンプトファイルを取得
        ClassPathResource resource =
                new ClassPathResource(path);

        // プロンプトファイルをUTF-8で読み込む
        try (InputStream inputStream = resource.getInputStream()) {
            
        	return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8);

        } catch (IOException e) {
            // ファイルの読み込みに失敗した場合は例外をスロー
            throw new IllegalStateException(
                    messageSource.getMessage(
                            "ai.prompt.error.read",
                            new Object[] { path },
                            locale),
                    e);
        }
    }
    
    /**
     * 学習対象言語に対応する発音表記生成用プロンプトを取得する。
     *
     * @param languageVariant 学習対象言語
     * @return 発音表記生成用プロンプト
     */
    public String getPronunciationPrompt(LanguageVariant languageVariant) {

        // 中国大陸向けの発音生成プロンプトを取得
        if (languageVariant == LanguageVariant.MAINLAND) {
            return loadPronunciationPrompt(MAINLAND_PRONUNCIATION_PATH);

        // 台湾向けの発音生成プロンプトを取得
        } else {
            return loadPronunciationPrompt(TAIWAN_PRONUNCIATION_PATH);
        }
    }
    
    /**
     * classpath上の発音表記生成用プロンプトファイルをUTF-8で読み込む。
     *
     * @param path プロンプトファイルのパス
     * @return 読み込んだ発音表記生成用プロンプト
     * @throws IllegalStateException プロンプトファイルの読み込みに失敗した場合
     */
    private String loadPronunciationPrompt(String path) {

        // クラスパス上の発音生成プロンプトファイルを取得
        ClassPathResource resource = new ClassPathResource(path);

        // 発音生成プロンプトファイルをUTF-8で読み込む
        try (InputStream inputStream =
                resource.getInputStream()) {
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8);

        } catch (IOException e) {
            // ファイルの読み込みに失敗した場合は例外をスロー
            throw new IllegalStateException(
                    "プロンプトファイルの読み込みに失敗しました: " + path,
                    e);
        }
    }
}
