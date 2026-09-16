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

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationHistoryService {

    private final AiGenerationHistoryRepository aiGenerationHistoryRepository;
    private static final int MAX_HISTORY_SIZE = 10;

 // ユーザーと生成元問題に紐づくAI生成履歴を取得
    public List<AiGenerationHistory> getGenerationHistories(
            Long userId,
            Long questionId) {

        return aiGenerationHistoryRepository
                .findTop10ByUserIdAndQuestionQuestionIdOrderByCreatedAtDesc(
                        userId,
                        questionId);
    }
    
 // AI生成履歴を更新
    @Transactional
    public void updateGenerationHistory(
            Users user,
            Question question,
            String chineseText) {

        List<AiGenerationHistory> histories =
                getGenerationHistories(
                        user.getId(),
                        question.getQuestionId());

        // すでに10件ある場合は最も古い履歴を削除
        if (histories.size() >= MAX_HISTORY_SIZE) {

            AiGenerationHistory oldestHistory =
                    histories.get(histories.size() - 1);

            aiGenerationHistoryRepository.delete(
                    oldestHistory);

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
    
    private void saveGenerationHistory(
            Users user,
            Question question,
            String chineseText) {

        AiGenerationHistory aiGenerationHistory =
                new AiGenerationHistory();

        aiGenerationHistory.setUser(user);
        aiGenerationHistory.setQuestion(question);
        aiGenerationHistory.setChineseText(chineseText);
        aiGenerationHistory.setCreatedAt(LocalDateTime.now());

        aiGenerationHistoryRepository.save(aiGenerationHistory);
        
        log.debug(
                "AI生成履歴を保存しました。userId={}, questionId={}, chineseText={}",
                user.getId(),
                question.getQuestionId(),
                chineseText);
    }
}
