package io.github.mawsonlakes790913.chineseoutputforge.dto;

import java.util.List;

import io.github.mawsonlakes790913.chineseoutputforge.constant.SubjectType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.VerbVariation;
import lombok.Data;

@Data
public class AiGenerationSourceDto {

    private int sourceIndex;
    
    private String japaneseText;

    private String chineseText;

    private String template;

    private SubjectType subjectType;

    private VerbVariation verbVariation;
    
    private List<String> generationHistory;
}
