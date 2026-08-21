package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;

@Component
public class SearchConditionConverter {

	//List<Difficulty>をList<String>に変換
	public List<String> convertDifficulty(List<Difficulty> difficulties) {

		List<String> difficultyList;
		
	    // 難易度
	    if (difficulties == null || difficulties.isEmpty()) {

	        difficultyList = List.of(
	                Difficulty.BEGINNER.name(),
	                Difficulty.INTERMEDIATE.name(),
	                Difficulty.ADVANCED.name());

	    } else {

	        difficultyList = new ArrayList<>();

	        for (Difficulty difficulty : difficulties) {
	            difficultyList.add(difficulty.name());
	        }
	    }
	    
	    return difficultyList;
	    
	}
}
