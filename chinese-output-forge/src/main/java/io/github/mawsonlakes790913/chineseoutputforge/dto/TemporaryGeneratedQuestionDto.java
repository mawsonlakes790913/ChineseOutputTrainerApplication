package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

/**
 * AIによって一時的に生成された問題情報を保持するDTO。
 */
@Data
public class TemporaryGeneratedQuestionDto {

	private int sourceIndex;
	private String japaneseText;
	private String chineseText;
	private String pinyin;
	private String zhuyin;
}
