package io.github.mawsonlakes790913.chineseoutputforge.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.Evaluation;
import io.github.mawsonlakes790913.chineseoutputforge.constant.FavoriteCondition;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.StudyCondition;

/**
 * 検索条件をRepositoryで使用する値へ変換するUtilクラス。
 * 未指定の検索条件にはデフォルト値を設定する。
 */
@Component
public class SearchConditionConverter {

	/**
	 * 難易度の検索条件を文字列の一覧へ変換する。
	 * 未指定の場合はすべての難易度を設定する。
	 *
	 * @param difficulties 難易度の検索条件
	 * @return 変換後の難易度の一覧
	 */
	public List<String> convertDifficulty(List<Difficulty> difficulties) {

	    List<String> difficultyList;

	    // 未指定の場合はすべての難易度を設定
	    if (difficulties == null || difficulties.isEmpty()) {
	        difficultyList = List.of(
	                Difficulty.BEGINNER.name(),
	                Difficulty.INTERMEDIATE.name(),
	                Difficulty.ADVANCED.name());

	    } else {
	        // 指定された難易度を文字列へ変換
	        difficultyList = new ArrayList<>();

	        for (Difficulty difficulty : difficulties) {
	            difficultyList.add(difficulty.name());
	        }
	    }

	    return difficultyList;
	}
	
	/**
	 * 理解度の検索条件を文字列の一覧へ変換する。
	 * 未指定の場合はすべての理解度を設定する。
	 *
	 * @param evaluations 理解度の検索条件
	 * @return 変換後の理解度の一覧
	 */
	public List<String> convertEvaluation(List<Evaluation> evaluations) {

	    List<String> evaluationList;

	    // 未指定の場合はすべての理解度を設定
	    if (evaluations == null || evaluations.isEmpty()) {
	        evaluationList = List.of(
	                Evaluation.HARD.name(),
	                Evaluation.GOOD.name(),
	                Evaluation.EASY.name());

	    } else {
	        // 指定された理解度を文字列へ変換
	        evaluationList = new ArrayList<>();

	        for (Evaluation evaluation : evaluations) {
	            evaluationList.add(evaluation.name());
	        }
	    }

	    return evaluationList;
	}
	
	/**
	 * お気に入り条件を文字列へ変換する。
	 * 未指定の場合はすべてを対象とする。
	 *
	 * @param favoriteCondition お気に入り条件
	 * @return 変換後のお気に入り条件
	 */
	public String convertFavoriteCondition(
	        FavoriteCondition favoriteCondition) {

	    String convertedFavoriteCondition;

	    // 未指定の場合はすべてを対象とする
	    if (favoriteCondition == null) {
	        convertedFavoriteCondition = FavoriteCondition.ALL.name();

	    } else {
	        // 指定されたお気に入り条件を文字列へ変換
	        convertedFavoriteCondition = favoriteCondition.name();
	    }

	    return convertedFavoriteCondition;
	}
	
	/**
	 * 学習状況の検索条件を文字列へ変換する。
	 * 未指定の場合はすべてを対象とする。
	 *
	 * @param studyCondition 学習状況の検索条件
	 * @return 変換後の学習状況
	 */
	public String convertStudyCondition(
	        StudyCondition studyCondition) {

	    String convertedStudyCondition;

	    // 未指定の場合はすべてを対象とする
	    if (studyCondition == null) {
	        convertedStudyCondition = StudyCondition.ALL.name();

	    } else {
	        // 指定された学習状況を文字列へ変換
	        convertedStudyCondition = studyCondition.name();
	    }

	    return convertedStudyCondition;
	}
	
	/**
	 * 学習対象言語の検索条件を文字列の一覧へ変換する。
	 *
	 * @param languageVariants 学習対象言語の検索条件
	 * @return 変換後の学習対象言語の一覧
	 */
	public List<String> convertLanguageVariant(
	        List<LanguageVariant> languageVariants) {

	    // 変換後の言語区分を保持するリストを作成
	    List<String> convertedLanguageVariants = new ArrayList<>();

	    // 指定された言語区分を文字列へ変換
	    for (LanguageVariant languageVariant : languageVariants) {
	        convertedLanguageVariants.add(languageVariant.name());
	    }

	    return convertedLanguageVariants;
	}
}
