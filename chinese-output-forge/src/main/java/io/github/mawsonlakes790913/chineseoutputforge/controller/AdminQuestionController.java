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
import io.github.mawsonlakes790913.chineseoutputforge.service.StructureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 管理者用の問題管理に関するリクエストを処理するController。
 * 問題の一覧表示・追加・編集・削除を行う。
 */
@Controller
@RequiredArgsConstructor
public class AdminQuestionController {
	
	private final AdminQuestionService adminQuestionService;
	private final PaginationService paginationService;
	private final StructureService structureService;
	
	/**
	 * 検索条件に基づいて問題を取得し、管理者用問題一覧画面を表示する。
	 *
	 * @param pageable ページング情報
	 * @param difficulties 難易度の検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariants 学習対象言語の検索条件
	 * @param japaneseKeyword 日本語の検索キーワード
	 * @param chineseKeyword 中国語の検索キーワード
	 * @param sortCondition 並び順
	 * @param request HTTPリクエスト
	 * @param model 画面に渡すデータ
	 * @return 管理者用問題一覧画面のビュー名
	 */
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
	        HttpServletRequest request,
	        Model model) {

	    // 現在のURLを取得
	    String currentUrl = request.getRequestURI();
	    if (request.getQueryString() != null) {
	        currentUrl += "?" + request.getQueryString();
	    }
	    model.addAttribute("currentUrl", currentUrl);

	    // 検索条件に一致する問題を取得
	    Page<AdminQuestionListDto> questionPage =
	            adminQuestionService.getFilteredAdminQuestions(
	                    difficulties,
	                    sourceCondition,
	                    structureIds,
	                    languageVariants,
	                    japaneseKeyword,
	                    chineseKeyword,
	                    sortCondition,
	                    pageable);

	    // ページネーション情報を作成
	    PaginationDto pagination =
	            paginationService.createPagination(questionPage);

	    // 現在のページに表示する問題の範囲を計算
	    long start = 0;
	    long end = 0;
	    if (questionPage.hasContent()) {
	        start =
	                (long) questionPage.getNumber()
	                * questionPage.getSize() + 1;

	        end =
	                start + questionPage.getNumberOfElements() - 1;
	    }

	    // 件数・ページネーション情報を画面へ渡す
	    model.addAttribute("start", start);
	    model.addAttribute("end", end);
	    model.addAttribute("total", questionPage.getTotalElements());
	    model.addAttribute("questionList", questionPage.getContent());
	    model.addAttribute("page", questionPage);
	    model.addAttribute("pagination", pagination);

	    // 検索条件を画面へ戻す
	    model.addAttribute("selectedDifficulties", difficulties);
	    model.addAttribute("selectedSourceCondition", sourceCondition);
	    model.addAttribute("selectedStructureIds", structureIds);
	    model.addAttribute("selectedLanguageVariants", languageVariants);
	    model.addAttribute("japaneseKeyword", japaneseKeyword);
	    model.addAttribute("chineseKeyword", chineseKeyword);
	    model.addAttribute("selectedSortCondition", sortCondition);

	    // 検索条件で使用する文法・構造一覧を取得
	    model.addAttribute(
	            "structures",
	            structureService.findStructures());

	    return "/admin/question/list";
	}
	
	/**
	 * 指定された問題を削除する。
	 *
	 * @param questionId 削除する問題のID
	 * @param returnUrl 削除後のリダイレクト先URL
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return 削除後のリダイレクト先
	 */
	@PostMapping("/admin/question/delete")
	public String postAdminQuestionDelete(
	        @RequestParam long questionId,
	        @RequestParam String returnUrl,
	        RedirectAttributes redirectAttributes) {

	    // 指定された問題を削除
	    adminQuestionService.deleteOneQuestion(questionId);

	    // 削除完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を削除しました。");

	    return "redirect:" + returnUrl;
	}
	
	/**
	 * 問題追加画面を表示する。
	 *
	 * @param form 問題追加フォーム
	 * @param model 画面に渡すデータ
	 * @return 問題追加画面のビュー名
	 */
	@GetMapping("/admin/question/add")
	public String getQuestionAdd(
	        @ModelAttribute QuestionForm form,
	        Model model) {

	    // 入力フォームを画面へ渡す
	    model.addAttribute("questionForm", form);

	    // 問題登録フォームで使用する選択肢を設定
	    setQuestionFormOptions(model);

	    return "admin/question/add";
	}
	
	/**
	 * 入力された内容を検証し、新しい問題を追加する。
	 * バリデーションエラーがある場合は問題追加画面を再表示する。
	 *
	 * @param form 問題追加フォーム
	 * @param bindingResult バリデーション結果
	 * @param model 画面に渡すデータ
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return 問題追加画面または問題一覧画面へのリダイレクト先
	 */
	@PostMapping("/admin/question/add")
	public String postQuestionAdd(
	        @ModelAttribute @Validated QuestionForm form,
	        BindingResult bindingResult,
	        Model model,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は入力画面を再表示
	    if (bindingResult.hasErrors()) {
	        return getQuestionAdd(form, model);
	    }

	    // 入力内容から問題を登録
	    adminQuestionService.addQuestion(form);

	    // 登録完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を追加しました。");

	    return "redirect:/admin/question/list";
	}
	
	/**
	 * 指定された問題の情報を取得し、問題編集画面を表示する。
	 *
	 * @param questionId 編集する問題のID
	 * @param model 画面に渡すデータ
	 * @return 問題編集画面のビュー名
	 */
	@GetMapping("/admin/question/edit")
	public String getAdminQuestionEdit(
	        @RequestParam long questionId,
	        Model model) {

	    // 変更前の問題情報を取得して画面へ渡す
	    OriginalQuestionDTO originalQuestion =
	            adminQuestionService.getOriginalQuestion(questionId);
	    model.addAttribute(
	            "originalQuestion",
	            originalQuestion);

	    // 変更前の問題情報から編集フォームの初期値を作成
	    QuestionForm form =
	            adminQuestionService.createQuestionForm(
	                    originalQuestion);
	    model.addAttribute(
	            "questionForm",
	            form);

	    // 問題編集フォームで使用する選択肢を設定
	    setQuestionFormOptions(model);

	    return "admin/question/edit";
	}
	
	/**
	 * 入力された内容を検証し、指定された問題を更新する。
	 * バリデーションエラーがある場合は問題編集画面を再表示する。
	 *
	 * @param questionId 編集する問題のID
	 * @param form 問題編集フォーム
	 * @param bindingResult バリデーション結果
	 * @param model 画面に渡すデータ
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @return 問題編集画面または問題一覧画面へのリダイレクト先
	 */
	@PostMapping("/admin/question/edit")
	public String postAdminQuestionEdit(
	        @RequestParam long questionId,
	        @ModelAttribute @Validated QuestionForm form,
	        BindingResult bindingResult,
	        Model model,
	        RedirectAttributes redirectAttributes) {

	    // バリデーションエラーがある場合は編集画面を再表示
	    if (bindingResult.hasErrors()) {

	        // 変更前の問題情報を取得して画面へ渡す
	        OriginalQuestionDTO originalQuestion =
	                adminQuestionService.getOriginalQuestion(questionId);
	        model.addAttribute(
	                "originalQuestion",
	                originalQuestion);

	        // 問題編集フォームで使用する選択肢を設定
	        setQuestionFormOptions(model);

	        return "admin/question/edit";
	    }

	    // 入力内容で問題を更新
	    adminQuestionService.updateOneQuestion(
	            questionId,
	            form);

	    // 編集完了メッセージを設定
	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "問題を編集しました。");

	    return "redirect:/admin/question/list";
	}
	
	/**
	 * 問題追加・編集フォームで使用する選択肢をModelに設定する。
	 *
	 * @param model 画面に渡すデータ
	 */
	private void setQuestionFormOptions(Model model) {

	    // 使用言語の選択肢を設定
	    model.addAttribute(
	            "languageVariants",
	            LanguageVariant.values());

	    // 難易度の選択肢を設定
	    model.addAttribute(
	            "difficulties",
	            Difficulty.values());

	    // 文法・構造の選択肢を設定
	    model.addAttribute(
	            "structures",
	            structureService.findStructures());
	}


}
