package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;

/**
 * 管理者用問題一覧に表示する問題情報を保持するProjection DTO。
 */
public interface AdminQuestionListDto {

    Long getQuestionId();
    LanguageVariant getLanguageVariant();
    String getChineseText();
    String getJapaneseText();
    Difficulty getDifficulty();
    String getStructureName();
    boolean isAiGenerated();
    String getOwnerLoginId();
}
