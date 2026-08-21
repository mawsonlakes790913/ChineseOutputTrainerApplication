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
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ReviewController {
	
	private final UserAccountService userAccountService;
	private final ReviewService reviewService;
	private final QuestionRepository questionRepository;
	
	@GetMapping("/review/menu")
	public String getReviewMenu(HttpSession session,
			   					Model model) {
	    
	    // セッションから情報を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("reviewQuestions");

	    Integer currentPage =
	            (Integer) session.getAttribute("reviewCurrentPage");
	    
	    // 中断したデータがあるか判定
	    boolean canResume = questions != null && currentPage != null;
	    
	    // 中断したデータ情報を返す
	    model.addAttribute("canResume", canResume);

	    if (canResume) {
		    model.addAttribute("currentPage", currentPage);
		    model.addAttribute("totalCount", questions.size());
	    } 

	    return "review/menu";
	}
	
	@GetMapping("/review/count")
	@ResponseBody
	public long getReviewCount(@AuthenticationPrincipal UserDetails loginUser,
								@RequestParam(name = "evaluations", required = false) 
									List<Evaluation> evaluations,
								@RequestParam(name = "difficulties", required = false) 
									List<Difficulty> difficulties,
								@RequestParam(name = "favoriteCondition", required = false)
									FavoriteCondition favoriteCondition
								) {
		
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 出題数を返す
	    return reviewService.countReviewQuestions(
	            userId,
	            evaluations,
	            difficulties,
	            favoriteCondition);
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
