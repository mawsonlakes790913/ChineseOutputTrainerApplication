package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

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

    public void saveGenerationHistory(
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
        
        log.info(
                "AI生成履歴を保存しました。userId={}, questionId={}, chineseText={}",
                user.getId(),
                question.getQuestionId(),
                chineseText);
    }
}
