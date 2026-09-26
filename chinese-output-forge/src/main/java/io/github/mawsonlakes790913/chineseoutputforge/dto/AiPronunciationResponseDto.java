package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

/**
 * AIによって生成された発音表記を保持するレスポンスDTO。
 */
@Data
public class AiPronunciationResponseDto {
	
	private final String pinyin;
	private final String zhuyin;	
	private final String alternativeAnswerPinyin;
	private final String alternativeAnswerZhuyin;
}
