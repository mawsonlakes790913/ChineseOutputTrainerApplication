package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {
	
	@GetMapping("/admin/menu")
	public String getAdminMenu() {
		return "/admin/menu";
	}

}
