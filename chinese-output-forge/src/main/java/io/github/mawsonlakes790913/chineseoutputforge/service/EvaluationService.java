package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistoryKey;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 問題に対するユーザーの理解度に関する業務処理を行うService。
 * 学習履歴の作成および理解度の更新を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {
	
	private final StudyHistoryRepository studyHistoryRepository;
	private final QuestionRepository questionRepository;
	private final MessageSource messageSource;
	
	/**
	 * 指定した問題に対するユーザーの理解度を更新する。
	 * 学習履歴が存在しない場合は新しく作成する。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @param evaluation 更新する理解度
	 * @param locale 現在の言語・地域情報
	 */
	public void updateEvaluation(
	        Users user,
	        Long questionId,
	        Evaluation evaluation,
	        Locale locale) {

	    // 操作可能な問題か確認
	    if (!questionRepository.existsAccessibleQuestion(
	            questionId,
	            user.getId())) {
	        throw new IllegalArgumentException(
	                messageSource.getMessage(
	                        "question.error.accessDenied",
	                        null,
	                        locale));
	    }

	    // 複合キー情報を作成
	    StudyHistoryKey key = new StudyHistoryKey();
	    key.setUserId(user.getId());
	    key.setQuestionId(questionId);

	    // 既存の学習履歴を取得し、存在しない場合は新規作成
	    StudyHistory studyHistory =
	            studyHistoryRepository.findByStudyHistoryKey(key)
	                    .orElseGet(() -> {
	                        StudyHistory newStudyHistory = new StudyHistory();
	                        newStudyHistory.setStudyHistoryKey(key);
	                        return newStudyHistory;
	                    });

	    // 評価情報を更新
	    studyHistory.setEvaluation(evaluation);
	    studyHistory.setEvaluationUpdatedAt(LocalDateTime.now());

	    // 学習履歴を保存
	    studyHistoryRepository.save(studyHistory);

	    // 評価更新をログに記録
	    log.debug(
	            "評価更新 userId={}, questionId={}, evaluation={}",
	            user.getId(),
	            questionId,
	            evaluation);
	}
}