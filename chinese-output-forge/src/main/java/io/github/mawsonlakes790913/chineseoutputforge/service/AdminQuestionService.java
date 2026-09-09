package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AdminQuestionService {
	
	private final StructureRepository structureRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final QuestionRepository questionRepository;

	public Page<AdminQuestionListDto> getFilteredAdminQuestions(
	        List<Difficulty> difficulties,
	        QuestionSourceCondition sourceCondition,
	        List<Long> structureIds,
			List<LanguageVariant> languageVariants,
			String japaneseKeyword,
			String chineseKeyword,
			Pageable pageable) {
		
		// 言語未選択なら全言語
		if (languageVariants == null || languageVariants.isEmpty()) {
			languageVariants = Arrays.asList(LanguageVariant.values());
		}

	    // 難易度未選択なら全難易度
	    if (difficulties == null || difficulties.isEmpty()) {
	        difficulties = Arrays.asList(Difficulty.values());
	    }
	    
		// 文法・構造未選択ならすべて選択
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		// キーワード未記入なら空白
		if (japaneseKeyword == null) {
		japaneseKeyword = "";
		}
		
		if (chineseKeyword == null) {
		chineseKeyword = "";
		}
		
		// 問題の生成元が未指定の場合はすべて
		if (sourceCondition == null) {
		    sourceCondition = QuestionSourceCondition.ALL;
		}

	    return questionRepository.findFilteredAdminQuestionList(
	    		searchConditionConverter.convertDifficulty(difficulties),
	    		sourceCondition.name(),
	    		structureIds,
	    		searchConditionConverter.convertLanguageVariant(languageVariants),
	    		japaneseKeyword,
	    		chineseKeyword,
	            pageable);
	}
}
