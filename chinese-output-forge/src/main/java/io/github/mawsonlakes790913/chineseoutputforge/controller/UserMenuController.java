package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserMenuController {
	
	@GetMapping("/user/menu")
	public String getUserMenu() {
		return "/user/menu";
	}

}