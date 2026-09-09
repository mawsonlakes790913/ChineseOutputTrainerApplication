package io.github.mawsonlakes790913.chineseoutputforge.form;

import org.hibernate.validator.constraints.Length;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionForm {
	
	private Long questionId;

    @NotNull
    private LanguageVariant languageVariant;

    @NotBlank
    @Length(max = 255)
    private String japaneseText;

    @NotBlank
    @Length(max = 255)
    private String chineseText;

    @Length(max = 255)
    private String alternativeAnswer;

    @NotBlank
    private String pinyin;

    @NotBlank
    private String zhuyin;

    @Length(max = 255)
    private String alternativeAnswerPinyin;

    @Length(max = 255)
    private String alternativeAnswerZhuyin;

    @NotNull
    private Difficulty difficulty;

    @NotNull
    private Long structureId;


    private boolean allowAiVariation;

    @Length(max = 255)
    private String template;
}