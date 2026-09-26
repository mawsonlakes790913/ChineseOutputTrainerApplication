package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.List;
import java.util.Locale;

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
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.ReviewQuestionLimit;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.ReviewService;
import io.github.mawsonlakes790913.chineseoutputforge.service.StructureService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 復習機能に関するリクエストを処理するController。
 * 復習の開始・表示・中断・再開・終了、理解度の記録を行う。
 */
@Controller
@RequiredArgsConstructor
public class ReviewController {
	
	private final UserAccountService userAccountService;
	private final ReviewService reviewService;
	private final QuestionModelUtil questionModelUtil;
	private final FavoriteService favoriteService;
	private final EvaluationService evaluationService;
	private final StructureService structureService;
	
	/**
	 * 復習メニュー画面を表示する。
	 * 中断中の復習情報と文法・構造一覧を取得して画面に渡す。
	 *
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @return 復習メニュー画面のビュー名
	 */
	@GetMapping("/review/menu")
	public String getReviewMenu(
	        HttpSession session,
	        Model model) {

	    // 言語切替後の戻り先を設定
	    model.addAttribute("languageVariantRedirect", "/review/menu");

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            (LanguageVariant) session.getAttribute("languageVariant");

	    // 未設定の場合は中国大陸向けを使用
	    if (languageVariant == null) {
	        languageVariant = LanguageVariant.MAINLAND;
	    }

	    // デフォルトの検索対象言語を画面へ渡す
	    model.addAttribute(
	            "selectedLanguageVariants",
	            List.of(languageVariant));

	    // セッションから中断中の復習データを取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("reviewQuestions");
	    Integer currentPage =
	            (Integer) session.getAttribute("reviewCurrentPage");

	    // 中断中の復習データがあるか判定
	    boolean canResume =
	            questions != null && currentPage != null;
	    model.addAttribute("canResume", canResume);

	    // 再開可能な場合は現在ページと総問題数を画面へ渡す
	    if (canResume) {
	        model.addAttribute("currentPage", currentPage);
	        model.addAttribute("totalCount", questions.size());
	    }

	    // 画面表示用の文法・構造一覧を取得
	    model.addAttribute(
	            "structures",
	            structureService.findStructures());

	    return "review/menu";
	}
	
	/**
	 * 指定された検索条件に一致する復習問題の件数を取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param languageVariants 学習対象言語の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param difficulties 難易度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @return 条件に一致する復習問題数
	 */
	@GetMapping("/review/count")
	@ResponseBody
	public long getReviewCount(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "languageVariants", required = false)
	        List<LanguageVariant> languageVariants,
	        @RequestParam(name = "evaluations", required = false)
	        List<Evaluation> evaluations,
	        @RequestParam(name = "difficulties", required = false)
	        List<Difficulty> difficulties,
	        @RequestParam(name = "favoriteCondition", required = false)
	        FavoriteCondition favoriteCondition,
	        @RequestParam(name = "sourceCondition", required = false)
	        QuestionSourceCondition sourceCondition,
	        @RequestParam(name = "structureIds", required = false)
	        List<Long> structureIds) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // 検索条件に一致する復習問題数を取得して返す
	    return reviewService.countReviewQuestions(
	            userId,
	            languageVariants,
	            evaluations,
	            difficulties,
	            favoriteCondition,
	            sourceCondition,
	            structureIds);
	}
	
	/**
	 * 指定された検索条件から問題を取得し、復習を開始する。
	 * 取得した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param languageVariants 学習対象言語の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param difficulties 難易度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param questionLimit 最大出題数
	 * @param random 問題をランダムに取得するかどうか
	 * @return 復習問題画面または復習メニュー画面へのリダイレクト先
	 */
	@GetMapping("/review/start")
	public String getReviewStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "languageVariants", required = false)
	        List<LanguageVariant> languageVariants,
	        @RequestParam(name = "evaluations", required = false)
	        List<Evaluation> evaluations,
	        @RequestParam(name = "difficulties", required = false)
	        List<Difficulty> difficulties,
	        @RequestParam(name = "favoriteCondition", required = false)
	        FavoriteCondition favoriteCondition,
	        @RequestParam(name = "sourceCondition", required = false)
	        QuestionSourceCondition sourceCondition,
	        @RequestParam(name = "structureIds", required = false)
	        List<Long> structureIds,
	        @RequestParam(name = "questionLimit", required = false)
	        ReviewQuestionLimit questionLimit,
	        @RequestParam(name = "random", required = false)
	        boolean random) {

	    // 既存の復習データを破棄
	    clearReviewSession(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // 検索条件に一致する復習問題を取得
	    List<Question> questions =
	            reviewService.getQuestion(
	                    userId,
	                    languageVariants,
	                    evaluations,
	                    difficulties,
	                    favoriteCondition,
	                    sourceCondition,
	                    structureIds,
	                    questionLimit,
	                    random);

	    // 復習対象問題が存在しない場合はメニューへ戻る
	    if (questions.isEmpty()) {
	        return "redirect:/review/menu";
	    }

	    // 復習問題と現在ページをセッションに保存
	    session.setAttribute("reviewQuestions", questions);
	    session.setAttribute("reviewCurrentPage", 0);

	    return "redirect:/review/question?page=0";
	}
	
	/**
	 * 指定されたページの復習問題を表示する。
	 * 現在ページをセッションに保存し、お気に入り状態も取得して画面に渡す。
	 *
	 * @param model 画面に渡すデータ
	 * @param session セッション情報
	 * @param page 表示する問題のページ番号
	 * @param loginUser ログインユーザー情報
	 * @return 復習問題画面または復習メニュー画面へのリダイレクト先
	 */
	@GetMapping("/review/question")
	public String getReviewQuestion(
	        Model model,
	        HttpSession session,
	        @RequestParam(defaultValue = "0") int page,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // セッションから復習問題を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("reviewQuestions");

	    // 復習データが存在しない場合はメニューへ戻る
	    if (questions == null) {
	        return "redirect:/review/menu";
	    }

	    // ページ番号が範囲外の場合はメニューへ戻る
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/review/menu";
	    }

	    // 現在表示している問題を取得
	    Question question = questions.get(page);

	    // 現在ページをセッションに保存
	    session.setAttribute("reviewCurrentPage", page);

	    // 問題表示に必要な情報を画面へ渡す
	    questionModelUtil.setQuestionModel(
	            model,
	            questions,
	            page,
	            session);

	    // ログイン中の場合はお気に入り状態を取得して画面へ渡す
	    if (loginUser != null) {
	        boolean isFavorite =
	                favoriteService.isFavorite(
	                        getLoginUser(loginUser),
	                        question.getQuestionId());
	        model.addAttribute("isFavorite", isFavorite);
	    }

	    return "review/question";
	}
	
	/**
	 * 中断した復習を保存されているページから再開する。
	 *
	 * @param session セッション情報
	 * @return 復習問題画面または復習メニュー画面へのリダイレクト先
	 */
	@GetMapping("/review/resume")
	public String getReviewResume(HttpSession session) {

	    // 中断中の復習データがない場合はメニューへ戻る
	    if (session.getAttribute("reviewQuestions") == null) {
	        return "redirect:/review/menu";
	    }

	    // 中断時のページ番号を取得して復習を再開
	    Integer page =
	            (Integer) session.getAttribute("reviewCurrentPage");

	    return "redirect:/review/question?page=" + page;
	}
	
	/**
	 * 復習に使用するセッション情報を削除し、復習を完了する。
	 *
	 * @param session セッション情報
	 * @return 学習完了画面へのリダイレクト先
	 */
	@GetMapping("/review/complete")
	public String getReviewComplete(HttpSession session) {

	    // 復習データを破棄して学習完了画面へ移動
	    clearReviewSession(session);

	    return "redirect:/complete";
	}
	
	/**
	 * 現在のページをセッションに保存し、復習を中断する。
	 *
	 * @param page 中断時のページ番号
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/review/suspend")
	public String getReviewSuspend(
	        @RequestParam int page,
	        HttpSession session) {

	    // 現在ページをセッションに保存して復習を中断
	    session.setAttribute("reviewCurrentPage", page);

	    return "redirect:/";
	}
	
	/**
	 * 復習に使用するセッション情報を削除し、復習を終了する。
	 *
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/review/quit")
	public String getReviewQuit(HttpSession session) {

	    // 復習データを破棄して復習を終了
	    clearReviewSession(session);

	    return "redirect:/";
	}
	
	/**
	 * 復習問題の理解度を保存し、次の問題へ進む。
	 * 最後の問題の場合は学習完了処理へ進む。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 評価する問題のID
	 * @param evaluation 保存する理解度
	 * @param page 現在のページ番号
	 * @param session セッション情報
	 * @param locale 現在の言語・地域情報
	 * @return 次の復習問題画面または学習完了処理へのリダイレクト先
	 */
	@PostMapping("/review/evaluation")
	public String postReviewEvaluation(
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

	    // セッションから復習問題を取得
	    List<Question> questions =
	            (List<Question>) session.getAttribute("reviewQuestions");

	    // 最後の問題の場合は復習を完了
	    if (page + 1 >= questions.size()) {
	        return "redirect:/review/complete";
	    }

	    // 次の問題へ移動
	    return "redirect:/review/question?page=" + (page + 1);
	}
	
	/**
	 * 復習に使用するセッション情報を削除する。
	 *
	 * @param session セッション情報
	 */
	private void clearReviewSession(HttpSession session) {

	    // 復習に使用するセッション情報を削除
	    session.removeAttribute("reviewQuestions");
	    session.removeAttribute("reviewCurrentPage");
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
