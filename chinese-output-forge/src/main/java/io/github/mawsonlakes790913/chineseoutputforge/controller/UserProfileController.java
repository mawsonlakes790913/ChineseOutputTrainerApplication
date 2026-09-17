package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

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
import io.github.mawsonlakes790913.chineseoutputforge.exception.CurrentPasswordMismatchException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.DuplicateSignupException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.PasswordSameException;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditEmailForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditLoginIdForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditPasswordForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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

	    Users user = getLoginUser(loginUser);

	    // 現在のユーザーIDを表示するため
	    model.addAttribute(
	            "currentLoginId",
	            user.getLoginId()
	    );

	    // 新しいユーザーIDの入力フォーム
	    model.addAttribute(
	            "editLoginIdForm",
	            form
	    );

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
	        
	    } catch (DuplicateSignupException e) {
	        
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getEditLoginId(loginUser, model, form);

	    } catch (IllegalArgumentException e) {

	        bindingResult.rejectValue(
	                "loginId",
	                "same",
	                e.getMessage());

	        return getEditLoginId(loginUser, model, form);
	    }
	    
	    // ログアウト状態にする
	    clearAuthentication(session);

	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.loginId.changed"
	    );

	    return "redirect:/login";
	}
	
	@GetMapping("/user/edit/email")
	public String getEditEmail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        Model model,
	        EditEmailForm form) {

	    Users user = getLoginUser(loginUser);

	    // 現在のユーザーIDを表示するため
	    model.addAttribute(
	            "currentEmail",
	            user.getEmail()
	    );

	    // 新しいユーザーIDの入力フォーム
	    model.addAttribute(
	            "editEmailForm",
	            form
	    );

	    return "user/edit/email";
	}
	
	@PostMapping("/user/edit/email")
	public String postEditEmail(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpSession session,
	        Model model,
	        @Validated EditEmailForm form,
	        BindingResult bindingResult,
	        Locale locale,
	        RedirectAttributes redirectAttributes) {

	    if (bindingResult.hasErrors()) {
	        return getEditEmail(loginUser, model, form);
	    }

	    try {
	    	userAccountService.updateEmail(
	    			loginUser.getUsername(),
	                form.getEmail(),
	                locale);

	    } catch (DuplicateSignupException e) {
	        
	        bindingResult.rejectValue(
	                e.getField(),
	                "duplicate",
	                e.getMessage());

	        return getEditEmail(loginUser, model, form);

	    } catch (IllegalArgumentException e) {

	        bindingResult.rejectValue(
	                "email",
	                "same",
	                e.getMessage());

	        return getEditEmail(loginUser, model, form);
	    }
	    
	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.email.changed");

	    return "redirect:/user/profile";
	}
	
	@GetMapping("/user/edit/password")
	public String getEditPassword(
	        Model model,
	        EditPasswordForm form) {
		model.addAttribute("editPasswordForm", form);
		return "user/edit/password";
	}
	
	@PostMapping("/user/edit/password")
	public String postEditPassword(@AuthenticationPrincipal UserDetails loginUser,
	        				 HttpSession session,
	        				 Model model,
							 @Validated EditPasswordForm form,
							 BindingResult bindingResult,
							 Locale locale,
							 RedirectAttributes redirectAttributes) {
		
		
		// ① 通常のバリデーションエラー確認
	    if (bindingResult.hasErrors()) {
	        return getEditPassword(model, form);
	    }


	    try {
	        // Serviceの業務処理
	        userAccountService.updatePassword(
	                loginUser.getUsername(),
	                form.getCurrentPassword(),
	                form.getNewPassword(),
	                locale);

	    } catch (CurrentPasswordMismatchException e) {

	        bindingResult.rejectValue(
	                "currentPassword",
	                "invalid",
	                e.getMessage()
	        );

	        return getEditPassword(model, form);

	    } catch (IllegalArgumentException e) {

	        bindingResult.reject(
	                "userNotFound",
	                e.getMessage()
	        );

	        return getEditPassword(model, form);
	    
	    } catch (PasswordSameException e) {

	        bindingResult.rejectValue(
	                "newPassword",
	                "same",
	                e.getMessage()
	        );

	        model.addAttribute("editPasswordForm", form);

	        return getEditPassword(model, form);
	    }

	    // ログアウト状態にする
	    clearAuthentication(session);

	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.password.changed"
	    );
	    
	    return "redirect:/login";
	    
	}
	
	@PostMapping("/user/delete")
	public String postUserDelete(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpServletRequest request,
	        Locale locale)
	        throws ServletException {

	    userAccountService.cancelMembership(
	            loginUser.getUsername(),
	            locale
	    );

	    request.logout();

	    return "redirect:/user/canceled";
	}
	
	@GetMapping("/user/canceled")
	public String getCanceled() {
		return "user/canceled";
	}
	
	private Users getLoginUser(UserDetails loginUser) {
		return userAccountService.getUserOne(loginUser.getUsername());
	}
	
	private void clearAuthentication(HttpSession session) {
	    SecurityContextHolder.clearContext();

	    session.removeAttribute(
	            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
	    );
	}

}
