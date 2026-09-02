package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import lombok.Data;

@Data
public class AiGeneratedQuestionDto {

    private Long sourceQuestionId;
    
    private String sourceJapaneseText;

    private String sourceChineseText;

    private String japaneseText;

    private String chineseText;

    private String pinyin;

    private String zhuyin;
    
    private Difficulty difficulty;
    
}
