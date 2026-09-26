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

/**
 * 問題のお気に入り機能に関する業務処理を行うService。
 * お気に入りの登録・解除・登録状態の確認を行う。
 */
@Transactional
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteService {
	
	private final FavoriteRepository favoriteRepository;
	private final QuestionRepository questionRepository;
	private final MessageSource messageSource;
	
	/**
	 * 指定した問題のお気に入り状態を切り替える。
	 * 未登録の場合はお気に入りに追加し、登録済みの場合は解除する。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @param locale 現在の言語・地域情報
	 * @return 切り替え後のお気に入り状態
	 */
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

	    // お気に入りの複合キーを作成
	    FavoriteKey key = createFavoriteKey(user, questionId);

	    // 未登録の場合はお気に入りに追加
	    if (!favoriteRepository.existsById(key)) {

	        // 対象の問題を取得
	        Question question = questionRepository.getReferenceById(questionId);

	        // お気に入りを作成してユーザーと問題を設定
	        Favorite favorite = new Favorite();
	        favorite.setFavoriteKey(key);
	        favorite.setUser(user);
	        favorite.setQuestion(question);

	        // お気に入りを保存
	        favoriteRepository.save(favorite);

	        // お気に入り追加をログに記録
	        log.debug(
	                "お気に入り追加 userId={}, questionId={}",
	                user.getId(),
	                questionId);

	        return true;

	    } else {
	        // 登録済みの場合はお気に入りを解除
	        favoriteRepository.deleteById(key);

	        // お気に入り解除をログに記録
	        log.debug(
	                "お気に入り解除 userId={}, questionId={}",
	                user.getId(),
	                questionId);

	        return false;
	    }
	}
	
	/**
	 * 指定した問題がお気に入りに登録されているか確認する。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @return お気に入りに登録されている場合はtrue
	 */
	public boolean isFavorite(Users user, long questionId) {

	    // お気に入りの複合キーを作成
	    FavoriteKey key = createFavoriteKey(user, questionId);

	    return favoriteRepository.existsById(key);
	}
	
	/**
	 * ユーザーと問題からお気に入り情報の複合主キーを作成する。
	 *
	 * @param user ユーザー
	 * @param questionId 問題ID
	 * @return お気に入り情報の複合主キー
	 */
	private FavoriteKey createFavoriteKey(Users user, long questionId) {

	    // ユーザーと問題に紐づくお気に入りの複合キーを作成
	    FavoriteKey key = new FavoriteKey();
	    key.setUserId(user.getId());
	    key.setQuestionId(questionId);

	    return key;
	}
	
}
