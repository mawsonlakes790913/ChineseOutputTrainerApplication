package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AccountStatus;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminUserSearchDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PaginationDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.service.AdminUserService;
import io.github.mawsonlakes790913.chineseoutputforge.service.PaginationService;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class AdminUserController {
	
	private final AdminUserService adminUserService;
	private final PaginationService paginationService;
	
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

}


