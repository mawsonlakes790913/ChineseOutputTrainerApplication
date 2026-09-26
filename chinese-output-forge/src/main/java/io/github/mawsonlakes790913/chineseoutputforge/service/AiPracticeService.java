package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Type;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AiProvider;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGenerationSourceDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.TemporaryGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.TemporaryGeneratedQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.AiGenerationHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI生成問題による学習に関する業務処理を行うService。
 * 生成元問題の取得、AIによる問題生成、生成履歴の更新、
 * 生成された問題の保存を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiPracticeService {
	
	private final StructureRepository structureRepository;
	private final QuestionRepository questionRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final AiPromptService aiPromptService;
	private final ObjectMapper objectMapper;
	private final MessageSource messageSource;
	private final OpenAIClient openAIClient;
	private final Client geminiClient;
	private final AiGenerationHistoryService aiGenerationHistoryService;
	private static final AiProvider AI_PROVIDER =
	        AiProvider.GEMINI;
	
	/**
	 * 指定された検索条件からAI生成元として利用可能な問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題数
	 */
	public Long countAiGenerationSourceQuestions(
	        long userId,
	        List<Difficulty> difficulties,
	        List<Evaluation> evaluations,
	        FavoriteCondition favoriteCondition,
	        List<Long> structureIds,
	        LanguageVariant languageVariant) {

		// 文法・構造未選択ならすべて選択
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

	/**
	 * 指定された検索条件からAI生成元として利用可能な問題を取得する。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題の一覧
	 */
	public List<Question> getAiGenerationSourceQuestions(
	        Long userId,
	        List<Difficulty> difficulties,
	        List<Evaluation> evaluations,
	        FavoriteCondition favoriteCondition,
	        List<Long> structureIds,
	        LanguageVariant languageVariant) {

		// 文法・構造未選択ならすべて選択
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
	
	/**
	 * 指定した問題リストのAI生成元として利用可能な問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題数
	 */
	public long countAiGenerationSourceQuestionsByList(
	        Long userId,
	        Long listId,
	        LanguageVariant languageVariant) {

	    return questionRepository.countAiGenerationSourceQuestionsByList(
	            userId,
	            listId,
	            languageVariant.name());
	}

	/**
	 * 指定した問題リストからAI生成元として利用可能な問題をすべて取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能なすべての問題
	 */
	public List<Question> getAiGenerationSourceQuestionsByList(
	        Long userId,
	        Long listId,
	        LanguageVariant languageVariant) {

	    return questionRepository.findAiGenerationSourceQuestionsByList(
	            userId,
	            listId,
	            languageVariant.name());
	}

	/**
	 * 指定した問題リストからAI生成元として利用可能な問題を
	 * ランダムに最大50件取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題の一覧
	 */
	public List<Question> getAiGenerationSourceQuestionsByListLimit50(
	        Long userId,
	        Long listId,
	        LanguageVariant languageVariant) {

	    return questionRepository.findAiGenerationSourceQuestionsByListLimit50(
	            userId,
	            listId,
	            languageVariant.name());
	}

	/**
	 * 指定した生成元問題をもとにAIで新しい問題を生成する。
	 * AIへの入力作成、問題生成、画面表示用DTOへの変換、
	 * AI生成履歴の更新を行う。
	 *
	 * @param user ユーザー
	 * @param sourceQuestions 生成元問題の一覧
	 * @param languageVariant 学習対象言語
	 * @param locale 現在の言語・地域情報
	 * @return AIによって生成された問題の一覧
	 */
	public List<AiGeneratedQuestionDto> generateQuestions(
	        Users user,
	        List<Question> sourceQuestions,
	        LanguageVariant languageVariant,
	        Locale locale) {

	    // AI問題生成の処理時間計測を開始
	    long startTime = System.currentTimeMillis();

	    // AI問題生成の共通ルールを取得
	    String commonPrompt = aiPromptService.getCommonPrompt(locale);

	    // 言語別ルールを取得
	    String languageProfile = aiPromptService.getLanguageProfile(
	            languageVariant,
	            locale);

	    // 生成元問題をAI送信用DTOへ変換
	    List<AiGenerationSourceDto> generationSources = createGenerationSources(
	            user,
	            sourceQuestions);

	    // 生成元問題をJSON形式に変換
	    String generationSourcesJson = convertGenerationSourcesToJson(
	            generationSources,
	            locale);

	    // AIへ送信する入力を作成
	    String input =
	            commonPrompt
	            + "\n\n"
	            + languageProfile
	            + "\n\n"
	            + "## 生成元問題\n"
	            + generationSourcesJson;

	    // 入力作成完了時刻を記録
	    long inputCompletedTime = System.currentTimeMillis();

	    // 設定されたAIプロバイダーで問題を生成
	    TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto =
	            switch (AI_PROVIDER) {
	                case CHAT_GPT ->
	                        generateQuestionsWithChatGPT(
	                                input,
	                                locale);

	                case GEMINI ->
	                        generateQuestionsWithGemini(
	                                input,
	                                locale);
	            };

	    // AI API処理完了時刻を記録
	    long apiCompletedTime = System.currentTimeMillis();

	    // 画面表示用DTOへ変換
	    List<AiGeneratedQuestionDto> generatedQuestions =
	            convertToGeneratedQuestions(
	                    temporaryGeneratedQuestionListDto,
	                    sourceQuestions);

	    // AI生成履歴を更新
	    updateGenerationHistory(
	            user,
	            sourceQuestions,
	            generatedQuestions);

	    // DTO変換・履歴更新完了時刻を記録
	    long conversionCompletedTime = System.currentTimeMillis();

	    // 処理時間をログに記録
	    log.debug("AI生成 入力作成時間: {} ms", inputCompletedTime - startTime);
	    log.debug("AI生成 API処理時間: {} ms", apiCompletedTime - inputCompletedTime);
	    log.debug("AI生成 DTO変換・履歴更新時間: {} ms", conversionCompletedTime - apiCompletedTime);
	    log.debug("AI生成 合計処理時間: {} ms", conversionCompletedTime - startTime);

	    return generatedQuestions;
	}
	
	/**
	 * AIによって生成された問題をユーザーの問題として保存する。
	 * 同じユーザーが同じ中国語文を保存済みの場合は保存しない。
	 *
	 * @param user ユーザー
	 * @param aiGeneratedQuestionDto 保存するAI生成問題
	 * @param locale 現在の言語・地域情報
	 * @return 保存された問題
	 */
	public Question saveGeneratedQuestion(
			Users user,
			AiGeneratedQuestionDto aiGeneratedQuestionDto,
			Locale locale
			) {

		// 同じユーザーが同じ中国語文をすでに保存している場合は保存しない
		Optional<Question> existingQuestion =
		        questionRepository.findByOwnerIdAndChineseText(
		                user.getId(),
		                aiGeneratedQuestionDto.getChineseText());

		if (existingQuestion.isPresent()) {
		    throw new IllegalStateException(
		            messageSource.getMessage(
		                    "aiPractice.save.error.duplicate",
		                    null,
		                    locale));
		}

		// 生成元問題を取得
		Question sourceQuestion = questionRepository
		        .findById(aiGeneratedQuestionDto.getSourceQuestionId())
		        .orElseThrow();

		// 保存するAI生成問題を作成して問題情報を設定
		Question savedQuestion = new Question();
		savedQuestion.setLanguageVariant(sourceQuestion.getLanguageVariant());
		savedQuestion.setJapaneseText(aiGeneratedQuestionDto.getJapaneseText());
		savedQuestion.setChineseText(aiGeneratedQuestionDto.getChineseText());
		savedQuestion.setPinyin(aiGeneratedQuestionDto.getPinyin());
		savedQuestion.setZhuyin(aiGeneratedQuestionDto.getZhuyin());
		savedQuestion.setStructure(sourceQuestion.getStructure());
		savedQuestion.setDifficulty(sourceQuestion.getDifficulty());
		savedQuestion.setAllowAiVariation(false);
		savedQuestion.setAiGenerated(true);
		savedQuestion.setOwner(user);

		// AI生成問題を保存
		Question savedQuestionResult = questionRepository.save(savedQuestion);

		// AI生成問題の保存完了をログに記録
		log.info(
		        "AI生成問題保存完了 userId={}, questionId={}, sourceQuestionId={}",
		        user.getId(),
		        savedQuestionResult.getQuestionId(),
		        sourceQuestion.getQuestionId());

		return savedQuestionResult;
	}
	
	/**
	 * 指定したユーザーが保存した中国語文に対応する問題IDを取得する。
	 *
	 * @param userId ユーザーID
	 * @param chineseText 中国語文
	 * @return 保存済みの問題ID。保存されていない場合はnull
	 */
	public Long getSavedQuestionId(
	        Long userId,
	        String chineseText) {

	    // ユーザーが保存した同じ中国語文の問題を取得
	    Optional<Question> savedQuestion =
	            questionRepository.findByOwnerIdAndChineseText(
	                    userId,
	                    chineseText);

	    // 保存済みの場合は問題IDを返す
	    if (savedQuestion.isPresent()) {
	        return savedQuestion.get().getQuestionId();
	    }

	    // 保存されていない場合はnullを返す
	    return null;
	}
	
	/**
	 * 生成元問題と過去のAI生成履歴からAI送信用DTOを作成する。
	 *
	 * @param user ユーザー
	 * @param sourceQuestions 生成元問題の一覧
	 * @return AI送信用の生成元問題DTOの一覧
	 */
	private List<AiGenerationSourceDto> createGenerationSources(
	        Users user,
	        List<Question> sourceQuestions) {

	    // AI送信用DTOの一覧を作成
	    List<AiGenerationSourceDto> generationSources = new ArrayList<>();

	    for (int i = 0; i < sourceQuestions.size(); i++) {

	        // 生成元問題を取得
	        Question sourceQuestion = sourceQuestions.get(i);

	        // ログインユーザーの生成元問題に対する直近10件のAI生成履歴を取得
	        List<AiGenerationHistory> aiGenerationHistories =
	                aiGenerationHistoryService
	                        .getGenerationHistories(
	                                user.getId(),
	                                sourceQuestion.getQuestionId());

	        // 生成元問題をAI送信用DTOへ設定
	        AiGenerationSourceDto source = new AiGenerationSourceDto();
	        source.setSourceIndex(i);
	        source.setJapaneseText(sourceQuestion.getJapaneseText());
	        source.setChineseText(sourceQuestion.getChineseText());
	        source.setTemplate(sourceQuestion.getTemplate());

	        // AI生成履歴から生成済みの中国語文を取得
	        List<String> chineseListHistory = new ArrayList<>();
	        for (int j = 0; j < aiGenerationHistories.size(); j++) {
	            String chineseTextHistory =
	                    aiGenerationHistories.get(j).getChineseText();
	            chineseListHistory.add(chineseTextHistory);
	        }

	        // 生成元問題に過去のAI生成文を設定
	        source.setGenerationHistory(chineseListHistory);

	        // AI送信用DTOの一覧に追加
	        generationSources.add(source);
	    }

	    return generationSources;
	}
	
	/**
	 * AI送信用の生成元問題DTOをJSON形式に変換する。
	 *
	 * @param generationSources AI送信用の生成元問題DTOの一覧
	 * @param locale 現在の言語・地域情報
	 * @return JSON形式に変換した生成元問題
	 */
	private String convertGenerationSourcesToJson(
	        List<AiGenerationSourceDto> generationSources,
	        Locale locale) {

	    // AI送信用DTOの一覧をJSON形式に変換
	    String generationSourcesJson;
	    try {
	        generationSourcesJson =
	                objectMapper.writeValueAsString(
	                        generationSources);

	    } catch (JsonProcessingException e) {
	        // JSON変換に失敗した場合は例外をスロー
	        throw new IllegalStateException(
	                messageSource.getMessage(
	                        "ai.generation.error.json",
	                        null,
	                        locale),
	                e);
	    }

	    return generationSourcesJson;
	}

	/**
	 * ChatGPT APIを使用して問題を生成する。
	 *
	 * @param input AIへ送信する入力
	 * @param locale 現在の言語・地域情報
	 * @return AIによって生成された問題
	 */
	private TemporaryGeneratedQuestionListDto generateQuestionsWithChatGPT(
	        String input,
	        Locale locale) {

	    // ChatGPTへのAPIリクエストを作成
	    StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params =
	            ResponseCreateParams.builder()
	                    .model(ChatModel.GPT_5_2)
	                    .input(input)
	                    .text(TemporaryGeneratedQuestionListDto.class)
	                    .build();

	    // APIへリクエストを送信
	    StructuredResponse<TemporaryGeneratedQuestionListDto> response =
	            openAIClient.responses().create(params);

	    // Structured Outputsの生成結果を取得
	    TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto =
	            null;
	    for (int i = 0; i < response.output().size(); i++) {
	        var output =
	                response.output().get(i);

	        // Message形式の出力を取得
	        if (output.isMessage()) {
	            var message = output.asMessage();

	            // Messageから生成されたテキストを取得
	            for (int j = 0; j < message.content().size(); j++) {
	                var content = message.content().get(j);
	                if (content.isOutputText()) {
	                    temporaryGeneratedQuestionListDto = content.asOutputText();
	                    break;
	                }
	            }
	        }

	        // 生成結果を取得できた場合は検索を終了
	        if (temporaryGeneratedQuestionListDto != null) {
	            break;
	        }
	    }

	    // AIの生成結果を取得できなかった場合は例外をスロー
	    if (temporaryGeneratedQuestionListDto == null) {
	        throw new IllegalStateException(
	                messageSource.getMessage(
	                        "ai.generation.error.response",
	                        null,
	                        locale));
	    }

	    return temporaryGeneratedQuestionListDto;
	}

	/**
	 * Gemini APIを使用して問題を生成する。
	 *
	 * @param input AIへ送信する入力
	 * @param locale 現在の言語・地域情報
	 * @return AIによって生成された問題
	 */
	private TemporaryGeneratedQuestionListDto generateQuestionsWithGemini(
	        String input,
	        Locale locale) {

	    // Geminiのレスポンス形式を定義するSchemaを作成
	    Schema responseSchema = createGeminiResponseSchema();

	    // Thinking LevelをLOWに設定
	    ThinkingConfig thinkingConfig =
	            ThinkingConfig.builder()
	                    .thinkingLevel(ThinkingLevel.Known.LOW)
	                    .build();

	    // GeminiへのAPIリクエスト設定を作成
	    GenerateContentConfig config =
	            GenerateContentConfig.builder()
	                    .responseMimeType("application/json")
	                    .responseSchema(responseSchema)
	                    .thinkingConfig(thinkingConfig)
	                    .build();

	    // APIへリクエストを送信
	    GenerateContentResponse response =
	            geminiClient.models.generateContent(
	                    "gemini-3.7-flash",
	                    input,
	                    config);

	    // AIが生成したJSONを取得
	    String responseJson = response.text();

	    // 生成されたJSONをDTOへ変換
	    TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;
	    try {
	        temporaryGeneratedQuestionListDto =
	                objectMapper.readValue(
	                        responseJson,
	                        TemporaryGeneratedQuestionListDto.class);

	    } catch (JsonProcessingException e) {
	        // JSONからDTOへの変換に失敗した場合は例外をスロー
	        throw new IllegalStateException(
	                messageSource.getMessage(
	                        "ai.generation.error.response",
	                        null,
	                        locale),
	                e);
	    }

	    return temporaryGeneratedQuestionListDto;
	}
	
	/**
	 * Geminiから受け取るAI生成問題のJSON Schemaを作成する。
	 *
	 * @return AI生成問題のレスポンスSchema
	 */
	private Schema createGeminiResponseSchema() {
		
	    // 1問分のStructured OutputsのJSON Schemaを作成
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

	    // レスポンス全体の出力形式を定義
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
	    
	    return responseSchema;
		
	}

	/**
	 * AIから受け取った生成結果を画面表示用DTOへ変換する。
	 *
	 * @param temporaryGeneratedQuestionListDto AIから受け取った生成結果
	 * @param sourceQuestions 生成元問題の一覧
	 * @return 画面表示用のAI生成問題一覧
	 */
	private List<AiGeneratedQuestionDto> convertToGeneratedQuestions(
	        TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto,
	        List<Question> sourceQuestions) {

	    // AIから生成された問題を取得
	    List<TemporaryGeneratedQuestionDto> temporaryGeneratedQuestionDtos =
	            temporaryGeneratedQuestionListDto.getQuestions();

	    // 最終的なAI生成問題を格納するListを作成
	    List<AiGeneratedQuestionDto> generatedQuestions = new ArrayList<>();
	    
	    // AIから返された生成問題を順番に処理
	    for (int i = 0;
	            i < temporaryGeneratedQuestionDtos.size();
	            i++) {

	        TemporaryGeneratedQuestionDto temporaryGeneratedQuestionDto =
	                temporaryGeneratedQuestionDtos.get(i);

	        // sourceIndexを基に生成元問題を取得
	        Question sourceQuestion =
	                sourceQuestions.get(
	                        temporaryGeneratedQuestionDto.getSourceIndex());

	        // 最終的なAI生成問題DTOを作成
	        AiGeneratedQuestionDto generatedQuestion =
	        		createGeneratedQuestion(
	        				temporaryGeneratedQuestionDto,
	        				sourceQuestion);

	        generatedQuestions.add(generatedQuestion);
	    }

	    return generatedQuestions;
	}
	
	/**
	 * AIから受け取った1問分の生成結果と生成元問題から
	 * 画面表示用のAI生成問題DTOを作成する。
	 *
	 * @param temporaryGeneratedQuestionDto AIから受け取った1問分の生成結果
	 * @param sourceQuestion 生成元問題
	 * @return 画面表示用のAI生成問題DTO
	 */
	private AiGeneratedQuestionDto createGeneratedQuestion(
	        TemporaryGeneratedQuestionDto temporaryGeneratedQuestionDto,
	        Question sourceQuestion) {

	    // 生成元問題とAI生成結果を画面表示用DTOへ設定
	    AiGeneratedQuestionDto generatedQuestion = new AiGeneratedQuestionDto();
	    generatedQuestion.setSourceQuestionId(sourceQuestion.getQuestionId());
	    generatedQuestion.setSourceJapaneseText(sourceQuestion.getJapaneseText());
	    generatedQuestion.setSourceChineseText(sourceQuestion.getChineseText());
	    generatedQuestion.setJapaneseText(temporaryGeneratedQuestionDto.getJapaneseText());
	    generatedQuestion.setChineseText(temporaryGeneratedQuestionDto.getChineseText());
	    generatedQuestion.setPinyin(temporaryGeneratedQuestionDto.getPinyin());
	    generatedQuestion.setZhuyin(temporaryGeneratedQuestionDto.getZhuyin());
	    generatedQuestion.setDifficulty(sourceQuestion.getDifficulty());

	    return generatedQuestion;
	}

	/**
	 * AIによって生成された各問題の生成履歴を更新する。
	 *
	 * @param user ユーザー
	 * @param sourceQuestions 生成元問題の一覧
	 * @param generatedQuestions AIによって生成された問題の一覧
	 */
	private void updateGenerationHistory(
	        Users user,
	        List<Question> sourceQuestions,
	        List<AiGeneratedQuestionDto> generatedQuestions) {

	    // AI生成問題を1件ずつ処理
	    for (int i = 0; i < generatedQuestions.size(); i++) {
	        AiGeneratedQuestionDto generatedQuestion =
	                generatedQuestions.get(i);

	        // sourceQuestionIdに対応する生成元問題を取得
	        Question sourceQuestion = null;
	        for (int j = 0; j < sourceQuestions.size(); j++) {
	            Question question = sourceQuestions.get(j);

	            if (question.getQuestionId().equals(generatedQuestion.getSourceQuestionId())) {
	                sourceQuestion = question;
	                break;
	            }
	        }

	        // 対応する生成元問題が存在しない場合は処理をスキップ
	        if (sourceQuestion == null) {
	            continue;
	        }

	        // AI生成履歴を更新
	        aiGenerationHistoryService.updateGenerationHistory(
	                user,
	                sourceQuestion,
	                generatedQuestion.getChineseText());
	    }
	}

}
