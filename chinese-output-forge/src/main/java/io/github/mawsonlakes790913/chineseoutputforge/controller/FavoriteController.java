package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.FavoriteService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;

/**
 * 問題のお気に入り機能に関するリクエストを処理するController。
 */
@Controller
@RequiredArgsConstructor
public class FavoriteController {

	private final FavoriteService favoriteService;
	private final UserAccountService userAccountService;
	
	/**
	 * 指定された問題のお気に入り状態を切り替える。
	 *
	 * @param questionId お気に入り状態を切り替える問題のID
	 * @param loginUser ログインユーザー情報
	 * @param locale 言語・地域情報
	 * @return 切り替え後のお気に入り状態
	 */
	@PostMapping("/favorite/toggle")
	@ResponseBody
	public boolean toggleFavorite(
	        @RequestParam Long questionId,
	        @AuthenticationPrincipal UserDetails loginUser,
	        Locale locale) {

	    // ログインユーザーを取得
	    Users user =
	            userAccountService.getUserOne(loginUser.getUsername());

	    // お気に入り状態を切り替えて変更後の状態を返す
	    return favoriteService.toggleFavorite(
	            user,
	            questionId,
	            locale);
	}
}
