package io.github.mawsonlakes790913.chineseoutputforge.controller;



import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.service.PracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PracticeController {
	
	private final PracticeService practiceService;
	private final QuestionModelUtil questionModelUtil;
	
	@GetMapping("/practice/menu")
	public String getPracticeMenu(HttpSession session, Model model) {
		
	    // 言語切替後の戻り先
	    model.addAttribute("languageVariantRedirect", "/practice/menu");
		
		// 通常問題数を取得
		PracticeMenuDto menu = 
				practiceService.countPracticeQuestions( (LanguageVariant) session.getAttribute("languageVariant"));
		model.addAttribute("practiceMenu", menu);
	    
	    // セッションから情報を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("practiceQuestions");

	    Integer currentPage =
	            (Integer) session.getAttribute("practiceCurrentPage");
	    
	    // 中断したデータがあるか判定
	    boolean canResume = questions != null && currentPage != null;
	    
	    // 中断したデータ情報を返す
	    model.addAttribute("canResume", canResume);

	    if (canResume) {
		    model.addAttribute("currentPage", currentPage);
		    model.addAttribute("totalCount", questions.size());
	    } 

	    return "practice/menu";
	}
	
	@GetMapping("/practice/start")
	public String getPracticeStart(
	        HttpSession session,
	        @RequestParam(required = false) Integer beginnerRange,
	        @RequestParam(required = false) Integer intermediateRange,
	        @RequestParam(required = false) Integer advancedRange,
	        @RequestParam(name = "random") boolean random,
	        RedirectAttributes redirectAttributes
	        ) {
		
		// 無選択を回避
	    int selectedCount = 0;

	    if (beginnerRange != null) selectedCount++;
	    if (intermediateRange != null) selectedCount++;
	    if (advancedRange != null) selectedCount++;
	    
	    if (selectedCount != 1) {
	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                "出題範囲を1つ選択してください。");
	        return "redirect:/practice/menu";
	    }
		
	    // 選択した難易度と問題開始点を取得
	    Difficulty difficulty;
	    int start;
		
	    if (beginnerRange != null) {
	        difficulty = Difficulty.BEGINNER;
	        start = beginnerRange;
	    } else if (intermediateRange != null) {
	        difficulty = Difficulty.INTERMEDIATE;
	        start = intermediateRange;
	    } else if (advancedRange != null) {
	        difficulty = Difficulty.ADVANCED;
	        start = advancedRange;
	    } else {
	        return "redirect:/practice/menu";
	    }
		
	    // 既存の学習状態を破棄
	    clearPracticeSession(session);
	    
	    //問題セットを取得
	    List<Question> questions = practiceService.getPracticeQuestions(
	    		(LanguageVariant) session.getAttribute("languageVariant"),
	    		difficulty, 
	    		start, 
	    		random);
	    
		// 問題が存在しない場合
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

		session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);
	    
	    return "redirect:/practice/question?page=0";	    
	}	
	
	@GetMapping("/practice/question")
	public String getPracticeQuestion(Model model,
								   HttpSession session,
								   @RequestParam(defaultValue = "0") int page
								   ) {
		
		// Sessionからquestions取得
		List<Question> questions = (List<Question>) session.getAttribute("practiceQuestions");
		
		// /questionへの直接アクセスを禁ずる
	    if (questions == null) {
	        return "redirect:/practice/menu";
	    }
	    
	    // pageが範囲外の場合
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/practice/menu";
	    }
	    
		// HTMLが必要な情報をModelへ格納
	    questionModelUtil.setQuestionModel(model, questions, page);
		
		return "practice/question";
	}
	
	@GetMapping("/practice/resume")
	public String getPracticeResume(Model model,
							  HttpSession session
							  ) {
		// 中断していないならmenuに戻す
		if (session.getAttribute("practiceQuestions") == null) {
		    return "redirect:/practice/menu";
		}
		// 中断時のページ情報を取得
		Integer page =
		        (Integer) session.getAttribute("practiceCurrentPage");
		
		return "redirect:/practice/question?page=" + page;
		
	}
	
	@GetMapping("/practice/complete")
	public String getPracticeComplete(HttpSession session) {
		clearPracticeSession(session);
		return "redirect:/complete";
	}
	
	@GetMapping("/practice/suspend")
	public String getPracticeSuspend(@RequestParam int page,
	                              HttpSession session) {
		
		log.info("getPracticeSuspend reached");

	    session.setAttribute("practiceCurrentPage", page);

	    return "redirect:/";
	}
	
	@GetMapping("/practice/quit")
	public String getPracticeQuit(HttpSession session) {

	    clearPracticeSession(session);

	    return "redirect:/";
	}

	private void clearPracticeSession(HttpSession session) {
	    session.removeAttribute("practiceQuestions");
	    session.removeAttribute("practiceCurrentPage");
	}
}
