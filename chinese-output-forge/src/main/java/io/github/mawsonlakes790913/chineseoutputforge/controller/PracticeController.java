package io.github.mawsonlakes790913.chineseoutputforge.controller;



import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.NewPracticeCountDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
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
	private final UserAccountService userAccountService;
	private final EvaluationService evaluationService;
	private final FavoriteService favoriteService;
	
	@GetMapping("/practice/menu")
	public String getPracticeMenu(@AuthenticationPrincipal UserDetails loginUser, HttpSession session, Model model) {
		
	    // 言語切替後の戻り先
	    model.addAttribute("languageVariantRedirect", "/practice/menu");
	    
	    // 学習対象言語を取得
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    // 未設定の場合は普通話
	    if (languageVariant == null) {
	        languageVariant = LanguageVariant.MAINLAND;
	    }
	    
	    // 通常問題数を取得
	    PracticeMenuDto menu =
	            practiceService.countPracticeQuestions(languageVariant);
		model.addAttribute("practiceMenu", menu);
		
	    // 未学習問題数を取得
	    if (loginUser != null) {
	    Users user = getLoginUser(loginUser);
		NewPracticeCountDto count = practiceService.countNewPracticeQuestions(user.getId());
		model.addAttribute("newQuestionCount", count);
	    }
	    
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
	    
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    if (languageVariant == null) {
	        languageVariant = LanguageVariant.MAINLAND;
	    }

	    //問題セットを取得
	    List<Question> questions =
	            practiceService.getPracticeQuestions(
	                    languageVariant,
	                    difficulty,
	                    start,
	                    random
	            );
	    
		// 問題が存在しない場合
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

		session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);
	    
	    return "redirect:/practice/question?page=0";	    
	}	
	
	@GetMapping("/practice/new/start")
	public String getPracticeNewStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "difficulties", required = false) 
			List<Difficulty> difficulty
	        ) {
		
	    // 既存の学習状態を破棄
		clearPracticeSession(session);
	    
	    //先に宣言
	    List<Question> questions;
	    
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    //問題セットを取得
	    questions = practiceService.getNewQuestions(userId, difficulty);
	    
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
								   @RequestParam(defaultValue = "0") int page,
								   @AuthenticationPrincipal UserDetails loginUser
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
	    questionModelUtil.setQuestionModel(model, questions, page, session);
	    
	    // 現在表示する問題を取得
	    Question question = questions.get(page);
	    
	    // ログインしている場合だけお気に入り判定
	    if (loginUser != null) {
	        boolean isFavorite = favoriteService.isFavorite(
	        		getLoginUser(loginUser),
	                question.getQuestionId());

	        model.addAttribute("isFavorite", isFavorite);
	    }
		
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
	
	@PostMapping("/practice/evaluation")
	public String postEvaluation(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long questionId,
	        @RequestParam Evaluation evaluation,
	        @RequestParam Integer page,
	        HttpSession session) {

	    // ユーザー情報を取得
	    Users user = userAccountService.getUserOne(
	            loginUser.getUsername());

	    // 理解度を保存
	    evaluationService.updateEvaluation(
	            user,
	            questionId,
	            evaluation);

	    // セッションから問題一覧を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("practiceQuestions");

	    // 最後の問題の場合
	    if (page + 1 >= questions.size()) {
	        return "redirect:/practice/complete";
	    }

	    // 次の問題へ
	    return "redirect:/practice/question?page=" + (page + 1);
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}
}
