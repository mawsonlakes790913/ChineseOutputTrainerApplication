package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.AiGenerationHistory;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.AiGenerationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AIによって生成された問題文の生成履歴を管理するService。
 * ユーザーと生成元問題ごとの履歴の取得・更新・保存を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationHistoryService {

    private final AiGenerationHistoryRepository aiGenerationHistoryRepository;

    private static final int MAX_HISTORY_SIZE = 10;

    /**
     * 指定したユーザーと生成元問題に紐づくAI生成履歴を取得する。
     *
     * @param userId ユーザーID
     * @param questionId 生成元問題ID
     * @return AI生成履歴の一覧
     */
    public List<AiGenerationHistory> getGenerationHistories(
            Long userId,
            Long questionId) {

        return aiGenerationHistoryRepository
                .findTop10ByUserIdAndQuestionQuestionIdOrderByCreatedAtDesc(
                        userId,
                        questionId);
    }

    /**
     * 指定したユーザーと生成元問題に紐づくAI生成履歴を更新する。
     * 履歴が上限に達している場合は最も古い履歴を削除し、
     * 今回生成された中国語文を新しい履歴として保存する。
     *
     * @param user ユーザー
     * @param question 生成元問題
     * @param chineseText AIによって生成された中国語文
     */
    @Transactional
    public void updateGenerationHistory(
            Users user,
            Question question,
            String chineseText) {

        // ユーザーと生成元問題に紐づく履歴を取得
        List<AiGenerationHistory> histories =
                getGenerationHistories(
                        user.getId(),
                        question.getQuestionId());

        // 上限に達している場合は最も古い履歴を削除
        if (histories.size() >= MAX_HISTORY_SIZE) {
            AiGenerationHistory oldestHistory = histories.get(histories.size() - 1);
            aiGenerationHistoryRepository.delete(oldestHistory);

            // 履歴削除をログに記録
            log.debug(
                    "AI生成履歴を削除しました。userId={}, questionId={}, chineseText={}",
                    user.getId(),
                    question.getQuestionId(),
                    oldestHistory.getChineseText());
        }

        // 今回生成された中国語文を履歴に保存
        saveGenerationHistory(
                user,
                question,
                chineseText);
    }

    /**
     * AIによって生成された中国語文を生成履歴として保存する。
     *
     * @param user ユーザー
     * @param question 生成元問題
     * @param chineseText AIによって生成された中国語文
     */
    private void saveGenerationHistory(
            Users user,
            Question question,
            String chineseText) {

        // AI生成履歴を作成して生成情報を設定
        AiGenerationHistory aiGenerationHistory = new AiGenerationHistory();
        aiGenerationHistory.setUser(user);
        aiGenerationHistory.setQuestion(question);
        aiGenerationHistory.setChineseText(chineseText);
        aiGenerationHistory.setCreatedAt(LocalDateTime.now());

        // AI生成履歴を保存
        aiGenerationHistoryRepository.save(aiGenerationHistory);

        // 履歴保存をログに記録
        log.debug(
                "AI生成履歴を保存しました。userId={}, questionId={}, chineseText={}",
                user.getId(),
                question.getQuestionId(),
                chineseText);
    }
}
