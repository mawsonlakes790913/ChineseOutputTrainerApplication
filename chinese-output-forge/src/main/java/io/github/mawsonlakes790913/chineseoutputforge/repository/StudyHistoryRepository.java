package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.StudyHistoryKey;

public interface StudyHistoryRepository extends JpaRepository<StudyHistory, StudyHistoryKey> {
	
	Optional<StudyHistory> findByStudyHistoryKey(StudyHistoryKey studyHistoryKey);
	
}	
