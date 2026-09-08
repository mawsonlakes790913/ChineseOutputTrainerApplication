package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.StudyCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserQuestionService {
	
	private final SearchConditionConverter searchConditionConverter;
	private final QuestionRepository questionRepository;
	private final StructureRepository structureRepository;
	
	public Page<UserQuestionListDto> getFilteredUserQuestionList(long userId,
			 List<Difficulty> difficulties,
			 List<Evaluation> evaluations,
			 StudyCondition studyCondition,
			 FavoriteCondition favoriteCondition,
			 QuestionSourceCondition sourceCondition,
			 List<Long> structureIds,
			 List<LanguageVariant> languageVariants,
			 String japaneseKeyword,
			 String chineseKeyword,
			 Pageable pageable) {
	
	// 文法・構造
	if (structureIds == null || structureIds.isEmpty()) {
		structureIds = structureRepository.findAllStructureIds();
	}
	
	// キーワード
	if (japaneseKeyword == null) {
	japaneseKeyword = "";
	}
	
	if (chineseKeyword == null) {
	chineseKeyword = "";
	}
	
	// ここで変換する
	List<String> convertedDifficulties = searchConditionConverter.convertDifficulty(difficulties);
	List<String> convertedEvaluations = searchConditionConverter.convertEvaluation(evaluations);
	String convertedStudyCondition = searchConditionConverter.convertStudyCondition(studyCondition);
	String convertedFavoriteCondition = searchConditionConverter.convertFavoriteCondition(favoriteCondition);
	List<String> convertedLanguageVariants =
	        searchConditionConverter.convertLanguageVariant(languageVariants);
	
	// 問題の生成元が未指定の場合はすべて
	if (sourceCondition == null) {
	    sourceCondition = QuestionSourceCondition.ALL;
	}
	
	return questionRepository.findFilteredUserQuestionList(
	userId,
	convertedDifficulties,
	convertedEvaluations,
	convertedStudyCondition,
	convertedFavoriteCondition,
	sourceCondition.name(),
	structureIds,
	convertedLanguageVariants,
	japaneseKeyword,
	chineseKeyword,
	pageable);
	}		

}
