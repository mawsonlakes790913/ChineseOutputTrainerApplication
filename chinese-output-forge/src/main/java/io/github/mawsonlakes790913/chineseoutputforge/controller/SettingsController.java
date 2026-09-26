package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 設定画面に関するリクエストを処理するController。
 */
@Controller
public class SettingsController {

	/**
	 * 設定画面を表示する。
	 *
	 * @return 設定画面のビュー名
	 */
    @GetMapping("/settings")
    public String getSettings() {

        return "settings";
    }
}
