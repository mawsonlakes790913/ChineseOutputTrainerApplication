package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Favorite;
import io.github.mawsonlakes790913.chineseoutputforge.entity.FavoriteKey;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Users;
import io.github.mawsonlakes790913.chineseoutputforge.repository.FavoriteRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Transactional
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteService {
	
	private final FavoriteRepository favoriteRepository;
	private final QuestionRepository questionRepository;
	private final MessageSource messageSource;
	
	public boolean toggleFavorite(Users user, long questionId, Locale locale) {
		
		// 操作可能な問題か確認
		if (!questionRepository.existsAccessibleQuestion(
		        questionId,
		        user.getId())) {

		    throw new IllegalArgumentException(
		            messageSource.getMessage(
		                    "question.error.accessDenied",
		                    null,
		                    locale));
		}
		
		FavoriteKey key = createFavoriteKey(user, questionId);
		
		// 存在確認とINSERT及びDELETE処理
		if (!favoriteRepository.existsById(key)) {
			
			//ここでINSERT
			Question question =
			        questionRepository.getReferenceById(questionId);

			Favorite favorite = new Favorite();
			favorite.setFavoriteKey(key);
			favorite.setUser(user);
			favorite.setQuestion(question);

			favoriteRepository.save(favorite);
	        
	        log.debug("お気に入り追加 userId={}, questionId={}",
	                 user.getId(), questionId);
	        
	        return true;
		    
		} else {

	        favoriteRepository.deleteById(key);

	        log.debug("お気に入り解除 userId={}, questionId={}",
	                 user.getId(), questionId);
			return false;

		}
	}
	
	public boolean isFavorite(Users user, long questionId) {
		
		FavoriteKey key = createFavoriteKey(user, questionId);
		
		return favoriteRepository.existsById(key);
	}
	

	private FavoriteKey createFavoriteKey(Users user, long questionId) {
	
	// 複合キー情報を取得
	FavoriteKey key = new FavoriteKey();
	key.setUserId(user.getId());
	key.setQuestionId(questionId);
	
	return key;
	}
	
}
