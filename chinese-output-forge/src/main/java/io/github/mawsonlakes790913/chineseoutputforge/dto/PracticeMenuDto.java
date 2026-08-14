package io.github.mawsonlakes790913.chineseoutputforge.dto;

import java.util.List;

import io.github.mawsonlakes790913.chineseoutputforge.value.Range;
import lombok.Data;

@Data
public class PracticeMenuDto {
	
    private long beginnerCount;
    private List<Range> beginnerRanges;
    private long intermediateCount;
    private List<Range> intermediateRanges;
    private long advancedCount;
    private List<Range> advancedRanges;
    
}
