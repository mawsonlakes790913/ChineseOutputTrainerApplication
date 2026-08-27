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
import io.github.mawsonlakes790913.chineseoutputforge.exception.CurrentPasswordMismatchException;
import io.github.mawsonlakes790913.chineseoutputforge.exception.PasswordSameException;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditLoginIdForm;
import io.github.mawsonlakes790913.chineseoutputforge.form.EditPasswordForm;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Controller
@RequiredArgsConstructor
@Slf4j
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
	
	@GetMapping("/user/edit/password")
	public String getEditPassword(
	        Model model,
	        EditPasswordForm form) {
		model.addAttribute("editpasswordForm", form);
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
	    	log.debug("パスワード変更開始 loginId={}", loginUser.getUsername());

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

	        model.addAttribute("editPasswordForm", form);

	        return getEditPassword(model, form);

	    } catch (IllegalArgumentException e) {

	        bindingResult.reject(
	                "userNotFound",
	                e.getMessage()
	        );

	        model.addAttribute("editPasswordForm", form);

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
	    SecurityContextHolder.clearContext();

	    // SessionからSpring Securityの認証情報だけ削除
	    session.removeAttribute(
	    	    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
	    	);

	    // 変更完了メッセージ
	    redirectAttributes.addFlashAttribute(
	            "messageKey",
	            "user.password.changed"
	    );
	    
	    return "redirect:/login";
	    
	}
	
	@PostMapping("/user/delete")
	public String cancelMembership(
	        @AuthenticationPrincipal UserDetails loginUser,
	        HttpServletRequest request)
	        throws ServletException {

	    userAccountService.cancelMembership(
	            loginUser.getUsername()
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

}
