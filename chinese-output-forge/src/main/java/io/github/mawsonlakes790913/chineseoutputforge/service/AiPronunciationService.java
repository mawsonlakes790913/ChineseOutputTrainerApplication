package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;
import java.util.Map;

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

import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationRequestDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationResponseDto;
import lombok.RequiredArgsConstructor;

/**
 * AIを使用した中国語の発音表記生成に関する業務処理を行うService。
 * 中国語文から拼音・注音の発音表記を生成する。
 */
@Service
@RequiredArgsConstructor
public class AiPronunciationService {
	
	private final AiPromptService aiPromptService;
	private final ObjectMapper objectMapper;
	private final Client geminiClient;

	/**
	 * 指定された中国語文の発音表記をAIで生成する。
	 * 学習対象言語に対応するプロンプトとリクエスト情報からAIへの入力を作成し、
	 * 拼音・注音の発音表記を取得する。
	 *
	 * @param request 発音表記生成に使用するリクエスト情報
	 * @return AIによって生成された発音表記
	 */
	public AiPronunciationResponseDto generatePronunciation(
	        AiPronunciationRequestDto request) {

	    // 学習対象言語に対応する発音表記生成用プロンプトを取得
	    String pronunciationPrompt =
	            aiPromptService.getPronunciationPrompt(request.getLanguageVariant());

	    // リクエストDTOをJSON形式に変換
	    String requestJson;
	    try {
	        requestJson =
	                objectMapper.writeValueAsString(
	                        request);

	    } catch (JsonProcessingException e) {
	        // JSON変換に失敗した場合は例外をスロー
	        throw new IllegalStateException("発音情報生成リクエストのJSON変換に失敗しました。", e);
	    }

	    // AIへ送信する入力を作成
	    String input =
	            pronunciationPrompt
	            + "\n\n"
	            + "## 中国語及び別解\n"
	            + requestJson;

	    // Geminiで発音表記を生成
	    return generatePronunciationWithGemini(input);
	}
	
	/**
	 * Gemini APIを使用して発音表記を生成する。
	 *
	 * @param input AIへ送信する入力
	 * @return AIによって生成された発音表記
	 */
	private AiPronunciationResponseDto generatePronunciationWithGemini(String input) {

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
	    GenerateContentResponse response = geminiClient.models.generateContent(
	                    "gemini-3.7-flash",
	                    input,
	                    config);

	    // AIが生成したJSONを取得
	    String responseJson = response.text();

	    // 生成されたJSONを発音表記DTOへ変換
	    AiPronunciationResponseDto aiPronunciationResponseDto;
	    try {
	        aiPronunciationResponseDto = objectMapper.readValue(
	                        responseJson,
	                        AiPronunciationResponseDto.class);

	    } catch (JsonProcessingException e) {
	        // JSONからDTOへの変換に失敗した場合は例外をスロー
	        throw new IllegalStateException("発音情報の生成結果を取得できませんでした。", e);
	    }

	    return aiPronunciationResponseDto;
	}
	
	/**
	 * Geminiから受け取る発音表記のJSON Schemaを作成する。
	 *
	 * @return 発音表記生成結果のレスポンスSchema
	 */
	private Schema createGeminiResponseSchema() {

	    return Schema.builder()
	            .type(Type.Known.OBJECT)
	            .properties(Map.of(
	                    "pinyin",
	                    Schema.builder()
	                            .type(Type.Known.STRING)
	                            .build(),
	                    "zhuyin",
	                    Schema.builder()
	                            .type(Type.Known.STRING)
	                            .build(),
	                    "alternativeAnswerPinyin",
	                    Schema.builder()
	                            .type(Type.Known.STRING)
	                            .nullable(true)
	                            .build(),
	                    "alternativeAnswerZhuyin",
	                    Schema.builder()
	                            .type(Type.Known.STRING)
	                            .nullable(true)
	                            .build()
	            ))
	            .required(List.of(
	                    "pinyin",
	                    "zhuyin",
	                    "alternativeAnswerPinyin",
	                    "alternativeAnswerZhuyin"))
	            .build();
	}
}
