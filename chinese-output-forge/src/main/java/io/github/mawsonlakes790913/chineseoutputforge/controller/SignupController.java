package io.github.mawsonlakes790913.chineseoutputforge.controller;


import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.form.SignupForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.SignupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
public class SignupController {
	
	private final SignupService signupService;
	
	@GetMapping("/signup")
	public String getSignup(@ModelAttribute SignupForm form) {
		return "signup/signup";
	}
	
	@PostMapping("/signup")
	public String postSignup(
							 @ModelAttribute SignupForm form,
							 BindingResult bindingResult,
							 RedirectAttributes redirectAttributes) {

	    try {
	    	log.debug("ユーザー登録開始 userId={}", form.getLoginId());
	    	
	    	Users user = new Users();

	    	user.setLoginId(form.getLoginId());
	    	user.setPassword(form.getPassword());
	    	
	        // ② Serviceの業務処理
	    	signupService.signup(user);

	    } catch (DuplicateKeyException e) {

	        // ③ Serviceで発生した重複エラーをBindingResultへ追加
	        bindingResult.rejectValue(
	                "loginId",
	                "duplicate",
	                e.getMessage());

	        return getSignup(form);
	    }
	    
	    redirectAttributes.addFlashAttribute(
	            "signupSuccess",
	            "ユーザー登録が完了しました");

	    return "redirect:/";
	}
	
//	@PostMapping("/signup")
//	public String postSignup(Model model,
//							 @ModelAttribute @Validated SignupForm form,
//							 BindingResult bindingResult) {
//		// ① 通常のバリデーションエラー確認
//	    if (bindingResult.hasErrors()) {
//	        return getSignup(model, form);
//	    }
//
//	    try {
//	    	log.debug("ユーザー登録開始 userId={}", form.getUserId());
//	    	
//	    	Users user = new Users();
//
//	    	user.setLoginId(form.getLoginId());
//	    	user.setPassword(form.getPassword());
//	    	
//	        // ② Serviceの業務処理
//	    	signupService.signup(users);
//
//	    } catch (DuplicateKeyException e) {
//
//	        // ③ Serviceで発生した重複エラーをBindingResultへ追加
//	        bindingResult.rejectValue(
//	                "userId",
//	                "duplicate",
//	                e.getMessage());
//
//	        return getSignup(model, form);
//	    }
//
//	    return "redirect:/signup/complete";
//	}
}
