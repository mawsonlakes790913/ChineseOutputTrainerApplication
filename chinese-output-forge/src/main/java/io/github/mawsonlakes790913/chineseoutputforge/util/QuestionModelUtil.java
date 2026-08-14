package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;


@Component
public class QuestionModelUtil {
	
    public void setQuestionModel(
            Model model,
            List<Question> questions,
            int page) {

        Question question = questions.get(page);

        model.addAttribute("question", question);
        model.addAttribute("nextPageIndex", page + 1);
        model.addAttribute("totalPages", questions.size());
        model.addAttribute("hasPrevious", page > 0);
        model.addAttribute("hasNext", page < questions.size() - 1);
    }
}
