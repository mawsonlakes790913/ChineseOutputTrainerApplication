package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 発音表記の設定変更に関するリクエストを処理するController。
 */
@Controller
@RequiredArgsConstructor
public class PronunciationTypeController {
	
	private final UserAccountService userAccountService;

	/**
	 * 発音表記の設定を変更する。
	 * ログイン中の場合はユーザー情報を更新し、変更後の設定をセッションに保存する。
	 *
	 * @param loginUser ログインユーザー情報
	 * @param pronunciationType 変更後の発音表記
	 * @param locale 現在の言語・地域情報
	 * @param session セッション情報
	 * @return ユーザー設定画面へのリダイレクト先
	 */
	@PostMapping("/pronunciation-type")
	public String postPronunciationTypeChange(
	        @AuthenticationPrincipal UserDetails loginUser,
	        @RequestParam PronunciationType pronunciationType,
	        Locale locale,
	        HttpSession session) {

	    // セッションから現在の発音表記を取得
	    PronunciationType currentPronunciationType =
	            (PronunciationType) session.getAttribute("pronunciationType");

	    // 同じ発音表記の場合は変更せず設定画面へ戻る
	    if (pronunciationType == currentPronunciationType) {
	        return "redirect:/user/settings";
	    }

	    // ログイン中の場合はDBの発音表記を更新
	    if (loginUser != null) {
	        userAccountService.updatePronunciationType(
	                loginUser.getUsername(),
	                pronunciationType,
	                locale);
	    }

	    // 変更後の発音表記をセッションに保存
	    session.setAttribute("pronunciationType", pronunciationType);

	    return "redirect:/user/settings";
	}
}
