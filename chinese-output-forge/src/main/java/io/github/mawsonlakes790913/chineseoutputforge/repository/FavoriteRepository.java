package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Favorite;
import io.github.mawsonlakes790913.chineseoutputforge.entity.FavoriteKey;


/**
 * お気に入り情報のDB操作を行うRepository。
 */
public interface FavoriteRepository
        extends JpaRepository<Favorite, FavoriteKey> {

    /**
     * 指定したお気に入り情報を取得する。
     *
     * @param favoritesKey お気に入り情報の複合主キー
     * @return お気に入り情報
     */
    Optional<Favorite> findByFavoriteKey(FavoriteKey favoritesKey);

    /**
     * 指定した問題のお気に入りをすべて削除する。
     *
     * @param questionId 問題ID
     */
    void deleteByQuestionQuestionId(Long questionId);

    /**
     * 指定したユーザーのお気に入りをすべて削除する。
     *
     * @param userId ユーザーID
     */
    @Modifying
    @Query(value = """
        DELETE FROM favorite
        WHERE user_id = :userId
        """, nativeQuery = true)
    void deleteByUserId(
            @Param("userId") Long userId);
}
