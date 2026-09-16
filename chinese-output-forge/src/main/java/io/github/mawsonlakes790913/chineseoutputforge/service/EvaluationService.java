package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistoryKey;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {
	
	private final StudyHistoryRepository studyHistoryRepository;
	
	public void updateEvaluation(
	        Users user,
	        Long questionId,
	        Evaluation evaluation) {

	    // 複合キー情報を作成
	    StudyHistoryKey key =
	            new StudyHistoryKey();

	    key.setUserId(user.getId());
	    key.setQuestionId(questionId);

	    // 既存の学習履歴を取得し、存在しない場合は新規作成
	    StudyHistory studyHistory =
	            studyHistoryRepository.findByStudyHistoryKey(key)
	                    .orElseGet(() -> {

	                        StudyHistory newStudyHistory =
	                                new StudyHistory();

	                        newStudyHistory.setStudyHistoryKey(key);

	                        return newStudyHistory;
	                    });

	    // 評価情報を更新
	    studyHistory.setEvaluation(evaluation);
	    studyHistory.setEvaluationUpdatedAt(
	            LocalDateTime.now());

	    studyHistoryRepository.save(studyHistory);

	    log.debug(
	            "評価更新 userId={}, questionId={}, evaluation={}",
	            user.getId(),
	            questionId,
	            evaluation);
	}	
}