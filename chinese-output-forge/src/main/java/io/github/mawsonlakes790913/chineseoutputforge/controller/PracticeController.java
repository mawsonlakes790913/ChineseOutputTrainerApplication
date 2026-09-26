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

/**
 * 通常学習機能に関するリクエストを処理するController。
 * 通常学習の開始・表示・中断・再開・終了、理解度の記録を行う。
 */
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
	
	/**
	 * 通常学習メニュー画面を表示する。
	 * 問題数、中断中の学習情報、ログイン中の場合はユーザーが所有するリストを取得して画面に渡す。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @return 通常学習メニュー画面のビュー名
	 */
	@GetMapping("/practice/menu")
	public String getPracticeMenu(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        Model model) {

	    // 言語切替後の戻り先を設定
	    model.addAttribute("languageVariantRedirect", "/practice/menu");

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログイン中の場合はユーザー情報と所有する問題リストを取得
	    Long userId = null;
	    if (loginUser != null) {
	        Users user = getLoginUser(loginUser);
	        userId = user.getId();

	        List<QuestionList> questionLists =
	                questionListService.getQuestionLists(user);
	        model.addAttribute("questionLists", questionLists);
	    }

	    // 全ての生成元を対象として通常問題数を取得
	    QuestionSourceCondition sourceCondition = null;
	    PracticeMenuDto menu =
	            practiceService.countPracticeQuestions(
	                    userId,
	                    languageVariant,
	                    sourceCondition);
	    model.addAttribute("practiceMenu", menu);

	    // ログイン中の場合は未学習問題数を取得
	    if (loginUser != null) {
	        NewPracticeCountDto count =
	                practiceService.countNewPracticeQuestions(
	                        userId,
	                        languageVariant);
	        model.addAttribute("newQuestionCount", count);
	    }

	    // セッションから中断中の通常学習データを取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("practiceQuestions");
	    Integer currentPage =
	            (Integer) session.getAttribute("practiceCurrentPage");

	    // 中断中の学習データがあるか判定
	    boolean canResume =
	            questions != null && currentPage != null;
	    model.addAttribute("canResume", canResume);

	    // 再開可能な場合は現在ページと総問題数を画面へ渡す
	    if (canResume) {
	        model.addAttribute("currentPage", currentPage);
	        model.addAttribute("totalCount", questions.size());
	    }

	    return "practice/menu";
	}
	
	/**
	 * 指定された生成元条件に一致する通常学習問題の件数を取得する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param sourceCondition 問題の生成元の検索条件
	 * @return 条件に一致する問題数
	 */
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/count")
	@ResponseBody
	public PracticeMenuDto getPracticeCount(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "sourceCondition", required = false)
	        QuestionSourceCondition sourceCondition) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // 検索条件に一致する通常問題数を取得して返す
	    return practiceService.countPracticeQuestions(
	            userId,
	            languageVariant,
	            sourceCondition);
	}
	
	/**
	 * 指定されたリストから学習対象となる問題数を取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param session セッション情報
	 * @param listId 対象リストのID
	 * @return リスト内の学習対象問題数
	 */
	@GetMapping("/practice/menu/count/by-list")
	@ResponseBody
	public long getPracticeCountByList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        @RequestParam Long listId) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // 指定したリストの学習対象問題数を取得して返す
	    return practiceService.countPracticeQuestionsByList(
	            user.getId(),
	            listId,
	            languageVariant);
	}
	
	/**
	 * 指定された難易度・範囲・生成元条件から問題を取得し、通常学習を開始する。
	 * 取得した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param beginnerRange 初級問題の開始位置
	 * @param intermediateRange 中級問題の開始位置
	 * @param advancedRange 上級問題の開始位置
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param random 問題をランダムに取得するかどうか
	 * @param redirectAttributes リダイレクト後に渡すFlash属性
	 * @param locale 現在の言語・地域情報
	 * @return 通常学習問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/practice/start")
	public String getPracticeStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(required = false) Integer beginnerRange,
	        @RequestParam(required = false) Integer intermediateRange,
	        @RequestParam(required = false) Integer advancedRange,
	        @RequestParam(name = "sourceCondition", required = false)
	        QuestionSourceCondition sourceCondition,
	        @RequestParam(name = "random") boolean random,
	        RedirectAttributes redirectAttributes,
	        Locale locale) {

	    // 選択された難易度範囲の数を確認
	    int selectedCount = 0;
	    if (beginnerRange != null) selectedCount++;
	    if (intermediateRange != null) selectedCount++;
	    if (advancedRange != null) selectedCount++;

	    // 難易度範囲が1つだけ選択されていない場合はエラーを表示
	    if (selectedCount != 1) {
	        String errorMessage =
	                messageSource.getMessage(
	                        "practice.error.selectOneRange",
	                        null,
	                        locale);

	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                errorMessage);

	        return "redirect:/practice/menu";
	    }

	    // 選択された難易度と問題開始位置を設定
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

	    // 既存の通常学習データを破棄
	    clearPracticeSession(session);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログイン中の場合はユーザーIDを取得
	    Long userId = null;
	    if (loginUser != null) {
	        Users user = getLoginUser(loginUser);
	        userId = user.getId();
	    }

	    // 選択された条件に一致する学習問題を取得
	    List<Question> questions =
	            practiceService.getPracticeQuestions(
	                    userId,
	                    languageVariant,
	                    difficulty,
	                    sourceCondition,
	                    start,
	                    random);

	    // 学習対象問題が存在しない場合はメニューへ戻る
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

	    // 学習問題と現在ページをセッションに保存
	    session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);

	    return "redirect:/practice/question?page=0";
	}
	
	/**
	 * 指定された難易度の未学習問題を取得し、通常学習を開始する。
	 * 取得した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param difficulty 難易度の検索条件
	 * @return 通常学習問題画面またはメニュー画面へのリダイレクト先
	 */
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/new/start")
	public String getPracticeNewStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "difficulties", required = false)
	        List<Difficulty> difficulties) {

	    // 既存の通常学習データを破棄
	    clearPracticeSession(session);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // 選択された難易度に一致する未学習問題を取得
	    List<Question> questions =
	            practiceService.getNewQuestions(
	                    userId,
	                    languageVariant,
	                    difficulties);

	    // 学習対象問題が存在しない場合はメニューへ戻る
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

	    // 学習問題と現在ページをセッションに保存
	    session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);

	    return "redirect:/practice/question?page=0";
	}
	
	/**
	 * 指定されたリストの問題を取得し、通常学習を開始する。
	 * 取得した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param listId 対象リストのID
	 * @return 通常学習問題画面またはメニュー画面へのリダイレクト先
	 */
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/practice/list/start")
	public String getPracticeListStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId) {

	    // 既存の通常学習データを破棄
	    clearPracticeSession(session);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定したリストから学習問題を取得
	    List<Question> questions =
	            practiceService.getPracticeQuestionsByList(
	                    user.getId(),
	                    listId,
	                    languageVariant);

	    // 学習対象問題が存在しない場合はメニューへ戻る
	    if (questions.isEmpty()) {
	        return "redirect:/practice/menu";
	    }

	    // 学習問題と現在ページをセッションに保存
	    session.setAttribute("practiceQuestions", questions);
	    session.setAttribute("practiceCurrentPage", 0);

	    return "redirect:/practice/question?page=0";
	}
	
	/**
	 * 指定されたページの通常学習問題を表示する。
	 * ログイン中の場合はお気に入り状態も取得して画面に渡す。
	 *
	 * @param model 画面に渡すデータ
	 * @param session セッション情報
	 * @param page 表示する問題のページ番号
	 * @param loginUser ログインユーザー情報
	 * @return 通常学習問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/practice/question")
	public String getPracticeQuestion(
	        Model model,
	        HttpSession session,
	        @RequestParam(defaultValue = "0") int page,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // セッションから学習問題を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("practiceQuestions");

	    // 学習データが存在しない場合はメニューへ戻る
	    if (questions == null) {
	        return "redirect:/practice/menu";
	    }

	    // ページ番号が範囲外の場合はメニューへ戻る
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/practice/menu";
	    }

	    // 問題表示に必要な情報を画面へ渡す
	    questionModelUtil.setQuestionModel(
	            model,
	            questions,
	            page,
	            session);

	    // 現在表示している問題を取得
	    Question question = questions.get(page);

	    // ログイン中の場合はお気に入り状態を取得して画面へ渡す
	    if (loginUser != null) {
	        boolean isFavorite =
	                favoriteService.isFavorite(
	                        getLoginUser(loginUser),
	                        question.getQuestionId());
	        model.addAttribute("isFavorite", isFavorite);
	    }

	    return "practice/question";
	}
	
	/**
	 * 中断した通常学習を保存されているページから再開する。
	 *
	 * @param session セッション情報
	 * @return 通常学習問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/practice/resume")
	public String getPracticeResume(HttpSession session) {

	    // 中断中の通常学習データがない場合はメニューへ戻る
	    if (session.getAttribute("practiceQuestions") == null) {
	        return "redirect:/practice/menu";
	    }

	    // 中断時のページ番号を取得して学習を再開
	    Integer page =
	            (Integer) session.getAttribute("practiceCurrentPage");

	    return "redirect:/practice/question?page=" + page;
	}
	
	/**
	 * 通常学習のセッション情報を削除し、学習を完了する。
	 *
	 * @param session セッション情報
	 * @return 学習完了画面へのリダイレクト先
	 */
	@GetMapping("/practice/complete")
	public String getPracticeComplete(HttpSession session) {

	    // 通常学習データを破棄して学習完了画面へ移動
	    clearPracticeSession(session);

	    return "redirect:/complete";
	}
	
	/**
	 * 現在のページをセッションに保存し、通常学習を中断する。
	 *
	 * @param page 中断時のページ番号
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/practice/suspend")
	public String getPracticeSuspend(
	        @RequestParam int page,
	        HttpSession session) {

	    // 現在ページをセッションに保存して通常学習を中断
	    session.setAttribute("practiceCurrentPage", page);

	    return "redirect:/";
	}
	
	/**
	 * 通常学習のセッション情報を削除し、学習を終了する。
	 *
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/practice/quit")
	public String getPracticeQuit(HttpSession session) {

	    // 通常学習データを破棄して学習を終了
	    clearPracticeSession(session);

	    return "redirect:/";
	}
	
	/**
	 * 通常学習問題の理解度を保存し、次の問題へ進む。
	 * 最後の問題の場合は学習完了処理へ進む。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 評価する問題のID
	 * @param evaluation 保存する理解度
	 * @param page 現在のページ番号
	 * @param session セッション情報
	 * @param locale 現在の言語・地域情報
	 * @return 次の通常学習問題画面または学習完了処理へのリダイレクト先
	 */
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/practice/evaluation")
	public String postPracticeEvaluation(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long questionId,
	        @RequestParam Evaluation evaluation,
	        @RequestParam Integer page,
	        HttpSession session,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 現在の問題の理解度を保存
	    evaluationService.updateEvaluation(
	            user,
	            questionId,
	            evaluation,
	            locale);

	    // セッションから学習問題を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("practiceQuestions");

	    // 最後の問題の場合は学習を完了
	    if (page + 1 >= questions.size()) {
	        return "redirect:/practice/complete";
	    }

	    // 次の問題へ移動
	    return "redirect:/practice/question?page=" + (page + 1);
	}
	
	/**
	 * 通常学習に使用するセッション情報を削除する。
	 *
	 * @param session セッション情報
	 */
	private void clearPracticeSession(HttpSession session) {

	    // 通常学習に使用するセッション情報を削除
	    session.removeAttribute("practiceQuestions");
	    session.removeAttribute("practiceCurrentPage");
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
	
	/**
	 * セッションから学習対象言語を取得する。
	 * 設定されていない場合は中国大陸向けを使用する。
	 *
	 * @param session セッション情報
	 * @return 学習対象言語
	 */
	private LanguageVariant getLanguageVariant(HttpSession session) {

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    // 未設定の場合は中国大陸向けを使用
	    if (languageVariant == null) {
	        return LanguageVariant.MAINLAND;
	    }

	    return languageVariant;
	}
}
