package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
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
			 List<Long> structureIds,
			 List<LanguageVariant> languageVariants,
			 String japaneseKeyword,
			 String chineseKeyword,
			 Pageable pageable) {
	
	// 初期状態の値を決める
//	// 普通話・國語
//	if (languageVariants == null || languageVariants.isEmpty()) {
//	    languageVariants = Arrays.asList(languageVariant);
//	}	
	
	// 難易度
	if (difficulties == null || difficulties.isEmpty()) {
	difficulties = Arrays.asList(Difficulty.values());
	}
	
	// 理解度
	if (evaluations == null || evaluations.isEmpty()) {
	evaluations = Arrays.asList(Evaluation.values());
	}
	
	// 学習条件
	if (studyCondition == null) {
	studyCondition = StudyCondition.ALL;
	}
	
	// お気に入り条件
	if (favoriteCondition == null) {
	favoriteCondition = FavoriteCondition.ALL;
	}
	
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
	
	return questionRepository.findFilteredUserQuestionList(
	userId,
	convertedDifficulties,
	convertedEvaluations,
	convertedStudyCondition,
	convertedFavoriteCondition,
	structureIds,
	convertedLanguageVariants,
	japaneseKeyword,
	chineseKeyword,
	pageable);
	}		

}
