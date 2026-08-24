package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class ReviewService {
	
	private final StudyHistoryRepository studyHistoryRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final QuestionRepository questionRepository;
	
	//復習出題数取得
	public long countReviewQuestions(Long userId, List<Evaluation> evaluations, 
												  List<Difficulty> difficulties,
												  FavoriteCondition favoriteCondition,
												  List<String> structures) {
		
		// ここで変換する
		List<String> convertedDifficulties = searchConditionConverter.convertDifficulty(difficulties);
		List<String> convertedEvaluations = searchConditionConverter.convertEvaluation(evaluations);
		String convertedFavoriteCondition = searchConditionConverter.convertFavoriteCondition(favoriteCondition);
		
	    return studyHistoryRepository.countReviewQuestions(
	    		userId,
	    		convertedEvaluations,
	    		convertedDifficulties,
	    		convertedFavoriteCondition,
	    		structures
	    		);
	}
	
	//問題取得
	public List<Question> getQuestion(Long userId, 
									  List<Evaluation> evaluations, 
									  List<Difficulty> difficulties,
									  FavoriteCondition favoriteCondition,
									  boolean random){
		
		List<Question> extractedQuestions = studyHistoryRepository.findReviewQuestions(userId,
				searchConditionConverter.convertEvaluation(evaluations),
				searchConditionConverter.convertDifficulty(difficulties),
				searchConditionConverter.convertFavoriteCondition(favoriteCondition));
		
		// シャッフルする
		if (random) {
			Collections.shuffle(extractedQuestions);
		} 
		
		return extractedQuestions;
	}
	
	// structure全件取得
	public List<String> findStructures() {
	    return questionRepository.findDistinctStructures();
	}

}
