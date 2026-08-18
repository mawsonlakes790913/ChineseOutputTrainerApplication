package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;
import java.util.Optional;

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
	
	public void updateEvaluation(Users user, Long questionId, Evaluation evaluation) {
		
		// 複合キー情報を取得
		StudyHistoryKey key = new StudyHistoryKey();
		key.setUserId(user.getId());
		key.setQuestionId(questionId);
		
		// 存在確認とUPSDATE及びINSERT処理
		Optional<StudyHistory> optionalStudyHistory =
		        studyHistoryRepository.findByStudyHistoryKey(key);
		
		if (optionalStudyHistory.isPresent()) {
			//ここでUPDATE
		    StudyHistory studyHistory = optionalStudyHistory.get();
		    studyHistory.setEvaluation(evaluation);
		    studyHistory.setEvaluationUpdatedAt(LocalDateTime.now());

		    studyHistoryRepository.save(studyHistory);
		    
		    log.info("評価更新(UPDATE) userId={}, questionId={}, evaluation={}",
		            user.getId(), questionId, evaluation);
		    
		} else {
			//ここでUPDATE
		    // INSERT
		    StudyHistory studyHistory = new StudyHistory();
		    studyHistory.setStudyHistoryKey(key);
		    studyHistory.setEvaluation(evaluation);
		    studyHistory.setEvaluationUpdatedAt(LocalDateTime.now());

		    studyHistoryRepository.save(studyHistory);
		    
		    log.info("評価更新(INSERT) userId={}, questionId={}, evaluation={}",
		            user.getId(), questionId, evaluation);
		}
	}		
}