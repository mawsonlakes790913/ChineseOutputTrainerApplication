package io.github.mawsonlakes790913.chineseoutputforge.dto;

/**
 * 問題リストの情報と指定した問題の登録状態を保持するProjection DTO。
 */
public interface QuestionListSelectionDto {

    Long getListId();
    String getListName();
    Boolean getRegistered();
}
