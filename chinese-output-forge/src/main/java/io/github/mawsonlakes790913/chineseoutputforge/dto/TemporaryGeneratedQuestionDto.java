package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

@Data
public class TemporaryGeneratedQuestionDto {

	private int sourceIndex;
	
//    private int candidateIndex;

	private String japaneseText;

	private String chineseText;

	private String pinyin;

	private String zhuyin;
}
