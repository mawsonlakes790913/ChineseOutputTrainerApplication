package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.AiGenerationHistory;

@Repository
public interface AiGenerationHistoryRepository
        extends JpaRepository<AiGenerationHistory, Long> {

	List<AiGenerationHistory>
    findTop5ByUserIdAndQuestionQuestionIdOrderByCreatedAtDesc(
            Long userId,
            Long questionId);
}
