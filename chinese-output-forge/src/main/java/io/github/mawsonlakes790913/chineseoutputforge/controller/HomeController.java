package io.github.mawsonlakes790913.chineseoutputforge.controller;

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
    public String getTutorial() {
        return "/about";
    }

}
