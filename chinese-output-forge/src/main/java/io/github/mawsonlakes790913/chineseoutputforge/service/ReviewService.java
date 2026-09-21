package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.ReviewQuestionLimit;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
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
												  QuestionSourceCondition sourceCondition,
												  List<Long> structureIds) {
		
		// 文法・構造が未指定の場合はすべて
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		// 出題元が未指定の場合はすべて
		if (sourceCondition == null) {
		    sourceCondition = QuestionSourceCondition.ALL;
		}
		
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
	    		sourceCondition.name(),
	    		structureIds
	    		);
	}
	
	//問題取得
	public List<Question> getQuestion(Long userId, 
									  List<LanguageVariant> languageVariants,
									  List<Evaluation> evaluations, 
									  List<Difficulty> difficulties,
									  FavoriteCondition favoriteCondition,
									  QuestionSourceCondition sourceCondition,
									  List<Long> structureIds,
									  ReviewQuestionLimit questionLimit,
									  boolean random){
		
		// 文法・構造が未指定の場合はすべて
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		// 出題元が未指定の場合はすべて
		if (sourceCondition == null) {
		    sourceCondition = QuestionSourceCondition.ALL;
		}
		
	    // 出題数が未指定の場合は最大50問
	    if (questionLimit == null) {
	        questionLimit = ReviewQuestionLimit.LIMIT_50;
	    }
	    
	    List<String> convertedLanguageVariants =
	            searchConditionConverter.convertLanguageVariant(languageVariants);

	    List<String> convertedEvaluations =
	            searchConditionConverter.convertEvaluation(evaluations);

	    List<String> convertedDifficulties =
	            searchConditionConverter.convertDifficulty(difficulties);

	    String convertedFavoriteCondition =
	            searchConditionConverter.convertFavoriteCondition(favoriteCondition);
	    
	    List<Question> extractedQuestions;
		
	    // 出題数によって取得方法を切り替える
	    if (questionLimit == ReviewQuestionLimit.ALL) {

	        extractedQuestions =
	                studyHistoryRepository.findAllReviewQuestions(
	                        userId,
	                        convertedLanguageVariants,
	                        convertedEvaluations,
	                        convertedDifficulties,
	                        convertedFavoriteCondition,
	                        sourceCondition.name(),
	                        structureIds);

	    } else {

	        extractedQuestions =
	                studyHistoryRepository.findReviewQuestions(
	                        userId,
	                        convertedLanguageVariants,
	                        convertedEvaluations,
	                        convertedDifficulties,
	                        convertedFavoriteCondition,
	                        sourceCondition.name(),
	                        structureIds);
	    }
		
	    // 必要に応じて取得した問題をシャッフル
	    if (random) {
	        Collections.shuffle(extractedQuestions);
	    }

	    return extractedQuestions;
	}

}
