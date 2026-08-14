package io.github.mawsonlakes790913.chineseoutputforge.service;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.value.Range;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PracticeService {
	
	private final QuestionRepository questionRepository;
	
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
	
	public PracticeMenuDto countPracticeQuestions(LanguageVariant languageVariant) {

		PracticeMenuDto count = new PracticeMenuDto();

		// 初級
		long beginnerCount =
				questionRepository.countByLanguageVariantAndDifficulty(
						languageVariant,
						Difficulty.BEGINNER
				);

		count.setBeginnerCount(beginnerCount);
		count.setBeginnerRanges(createRanges(beginnerCount));


		// 中級
		long intermediateCount =
				questionRepository.countByLanguageVariantAndDifficulty(
						languageVariant,
						Difficulty.INTERMEDIATE
				);

		count.setIntermediateCount(intermediateCount);
		count.setIntermediateRanges(createRanges(intermediateCount));


		// 上級
		long advancedCount =
				questionRepository.countByLanguageVariantAndDifficulty(
						languageVariant,
						Difficulty.ADVANCED
				);

		count.setAdvancedCount(advancedCount);
		count.setAdvancedRanges(createRanges(advancedCount));

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
	
	
	
}
