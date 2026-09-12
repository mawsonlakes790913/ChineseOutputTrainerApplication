package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {
	
	@GetMapping("/")
	public String getHome(Model model, HttpSession session) {
		
		String logoutMessage =
		        (String) session.getAttribute("logoutMessage");

		if (logoutMessage != null) {
		    model.addAttribute("logoutMessage", logoutMessage);
		    session.removeAttribute("logoutMessage");
		}
		
	    return "/home";
	}
	
	@GetMapping("/about")
	public String getAbout(Locale locale) {

	    if (Locale.SIMPLIFIED_CHINESE.equals(locale)) {
	        return "about/about-zh-cn";
	    }

	    if (Locale.TRADITIONAL_CHINESE.equals(locale)) {
	        return "about/about-zh-tw";
	    }

	    return "about/about";
	}
    

}
