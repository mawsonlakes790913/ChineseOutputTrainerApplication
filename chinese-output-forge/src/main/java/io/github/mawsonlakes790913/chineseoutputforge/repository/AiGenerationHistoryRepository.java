package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.AiGenerationHistory;

/**
 * AI問題生成履歴のDB操作を行うRepository。
 */
@Repository
public interface AiGenerationHistoryRepository
        extends JpaRepository<AiGenerationHistory, Long> {

    /**
     * 指定したユーザー・問題のAI生成履歴を新しい順に最大10件取得する。
     *
     * @param userId ユーザーID
     * @param questionId 問題ID
     * @return AI生成履歴の一覧
     */
    List<AiGenerationHistory>
    findTop10ByUserIdAndQuestionQuestionIdOrderByCreatedAtDesc(
            Long userId,
            Long questionId);

    /**
     * 指定したユーザーのAI生成履歴をすべて削除する。
     *
     * @param userId ユーザーID
     */
    @Modifying
    @Query(value = """
            DELETE FROM ai_generation_history
            WHERE user_id = :userId
            """, nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}
