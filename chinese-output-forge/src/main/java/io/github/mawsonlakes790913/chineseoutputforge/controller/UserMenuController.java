package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ユーザーメニューおよびユーザー設定画面に関するリクエストを処理するController。
 */
@Controller
public class UserMenuController {
	
	/**
	 * ユーザーメニュー画面を表示する。
	 *
	 * @return ユーザーメニュー画面のビュー名
	 */
	@GetMapping("/user/menu")
	public String getUserMenu() {
		return "/user/menu";
	}
	
	/**
	 * ユーザー設定画面を表示する。
	 *
	 * @return ユーザー設定画面のビュー名
	 */
	@GetMapping("/user/settings")
	public String getSettings() {
	    return "user/settings";
	}

}