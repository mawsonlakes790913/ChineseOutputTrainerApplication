package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import jakarta.servlet.http.HttpSession;

@Component
public class QuestionModelUtil {

    public void setQuestionModel(
            Model model,
            List<Question> questions,
            int page,
            HttpSession session) {

        Question question = questions.get(page);

        model.addAttribute("question", question);
        model.addAttribute("nextPageIndex", page + 1);
        model.addAttribute("totalPages", questions.size());
        model.addAttribute("hasPrevious", page > 0);
        model.addAttribute("hasNext", page < questions.size() - 1);

        // 表示する発音記号を決定
        PronunciationType pronunciationType =
                (PronunciationType) session.getAttribute("pronunciationType");

        if (pronunciationType == null) {
            pronunciationType = PronunciationType.PINYIN;
        }

        switch (pronunciationType) {

        case PINYIN -> {
            model.addAttribute(
                    "pronunciation",
                    question.getPinyin()
            );

            model.addAttribute(
                    "alternativePronunciation",
                    question.getAlternativeAnswerPinyin()
            );
        }

        case ZHUYIN -> {
            model.addAttribute(
                    "pronunciation",
                    question.getZhuyin()
            );

            model.addAttribute(
                    "alternativePronunciation",
                    question.getAlternativeAnswerZhuyin()
            );
        }

        case NONE -> {
            model.addAttribute(
                    "pronunciation",
                    null
            );

            model.addAttribute(
                    "alternativePronunciation",
                    null
            );
        }

        }
    }
}
