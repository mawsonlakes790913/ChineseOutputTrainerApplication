package io.github.mawsonlakes790913.chineseoutputforge.service;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PracticeSearchCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.NewPracticeCountDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import io.github.mawsonlakes790913.chineseoutputforge.value.Range;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PracticeService {
	
	private final QuestionRepository questionRepository;
	private final SearchConditionConverter searchConditionConverter;
	
	// 非ログインユーザー用問題数取得
	public List<Question> getPracticeQuestions(LanguageVariant languageVariant,
				Difficulty difficulty,
				int start,
				boolean random){

		int offset = start - 1;
		
		List<Question> extractedQuestions = questionRepository.findQuestionsByLanguageVariantAndDifficulty(
		languageVariant.name(),
		difficulty.name(),
		offset
		);
		
		// シャッフルする
		if (random) {
		Collections.shuffle(extractedQuestions);
		} 
		
		
		return extractedQuestions;
	}	
	
	// ログインユーザー用問題数取得
	public List<Question> getAvailablePracticeQuestions(
			Long userId,
			LanguageVariant languageVariant,
			Difficulty difficulty,
			PracticeSearchCondition searchCondition,
			int start,
			boolean random){

	int offset = start - 1;
	
	List<Question> extractedQuestions = questionRepository.findAvailableQuestionsByUserIdAndLanguageVariantAndDifficulty(
	userId,
	languageVariant.name(),
	difficulty.name(),
	searchCondition.name(),
	offset
	);
	
	// シャッフルする
	if (random) {
	Collections.shuffle(extractedQuestions);
	} 
	
	
	return extractedQuestions;
}	
	
	// 問題数取得
	public PracticeMenuDto countPracticeQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        PracticeSearchCondition searchCondition) {
		
	    // 条件が未指定の場合はすべて
	    if (searchCondition == null) {
	        searchCondition = PracticeSearchCondition.ALL;
	    }

	    PracticeMenuDto count = new PracticeMenuDto();

	    // 初級
	    long beginnerCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.BEGINNER,
	            searchCondition
	    );

	    count.setBeginnerCount(beginnerCount);
	    count.setBeginnerRanges(createRanges(beginnerCount));

	    // 中級
	    long intermediateCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.INTERMEDIATE,
	            searchCondition
	    );

	    count.setIntermediateCount(intermediateCount);
	    count.setIntermediateRanges(createRanges(intermediateCount));

	    // 上級
	    long advancedCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.ADVANCED,
	            searchCondition
	    );

	    count.setAdvancedCount(advancedCount);
	    count.setAdvancedRanges(createRanges(advancedCount));

	    return count;
	}
	
	public NewPracticeCountDto countNewPracticeQuestions(
			Long userId,
			LanguageVariant languageVariant) {
		
		NewPracticeCountDto count = new NewPracticeCountDto();
		
	    long beginnerCount = questionRepository.countUnlearnedQuestions(
	    		userId, 
	    		languageVariant.name(),
	    		Difficulty.BEGINNER.name());
	    count.setBeginnerCount(beginnerCount);	    
	    
	    long intermediateCount = questionRepository.countUnlearnedQuestions(
	    		userId, 
	    		languageVariant.name(),
	    		Difficulty.INTERMEDIATE.name());
	    count.setIntermediateCount(intermediateCount);

	    long advancedCount = questionRepository.countUnlearnedQuestions(
	    		userId, 
	    		languageVariant.name(),
	    		Difficulty.ADVANCED.name());
	    count.setAdvancedCount(advancedCount);
		
		return count;
	}

	private List<Range> createRanges(long count) {
		List<Range> ranges = new ArrayList<>();

		for (long start = 1; start <= count; start += 50) {

		    if (start + 49 <= count) {
		        ranges.add(new Range(start, start + 49));
		    } else {
		        ranges.add(new Range(start, count));
		    }
		}

		return ranges;
	}	
	
	public List<Question> getNewQuestions(
			Long userId, 
			LanguageVariant languageVariant,
			List<Difficulty> difficulty) {
		
		List<Question> extractedNewQuestions = questionRepository.findUnlearnedQuestionsByUserIdAndDifficulty(
				userId, 
				languageVariant.name(),
				searchConditionConverter.convertDifficulty(difficulty));
		
		return extractedNewQuestions;
	}
	
	private long countQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        Difficulty difficulty,
	        PracticeSearchCondition practiceSearchCondition) {

	    // 非ログイン
	    if (userId == null) {
	        return questionRepository.countByLanguageVariantAndDifficulty(
	                languageVariant.name(),
	                difficulty.name()
	        );
	    }
	    
	    return questionRepository.countPracticeQuestions(
	            userId,
	            languageVariant.name(),
	            difficulty.name(),
	            practiceSearchCondition.name()
	    );
	}
	
}
