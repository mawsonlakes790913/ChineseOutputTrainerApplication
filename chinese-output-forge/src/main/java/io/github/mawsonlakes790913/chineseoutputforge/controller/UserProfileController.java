package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditLoginIdForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
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
	
	@GetMapping("/user/edit/loginId")
	public String getEditLoginId(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model,
	        EditLoginIdForm form) {

	    if (form.getLoginId() == null) {
	        Users user = getLoginUser(loginUser);
	        form.setLoginId(user.getLoginId());
	    }
	    
	    model.addAttribute("editLoginIdForm", form);
	    model.addAttribute("currentLoginId", loginUser.getUsername());

	    return "user/edit/loginId";
	}
	
	@PostMapping("/user/edit/loginId")
	public String postEditLoginId(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        Model model,
	        @Validated EditLoginIdForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    if (bindingResult.hasErrors()) {
	        return getEditLoginId(loginUser, model, form);
	    }

	    try {
	    	userAccountService.updateLoginId(
	                loginUser.getUsername(),
	                form.getLoginId(),
	                locale);

	    } catch (DuplicateKeyException e) {

	        bindingResult.rejectValue(
	                "loginId",
	                "duplicate",
	                e.getMessage());

	        model.addAttribute("editLoginIdForm", form);

	        return getEditLoginId(loginUser, model, form);

	    } catch (IllegalArgumentException e) {

	        bindingResult.rejectValue(
	                "loginId",
	                "same",
	                e.getMessage());

	        model.addAttribute("editLoginIdForm", form);

	        return getEditLoginId(loginUser, model, form);
	    }
	    
	    // ログアウト状態にする
	    SecurityContextHolder.clearContext();

	    // SessionからSpring Securityの認証情報だけ削除
	    session.removeAttribute(
	    	    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
	    	);

	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.loginId.changed"
	    );

	    return "redirect:/login";
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}

}
