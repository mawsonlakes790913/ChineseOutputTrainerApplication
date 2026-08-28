package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.mawsonlakes790913.chineseoutputforge.constant.PronunciationType;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PronunciationTypeController {
	
	private final UserAccountService userAccountService;

    @GetMapping("/pronunciation-type")
    public String changePronunciationType(
    		@AuthenticationPrincipal UserDetails loginUser,
            @RequestParam PronunciationType pronunciationType,
            Locale locale,
            HttpSession session) {
    	
    	PronunciationType current =
	            (PronunciationType) session.getAttribute("pronunciationType");
    	
	    // 同じ発音記号なら変更処理をしない
	    if (pronunciationType == current) {
	        return "redirect:/user/settings";
	    }
	    
	    // ログインしていればDBの発音記号を更新
	    if (loginUser != null) {
	    	userAccountService.updatePronunciationType(loginUser.getUsername(), 
	    											 pronunciationType, 
	    											 locale);
	    }

	    // 発音記号をSessionに保存
        session.setAttribute("pronunciationType", pronunciationType);

        return "redirect:/user/settings";
    }
}
