package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AdminQuestionSortCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.OriginalQuestionDTO;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.form.QuestionForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminQuestionService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminQuestionController {
	
	private final AdminQuestionService adminQuestionService;
	private final PaginationService paginationService;
	private final ReviewService reviewService;
	
	@GetMapping("/admin/question/list")
	public String getAdminQuestionList(
	        @PageableDefault(page = 0, size = 50) Pageable pageable,
	        @RequestParam(required = false) List<Difficulty> difficulties,
	        @RequestParam(required = false) QuestionSourceCondition sourceCondition,
	        @RequestParam(required = false) List<Long> structureIds,
	        @RequestParam(required = false) List<LanguageVariant> languageVariants,
	        @RequestParam(required = false, defaultValue = "") String japaneseKeyword,
	        @RequestParam(required = false, defaultValue = "") String chineseKeyword,
	        @RequestParam(
	                required = false,
	                defaultValue = "UPDATED_DESC"
	        )
	        AdminQuestionSortCondition sortCondition,
	        HttpSession session,
	        HttpServletRequest request,
	        Model model) {
		
		// 現在のURLを取得
		String currentUrl = request.getRequestURI();

		if (request.getQueryString() != null) {
		    currentUrl += "?" + request.getQueryString();
		}

		model.addAttribute("currentUrl", currentUrl);
		
		Page<AdminQuestionListDto> allFilteredQuestionList = 
				adminQuestionService.getFilteredAdminQuestions(
						difficulties,
						sourceCondition,
						structureIds,
						languageVariants,
						japaneseKeyword,
						chineseKeyword,
						sortCondition,
						pageable
						);
						
		PaginationDto pagination = paginationService.createPagination(allFilteredQuestionList);
		
		long start = allFilteredQuestionList.getNumber() * allFilteredQuestionList.getSize() + 1;
		long end = start + allFilteredQuestionList.getNumberOfElements() - 1;
		
		// ページ情報
		model.addAttribute("start", start);
		model.addAttribute("end", end);
		model.addAttribute("total", allFilteredQuestionList.getTotalElements());

		model.addAttribute("questionList", allFilteredQuestionList.getContent());
		model.addAttribute("page", allFilteredQuestionList);
		model.addAttribute("pagination", pagination);

		// 検索条件
		model.addAttribute("selectedDifficulties", difficulties);
		model.addAttribute("selectedSourceCondition", sourceCondition);
		model.addAttribute("selectedStructureIds", structureIds);
		model.addAttribute("selectedLanguageVariants", languageVariants);
		model.addAttribute("japaneseKeyword", japaneseKeyword);
		model.addAttribute("chineseKeyword", chineseKeyword);
		model.addAttribute("selectedSortCondition", sortCondition);

		// 構文一覧
	    model.addAttribute(
	            "structures",
	            reviewService.findStructures());
		
		return "/admin/question/list";
		
	}
	
	@PostMapping("/admin/question/delete")
	public String postAdminQuestionDelete(
	        @RequestParam long questionId,
	        @RequestParam String returnUrl,
	        RedirectAttributes redirectAttributes) {

	    adminQuestionService.deleteOneQuestion(questionId);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を削除しました。");

	    return "redirect:" + returnUrl;
	}
	
	@GetMapping("/admin/question/add")
	public String getQuestionAdd(
	        @ModelAttribute QuestionForm form,
	        Model model) {

	    model.addAttribute("questionForm", form);

	    setQuestionFormOptions(model);

	    return "admin/question/add";
	}
	
	@PostMapping("/admin/question/add")
	public String postQuestionAdd(
	        @ModelAttribute @Validated QuestionForm form,
	        BindingResult bindingResult,
	        Model model,
	        RedirectAttributes redirectAttributes) {

	    // 通常のバリデーションエラー確認
	    if (bindingResult.hasErrors()) {
	        return getQuestionAdd(form, model);
	    }

	    log.info("問題登録 {}", form);

	    adminQuestionService.addQuestion(form);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を追加しました。");

	    return "redirect:/admin/question/list";
	}
	
	@GetMapping("/admin/question/edit")
	public String getAdminQuestionEdit(
	        @RequestParam long questionId,
	        Model model) {

	    // 変更前の問題情報
	    OriginalQuestionDTO originalQuestion =
	            adminQuestionService.getOriginalQuestion(questionId);

	    model.addAttribute(
	            "originalQuestion",
	            originalQuestion);

	    // 編集フォームの初期値
	    QuestionForm form = new QuestionForm();

	    form.setLanguageVariant(
	            originalQuestion.getLanguageVariant());

	    form.setJapaneseText(
	            originalQuestion.getJapaneseText());

	    form.setChineseText(
	            originalQuestion.getChineseText());

	    form.setAlternativeAnswer(
	            originalQuestion.getAlternativeAnswer());

	    form.setDifficulty(
	            originalQuestion.getDifficulty());

	    form.setStructureId(
	            originalQuestion.getStructureId());

	    form.setAllowAiVariation(
	            originalQuestion.isAllowAiVariation());

	    form.setTemplate(
	            originalQuestion.getTemplate());

	    model.addAttribute(
	            "questionForm",
	            form);

	    setQuestionFormOptions(model);

	    return "admin/question/edit";
	}
	
	@PostMapping("/admin/question/edit")
	public String postAdminQuestionEdit(
	        @RequestParam long questionId,
	        @ModelAttribute @Validated QuestionForm form,
	        BindingResult bindingResult,
	        Model model,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラー
	    if (bindingResult.hasErrors()) {

	        // 変更前の問題情報
	        OriginalQuestionDTO originalQuestion =
	                adminQuestionService.getOriginalQuestion(questionId);

	        model.addAttribute(
	                "originalQuestion",
	                originalQuestion);

	        setQuestionFormOptions(model);

	        return "admin/question/edit";
	    }

	    log.info("問題更新 {}", form);

	    adminQuestionService.updateOneQuestion(
	            questionId,
	            form);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を編集しました。");

	    return "redirect:/admin/question/list";
	}
	
	private void setQuestionFormOptions(Model model) {

	    // 使用言語
	    model.addAttribute(
	            "languageVariants",
	            LanguageVariant.values());

	    // 難易度
	    model.addAttribute(
	            "difficulties",
	            Difficulty.values());

	    // 文法・構造
	    model.addAttribute(
	            "structures",
	            reviewService.findStructures());
	}


}
