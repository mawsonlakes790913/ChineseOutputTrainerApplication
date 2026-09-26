package io.github.mawsonlakes790913.chineseoutputforge.dto;

import lombok.Data;

/**
 * 通常学習の未学習問題数を難易度別に保持するDTO。
 */
@Data
public class NewPracticeCountDto {
	
    private long beginnerCount;
    private long intermediateCount;
    private long advancedCount;
}
