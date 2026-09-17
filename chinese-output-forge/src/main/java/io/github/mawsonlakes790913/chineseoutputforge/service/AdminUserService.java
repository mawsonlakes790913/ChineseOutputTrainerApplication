package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.AccountStatus;
import io.github.mawsonlakes790913.chineseoutputforge.constant.AdminUserSortCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Role;
import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminUserSearchDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {
	
	private final UserRepository userRepository;
	
	// ユーザー一覧取得
	public Page<Users> getUsers(
	        AdminUserSearchDto searchDto,
	        AdminUserSortCondition sortCondition,
	        Pageable pageable) {

	    if (searchDto.getAccountStatus() == null) {
	        searchDto.setAccountStatus(AccountStatus.ALL);
	    }

	    return userRepository.findUsers(
	            searchDto.getLoginId(),
	            searchDto.getAccountStatus().name(),
	            searchDto.getEmail(),
	            sortCondition.name(),
	            pageable);
	}
	
	// ユーザー凍結
	public void lockUser(Long userId) {

	    Users user = userRepository.findById(userId)
	            .orElseThrow();
	    
	    if (user.getRole() == Role.ADMIN) {
	        throw new IllegalStateException("管理者ユーザーは凍結できません。");
	    }
	    
	    user.setAccountLocked(true);
	    userRepository.save(user);
	    
	    log.info(
	            "ユーザー凍結完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());
	}
	
	// ユーザー凍結解除
	public void unlockUser(Long userId) {

	    Users user = userRepository.findById(userId)
	            .orElseThrow();

	    user.setAccountLocked(false);
	    userRepository.save(user);
	    
	    log.info(
	            "ユーザー凍結解除完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());
	}

}
