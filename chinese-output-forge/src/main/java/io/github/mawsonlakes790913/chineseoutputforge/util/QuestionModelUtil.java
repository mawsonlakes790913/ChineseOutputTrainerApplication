package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import jakarta.servlet.http.HttpSession;

/**
 * 問題画面の表示に必要な情報をModelへ設定するUtilクラス。
 * 通常問題およびAI生成問題のページ情報と発音表記を設定する。
 */
@Component
public class QuestionModelUtil {

	/**
	 * 通常問題の表示に必要な情報をModelへ設定する。
	 * 現在の問題、ページ情報、およびユーザー設定に応じた発音表記を設定する。
	 *
	 * @param model 画面へ渡すModel
	 * @param questions 出題する問題の一覧
	 * @param page 現在の問題のインデックス
	 * @param session HTTPセッション
	 */
	public void setQuestionModel(
	        Model model,
	        List<Question> questions,
	        int page,
	        HttpSession session) {

	    // 現在の問題を取得
	    Question question = questions.get(page);

	    // 問題とページ情報をModelに設定
	    model.addAttribute("question", question);
	    model.addAttribute("nextPageIndex", page + 1);
	    model.addAttribute("totalPages", questions.size());
	    model.addAttribute("hasPrevious", page > 0);
	    model.addAttribute("hasNext", page < questions.size() - 1);

	    // 表示する発音表記を取得
	    PronunciationType pronunciationType =
	            (PronunciationType) session.getAttribute("pronunciationType");

	    // 発音表記が未設定の場合は拼音を使用
	    if (pronunciationType == null) {
	        pronunciationType = PronunciationType.PINYIN;
	    }

	    // 発音表記の設定に応じてModelへ表示内容を設定
	    switch (pronunciationType) {
	    case PINYIN -> {
	        model.addAttribute(
	                "pronunciation",
	                question.getPinyin());
	        model.addAttribute(
	                "alternativePronunciation",
	                question.getAlternativeAnswerPinyin());
	    }

	    case ZHUYIN -> {
	        model.addAttribute(
	                "pronunciation",
	                question.getZhuyin());
	        model.addAttribute(
	                "alternativePronunciation",
	                question.getAlternativeAnswerZhuyin());
	    }

	    case NONE -> {
	        model.addAttribute(
	                "pronunciation",
	                null);
	        model.addAttribute(
	                "alternativePronunciation",
	                null);
	    }
	    }
	}
    
    /**
     * AI生成問題の表示に必要な情報をModelへ設定する。
     * 現在の問題、ページ情報、およびユーザー設定に応じた発音表記を設定する。
     *
     * @param model 画面へ渡すModel
     * @param questions AI生成問題の一覧
     * @param page 現在の問題のインデックス
     * @param session HTTPセッション
     */
	public void setAiQuestionModel(
	        Model model,
	        List<AiGeneratedQuestionDto> questions,
	        int page,
	        HttpSession session) {

	    // 現在のAI生成問題を取得
	    AiGeneratedQuestionDto question = questions.get(page);

	    // 問題とページ情報をModelに設定
	    model.addAttribute("question", question);
	    model.addAttribute("nextPageIndex", page + 1);
	    model.addAttribute("totalPages", questions.size());
	    model.addAttribute("hasPrevious", page > 0);
	    model.addAttribute("hasNext", page < questions.size() - 1);

	    // 表示する発音表記を取得
	    PronunciationType pronunciationType =
	            (PronunciationType) session.getAttribute("pronunciationType");

	    // 発音表記が未設定の場合は拼音を使用
	    if (pronunciationType == null) {
	        pronunciationType = PronunciationType.PINYIN;
	    }

	    // 発音表記の設定に応じてModelへ表示内容を設定
	    switch (pronunciationType) {
	    case PINYIN -> {
	        model.addAttribute(
	                "pronunciation",
	                question.getPinyin());
	    }

	    case ZHUYIN -> {
	        model.addAttribute(
	                "pronunciation",
	                question.getZhuyin());
	    }

	    case NONE -> {
	        model.addAttribute(
	                "pronunciation",
	                null);
	    }
	    }
	}
}
