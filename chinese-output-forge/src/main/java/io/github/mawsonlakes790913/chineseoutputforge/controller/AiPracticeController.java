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
import io.github.mawsonlakes790913.chineseoutputforge.dto.AiGeneratedQuestionDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.QuestionList;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AiPracticeService;
import io.github.mawsonlakes790913.chineseoutputforge.service.EvaluationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.QuestionListService;
import io.github.mawsonlakes790913.chineseoutputforge.service.StructureService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import io.github.mawsonlakes790913.chineseoutputforge.util.QuestionModelUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * AI生成問題による学習機能に関するリクエストを処理するController。
 * AI生成学習の開始・表示・中断・再開・終了、生成問題の保存・理解度の記録を行う。
 */
@Controller
@RequiredArgsConstructor
public class AiPracticeController {
	
	private final UserAccountService userAccountService;
	private final AiPracticeService aiPracticeService;
	private final QuestionModelUtil questionModelUtil;
	private final EvaluationService evaluationService;
	private final FavoriteService favoriteService;
	private final StructureService structureService;
	private final QuestionListService questionListService;
	
	/**
	 * AI生成学習メニュー画面を表示する。
	 * 中断中の学習情報、文法・構造一覧、ユーザーが所有するリストを取得して画面に渡す。
	 *
	 * @param session セッション情報
	 * @param model 画面に渡すデータ
	 * @param loginUser ログインユーザー情報
	 * @return AI生成学習メニュー画面のビュー名
	 */
	@GetMapping("/ai-practice/menu")
	public String getAiPracticeMenu(
	        HttpSession session,
	        Model model,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // 言語切替後の戻り先を設定
	    model.addAttribute(
	            "languageVariantRedirect",
	            "/ai-practice/menu");

	    // セッションから中断中のAI生成学習データを取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute(
	                    "aiPracticeQuestions");
	    Integer currentPage =
	            (Integer) session.getAttribute(
	                    "aiPracticeQuestionsCurrentPage");

	    // 中断中の学習データがあるか判定
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

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // ユーザーが所有する問題リストを取得して画面へ渡す
	    List<QuestionList> questionLists =
	            questionListService.getQuestionLists(user);
	    model.addAttribute(
	            "questionLists",
	            questionLists);

	    return "/ai-practice/menu";
	}
	
	/**
	 * 指定された検索条件に一致するAI生成元問題の件数を取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param evaluations 理解度の検索条件
	 * @param difficulties 難易度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param session セッション情報
	 * @return AI生成元として利用可能な問題数
	 */
	@GetMapping("/ai-practice/count")
	@ResponseBody
	public long getAiPracticeCount(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam(name = "evaluations", required = false)
	        List<Evaluation> evaluations,
	        @RequestParam(name = "difficulties", required = false)
	        List<Difficulty> difficulties,
	        @RequestParam(name = "favoriteCondition", required = false)
	        FavoriteCondition favoriteCondition,
	        @RequestParam(name = "structureIds", required = false)
	        List<Long> structureIds,
	        HttpSession session) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // 検索条件に一致するAI生成元問題の件数を取得して返す
	    return aiPracticeService.countAiGenerationSourceQuestions(
	            userId,
	            difficulties,
	            evaluations,
	            favoriteCondition,
	            structureIds,
	            languageVariant);
	}
	
	/**
	 * 指定されたリストからAI生成元として利用可能な問題数を取得する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param listId 対象リストのID
	 * @param session セッション情報
	 * @return AI生成元として利用可能な問題数
	 */
	@GetMapping("/ai-practice/count/by-list")
	@ResponseBody
	public long getAiPracticeCountByList(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        HttpSession session) {

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // 指定したリストからAI生成元として利用可能な問題数を取得して返す
	    return aiPracticeService.countAiGenerationSourceQuestionsByList(
	            user.getId(),
	            listId,
	            languageVariant);
	}
	
	/**
	 * 指定された検索条件からAI生成元問題を取得し、AI生成学習を開始する。
	 * 生成した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param evaluations 理解度の検索条件
	 * @param difficulties 難易度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param locale 言語・地域情報
	 * @return AI生成問題画面またはメニュー画面へのリダイレクト先
	 */
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
	        Locale locale) {

	    // 既存のAI生成学習データを破棄
	    clearAiPracticeSession(session);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);
	    Long userId = user.getId();

	    // 検索条件に一致するAI生成元問題を取得
	    List<Question> sourceQuestions =
	            aiPracticeService.getAiGenerationSourceQuestions(
	                    userId,
	                    difficulties,
	                    evaluations,
	                    favoriteCondition,
	                    structureIds,
	                    languageVariant);

	    // 生成元問題が1件もない場合は学習を開始せずメニューへ戻る
	    if (sourceQuestions.isEmpty()) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 取得した問題を生成元としてAI問題を生成
	    List<AiGeneratedQuestionDto> aiPracticeQuestions =
	            aiPracticeService.generateQuestions(
	                    user,
	                    sourceQuestions,
	                    languageVariant,
	                    locale);

	    // 生成した問題と現在ページをセッションに保存
	    session.setAttribute(
	            "aiPracticeQuestions",
	            aiPracticeQuestions);
	    session.setAttribute(
	            "aiPracticeQuestionsCurrentPage",
	            0);

	    return "redirect:/ai-practice/question?page=0";
	}
	
	/**
	 * 指定されたリストの問題を生成元としてAI生成学習を開始する。
	 * 生成した問題と現在ページをセッションに保存する。
	 *
	 * @param session セッション情報
	 * @param loginUser ログインユーザー情報
	 * @param listId 対象リストのID
	 * @param limit50 最大50問に制限するかどうか
	 * @param locale 言語・地域情報
	 * @return AI生成問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/list/start")
	public String getAiPracticeListStart(
	        HttpSession session,
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam Long listId,
	        @RequestParam boolean limit50,
	        Locale locale) {

	    // 既存のAI生成学習データを破棄
	    clearAiPracticeSession(session);

	    // セッションから学習対象言語を取得
	    LanguageVariant languageVariant =
	            getLanguageVariant(session);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // 指定したリストからAI生成元問題を取得
	    List<Question> sourceQuestions;
	    if (limit50) {

	        // 最大50件取得
	        sourceQuestions =
	                aiPracticeService
	                        .getAiGenerationSourceQuestionsByListLimit50(
	                                user.getId(),
	                                listId,
	                                languageVariant);

	    } else {

	        // 全件取得
	        sourceQuestions =
	                aiPracticeService
	                        .getAiGenerationSourceQuestionsByList(
	                                user.getId(),
	                                listId,
	                                languageVariant);
	    }

	    // 生成元問題が1件もない場合は学習を開始せずメニューへ戻る
	    if (sourceQuestions.isEmpty()) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 取得した問題を生成元としてAI問題を生成
	    List<AiGeneratedQuestionDto> aiPracticeQuestions =
	            aiPracticeService.generateQuestions(
	                    user,
	                    sourceQuestions,
	                    languageVariant,
	                    locale);

	    // 生成した問題と現在ページをセッションに保存
	    session.setAttribute(
	            "aiPracticeQuestions",
	            aiPracticeQuestions);
	    session.setAttribute(
	            "aiPracticeQuestionsCurrentPage",
	            0);

	    return "redirect:/ai-practice/question?page=0";
	}
	
	/**
	 * 指定されたページのAI生成問題を表示する。
	 * 保存済み問題とお気に入りの状態も取得して画面に渡す。
	 *
	 * @param model 画面に渡すデータ
	 * @param session セッション情報
	 * @param page 表示する問題のページ番号
	 * @param loginUser ログインユーザー情報
	 * @return AI生成問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/question")
	public String getAiPracticeQuestion(
	        Model model,
	        HttpSession session,
	        @RequestParam(defaultValue = "0") int page,
	        @AuthenticationPrincipal UserDetails loginUser) {

	    // セッションからAI生成問題を取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute(
	                    "aiPracticeQuestions");

	    // 学習データが存在しない場合はメニューへ戻る
	    if (questions == null) {
	        return "redirect:/ai-practice/menu";
	    }

	    // ページ番号が範囲外の場合はメニューへ戻る
	    if (page < 0 || page >= questions.size()) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 現在表示するAI生成問題を取得
	    AiGeneratedQuestionDto question = questions.get(page);

	    // 現在ページをセッションに保存
	    session.setAttribute(
	            "aiPracticeQuestionsCurrentPage",
	            page);

	    // ログインユーザーを取得
	    Users user = getLoginUser(loginUser);

	    // AI生成問題が保存済みか確認し、保存済みの場合は問題IDを取得
	    Long savedQuestionId =
	            aiPracticeService.getSavedQuestionId(
	                    user.getId(),
	                    question.getChineseText());

	    // 問題表示に必要な情報を画面へ渡す
	    questionModelUtil.setAiQuestionModel(
	            model,
	            questions,
	            page,
	            session);

	    // 保存済み問題の場合はお気に入り状態を取得
	    boolean isFavorite = false;
	    if (savedQuestionId != null) {
	        isFavorite = favoriteService.isFavorite(
	                user,
	                savedQuestionId);
	    }

	    // 保存済み問題のIDとお気に入り状態を画面へ渡す
	    model.addAttribute("savedQuestionId", savedQuestionId);
	    model.addAttribute("isFavorite", isFavorite);

	    return "ai-practice/question";
	}
	
	/**
	 * 中断したAI生成学習を保存されているページから再開する。
	 *
	 * @param session セッション情報
	 * @return AI生成問題画面またはメニュー画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/resume")
	public String getAiPracticeResume(HttpSession session) {

	    // 中断中のAI生成学習データがない場合はメニューへ戻る
	    if (session.getAttribute("aiPracticeQuestions") == null) {
	        return "redirect:/ai-practice/menu";
	    }

	    // 中断時のページ番号を取得して学習を再開
	    Integer page =
	            (Integer) session.getAttribute(
	                    "aiPracticeQuestionsCurrentPage");

	    return "redirect:/ai-practice/question?page=" + page;
	}
	
	/**
	 * AI生成学習のセッション情報を削除し、学習を完了する。
	 *
	 * @param session セッション情報
	 * @return 学習完了画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/complete")
	public String getAiPracticeComplete(HttpSession session) {

	    // AI生成学習データを破棄して学習完了画面へ移動
	    clearAiPracticeSession(session);

	    return "redirect:/complete";
	}
	
	/**
	 * 現在のページをセッションに保存し、AI生成学習を中断する。
	 *
	 * @param page 中断時のページ番号
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/suspend")
	public String getAiPracticeSuspend(
	        @RequestParam int page,
	        HttpSession session) {

	    // 現在ページをセッションに保存してAI生成学習を中断
	    session.setAttribute(
	            "aiPracticeQuestionsCurrentPage",
	            page);

	    return "redirect:/";
	}
	
	/**
	 * AI生成学習のセッション情報を削除し、学習を終了する。
	 *
	 * @param session セッション情報
	 * @return トップ画面へのリダイレクト先
	 */
	@GetMapping("/ai-practice/quit")
	public String getAiPracticeQuit(HttpSession session) {

	    // AI生成学習データを破棄して学習を終了
	    clearAiPracticeSession(session);

	    return "redirect:/";
	}
	
	/**
	 * 現在のAI生成問題をユーザーの問題として保存する。
	 *
	 * @param session セッション情報
	 * @param page 保存する問題のページ番号
	 * @param loginUser ログインユーザー情報
	 * @param locale 言語・地域情報
	 * @return 保存した問題のID
	 */
	@PostMapping("/ai-practice/save")
	@ResponseBody
	public Long postAiPracticeSave(
	        HttpSession session,
	        @RequestParam int page,
	        @AuthenticationPrincipal UserDetails loginUser,
	        Locale locale) {

	    // セッションからAI生成問題を取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute(
	                    "aiPracticeQuestions");

	    // 現在表示しているAI生成問題を取得
	    AiGeneratedQuestionDto question = questions.get(page);

	    // AI生成問題をログインユーザーの問題として保存
	    Question savedQuestion =
	            aiPracticeService.saveGeneratedQuestion(
	                    getLoginUser(loginUser),
	                    question,
	                    locale);

	    // 保存した問題のIDを返す
	    return savedQuestion.getQuestionId();
	}
	
	/**
	 * AI生成問題の理解度を保存し、次の問題へ進む。
	 * 最後の問題の場合は学習完了処理へ進む。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param questionId 評価する問題のID
	 * @param evaluation 保存する理解度
	 * @param page 現在のページ番号
	 * @param session セッション情報
	 * @param locale 言語・地域情報
	 * @return 次のAI生成問題画面または学習完了処理へのリダイレクト先
	 */
	@PostMapping("/ai-practice/evaluation")
	public String postAiPracticeEvaluation(
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

	    // セッションからAI生成問題を取得
	    List<AiGeneratedQuestionDto> questions =
	            (List<AiGeneratedQuestionDto>) session.getAttribute(
	                    "aiPracticeQuestions");

	    // 最後の問題の場合は学習を完了
	    if (page + 1 >= questions.size()) {
	        return "redirect:/ai-practice/complete";
	    }

	    // 次の問題へ移動
	    return "redirect:/ai-practice/question?page=" + (page + 1);
	}
	
	/**
	 * AI生成学習に使用するセッション情報を削除する。
	 *
	 * @param session セッション情報
	 */
	private void clearAiPracticeSession(HttpSession session) {

	    // AI生成学習に使用するセッション情報を削除
	    session.removeAttribute("aiPracticeQuestions");
	    session.removeAttribute("aiPracticeQuestionsCurrentPage");
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
