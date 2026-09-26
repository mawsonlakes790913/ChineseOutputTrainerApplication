package io.github.mawsonlakes790913.chineseoutputforge.dto;

import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import lombok.Data;

/**
 * AIによる発音表記生成に使用するリクエスト情報を保持するDTO。
 */
@Data
public class AiPronunciationRequestDto {

	private final LanguageVariant languageVariant;
	private final String chineseText;
	private final String alternativeAnswer;
}
