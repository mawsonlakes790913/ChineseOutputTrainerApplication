package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
	
	Optional<Users> findByLoginId(String loginId);
	
	boolean existsByLoginId(String loginId);

	void deleteByLoginId(String loginId);
	
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
	        """)
	Page<Users> findUsers(
	        @Param("loginId") String loginId,
	        @Param("accountStatus") String accountStatus,
	        Pageable pageable);

}
