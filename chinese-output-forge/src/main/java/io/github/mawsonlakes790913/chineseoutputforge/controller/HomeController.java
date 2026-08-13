package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
	
	@GetMapping("/")
	public String getHome() {
	    return "/home";
	}
	
    @GetMapping("/about")
    public String getTutorial() {
        return "/about";
    }

}
