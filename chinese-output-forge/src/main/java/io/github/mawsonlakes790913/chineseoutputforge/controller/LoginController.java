package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

	@GetMapping("/login")
	public String getLogin(Model model, HttpSession session) {
		
	    String loginErrorMessage =
	            (String) session.getAttribute("loginErrorMessage");

	    if (loginErrorMessage != null) {
	        model.addAttribute("loginErrorMessage", loginErrorMessage);
	        session.removeAttribute("loginErrorMessage");
	    }
		
		return "/login";
	}
}
