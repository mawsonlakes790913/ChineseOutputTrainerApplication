package io.github.mawsonlakes790913.chineseoutputforge.controller;

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

@Controller
@RequiredArgsConstructor
public class FavoriteController {

	
	private final FavoriteService favoriteService;
	private final UserAccountService userAccountService;
	
	@PostMapping("/favorite/toggle")
	@ResponseBody
	public boolean toggleFavorite(
	        @RequestParam Long questionId,
	        @AuthenticationPrincipal UserDetails loginUser) {
		
		// ユーザー情報を取得
		Users user = userAccountService.getUserOne(loginUser.getUsername());

	    return favoriteService.toggleFavorite(
	            user,
	            questionId);
	}
}
