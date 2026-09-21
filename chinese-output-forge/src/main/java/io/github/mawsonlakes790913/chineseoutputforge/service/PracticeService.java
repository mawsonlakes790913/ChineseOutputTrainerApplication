package io.github.mawsonlakes790913.chineseoutputforge.service;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
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
	private static final int PRACTICE_RANGE_SIZE = 50;
	
	// 非ログインユーザー用問題数取得
	// 通常学習の問題を取得
	public List<Question> getPracticeQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        Difficulty difficulty,
	        QuestionSourceCondition sourceCondition,
	        int start,
	        boolean random) {

	    int offset = start - 1;

	    List<Question> extractedQuestions;

	    // 非ログイン
	    if (userId == null) {

	        extractedQuestions =
	                questionRepository.findAnonymousPracticeQuestions(
	                        languageVariant.name(),
	                        difficulty.name(),
	                        offset);

	    } else {

	        // 条件が未指定の場合はすべて
	        if (sourceCondition == null) {
	            sourceCondition = QuestionSourceCondition.ALL;
	        }

	        extractedQuestions =
	                questionRepository.findPracticeQuestions(
	                        userId,
	                        languageVariant.name(),
	                        difficulty.name(),
	                        sourceCondition.name(),
	                        offset);
	    }

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
	        QuestionSourceCondition sourceCondition) {
		
	    // 条件が未指定の場合はすべて
	    if (sourceCondition == null) {
	    	sourceCondition = QuestionSourceCondition.ALL;
	    }

	    PracticeMenuDto count = new PracticeMenuDto();

	    // 初級
	    long beginnerCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.BEGINNER,
	            sourceCondition
	    );

	    count.setBeginnerCount(beginnerCount);
	    count.setBeginnerRanges(createRanges(beginnerCount));

	    // 中級
	    long intermediateCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.INTERMEDIATE,
	            sourceCondition
	    );

	    count.setIntermediateCount(intermediateCount);
	    count.setIntermediateRanges(createRanges(intermediateCount));

	    // 上級
	    long advancedCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.ADVANCED,
	            sourceCondition
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

		for (long start = 1; start <= count; start += PRACTICE_RANGE_SIZE) {

		    if (start + (PRACTICE_RANGE_SIZE - 1) <= count) {
		        ranges.add(new Range(start, start + (PRACTICE_RANGE_SIZE - 1)));
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
		
		   return questionRepository
		            .findUnlearnedQuestions(
		                    userId,
		                    languageVariant.name(),
		                    searchConditionConverter.convertDifficulty(difficulty));
		}
	
	private long countQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        Difficulty difficulty,
	        QuestionSourceCondition sourceCondition) {

	    // 非ログイン
	    if (userId == null) {
	        return questionRepository.countAnonymousPracticeQuestions(
	                languageVariant.name(),
	                difficulty.name()
	        );
	    }
	    
	    return questionRepository.countPracticeQuestions(
	            userId,
	            languageVariant.name(),
	            difficulty.name(),
	            sourceCondition.name()
	    );
	}
	
}
