package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiPracticeService {
	
	private final StructureRepository structureRepository;
	private final QuestionRepository questionRepository;
	private final SearchConditionConverter searchConditionConverter;
	
	public Long countAiGenerationSourceQuestions(long userId,
												 List<Difficulty> difficulties,
												 List<Evaluation> evaluations,
												 FavoriteCondition favoriteCondition,
												 List<Long> structureIds,
												 LanguageVariant languageVariant) {
		
		// 難易度
		if (difficulties == null || difficulties.isEmpty()) {
		difficulties = Arrays.asList(Difficulty.values());
		}
		
		// 理解度
		if (evaluations == null || evaluations.isEmpty()) {
		evaluations = Arrays.asList(Evaluation.values());
		}
		
		// お気に入り条件
		if (favoriteCondition == null) {
		favoriteCondition = FavoriteCondition.ALL;
		}
		
		// 文法・構造
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		return questionRepository.countAiGenerationSourceQuestions(
				userId,
				searchConditionConverter.convertDifficulty(difficulties),
				searchConditionConverter.convertEvaluation(evaluations),
				searchConditionConverter.convertFavoriteCondition(favoriteCondition),
				structureIds,
				languageVariant.name());
				
																	
	}

}
