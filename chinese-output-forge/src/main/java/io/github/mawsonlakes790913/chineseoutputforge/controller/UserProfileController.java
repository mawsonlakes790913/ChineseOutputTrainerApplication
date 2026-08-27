package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class UserProfileController {
	
	private final UserAccountService userAccountService;
	
	@GetMapping("/user/profile")
	public String getUserProfile(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model) {

	    Users user = getLoginUser(loginUser);
	    model.addAttribute("user", user);

	    return "user/profile";
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
