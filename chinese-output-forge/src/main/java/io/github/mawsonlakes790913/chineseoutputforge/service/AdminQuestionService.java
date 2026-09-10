package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AdminQuestionSortCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationRequestDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiPronunciationResponseDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.OriginalQuestionDTO;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;
import io.github.mawsonlakes790913.chineseoutputforge.form.QuestionForm;
import io.github.mawsonlakes790913.chineseoutputforge.repository.FavoriteRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class AdminQuestionService {
	
	private final StructureRepository structureRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final QuestionRepository questionRepository;
	private final FavoriteRepository favoriteRepository;
	private final StudyHistoryRepository studyHistoryRepository;
	private final AiPronunciationService aiPronunciationService;


	public Page<AdminQuestionListDto> getFilteredAdminQuestions(
	        List<Difficulty> difficulties,
	        QuestionSourceCondition sourceCondition,
	        List<Long> structureIds,
			List<LanguageVariant> languageVariants,
			String japaneseKeyword,
			String chineseKeyword,
			AdminQuestionSortCondition sortCondition,
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
		
		// 並び順が未指定の場合は最終更新日時の降順
		if (sortCondition == null) {
		    sortCondition = AdminQuestionSortCondition.UPDATED_DESC;
		}

	    return questionRepository.findFilteredAdminQuestionList(
	    		searchConditionConverter.convertDifficulty(difficulties),
	    		sourceCondition.name(),
	    		structureIds,
	    		searchConditionConverter.convertLanguageVariant(languageVariants),
	    		japaneseKeyword,
	    		chineseKeyword,
	    	    sortCondition.name(),
	            pageable);
	}
	
	@Transactional
	public void deleteOneQuestion(Long questionId) {

	    favoriteRepository.deleteByQuestionQuestionId(questionId);
	    studyHistoryRepository.deleteByStudyHistoryKeyQuestionId(questionId);
	    questionRepository.deleteById(questionId);

	    log.info("問題削除 questionId={}", questionId);
	}
	
	public void addQuestion(QuestionForm form) {
		
    	Question question = new Question();
    	
        // 発音生成用DTOを作成
        AiPronunciationRequestDto request =
                new AiPronunciationRequestDto(
                        form.getLanguageVariant(),
                        form.getChineseText(),
                        form.getAlternativeAnswer()
                );

        // AIで発音情報を生成
        AiPronunciationResponseDto pronunciation =
                aiPronunciationService.generatePronunciation(request);

    	// 文法コード以外をQuestionにSET
    	copyQuestionForm(question, form, pronunciation);
    	
    	// 文法コードをQuestionにSET
        Structure structure = structureRepository
                .findById(form.getStructureId())
                .orElseThrow();
        
        question.setStructure(structure);
    	
        // INSERT
    	Question savedQuestion = questionRepository.save(question);
    	
    	log.info("問題登録完了 questionId={}", savedQuestion.getQuestionId());
		
	}
	
	public void updateOneQuestion(
			long questionId, 
			QuestionForm form
			) {
		
		Question question = questionRepository.findById(questionId)
		        .orElseThrow(() ->
		                new IllegalArgumentException("Question not found."));
		
		log.info("問題更新前 {}", question);
		
        // 発音生成用DTOを作成
        AiPronunciationRequestDto request =
                new AiPronunciationRequestDto(
                        form.getLanguageVariant(),
                        form.getChineseText(),
                        form.getAlternativeAnswer()
                );

        // AIで発音情報を生成
        AiPronunciationResponseDto pronunciation =
                aiPronunciationService.generatePronunciation(request);
        
    	// 文法コード以外をQuestionにSET
		copyQuestionForm(question, form, pronunciation);
		
    	// 文法コードをQuestionにSET
        Structure structure = structureRepository
                .findById(form.getStructureId())
                .orElseThrow();
        
        question.setStructure(structure);

        // UPDATE
		questionRepository.save(question);

		log.info("問題更新後 {}", question);
	}
	
	public OriginalQuestionDTO getOriginalQuestion(long questionId) {

	    Question question = questionRepository.findById(questionId)
	            .orElseThrow(() ->
	                    new IllegalArgumentException("Question not found."));

	    OriginalQuestionDTO dto = new OriginalQuestionDTO();

	    dto.setLanguageVariant(question.getLanguageVariant());
	    dto.setJapaneseText(question.getJapaneseText());
	    dto.setChineseText(question.getChineseText());
	    dto.setAlternativeAnswer(question.getAlternativeAnswer());

	    dto.setPinyin(question.getPinyin());
	    dto.setZhuyin(question.getZhuyin());
	    dto.setAlternativeAnswerPinyin(
	            question.getAlternativeAnswerPinyin());
	    dto.setAlternativeAnswerZhuyin(
	            question.getAlternativeAnswerZhuyin());

	    dto.setDifficulty(question.getDifficulty());
	    dto.setStructureId(
	            question.getStructure().getStructureId());
	    dto.setStructureName(
	            question.getStructure().getName());
	    dto.setAllowAiVariation(
	            question.getAllowAiVariation());
	    dto.setTemplate(question.getTemplate());
	    dto.setAiGenerated(question.isAiGenerated());
	    if (question.getOwner() != null) {
	        dto.setOwnerLoginId(
	                question.getOwner().getLoginId());
	    }

	    return dto;
	}
	
	private void copyQuestionForm(
			Question question, 
			QuestionForm form,
			AiPronunciationResponseDto pronunciation) {
		question.setLanguageVariant(form.getLanguageVariant());
		question.setJapaneseText(form.getJapaneseText());
		question.setChineseText(form.getChineseText());
		question.setAlternativeAnswer(form.getAlternativeAnswer());
		question.setPinyin(pronunciation.getPinyin());
		question.setZhuyin(pronunciation.getZhuyin());
	    question.setAlternativeAnswerPinyin(
	            pronunciation.getAlternativeAnswerPinyin());
	    question.setAlternativeAnswerZhuyin(
	            pronunciation.getAlternativeAnswerZhuyin());
	    question.setDifficulty(form.getDifficulty());
		question.setAllowAiVariation(form.isAllowAiVariation());
		question.setTemplate(form.getTemplate());
	}
}
