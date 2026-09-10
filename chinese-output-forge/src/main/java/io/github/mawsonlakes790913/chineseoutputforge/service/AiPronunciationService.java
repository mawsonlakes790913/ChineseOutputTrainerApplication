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

@Service
@RequiredArgsConstructor
public class AiPronunciationService {
	
	private final AiPromptService aiPromptService;
	private final ObjectMapper objectMapper;
	private final Client geminiClient;

	public AiPronunciationResponseDto generatePronunciation(AiPronunciationRequestDto request) {
		
	    // 言語別ルールを取得
	    String pronunciationPrompt =
	            aiPromptService.getPronunciationPrompt(request.getLanguageVariant());
	    
	    // DTOをJSON形式に変換
	    String requestJson;
	    try {

	    	requestJson =
	                objectMapper.writeValueAsString(
	                		request);

	    } catch (JsonProcessingException e) {

	        throw new IllegalStateException(
	                "発音情報生成リクエストのJSON変換に失敗しました。",
	                e);
	    }
	    
	    // AIへ送信する入力を作成
	    String input =
	    		pronunciationPrompt
	            + "\n\n"
	            + "## 中国語及び別解\n"
	            + requestJson;
	    
	    return generatePronunciationWithGemini(input);
	}
	
	private AiPronunciationResponseDto generatePronunciationWithGemini(String input) {
		
		// 発音情報の出力形式を定義
		Schema responseSchema =
		        Schema.builder()
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

		// Thinking LevelをLOWに設定
		ThinkingConfig thinkingConfig =
		        ThinkingConfig.builder()
		                .thinkingLevel(ThinkingLevel.Known.LOW)
		                .build();

		// APIリクエストを作成(Gemini)
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
	    String responseJson =
	            response.text();
	    
	    // JSONをDTOへ変換
	    AiPronunciationResponseDto aiPronunciationResponseDto;
	    
	    try {

	    	aiPronunciationResponseDto =
	                objectMapper.readValue(
	                        responseJson,
	                        AiPronunciationResponseDto.class);

	    } catch (JsonProcessingException e) {

	    	throw new IllegalStateException(
	    	        "発音情報の生成結果を取得できませんでした。",
	    	        e);
	    }

	    return aiPronunciationResponseDto;
	    
	}
}
