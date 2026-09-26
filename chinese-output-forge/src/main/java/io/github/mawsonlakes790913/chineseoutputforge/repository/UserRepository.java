package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

/**
 * アプリケーションのユーザー情報のDB操作を行うRepository。
 */
public interface UserRepository extends JpaRepository<Users, Long> {
	
    /**
     * 指定したログインIDのユーザーを取得する。
     *
     * @param loginId ログインID
     * @return 指定したログインIDのユーザー
     */
	Optional<Users> findByLoginId(String loginId);
	
    /**
     * 指定したログインIDが既に存在するか確認する。
     *
     * @param loginId ログインID
     * @return ログインIDが存在する場合はtrue
     */
	boolean existsByLoginId(String loginId);
	
    /**
     * 指定したメールアドレスが既に存在するか確認する。
     *
     * @param email メールアドレス
     * @return メールアドレスが存在する場合はtrue
     */
	boolean existsByEmail(String email);

    /**
     * 指定したログインIDのユーザーを削除する。
     *
     * @param loginId ログインID
     */
	void deleteByLoginId(String loginId);
	
    /**
     * 指定したメールアドレスのユーザーを取得する。
     *
     * @param email メールアドレス
     * @return 指定したメールアドレスのユーザー
     */
	Optional<Users> findByEmail(String email);
	
    /**
     * 指定された検索条件に一致するユーザーを指定した並び順でページング取得する。
     *
     * @param loginId ログインIDの検索キーワード
     * @param accountStatus アカウント状態の検索条件
     * @param email メールアドレスの検索キーワード
     * @param sortCondition 並び順
     * @param pageable ページング情報
     * @return 条件に一致するユーザーのページ
     */
	@Query(
	        value = """
	                SELECT *
	                FROM users
	                WHERE (
	                    :loginId IS NULL
	                    OR :loginId = ''
	                    OR login_id LIKE CONCAT('%', :loginId, '%')
	                )
	                AND (
	                    :accountStatus = 'ALL'
	                    OR (:accountStatus = 'ACTIVE' AND account_locked = false)
	                    OR (:accountStatus = 'LOCKED' AND account_locked = true)
	                )
	                AND (
	                    :email IS NULL
	                    OR :email = ''
	                    OR LOWER(email)
	                        LIKE LOWER(CONCAT('%', :email, '%'))
	                )
	                ORDER BY
	                    CASE
	                        WHEN :sortCondition = 'LOGIN_ID_ASC'
	                        THEN login_id
	                    END ASC,
	                    CASE
	                        WHEN :sortCondition = 'EMAIL_ASC'
	                        THEN email
	                    END ASC
	                """,
	        countQuery = """
	                SELECT COUNT(*)
	                FROM users
	                WHERE (
	                    :loginId IS NULL
	                    OR :loginId = ''
	                    OR login_id LIKE CONCAT('%', :loginId, '%')
	                )
	                AND (
	                    :accountStatus = 'ALL'
	                    OR (:accountStatus = 'ACTIVE' AND account_locked = false)
	                    OR (:accountStatus = 'LOCKED' AND account_locked = true)
	                )
	                AND (
	                    :email IS NULL
	                    OR :email = ''
	                    OR LOWER(email)
	                        LIKE LOWER(CONCAT('%', :email, '%'))
	                )
	                """,
	        nativeQuery = true
	)
	Page<Users> findUsers(
	        @Param("loginId") String loginId,
	        @Param("accountStatus") String accountStatus,
	        @Param("email") String email,
	        @Param("sortCondition") String sortCondition,
	        Pageable pageable);

}
