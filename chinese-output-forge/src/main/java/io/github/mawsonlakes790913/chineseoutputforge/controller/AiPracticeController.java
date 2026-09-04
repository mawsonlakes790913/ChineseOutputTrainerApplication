package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AiPracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AiPracticeController {
	
	private final ReviewService reviewService;
	private final UserAccountService userAccountService;
	private final AiPracticeService aiPracticeService;
	private final QuestionModelUtil questionModelUtil;
	
	@GetMapping("/ai-practice/menu")
	public String getAiPracticeMenu(
			HttpSession session,
			Model model) {
		
		// 言語切替後の戻り先
		model.addAttribute("languageVariantRedirect", "/ai-practice/menu");
	    
	    // セッションから情報を取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute("aiPracticeQuestions");

	    Integer currentPage =
	            (Integer) session.getAttribute("aiPracticeQuestionsCurrentPage");
	    
	    // 中断したデータがあるか判定
	    boolean canResume = questions != null && currentPage != null;
	    
	    // 中断したデータ情報を返す
	    model.addAttribute("canResume", canResume);

	    if (canResume) {
		    model.addAttribute("currentPage", currentPage);
		    model.addAttribute("totalCount", questions.size());
	    } 
	    
	    // 画面表示用structureを取得
	    model.addAttribute(
	            "structures",
	            reviewService.findStructures());
		
		return "/ai-practice/menu";
	}
	
	@GetMapping("/ai-practice/count")
	@ResponseBody
	public long getAiPracticeCount(@AuthenticationPrincipal UserDetails loginUser,
								@RequestParam(name = "evaluations", required = false) 
									List<Evaluation> evaluations,
								@RequestParam(name = "difficulties", required = false) 
									List<Difficulty> difficulties,
								@RequestParam(name = "favoriteCondition", required = false)
									FavoriteCondition favoriteCondition,
								@RequestParam(name = "structureIds", required = false)
									List<Long> structureIds,
								HttpSession session
								) {
		
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 学習対象言語を取得
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");
	    
	    // 未設定の場合は普通話
	    if (languageVariant == null) {
	        languageVariant = LanguageVariant.MAINLAND;
	    }
	   
	    
	    // 出題数を返す
	    return aiPracticeService.countAiGenerationSourceQuestions(
	            userId,
	            difficulties,
	            evaluations,
	            favoriteCondition,
	            structureIds,
	            languageVariant
	            );
	}
	
	@GetMapping("/ai-practice/start")
	public String getAiPracticeStart(
			 HttpSession session,
			 @AuthenticationPrincipal UserDetails loginUser,					 
			 @RequestParam(name = "evaluations", required = false) 
					List<Evaluation> evaluations,
			 @RequestParam(name = "difficulties", required = false) 
					List<Difficulty> difficulties,
		     @RequestParam(name = "favoriteCondition", required = false)
					FavoriteCondition favoriteCondition,
			 @RequestParam(name = "structureIds", required = false)
			 		List<Long> structureIds,
			 Locale locale
			
			) {
		
	    // 既存の学習状態を破棄
		clearAiPracticeSession(session);
		
	    // 学習対象言語を取得
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");
	    
	    // 未設定の場合は普通話
	    if (languageVariant == null) {
	        languageVariant = LanguageVariant.MAINLAND;
	    }
	    
	    //先に宣言
	    List<Question> sourceQuestions;
	    
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 新しい問題セットを作成
	    sourceQuestions = aiPracticeService.getQuestion(userId, difficulties, evaluations, favoriteCondition, structureIds, languageVariant);
	    
	    // 問題が1件もない場合は開始しない
	    if (sourceQuestions.isEmpty()) {
	        return "redirect:/ai-practice/menu";
	    }

	    // AIで問題を生成
	    List<AiGeneratedQuestionDto> aiPracticeQuestions;

	    aiPracticeQuestions = aiPracticeService.generateQuestions(
	    		user,
	    		sourceQuestions,
	    		languageVariant,
	    		locale);
	    
		session.setAttribute("aiPracticeQuestions", aiPracticeQuestions);
	    session.setAttribute("aiPracticeQuestionsCurrentPage", 0);
	    
	    return "redirect:/ai-practice/question?page=0";

	}
	
	@GetMapping("/ai-practice/question")
	public String getAiPracticeQuestion(
	        Model model,
	        HttpSession session,
	        @RequestParam(defaultValue = "0") int page,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // Sessionからquestions取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute("aiPracticeQuestions");

	    // /questionへの直接アクセスを禁ずる
	    if (questions == null) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 範囲外のページへのアクセスを禁ずる
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 現在表示する問題を取得
	    AiGeneratedQuestionDto question = questions.get(page);

	    // 現在ページをSessionへ保存
	    session.setAttribute("aiPracticeQuestionsCurrentPage", page);

	    // HTMLが必要な情報をModelへ格納
	    questionModelUtil.setAiQuestionModel(
	            model,
	            questions,
	            page,
	            session
	    );

//	    // お気に入り判定
//	    if (loginUser != null) {
//	        boolean isFavorite = favoriteService.isFavorite(
//	                getLoginUser(loginUser),
//	                question.sourceQuestionId()
//	        );
//
//	        model.addAttribute("isFavorite", isFavorite);
//	    }

	    return "ai-practice/question";
	}
	
	@GetMapping("/ai-practice/resume")
	public String getAiPracticeResume(Model model,
							  HttpSession session
							  ) {
		// 中断していないならmenuに戻す
		if (session.getAttribute("aiPracticeQuestions") == null) {
		    return "redirect:/ai-practice/menu";
		}
		// 中断時のページ情報を取得
		Integer page =
		        (Integer) session.getAttribute("aiPracticeQuestionsCurrentPage");
		
		return "redirect:/ai-practice/question?page=" + page;
		
	}
	
	@GetMapping("/ai-practice/complete")
	public String getAiPracticeComplete(HttpSession session) {
		clearAiPracticeSession(session);
		return "redirect:/complete";
	}
	
	@GetMapping("/ai-practice/suspend")
	public String getAiPracticeSuspend(@RequestParam int page,
	                              HttpSession session) {
		
		log.info("getAiPracticeSuspend reached");

	    session.setAttribute("aiPracticeQuestionsCurrentPage", page);

	    return "redirect:/";
	}
	
	@GetMapping("/ai-practice/quit")
	public String getAiPracticeQuit(HttpSession session) {

		clearAiPracticeSession(session);

	    return "redirect:/";
	}
	
	private void clearAiPracticeSession(HttpSession session) {
	    session.removeAttribute("aiPracticeQuestions");
	    session.removeAttribute("aiPracticeQuestionsCurrentPage");
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
