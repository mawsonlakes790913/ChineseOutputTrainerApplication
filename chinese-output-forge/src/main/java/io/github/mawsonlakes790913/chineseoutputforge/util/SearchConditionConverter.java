package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;

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
	
	public List<String> convertEvaluation(List<Evaluation> evaluations) {
		
		List<String> evaluationList;
		
		if (evaluations == null || evaluations.isEmpty()) {

	        evaluationList = List.of(
	                Evaluation.HARD.name(),
	                Evaluation.GOOD.name(),
	                Evaluation.EASY.name());

	    } else {

	        evaluationList = new ArrayList<>();

	        for (Evaluation evaluation : evaluations) {
	            evaluationList.add(evaluation.name());
	        }
	   
	    }
	        
		return evaluationList;
		
	}
	
	public String convertFavoriteCondition(FavoriteCondition favoriteCondition) {
		
		String convertedFavoriteCondition;
		
		if (favoriteCondition == null) {
			
			convertedFavoriteCondition = FavoriteCondition.ALL.name();
			
		} else {
			
			convertedFavoriteCondition = favoriteCondition.name();
					
		}
		
		return convertedFavoriteCondition;
		
	}
}
