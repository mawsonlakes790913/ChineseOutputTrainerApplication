package io.github.mawsonlakes790913.chineseoutputforge.dto;

import java.util.List;

import lombok.Data;

/**
 * AI問題生成に使用する生成元問題の情報を保持するDTO。
 */
@Data
public class AiGenerationSourceDto {

    private int sourceIndex;
    private String japaneseText;
    private String chineseText;
    private String template;
    private List<String> generationHistory;
}
