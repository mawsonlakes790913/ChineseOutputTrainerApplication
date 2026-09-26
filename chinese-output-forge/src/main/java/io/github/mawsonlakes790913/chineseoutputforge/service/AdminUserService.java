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

/**
 * 管理者用のユーザー管理に関する業務処理を行うService。
 * ユーザーの一覧取得・凍結・凍結解除を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {
	
	private final UserRepository userRepository;
	
	/**
	 * 指定された検索条件に一致するユーザーをページング取得する。
	 *
	 * @param searchDto ユーザーの検索条件
	 * @param sortCondition 並び順
	 * @param pageable ページング情報
	 * @return 条件に一致するユーザーのページ
	 */
	public Page<Users> getUsers(
	        AdminUserSearchDto searchDto,
	        AdminUserSortCondition sortCondition,
	        Pageable pageable) {

	    // アカウント状態が未指定の場合はすべて
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
	
	/**
	 * 指定したユーザーのアカウントを凍結する。
	 * 管理者ユーザーは凍結対象外とする。
	 *
	 * @param userId 凍結するユーザーID
	 */
	public void lockUser(Long userId) {

	    // 凍結対象のユーザーを取得
	    Users user = userRepository.findById(userId)
	            .orElseThrow();
	    
	    // 管理者ユーザーは凍結不可
	    if (user.getRole() == Role.ADMIN) {
	        throw new IllegalStateException(
	                "管理者ユーザーは凍結できません。");
	    }
	    
	    // アカウントを凍結
	    user.setAccountLocked(true);
	    userRepository.save(user);
	    
	    log.info(
	            "ユーザー凍結完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());
	}
	
	/**
	 * 指定したユーザーのアカウント凍結を解除する。
	 *
	 * @param userId 凍結を解除するユーザーID
	 */
	public void unlockUser(Long userId) {

	    // 凍結解除対象のユーザーを取得
	    Users user = userRepository.findById(userId)
	            .orElseThrow();

	    // アカウントの凍結を解除
	    user.setAccountLocked(false);
	    userRepository.save(user);
	    
	    log.info(
	            "ユーザー凍結解除完了 userId={}, loginId={}",
	            user.getId(),
	            user.getLoginId());
	}
}
