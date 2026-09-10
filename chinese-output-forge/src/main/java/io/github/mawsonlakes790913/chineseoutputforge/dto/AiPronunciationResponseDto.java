package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

@Data
public class AiPronunciationResponseDto {
	
	private final String pinyin;
	private final String zhuyin;	
	private final String alternativeAnswerPinyin;
	private final String alternativeAnswerZhuyin;
	
}
