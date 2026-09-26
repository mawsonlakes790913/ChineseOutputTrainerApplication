package io.github.mawsonlakes790913.chineseoutputforge.dto;

import java.util.List;

import lombok.Data;

/**
 * AIによって一時的に生成された問題の一覧を保持するDTO。
 */
@Data
public class TemporaryGeneratedQuestionListDto {

    private List<TemporaryGeneratedQuestionDto> questions;
}
