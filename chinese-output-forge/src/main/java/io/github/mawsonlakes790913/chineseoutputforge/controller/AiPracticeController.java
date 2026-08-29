package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;

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
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AiPracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AiPracticeController {
	
	private final ReviewService reviewService;
	private final UserAccountService userAccountService;
	private final AiPracticeService aiPracticeService;
	
	@GetMapping("/ai-practice/menu")
	public String getAiPracticeMenu(
			HttpSession session,
			Model model) {
		
		// 言語切替後の戻り先
		model.addAttribute("languageVariantRedirect", "/ai-practice/menu");
	    
	    // セッションから情報を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("aiPracticeQuestions");

	    Integer currentPage =
	            (Integer) session.getAttribute("aiPracticeCurrentPage");
	    
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
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
