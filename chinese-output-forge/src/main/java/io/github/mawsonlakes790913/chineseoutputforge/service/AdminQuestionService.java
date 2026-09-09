package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
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

	public Page<AdminQuestionListDto> getFilteredAdminQuestions(
	        List<Difficulty> difficulties,
	        QuestionSourceCondition sourceCondition,
	        List<Long> structureIds,
			List<LanguageVariant> languageVariants,
			String japaneseKeyword,
			String chineseKeyword,
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

	    return questionRepository.findFilteredAdminQuestionList(
	    		searchConditionConverter.convertDifficulty(difficulties),
	    		sourceCondition.name(),
	    		structureIds,
	    		searchConditionConverter.convertLanguageVariant(languageVariants),
	    		japaneseKeyword,
	    		chineseKeyword,
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

    	// 文法コード以外をQuestionにSET
    	copyQuestionForm(question, form);
    	
    	// 文法コードをQuestionにSET
        Structure structure = structureRepository
                .findById(form.getStructureId())
                .orElseThrow();
        
        question.setStructure(structure);
    	
        // INSERT
    	Question savedQuestion = questionRepository.save(question);
    	
    	log.info("問題登録完了 questionId={}", savedQuestion.getQuestionId());
		
	}
	
	private void copyQuestionForm(Question question, QuestionForm form) {
		question.setLanguageVariant(form.getLanguageVariant());
		question.setJapaneseText(form.getJapaneseText());
		question.setChineseText(form.getChineseText());
		question.setAlternativeAnswer(form.getAlternativeAnswer());
		question.setPinyin(form.getPinyin());
		question.setZhuyin(form.getZhuyin());
		question.setAlternativeAnswerPinyin(form.getAlternativeAnswerPinyin());
		question.setAlternativeAnswerZhuyin(form.getAlternativeAnswerZhuyin());
		question.setDifficulty(form.getDifficulty());
		question.setAllowAiVariation(form.isAllowAiVariation());
		question.setTemplate(form.getTemplate());
	}
}
