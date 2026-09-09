package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;

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
