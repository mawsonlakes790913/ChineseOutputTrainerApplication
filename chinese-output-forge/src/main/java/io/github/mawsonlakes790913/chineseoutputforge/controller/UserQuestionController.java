package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.StudyCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserQuestionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class UserQuestionController {
	
	private final UserAccountService userAccountService;
	private final UserQuestionService userQuestionService;
	private final PaginationService paginationService;
	private final ReviewService reviewService;
	
	@GetMapping("/user/question/list")
	public String getUserQuestionList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @PageableDefault(page = 0, size = 50) Pageable pageable,
	        @RequestParam(required = false) List<Difficulty> difficulties,
	        @RequestParam(required = false) List<Evaluation> evaluations,
	        @RequestParam(required = false) StudyCondition studyCondition,
	        @RequestParam(required = false) FavoriteCondition favoriteCondition,
	        @RequestParam(required = false) List<Long> structureIds,
	        @RequestParam(required = false) List<LanguageVariant> languageVariants,
	        @RequestParam(required = false, defaultValue = "") String japaneseKeyword,
	        @RequestParam(required = false, defaultValue = "") String chineseKeyword,
	        HttpSession session,
	        Model model) {

	    Users user = userAccountService.getUserOne(loginUser.getUsername());
	    Long userId = user.getId();
	    
	    // 学習対象言語が未指定の場合は、
	    // セッションで設定されている学習対象言語を使用
		// 検索条件を画面へ戻す
	    // 学習対象言語
	    if (languageVariants == null || languageVariants.isEmpty()) {

	        LanguageVariant languageVariant =
	                (LanguageVariant) session.getAttribute("languageVariant");

	        if (languageVariant == null) {
	            languageVariant = LanguageVariant.MAINLAND;
	        }

	        languageVariants = Arrays.asList(languageVariant);
	    }

	    model.addAttribute(
	            "selectedLanguageVariants",
	            languageVariants
	    );

	    // 検索（パラメータが未指定ならService側で全件扱い）
	    Page<UserQuestionListDto> questionList =
	    		userQuestionService.getFilteredUserQuestionList(
	                    userId,
	                    difficulties,
	                    evaluations,
	                    studyCondition,
	                    favoriteCondition,
	                    structureIds,
	                    languageVariants,
	                    japaneseKeyword,
	                    chineseKeyword,
	                    pageable);

	    PaginationDto pagination =
	    		paginationService.createPagination(questionList);
	    
		long start = questionList.getNumber() * questionList.getSize() + 1;
		long end = start + questionList.getNumberOfElements() - 1;

		model.addAttribute("start", start);
		model.addAttribute("end", end);
		model.addAttribute("total", questionList.getTotalElements());

	    // 一覧
	    model.addAttribute("questionList", questionList.getContent());
	    model.addAttribute("page", questionList);
	    model.addAttribute("pagination", pagination);

	    // 選択肢用structureを取得
	    model.addAttribute(
	            "structures",
	            reviewService.findStructures());
	    
	    // 表示する発音記号を取得
	    PronunciationType pronunciationType =
	            (PronunciationType) session.getAttribute("pronunciationType");
	    if (pronunciationType == null) {
	        pronunciationType = PronunciationType.PINYIN;
	    }
	    model.addAttribute("pronunciationType", pronunciationType);

	    // 検索条件を画面へ戻す
	    model.addAttribute("selectedDifficulties", difficulties);
	    model.addAttribute("selectedEvaluations", evaluations);
	    model.addAttribute("selectedStudyCondition", studyCondition);
	    model.addAttribute("selectedFavoriteCondition", favoriteCondition);
	    model.addAttribute("selectedStructureIds", structureIds);
	    model.addAttribute("japaneseKeyword", japaneseKeyword);
	    model.addAttribute("chineseKeyword", chineseKeyword);

	    return "user/question/list";
	}

}
