package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
	
	// 指定したログインIDのユーザーを取得
	Optional<Users> findByLoginId(String loginId);
	
	// 指定したログインIDが既に存在するか確認
	boolean existsByLoginId(String loginId);
	
	// 指定したメールアドレスが既に存在するか確認
	boolean existsByEmail(String email);

	// 指定したログインIDのユーザーを削除
	void deleteByLoginId(String loginId);
	
	// 指定したメールアドレスのユーザーを取得
	Optional<Users> findByEmail(String email);
	
	// 検索条件に一致するユーザーを指定した並び順でページング取得
	@Query("""
	        SELECT u
	        FROM Users u
	        WHERE
	            (:loginId IS NULL
	                OR :loginId = ''
	                OR u.loginId LIKE CONCAT('%', :loginId, '%'))
	        AND
	            (:accountStatus = 'ALL'
	                OR (:accountStatus = 'ACTIVE' AND u.accountLocked = false)
	                OR (:accountStatus = 'LOCKED' AND u.accountLocked = true))
			AND (
			    :email IS NULL
			    OR :email = ''
			    OR LOWER(u.email)
			        LIKE LOWER(CONCAT('%', :email, '%'))
			)
			ORDER BY
				CASE
				    WHEN :sortCondition = 'LOGIN_ID_ASC'
				    THEN u.loginId
				END ASC,
			    CASE
			        WHEN :sortCondition = 'EMAIL_ASC'
			        THEN u.email
			    END ASC
	        """)
	Page<Users> findUsers(
	        @Param("loginId") String loginId,
	        @Param("accountStatus") String accountStatus,
	        @Param("email") String email,
	        @Param("sortCondition") String sortCondition,
	        Pageable pageable);

}
