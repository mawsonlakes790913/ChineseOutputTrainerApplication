package io.github.mawsonlakes790913.chineseoutputforge.controller;


import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
import io.github.mawsonlakes790913.chineseoutputforge.form.SignupForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.SignupService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class SignupController {
	
	private final SignupService signupService;
	private final MessageSource messageSource;
	
	@GetMapping("/signup")
	public String getSignup(@ModelAttribute SignupForm form) {
		return "signup/signup";
	}
	
	@PostMapping("/signup")
	public String postSignup(
							 @ModelAttribute @Validated SignupForm form,
							 BindingResult bindingResult,
							 RedirectAttributes redirectAttributes,
							 Locale locale) {

		// ① 通常のバリデーションエラー確認
	    if (bindingResult.hasErrors()) {
	        return getSignup(form);
	    }
		
	    try {
	    	// ② Serviceの業務処理
	    	signupService.signup(form, locale);

	    } catch (DuplicateSignupException e) {

	        // ③ Serviceで発生した重複エラーをBindingResultへ追加
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getSignup(form);
	    }
	    
	    redirectAttributes.addFlashAttribute(
	            "signupSuccess",
	            messageSource.getMessage(
	                    "signup.success",
	                    null,
	                    locale));

	    return "redirect:/login";
	}
}
