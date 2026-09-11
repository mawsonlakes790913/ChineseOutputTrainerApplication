package io.github.mawsonlakes790913.chineseoutputforge.controller;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AccountStatus;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminUserSearchDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminUserService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import io.github.mawsonlakes790913.chineseoutputforge.service.UserAccountService;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class AdminUserController {
	
	private final AdminUserService adminUserService;
	private final PaginationService paginationService;
	private final UserAccountService userAccountService;
	
	@GetMapping("/admin/user/list")
	public String getUserList(
	        @ModelAttribute AdminUserSearchDto searchDto,
	        @PageableDefault(page = 0, size = 50) Pageable pageable,
	        Model model) {
		
	    if (searchDto.getAccountStatus() == null) {
	        searchDto.setAccountStatus(AccountStatus.ALL);
	    }

	    Page<Users> userList =
	            adminUserService.getUsers(searchDto, pageable);

	    PaginationDto pagination =
	            paginationService.createPagination(userList);

	    // 一覧
	    model.addAttribute("userList", userList.getContent());
	    model.addAttribute("page", userList);
	    model.addAttribute("pagination", pagination);

	    // 検索条件
	    model.addAttribute("searchDto", searchDto);

	    return "/admin/user/list";
	}
	
	// ユーザー凍結
	@PostMapping("/admin/user/lock")
	public String lockUser(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes) {

	    adminUserService.lockUser(userId);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーを凍結しました。");

	    return "redirect:/admin/user/list";
	}


	// ユーザー凍結解除
	@PostMapping("/admin/user/unlock")
	public String unlockUser(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes) {

	    adminUserService.unlockUser(userId);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーの凍結を解除しました。");

	    return "redirect:/admin/user/list";
	}
	
	// ユーザー削除
	@PostMapping("/admin/user/delete")
	public String deleteUser(
	        @RequestParam Long userId,
	        RedirectAttributes redirectAttributes,
	        Locale local) {

	    userAccountService.deleteUser(userId, local);

	    redirectAttributes.addFlashAttribute(
	            "successMessage",
	            "ユーザーを削除しました。");

	    return "redirect:/admin/user/list";
	}

}


