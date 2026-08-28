package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class ReviewService {
	
	private final StudyHistoryRepository studyHistoryRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final StructureRepository structureRepository;
	
	//復習出題数取得
	public long countReviewQuestions(Long userId, List<LanguageVariant> languageVariants,
												  List<Evaluation> evaluations, 
												  List<Difficulty> difficulties,
												  FavoriteCondition favoriteCondition,
												  List<Long> structureIds) {
		
		// ここで変換する
		List<String> convertedLanguageVariants = searchConditionConverter.convertLanguageVariant(languageVariants);
		List<String> convertedDifficulties = searchConditionConverter.convertDifficulty(difficulties);
		List<String> convertedEvaluations = searchConditionConverter.convertEvaluation(evaluations);
		String convertedFavoriteCondition = searchConditionConverter.convertFavoriteCondition(favoriteCondition);
		
	    return studyHistoryRepository.countReviewQuestions(
	    		userId,
	    		convertedLanguageVariants,
	    		convertedEvaluations,
	    		convertedDifficulties,
	    		convertedFavoriteCondition,
	    		structureIds
	    		);
	}
	
	//問題取得
	public List<Question> getQuestion(Long userId, 
									  List<LanguageVariant> languageVariants,
									  List<Evaluation> evaluations, 
									  List<Difficulty> difficulties,
									  FavoriteCondition favoriteCondition,
									  List<Long> structureIds,
									  boolean random){
		
		List<Question> extractedQuestions = studyHistoryRepository.findReviewQuestions(userId,
				searchConditionConverter.convertLanguageVariant(languageVariants),
				searchConditionConverter.convertEvaluation(evaluations),
				searchConditionConverter.convertDifficulty(difficulties),
				searchConditionConverter.convertFavoriteCondition(favoriteCondition),
				structureIds);
		
		// シャッフルする
		if (random) {
			Collections.shuffle(extractedQuestions);
		} 
		
		return extractedQuestions;
	}
	
	// structure全件取得
	public List<Structure> findStructures() {
	    return structureRepository.findAll();
	}

}
