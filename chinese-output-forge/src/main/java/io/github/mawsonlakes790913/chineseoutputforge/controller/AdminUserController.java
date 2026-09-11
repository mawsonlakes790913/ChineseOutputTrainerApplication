package io.github.mawsonlakes790913.chineseoutputforge.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
			@PageableDefault(page = 0, size = 50) Pageable pageable,
			Model model
			) {
		Page<Users> userList = adminUserService.getUsers(pageable);
		
	    PaginationDto pagination =
	    		paginationService.createPagination(userList);
	    
	    // 一覧
	    model.addAttribute("userList", userList.getContent());
	    model.addAttribute("page", userList);
	    model.addAttribute("pagination", pagination);
	    
		return "/admin/user/list";
		
	}

}


