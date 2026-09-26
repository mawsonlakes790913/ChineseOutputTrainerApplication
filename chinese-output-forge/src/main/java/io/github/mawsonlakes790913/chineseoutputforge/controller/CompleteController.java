package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 学習完了画面に関するリクエストを処理するController。
 */
@Controller
public class CompleteController {
	
    /**
     * 学習完了画面を表示する。
     *
     * @return 学習完了画面のビュー名
     */
	@GetMapping("/complete")
	public String getComplete() {
		return "complete";
	}
}
