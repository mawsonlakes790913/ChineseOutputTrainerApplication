package io.github.mawsonlakes790913.chineseoutputforge.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.ReviewQuestionLimit;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StructureRepository;
import io.github.mawsonlakes790913.chineseoutputforge.repository.StudyHistoryRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import lombok.RequiredArgsConstructor;

/**
 * 復習に関する業務処理を行うService。
 * 復習条件に一致する問題数の取得および復習問題の取得を行う。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
	
	private final StudyHistoryRepository studyHistoryRepository;
	private final SearchConditionConverter searchConditionConverter;
	private final StructureRepository structureRepository;
	
	/**
	 * 指定された復習条件に一致する問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariants 学習対象言語
	 * @param evaluations 理解度
	 * @param difficulties 難易度
	 * @param favoriteCondition お気に入り条件
	 * @param sourceCondition 問題の生成元
	 * @param structureIds 文法・構造IDの一覧
	 * @return 復習条件に一致する問題数
	 */
	public long countReviewQuestions(Long userId, List<LanguageVariant> languageVariants,
												  List<Evaluation> evaluations, 
												  List<Difficulty> difficulties,
												  FavoriteCondition favoriteCondition,
												  QuestionSourceCondition sourceCondition,
												  List<Long> structureIds) {
		
		// 文法・構造が未指定の場合はすべて
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		// 問題の生成元が未指定の場合はすべて
		if (sourceCondition == null) {
		    sourceCondition = QuestionSourceCondition.ALL;
		}
		
		// 検索条件をRepository用の値に変換
		List<String> convertedLanguageVariants = searchConditionConverter.convertLanguageVariant(languageVariants);
		List<String> convertedDifficulties = searchConditionConverter.convertDifficulty(difficulties);
		List<String> convertedEvaluations = searchConditionConverter.convertEvaluation(evaluations);
		String convertedFavoriteCondition = searchConditionConverter.convertFavoriteCondition(favoriteCondition);
		
	    return studyHistoryRepository.countReviewQuestions(
	    		userId,
	    		convertedLanguageVariants,
	    		convertedEvaluations,
	    		convertedDifficulties,
	    		convertedFavoriteCondition,
	    		sourceCondition.name(),
	    		structureIds
	    		);
	}
	
	/**
	 * 指定された復習条件に一致する問題を取得する。
	 * 出題数の設定に応じて取得対象を切り替え、
	 * 指定された場合は取得した問題をランダムに並び替える。
	 *
	 * @param userId ユーザーID
	 * @param languageVariants 学習対象言語
	 * @param evaluations 理解度
	 * @param difficulties 難易度
	 * @param favoriteCondition お気に入り条件
	 * @param sourceCondition 問題の生成元
	 * @param structureIds 文法・構造IDの一覧
	 * @param questionLimit 最大出題数
	 * @param random ランダムに並び替える場合はtrue
	 * @return 復習で使用する問題の一覧
	 */
	public List<Question> getQuestion(Long userId, 
									  List<LanguageVariant> languageVariants,
									  List<Evaluation> evaluations, 
									  List<Difficulty> difficulties,
									  FavoriteCondition favoriteCondition,
									  QuestionSourceCondition sourceCondition,
									  List<Long> structureIds,
									  ReviewQuestionLimit questionLimit,
									  boolean random){
		
		// 文法・構造が未指定の場合はすべて
		if (structureIds == null || structureIds.isEmpty()) {
			structureIds = structureRepository.findAllStructureIds();
		}
		
		// 問題の生成元が未指定の場合はすべて
		if (sourceCondition == null) {
		    sourceCondition = QuestionSourceCondition.ALL;
		}
		
	    // 出題数が未指定の場合は最大50問
	    if (questionLimit == null) {
	        questionLimit = ReviewQuestionLimit.LIMIT_50;
	    }
	    
	    // 検索条件をRepository用の値に変換
	    List<String> convertedLanguageVariants =
	            searchConditionConverter.convertLanguageVariant(languageVariants);
	    List<String> convertedEvaluations =
	            searchConditionConverter.convertEvaluation(evaluations);
	    List<String> convertedDifficulties =
	            searchConditionConverter.convertDifficulty(difficulties);
	    String convertedFavoriteCondition =
	            searchConditionConverter.convertFavoriteCondition(favoriteCondition);
	    List<Question> extractedQuestions;
		
	    // 出題数によって取得方法を切り替える
	    if (questionLimit == ReviewQuestionLimit.ALL) {

	        extractedQuestions =
	                studyHistoryRepository.findAllReviewQuestions(
	                        userId,
	                        convertedLanguageVariants,
	                        convertedEvaluations,
	                        convertedDifficulties,
	                        convertedFavoriteCondition,
	                        sourceCondition.name(),
	                        structureIds);

	    } else {

	        extractedQuestions =
	                studyHistoryRepository.findReviewQuestions(
	                        userId,
	                        convertedLanguageVariants,
	                        convertedEvaluations,
	                        convertedDifficulties,
	                        convertedFavoriteCondition,
	                        sourceCondition.name(),
	                        structureIds);
	    }
		
	    // 必要に応じて取得した問題をシャッフル
	    if (random) {
	        Collections.shuffle(extractedQuestions);
	    }

	    return extractedQuestions;
	}

}
