package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

/**
 * ホーム画面およびAbout画面に関するリクエストを処理するController。
 */
@Controller
public class HomeController {
	
	/**
	 * ホーム画面を表示する。
	 * ログアウトメッセージがセッションに存在する場合は画面に渡し、セッションから削除する。
	 *
	 * @param model 画面に渡すデータ
	 * @param session セッション情報
	 * @return ホーム画面のビュー名
	 */
	@GetMapping("/")
	public String getHome(Model model, HttpSession session) {

	    // セッションからログアウトメッセージを取得
	    String logoutMessage =
	            (String) session.getAttribute("logoutMessage");

	    // メッセージがある場合は画面へ渡し、セッションから削除
	    if (logoutMessage != null) {
	        model.addAttribute("logoutMessage", logoutMessage);
	        session.removeAttribute("logoutMessage");
	    }

	    return "/home";
	}
	
	/**
	 * 現在の言語設定に対応するAbout画面を表示する。
	 *
	 * @param locale 現在の言語・地域情報
	 * @return 言語設定に対応するAbout画面のビュー名
	 */
	@GetMapping("/about")
	public String getAbout(Locale locale) {

	    // 簡体字中国語の場合は中国大陸向けAbout画面を表示
	    if (Locale.SIMPLIFIED_CHINESE.equals(locale)) {
	        return "about/about-zh-cn";
	    }

	    // 繁体字中国語の場合は台湾向けAbout画面を表示
	    if (Locale.TRADITIONAL_CHINESE.equals(locale)) {
	        return "about/about-zh-tw";
	    }

	    // その他の場合は日本語のAbout画面を表示
	    return "about/about";
	}
    

}
