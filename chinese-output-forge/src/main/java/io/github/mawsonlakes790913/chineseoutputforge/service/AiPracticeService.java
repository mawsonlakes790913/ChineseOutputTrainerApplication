package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGenerationSourceDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.TemporaryGeneratedQuestionListDto;
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
	private final OpenAIClient openAIClient;
	
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
		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = 
				generateQuestionsWithChatGPT(input, locale);
		
		// Geminiで生成(未実装)
//		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = 
//				generateQuestionsWithGemini(input, locale);
		
	}
	
	private TemporaryGeneratedQuestionListDto generateQuestionsWithChatGPT(
	        String input,
	        Locale locale) {
		
		// 3. APIリクエストを作成(ChatGPT)
		StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params =
		        ResponseCreateParams.builder()
		                .model(ChatModel.GPT_5_2)
		                .input(input)
		                .text(TemporaryGeneratedQuestionListDto.class)
		                .build();
		
		// 4. APIへリクエストを送信
		StructuredResponse<TemporaryGeneratedQuestionListDto> response =
		        openAIClient.responses().create(params);
		
		// 5. responseの中からAIが実際に生成した部分を取り出す
		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = null;

		// responseからStructured Outputsの生成結果を取得
		// responseの出力を順番に確認
		for (int i = 0; i < response.output().size(); i++) {

		    var output = response.output().get(i);

		    if (output.isMessage()) {

		        var message = output.asMessage();

		        // messageの中身を順番に確認
		        for (int j = 0; j < message.content().size(); j++) {

		            var content = message.content().get(j);

		            if (content.isOutputText()) {

		                // Structured Outputsによって
		                // TemporaryGeneratedQuestionListDtoへ変換済みの値を取得
		            	temporaryGeneratedQuestionListDto =
		            	        content.asOutputText();

		                break;
		            }
		        }
		    }

		    if (temporaryGeneratedQuestionListDto != null) {
		        break;
		    }
		}
		
		// AIの生成結果を取得できなかった場合はエラーを出す
		if (temporaryGeneratedQuestionListDto == null) {

		    throw new IllegalStateException(
		            messageSource.getMessage(
		                    "ai.generation.error.response",
		                    null,
		                    locale));
		}

	    return temporaryGeneratedQuestionListDto;
	}

}
