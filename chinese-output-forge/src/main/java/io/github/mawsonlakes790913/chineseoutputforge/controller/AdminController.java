package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理者メニューに関するリクエストを処理するController。
 */
@Controller
public class AdminController {

	/**
	 * 管理者メニュー画面を表示する。
	 *
	 * @return 管理者メニュー画面のビュー名
	 */
    @GetMapping("/admin/menu")
    public String getAdminMenu() {
        return "/admin/menu";
    }
}
