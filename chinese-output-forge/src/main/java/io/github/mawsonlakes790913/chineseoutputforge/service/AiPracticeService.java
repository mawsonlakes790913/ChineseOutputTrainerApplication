package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGenerationSourceDto;
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
	private final AiPromptService aiPromptService;
	private final ObjectMapper objectMapper;
	private final MessageSource messageSource;
	
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
	
	public List<AiGeneratedQuestionDto> generateQuestions(
	        List<Question> sourceQuestions,
	        LanguageVariant languageVariant,
	        Locale locale) {
		
		// AI問題生成の共通ルールを取得
		String commonPrompt =
		        aiPromptService.getCommonPrompt(locale);
		
		// 言語別ルールを取得
		String languageProfile =
		        aiPromptService.getLanguageProfile(languageVariant, locale);
		
		// ソース問題を取得
		List<AiGenerationSourceDto> generationSources = new ArrayList<>();
		
		for(int i = 0; i < sourceQuestions.size(); i++) {
			Question sourceQuestion = sourceQuestions.get(i);
	        AiGenerationSourceDto source =
	                new AiGenerationSourceDto();
			source.setSourceIndex(i);
			source.setJapaneseText(sourceQuestion.getJapaneseText());
			source.setChineseText(sourceQuestion.getChineseText());
			source.setTemplate(sourceQuestion.getTemplate());
			source.setSubjectType(
			        sourceQuestion.getSubjectType());

			source.setVerbVariation(
			        sourceQuestion.getVerbVariation());
			
			generationSources.add(source);
		}
		
		// 生成元問題をJSON形式に変換
		String generationSourcesJson;

		try {

		    generationSourcesJson =
		            objectMapper.writeValueAsString(
		                    generationSources);

		} catch (JsonProcessingException e) {

		    throw new IllegalStateException(
		            messageSource.getMessage(
		                    "ai.generation.error.json",
		                    null,
		                    locale),
		            e);
		}
		
		// AIへ送信する入力を作成
		String input =
		        commonPrompt
		        + "\n\n"
		        + languageProfile
		        + "\n\n"
		        + "## 生成元問題\n"
		        + generationSourcesJson;
		
		// ChatGPTで生成(未実装)
//		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = 
//				generateQuestionsWithChatGPT(input, locale);
		
		// Geminiで生成(未実装)
//		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = 
//				generateQuestionsWithGemini(input, locale);
		
	}

}
