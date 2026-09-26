package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

/**
 * ログイン画面に関するリクエストを処理するController。
 */
@Controller
public class LoginController {

	/**
	 * ログイン画面を表示する。
	 * ログインエラーメッセージがセッションに存在する場合は画面に渡し、セッションから削除する。
	 *
	 * @param model 画面に渡すデータ
	 * @param session セッション情報
	 * @return ログイン画面のビュー名
	 */
	@GetMapping("/login")
	public String getLogin(Model model, HttpSession session) {

	    // セッションからログインエラーメッセージを取得
	    String loginErrorMessage =
	            (String) session.getAttribute("loginErrorMessage");

	    // メッセージがある場合は画面へ渡し、セッションから削除
	    if (loginErrorMessage != null) {
	        model.addAttribute("loginErrorMessage", loginErrorMessage);
	        session.removeAttribute("loginErrorMessage");
	    }

	    return "/login";
	}
}
