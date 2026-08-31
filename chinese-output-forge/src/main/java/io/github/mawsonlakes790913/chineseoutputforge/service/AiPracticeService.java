package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
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
	
	public List<Question> getQuestion(
			 Long userId,
			 List<Difficulty> difficulties,
			 List<Evaluation> evaluations,
			 FavoriteCondition favoriteCondition,
			 List<Long> structureIds,
			 LanguageVariant languageVariant) {
		
		// 文法・構造
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		return questionRepository.findAiGenerationSourceQuestions(
				userId,
				searchConditionConverter.convertDifficulty(difficulties),
				searchConditionConverter.convertEvaluation(evaluations),
				searchConditionConverter.convertFavoriteCondition(favoriteCondition),
				structureIds,
				languageVariant.name());
		
	}
	


}
