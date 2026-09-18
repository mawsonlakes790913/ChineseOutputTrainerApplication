package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.PasswordResetToken;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {
	
	Optional<PasswordResetToken> findByUser(Users user);

    Optional<PasswordResetToken> findByToken(String token);
    
}
