package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.StudyCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.FavoriteRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ユーザー用問題一覧に関する業務処理を行うService。
 * 問題の検索・一覧取得、およびユーザーが所有するAI生成問題の削除を行う。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserQuestionService {
	
	private final SearchConditionConverter searchConditionConverter;
	private final QuestionRepository questionRepository;
	private final StructureRepository structureRepository;
	private final FavoriteRepository favoriteRepository;
	private final StudyHistoryRepository studyHistoryRepository;
	
	/**
	 * 指定された検索条件に一致するユーザー用問題一覧を取得する。
	 * 未指定の検索条件には検索用の値を設定してRepositoryへ渡す。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度
	 * @param evaluations 理解度
	 * @param studyCondition 学習状況
	 * @param favoriteCondition お気に入り条件
	 * @param sourceCondition 問題の生成元
	 * @param structureIds 文法・構造IDの一覧
	 * @param languageVariants 学習対象言語
	 * @param listId 問題リストID
	 * @param japaneseKeyword 日本語の検索キーワード
	 * @param chineseKeyword 中国語の検索キーワード
	 * @param pageable ページング情報
	 * @return 条件に一致するユーザー用問題一覧
	 */
	public Page<UserQuestionListDto> getFilteredUserQuestionList(long userId,
			 List<Difficulty> difficulties,
			 List<Evaluation> evaluations,
			 StudyCondition studyCondition,
			 FavoriteCondition favoriteCondition,
			 QuestionSourceCondition sourceCondition,
			 List<Long> structureIds,
			 List<LanguageVariant> languageVariants,
			 Long listId,
			 String japaneseKeyword,
			 String chineseKeyword,
			 Pageable pageable) {
	
	// 文法・構造が未指定の場合はすべて
	if (structureIds == null || structureIds.isEmpty()) {
		structureIds = structureRepository.findAllStructureIds();
	}
	
	// キーワードが未入力の場合は空文字
	if (japaneseKeyword == null) {
	japaneseKeyword = "";
	}
	
	if (chineseKeyword == null) {
	chineseKeyword = "";
	}
	
	// 検索条件をRepository用の値に変換
	List<String> convertedDifficulties = searchConditionConverter.convertDifficulty(difficulties);
	List<String> convertedEvaluations = searchConditionConverter.convertEvaluation(evaluations);
	String convertedStudyCondition = searchConditionConverter.convertStudyCondition(studyCondition);
	String convertedFavoriteCondition = searchConditionConverter.convertFavoriteCondition(favoriteCondition);
	List<String> convertedLanguageVariants =
	        searchConditionConverter.convertLanguageVariant(languageVariants);
	
	// 問題の生成元が未指定の場合はすべて
	if (sourceCondition == null) {
	    sourceCondition = QuestionSourceCondition.ALL;
	}
	
	return questionRepository.findUserQuestionList(
	userId,
	convertedDifficulties,
	convertedEvaluations,
	convertedStudyCondition,
	convertedFavoriteCondition,
	sourceCondition.name(),
	structureIds,
	convertedLanguageVariants,
	listId,
	japaneseKeyword,
	chineseKeyword,
	pageable);
	}		
	
	/**
	 * ユーザーが所有するAI生成問題を削除する。
	 * 削除対象に関連するお気に入りと学習履歴も削除する。
	 *
	 * @param userId ユーザーID
	 * @param questionId 削除する問題ID
	 */
	@Transactional
	public void deleteOwnedQuestion(Long userId, Long questionId) {

		// 削除対象の問題を取得
		Question question = questionRepository.findById(questionId)
		        .orElseThrow();

		// ユーザーが所有するAI生成問題の場合のみ削除
		if (question.isAiGenerated()
		        && question.getOwner().getId().equals(userId)) {

		    // 関連するお気に入りと学習履歴を削除
		    favoriteRepository.deleteByQuestionQuestionId(questionId);
		    studyHistoryRepository
		            .deleteByStudyHistoryKeyQuestionId(questionId);

		    // 問題を削除
		    questionRepository.deleteById(questionId);

		    log.info(
		            "所有問題削除完了 userId={}, questionId={}",
		            userId,
		            questionId);
		}
	}

}
