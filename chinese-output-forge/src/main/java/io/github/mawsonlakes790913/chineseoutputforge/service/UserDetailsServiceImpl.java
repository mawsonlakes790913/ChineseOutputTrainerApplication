package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import lombok.RequiredArgsConstructor;

/**
 * Spring Securityの認証で使用するユーザー情報を取得するService。
 * ログインIDからユーザーを取得し、UserDetailsへ変換する。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
	
	private final UserAccountService userAccountService;
	
	/**
	 * ログインIDから認証に使用するユーザー情報を取得する。
	 * ユーザーのロールとアカウント凍結状態をUserDetailsへ設定する。
	 *
	 * @param loginId ログインID
	 * @return Spring Securityの認証で使用するユーザー情報
	 * @throws UsernameNotFoundException ユーザーが存在しない場合
	 */
	@Override
	public UserDetails loadUserByUsername(String loginId)
			throws UsernameNotFoundException {
		
		// ログインIDからユーザーを取得
        Users loginUser = userAccountService.getUserOne(loginId);
        
        // ユーザーが存在しない場合は認証エラー
        if (loginUser == null) {
            throw new UsernameNotFoundException("user not found"); 
        }
        
        // ユーザーのロールから権限情報を作成
        GrantedAuthority authority =
                new SimpleGrantedAuthority(
                        "ROLE_" + loginUser.getRole().name()
                );
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(authority);
        
        // 認証に使用するUserDetailsを生成
        return new User(
                loginUser.getLoginId(),
                loginUser.getPassword(),
                true,
                true,
                true,
                !loginUser.isAccountLocked(),
                authorities
        );
	}
}
