package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Favorite;
import io.github.mawsonlakes790913.chineseoutputforge.entity.FavoriteKey;


public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteKey> {
	
	Optional<Favorite> findByFavoriteKey(FavoriteKey favoritesKey);
	
}
