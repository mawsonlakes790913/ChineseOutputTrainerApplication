package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.StudyCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListService;
import io.github.mawsonlakes790913.chineseoutputforge.service.StructureService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * ユーザー用問題一覧に関するリクエストを処理するController。
 * 問題の検索・一覧表示・削除、理解度の更新を行う。
 */
@Controller
@RequiredArgsConstructor
public class UserQuestionController {
	
	private final UserAccountService userAccountService;
	private final UserQuestionService userQuestionService;
	private final PaginationService paginationService;
	private final MessageSource messageSource;
	private final EvaluationService evaluationService;
	private final StructureService structureService;
	private final QuestionListService questionListService;

	/**
	 * ログインユーザーの問題一覧を検索条件に基づいて取得し、問題一覧画面を表示する。
	 * 学習対象言語が未指定の場合は、セッションに保存されている学習対象言語を使用する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param pageable ページング情報
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param studyCondition 学習状況の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariants 学習対象言語の検索条件
	 * @param listId 問題リストの検索条件
	 * @param japaneseKeyword 日本語の検索キーワード
	 * @param chineseKeyword 中国語の検索キーワード
	 * @param session セッション情報
	 * @param request HTTPリクエスト
	 * @param model 画面に渡すデータ
	 * @return ユーザー問題一覧画面のビュー名
	 */
	@GetMapping("/user/question/list")
	public String getUserQuestionList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @PageableDefault(page = 0, size = 50) Pageable pageable,
	        @RequestParam(required = false) List<Difficulty> difficulties,
	        @RequestParam(required = false) List<Evaluation> evaluations,
	        @RequestParam(required = false) StudyCondition studyCondition,
	        @RequestParam(required = false) FavoriteCondition favoriteCondition,
	        @RequestParam(required = false) QuestionSourceCondition sourceCondition,
	        @RequestParam(required = false) List<Long> structureIds,
	        @RequestParam(required = false) List<LanguageVariant> languageVariants,
	        @RequestParam(required = false) Long listId,
	        @RequestParam(required = false, defaultValue = "") String japaneseKeyword,
	        @RequestParam(required = false, defaultValue = "") String chineseKeyword,
	        HttpSession session,
	        HttpServletRequest request,
	        Model model) {

	    // 現在のURLを取得
	    String currentUrl = request.getRequestURI();
	    if (request.getQueryString() != null) {
	        currentUrl += "?" + request.getQueryString();
	    }
	    model.addAttribute("currentUrl", currentUrl);

	    // 言語切替後の戻り先を設定
	    model.addAttribute("languageVariantRedirect", "/user/question/list");

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // 学習対象言語が未指定の場合はセッションの設定を使用
	    if (languageVariants == null || languageVariants.isEmpty()) {
	        LanguageVariant languageVariant =
	                (LanguageVariant) session.getAttribute("languageVariant");

	        // セッションにも設定がない場合は中国大陸向けを使用
	        if (languageVariant == null) {
	            languageVariant = LanguageVariant.MAINLAND;
	        }

	        languageVariants = Arrays.asList(languageVariant);
	    }
	    model.addAttribute(
	            "selectedLanguageVariants",
	            languageVariants
	    );

	    // 検索条件に一致する問題を取得
	    Page<UserQuestionListDto> questionPage =
	            userQuestionService.getFilteredUserQuestionList(
	                    userId,
	                    difficulties,
	                    evaluations,
	                    studyCondition,
	                    favoriteCondition,
	                    sourceCondition,
	                    structureIds,
	                    languageVariants,
	                    listId,
	                    japaneseKeyword,
	                    chineseKeyword,
	                    pageable);

	    // ページネーション情報を作成
	    PaginationDto pagination =
	            paginationService.createPagination(questionPage);

	    // 現在のページに表示する問題の範囲を算出
	    long start = questionPage.getNumber() * questionPage.getSize() + 1;
	    long end = start + questionPage.getNumberOfElements() - 1;
	    model.addAttribute("start", start);
	    model.addAttribute("end", end);
	    model.addAttribute("total", questionPage.getTotalElements());

	    // 問題一覧とページネーション情報をModelに設定
	    model.addAttribute("questionList", questionPage.getContent());
	    model.addAttribute("page", questionPage);
	    model.addAttribute("pagination", pagination);

	    // 検索条件の選択肢として使用する文法・構造を取得
	    model.addAttribute(
	            "structures",
	            structureService.findStructures());

	    // 表示する発音表記を取得
	    PronunciationType pronunciationType =
	            (PronunciationType) session.getAttribute("pronunciationType");

	    // 未設定の場合は拼音を使用
	    if (pronunciationType == null) {
	        pronunciationType = PronunciationType.PINYIN;
	    }
	    model.addAttribute("pronunciationType", pronunciationType);

	    // 学習済みで理解度が未指定の場合は全選択として表示
	    if (studyCondition == StudyCondition.LEARNED_ONLY
	            && (evaluations == null || evaluations.isEmpty())) {
	        evaluations = Arrays.asList(Evaluation.values());
	    }

	    // 検索条件をModelに設定
	    model.addAttribute("selectedDifficulties", difficulties);
	    model.addAttribute("selectedEvaluations", evaluations);
	    model.addAttribute("selectedStudyCondition", studyCondition);
	    model.addAttribute("selectedFavoriteCondition", favoriteCondition);
	    model.addAttribute("selectedSourceCondition", sourceCondition);
	    model.addAttribute("selectedStructureIds", structureIds);

	    // ユーザーが所有する問題リストを取得
	    model.addAttribute(
	            "questionLists",
	            questionListService.getQuestionLists(user));

	    // 選択した問題リストとキーワードをModelに設定
	    model.addAttribute("selectedListId", listId);
	    model.addAttribute("japaneseKeyword", japaneseKeyword);
	    model.addAttribute("chineseKeyword", chineseKeyword);

	    return "user/question/list";
	}
	
	/**
	 * ログインユーザーが所有する指定された問題を削除する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 削除する問題のID
	 * @param returnUrl 削除後に戻る問題一覧画面のURL
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return 削除後のリダイレクト先
	 */
	@PostMapping("/user/question/delete")
	public String postUserQuestionDelete(
			@AuthenticationPrincipal UserDetails loginUser,
			@RequestParam long questionId,
			@RequestParam String returnUrl,
			RedirectAttributes redirectAttributes,
			Locale locale) {

		// ユーザーIDを取得
		Users user = getLoginUser(loginUser);
		Long userId = user.getId();

		// ユーザーが所有する問題を削除
		userQuestionService.deleteOwnedQuestion(userId, questionId);

		// 削除成功メッセージを設定
		redirectAttributes.addFlashAttribute(
				"successMessage",
				messageSource.getMessage(
						"user.question.delete.success",
						null,
						locale));

		return "redirect:" + returnUrl;
	}
	
	/**
	 * 指定された問題の理解度を更新する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 理解度を更新する問題のID
	 * @param evaluation 更新後の理解度
	 * @param locale 現在の言語・地域情報
	 */
	@PostMapping("/evaluation/toggle")
	@ResponseBody
	public void postEvaluationToggle(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long questionId,
	        @RequestParam Evaluation evaluation,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定された問題の理解度を更新
	    evaluationService.updateEvaluation(
	            user,
	            questionId,
	            evaluation,
	            locale);
	}
	
	/**
	 * ログインユーザー情報からUsersを取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @return ログイン中のUsers
	 */
	private Users getLoginUser(UserDetails loginUser) {
	    return userAccountService.getUserOne(loginUser.getUsername());
	}

}
