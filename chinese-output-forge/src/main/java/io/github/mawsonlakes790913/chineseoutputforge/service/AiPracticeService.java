package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
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
import io.github.mawsonlakes790913.chineseoutputforge.dto.TemporaryGeneratedQuestionDto;
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
	private final Client geminiClient;
	
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
		
		// 使用するAIを一時的に切り替える
		boolean useChatGPT = true;

		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;

		if (useChatGPT) {

		    temporaryGeneratedQuestionListDto =
		            generateQuestionsWithChatGPT(
		                    input,
		                    locale);

		} else {

		    temporaryGeneratedQuestionListDto =
		            generateQuestionsWithGemini(
		                    input,
		                    locale);
		}
		
		// 画面表示用DTOへ変換
		List<AiGeneratedQuestionDto> generatedQuestions =
		        convertToGeneratedQuestions(
		                temporaryGeneratedQuestionListDto,
		                sourceQuestions);

		return generatedQuestions;		
		
		
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
	
	private TemporaryGeneratedQuestionListDto generateQuestionsWithGemini(
	        String input,
	        Locale locale) {
		
		// 3. APIリクエストを作成(Gemini)
		// 1問分の出力形式を定義する
		// Structured OutputsのJSON Schemaを作成
		Schema questionSchema =
		        Schema.builder()
		                .type(Type.Known.OBJECT)
		                .properties(Map.of(
		                        "sourceIndex",
		                        Schema.builder()
		                                .type(Type.Known.INTEGER)
		                                .build(),
		                        "japaneseText",
		                        Schema.builder()
		                                .type(Type.Known.STRING)
		                                .build(),
		                        "chineseText",
		                        Schema.builder()
		                                .type(Type.Known.STRING)
		                                .build(),
		                        "pinyin",
		                        Schema.builder()
		                                .type(Type.Known.STRING)
		                                .build(),
		                        "zhuyin",
		                        Schema.builder()
		                                .type(Type.Known.STRING)
		                                .build()
		                ))
		                .required(List.of(
		                        "sourceIndex",
		                        "japaneseText",
		                        "chineseText",
		                        "pinyin",
		                        "zhuyin"))
		                .build();

		// レスポンス全体の出力形式を定義する

		Schema responseSchema =
		        Schema.builder()
		                .type(Type.Known.OBJECT)
		                .properties(Map.of(
		                        "questions",
		                        Schema.builder()
		                                .type(Type.Known.ARRAY)
		                                .items(questionSchema)
		                                .build()
		                ))
		                .required(List.of("questions"))
		                .build();


				// APIリクエストを作成(Gemini)
				GenerateContentConfig config =
				        GenerateContentConfig.builder()
				                .responseMimeType("application/json")
				                .responseSchema(responseSchema)
				                .build();

		// 4. APIへリクエストを送信
		GenerateContentResponse response =
		        geminiClient.models.generateContent(
		                "gemini-3.7-flash",
		                input,
		                config);

		// 5. AIが生成したJSONを取得
		String responseJson =
		        response.text();

		// JSONをDTOへ変換
		TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;

		try {
		    temporaryGeneratedQuestionListDto =
		            objectMapper.readValue(
		                    responseJson,
		                    TemporaryGeneratedQuestionListDto.class);

		} catch (JsonProcessingException e) {
		    throw new IllegalStateException(
		            messageSource.getMessage(
		                    "ai.generation.error.response",
		                    null,
		                    locale),
		            e);
		}
		
		return temporaryGeneratedQuestionListDto;
		
	}
	
	private List<AiGeneratedQuestionDto> convertToGeneratedQuestions(
	        TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto,
	        List<Question> sourceQuestions) {

		// DTOの中から生成された複数の問題を取得
	    List<TemporaryGeneratedQuestionDto> temporaryGeneratedQuestionDtos =
	            temporaryGeneratedQuestionListDto.getQuestions();

	 // 最終的なAI生成問題を格納するListを作成
	    List<AiGeneratedQuestionDto> generatedQuestions =
	            new ArrayList<>();

	 // AIが生成した問題を順番に処理
	    for (int i = 0; i < temporaryGeneratedQuestionDtos.size(); i++) {

	        TemporaryGeneratedQuestionDto temporaryGeneratedQuestionDto =
	                temporaryGeneratedQuestionDtos.get(i);

	        // sourceIndexを基に生成元問題を取得
	        Question sourceQuestion =
	                sourceQuestions.get(
	                        temporaryGeneratedQuestionDto.getSourceIndex());

	        // 最終的なAI生成問題DTOを作成
	        AiGeneratedQuestionDto generatedQuestion =
	                new AiGeneratedQuestionDto();

	        generatedQuestion.setSourceQuestionId(
	                sourceQuestion.getQuestionId());
	        
	        generatedQuestion.setSourceJapaneseText(
	                sourceQuestion.getJapaneseText());

	        generatedQuestion.setSourceChineseText(
	                sourceQuestion.getChineseText());

	        generatedQuestion.setJapaneseText(
	                temporaryGeneratedQuestionDto.getJapaneseText());

	        generatedQuestion.setChineseText(
	                temporaryGeneratedQuestionDto.getChineseText());

	        generatedQuestion.setPinyin(
	                temporaryGeneratedQuestionDto.getPinyin());

	        generatedQuestion.setZhuyin(
	                temporaryGeneratedQuestionDto.getZhuyin());

	        generatedQuestion.setDifficulty(
	                sourceQuestion.getDifficulty());

	        // Listへ追加
	        generatedQuestions.add(generatedQuestion);
	    }

	    return generatedQuestions;
	}

}
