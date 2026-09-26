package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import lombok.Data;

/**
 * 管理者用問題編集画面で使用する変更前の問題情報を保持するDTO。
 */
@Data
public class OriginalQuestionDTO {

    private LanguageVariant languageVariant;
    private String japaneseText;
    private String chineseText;
    private String alternativeAnswer;
    private String pinyin;
    private String zhuyin;
    private String alternativeAnswerPinyin;
    private String alternativeAnswerZhuyin;
    private Difficulty difficulty;
    private long structureId;
    private String structureName;
    private boolean allowAiVariation;
    private String template;
    private boolean aiGenerated;
    private String ownerLoginId;
}
