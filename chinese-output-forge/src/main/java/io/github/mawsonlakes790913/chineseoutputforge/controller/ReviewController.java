package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ReviewController {
	
	private final UserAccountService userAccountService;
	private final ReviewService reviewService;
	private final QuestionModelUtil questionModelUtil;
	private final FavoriteService favoriteService;
	private final EvaluationService evaluationService;
	
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
	    
	    // 画面表示用structureを取得
	    model.addAttribute(
	            "structures",
	            reviewService.findStructures());

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
									FavoriteCondition favoriteCondition,
								@RequestParam(name = "structureIds", required = false)
									List<Long> structureIds
								) {
		
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 出題数を返す
	    return reviewService.countReviewQuestions(
	            userId,
	            evaluations,
	            difficulties,
	            favoriteCondition,
	            structureIds);
	}
	
	@GetMapping("/review/start")
	public String getReviewStart(
							 HttpSession session,
							 @AuthenticationPrincipal UserDetails loginUser,
							 @RequestParam(name = "evaluations", required = false) 
									List<Evaluation> evaluations,
							 @RequestParam(name = "difficulties", required = false) 
									List<Difficulty> difficulties,
						     @RequestParam(name = "favoriteCondition", required = false)
									FavoriteCondition favoriteCondition,
							 @RequestParam(name = "random", required = false)
									boolean random
							 ) {
	    // 既存の学習状態を破棄
		clearReviewSession(session);
	    
	    //先に宣言
	    List<Question> questions;
	    
	    // user_id(文字列)からUsersを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();
	    
	    // 新しい問題セットを作成
	    questions = reviewService.getQuestion(userId, evaluations, difficulties, favoriteCondition, random);

	    // 問題が1件もない場合は開始しない
	    if (questions.isEmpty()) {
	        return "redirect:/review/menu";
	    }
	    
		session.setAttribute("reviewQuestions", questions);
	    session.setAttribute("reviewCurrentPage", 0);
	    
	    return "redirect:/review/question?page=0";
	}
	
	@GetMapping("/review/question")
	public String getReviewQuestion(
	        Model model,
	        HttpSession session,
	        @RequestParam(defaultValue = "0") int page,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // Sessionからquestions取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("reviewQuestions");

	    // /questionへの直接アクセスを禁ずる
	    if (questions == null) {
	        return "redirect:/review/menu";
	    }

	    // 範囲外のページへのアクセスを禁ずる
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/review/menu";
	    }

	    // 現在表示する問題を取得
	    Question question = questions.get(page);

	    // 現在ページをSessionへ保存
	    session.setAttribute("reviewCurrentPage", page);

	    // HTMLが必要な情報をModelへ格納
	    questionModelUtil.setQuestionModel(
	            model,
	            questions,
	            page,
	            session
	    );

	    // お気に入り判定
	    if (loginUser != null) {
	        boolean isFavorite = favoriteService.isFavorite(
	                getLoginUser(loginUser),
	                question.getQuestionId()
	        );

	        model.addAttribute("isFavorite", isFavorite);
	    }

	    return "review/question";
	}
	
	@GetMapping("/review/resume")
	public String getReviewResume(Model model,
							  HttpSession session
							  ) {
		// 中断していないならmenuに戻す
		if (session.getAttribute("reviewQuestions") == null) {
		    return "redirect:/review/menu";
		}
		// 中断時のページ情報を取得
		Integer page =
		        (Integer) session.getAttribute("reviewCurrentPage");
		
		return "redirect:/review/question?page=" + page;
		
	}
	
	@GetMapping("/review/complete")
	public String getReviewComplete(HttpSession session) {
		clearReviewSession(session);
		return "redirect:/complete";
	}
	

	@GetMapping("/review/suspend")
	public String getReviewSuspend(@RequestParam int page,
	                              HttpSession session) {
		
		log.info("getReviewSuspend reached");

	    session.setAttribute("reviewCurrentPage", page);

	    return "redirect:/";
	}
	
	@GetMapping("/review/quit")
	public String getReviewQuit(HttpSession session) {

	    clearReviewSession(session);

	    return "redirect:/";
	}
	
	@PostMapping("/review/evaluation")
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
	            (List<Question>) session.getAttribute("reviewQuestions");

	    // 最後の問題の場合
	    if (page + 1 >= questions.size()) {
	        return "redirect:/review/complete";
	    }

	    // 次の問題へ
	    return "redirect:/review/question?page=" + (page + 1);
	}
	
	private void clearReviewSession(HttpSession session) {
	    session.removeAttribute("reviewQuestions");
	    session.removeAttribute("reviewCurrentPage");
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
