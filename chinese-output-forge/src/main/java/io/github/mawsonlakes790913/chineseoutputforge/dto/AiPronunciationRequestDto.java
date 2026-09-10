package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import lombok.Data;

@Data
public class AiPronunciationRequestDto {

	private final LanguageVariant languageVariant;
	private final String chineseText;
	private final String alternativeAnswer;
}
