package io.github.mawsonlakes790913.chineseoutputforge.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {
	
	private final UserRepository userRepository;
	
	// ユーザー一覧取得
	public Page<Users> getUsers(Pageable pageable){
		Page<Users> users = userRepository.findAll(pageable);
		return users;
	}

}
