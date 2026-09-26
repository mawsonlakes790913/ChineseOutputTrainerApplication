package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;

/**
 * 問題リストに登録されている問題の表示情報を保持するProjection DTO。
 */
public interface QuestionListItemDto {

    Long getQuestionId();
    String getChineseText();
    String getJapaneseText();
    Difficulty getDifficulty();
    Evaluation getEvaluation();
    boolean isFavorite();
    boolean isAiGenerated();
    String getPinyin();
    String getZhuyin();
    String getAlternativeAnswer();
    String getAlternativeAnswerPinyin();
    String getAlternativeAnswerZhuyin();
    String getStructureName();
}
