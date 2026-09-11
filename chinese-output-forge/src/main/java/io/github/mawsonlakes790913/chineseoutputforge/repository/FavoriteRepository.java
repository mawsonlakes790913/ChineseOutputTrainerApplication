package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Favorite;
import io.github.mawsonlakes790913.chineseoutputforge.entity.FavoriteKey;


public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteKey> {
	
	Optional<Favorite> findByFavoriteKey(FavoriteKey favoritesKey);
	
	@Modifying
	@Query("""
	        DELETE FROM Favorite f
	        WHERE f.favoriteKey.userId = :userId
	        """)
	void deleteByFavoriteKeyUserId(
	        @Param("userId") Long userId);
	
	void deleteByQuestionQuestionId(Long questionId);
	
}
