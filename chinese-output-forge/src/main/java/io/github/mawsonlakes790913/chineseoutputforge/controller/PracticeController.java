package io.github.mawsonlakes790913.chineseoutputforge.controller;



import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.NewPracticeCountDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PracticeController {
	
	private final PracticeService practiceService;
	private final QuestionModelUtil questionModelUtil;
	private final UserAccountService userAccountService;
	private final EvaluationService evaluationService;
	private final FavoriteService favoriteService;
	private final MessageSource messageSource;
	private final QuestionListService questionListService;
	
	@GetMapping("/practice/menu")
	public String getPracticeMenu(
			@AuthenticationPrincipal UserDetails loginUser, 
			HttpSession session, 
			Model model) {
		
	    // 言語切替後の戻り先
	    model.addAttribute("languageVariantRedirect", "/practice/menu");
	    
	    // 学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);
	    
	    // ログインしていればIDを取得
	    Long userId = null;
	    
	    if (loginUser != null) {
	        Users user = getLoginUser(loginUser);
	        userId = user.getId();
	        
	     // ユーザーが所有するリストを取得
	        List<QuestionList> questionLists =
	                questionListService.getQuestionLists(user);

	        model.addAttribute(
	                "questionLists",
	                questionLists);	        
	        
	    }
	    
	    // メソッド呼び出しのためにPracticesourceConditionを宣言
	    QuestionSourceCondition sourceCondition = null;
	    
	    // 通常問題数を取得
	    PracticeMenuDto menu =
	            practiceService.countPracticeQuestions(
	                    userId,
	                    languageVariant,
	                    sourceCondition
	            );

	    model.addAttribute("practiceMenu", menu);

		
	    // 未学習問題数を取得
	    if (loginUser != null) {
		NewPracticeCountDto count = practiceService.countNewPracticeQuestions(userId, languageVariant);
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
	
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/count")
	@ResponseBody
	public PracticeMenuDto getPracticeCount(
			HttpSession session,
			@AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "sourceCondition", required = false) 
			QuestionSourceCondition sourceCondition
			) {
		
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 言語情報を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);
	    
	    // 出題数を返す
	    return practiceService.countPracticeQuestions(
	            userId,
	            languageVariant,
	            sourceCondition);
	}
	
	// 指定したリストの問題数を取得
	@GetMapping("/practice/menu/count/by-list")
	@ResponseBody
	public long getPracticeCountByList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        @RequestParam Long listId) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // 指定したリストの問題数を取得
	    return practiceService.countPracticeQuestionsByList(
	            user.getId(),
	            listId,
	            languageVariant);
	}
	
	@GetMapping("/practice/start")
	public String getPracticeStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser, 
	        @RequestParam(required = false) Integer beginnerRange,
	        @RequestParam(required = false) Integer intermediateRange,
	        @RequestParam(required = false) Integer advancedRange,
	        @RequestParam(name = "sourceCondition", required = false) QuestionSourceCondition sourceCondition,
	        @RequestParam(name = "random") boolean random,
	        RedirectAttributes redirectAttributes,
	        Locale locale
	        ) {
		
		// 無選択を回避
	    int selectedCount = 0;

	    if (beginnerRange != null) selectedCount++;
	    if (intermediateRange != null) selectedCount++;
	    if (advancedRange != null) selectedCount++;
	    
	    if (selectedCount != 1) {
	        String errorMessage = messageSource.getMessage(
	                "practice.error.selectOneRange",
	                null,
	                locale
	        );

	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                errorMessage
	        );

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
	    
	    // 言語情報を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);
	    
	    // ログインしていればIDを取得
	    Long userId = null;
	    
	    if (loginUser != null) {
	        Users user = getLoginUser(loginUser);
	        userId = user.getId();
	    }
	    
	    // 問題セットを取得
	    List<Question> questions =
	            practiceService.getPracticeQuestions(
	                    userId,
	                    languageVariant,
	                    difficulty,
	                    sourceCondition,
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
	
	// 指定したリストの問題数を取得
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/new/start")
	public String getPracticeNewStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "difficulties", required = false) 
			List<Difficulty> difficulty
			) {
		
	    // 既存の学習状態を破棄
		clearPracticeSession(session);
		
	    // 言語情報を取得
		LanguageVariant languageVariant =
		        getLanguageVariant(session);
	    
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    //問題セットを取得
	    List<Question> questions =
	            practiceService.getNewQuestions(
	                    userId,
	                    languageVariant,
	                    difficulty);
	    
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

		session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);
	    
	    return "redirect:/practice/question?page=0";	    
	}
	
	// 指定したリストの問題を取得する
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/list/start")
	public String getPracticeListStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId) {

	    // 既存の学習状態を破棄
	    clearPracticeSession(session);

	    // 言語情報を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 問題セットを取得
	    List<Question> questions =
	            practiceService.getPracticeQuestionsByList(
	                    user.getId(),
	                    listId,
	                    languageVariant);

	    // 問題が存在しない場合
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

	    // 学習状態をセッションに保存
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
	public String getPracticeResume(
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
	
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/practice/evaluation")
	public String postPracticeEvaluation(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long questionId,
	        @RequestParam Evaluation evaluation,
	        @RequestParam Integer page,
	        HttpSession session,
	        Locale locale) {

	    // ユーザー情報を取得
		Users user = getLoginUser(loginUser);

	    // 理解度を保存
	    evaluationService.updateEvaluation(
	            user,
	            questionId,
	            evaluation,
	            locale);

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
	
	private LanguageVariant getLanguageVariant(HttpSession session) {

	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    if (languageVariant == null) {
	        return LanguageVariant.MAINLAND;
	    }

	    return languageVariant;
	}
}
