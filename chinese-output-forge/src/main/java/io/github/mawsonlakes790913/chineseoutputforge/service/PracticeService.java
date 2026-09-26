package io.github.mawsonlakes790913.chineseoutputforge.service;



import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.mawsonlakes790913.chineseoutputforge.constant.Difficulty;
import io.github.mawsonlakes790913.chineseoutputforge.constant.LanguageVariant;
import io.github.mawsonlakes790913.chineseoutputforge.constant.QuestionSourceCondition;
import io.github.mawsonlakes790913.chineseoutputforge.dto.NewPracticeCountDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.PracticeMenuDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.repository.QuestionRepository;
import io.github.mawsonlakes790913.chineseoutputforge.util.SearchConditionConverter;
import io.github.mawsonlakes790913.chineseoutputforge.value.Range;
import lombok.RequiredArgsConstructor;

/**
 * 通常学習に関する業務処理を行うService。
 * 通常学習で使用する問題の取得、問題数の集計、出題範囲の作成を行う。
 */
@Service
@RequiredArgsConstructor
public class PracticeService {
	
	private final QuestionRepository questionRepository;
	private final SearchConditionConverter searchConditionConverter;
	private static final int PRACTICE_RANGE_SIZE = 50;
	
	/**
	 * 通常学習で使用する問題を取得する。
	 * 非ログインユーザーとログインユーザーで取得対象を切り替え、
	 * 指定された場合は取得した問題をランダムに並び替える。
	 *
	 * @param userId ユーザーID。非ログインの場合はnull
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @param sourceCondition 問題の生成元
	 * @param start 出題範囲の開始位置
	 * @param random ランダムに並び替える場合はtrue
	 * @return 通常学習で使用する問題の一覧
	 */
	public List<Question> getPracticeQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        Difficulty difficulty,
	        QuestionSourceCondition sourceCondition,
	        int start,
	        boolean random) {

	    // 出題開始位置からデータ取得用のオフセットを算出
	    int offset = start - 1;

	    List<Question> extractedQuestions;

	    // 非ログインの場合はオリジナル問題のみ取得
	    if (userId == null) {
	        extractedQuestions =
	                questionRepository.findAnonymousPracticeQuestions(
	                        languageVariant.name(),
	                        difficulty.name(),
	                        offset);

	    } else {
	        // 条件が未指定の場合はすべて
	        if (sourceCondition == null) {
	            sourceCondition = QuestionSourceCondition.ALL;
	        }

	        // ログインユーザーが利用可能な問題を条件に応じて取得
	        extractedQuestions =
	                questionRepository.findPracticeQuestions(
	                        userId,
	                        languageVariant.name(),
	                        difficulty.name(),
	                        sourceCondition.name(),
	                        offset);
	    }

	    // ランダム出題の場合は問題をシャッフル
	    if (random) {
	        Collections.shuffle(extractedQuestions);
	    }

	    return extractedQuestions;
	}
	
	/**
	 * 通常学習で使用できる問題数を難易度別に取得する。
	 * 各難易度の問題数と出題範囲を作成して返す。
	 *
	 * @param userId ユーザーID。非ログインの場合はnull
	 * @param languageVariant 学習対象言語
	 * @param sourceCondition 問題の生成元
	 * @return 難易度別の問題数と出題範囲
	 */
	public PracticeMenuDto countPracticeQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        QuestionSourceCondition sourceCondition) {

	    // 問題の生成元が未指定の場合はすべて
	    if (sourceCondition == null) {
	        sourceCondition = QuestionSourceCondition.ALL;
	    }

	    // 問題数と出題範囲を保持するDTOを作成
	    PracticeMenuDto count = new PracticeMenuDto();

	    // 初級の問題数と出題範囲を設定
	    long beginnerCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.BEGINNER,
	            sourceCondition
	    );
	    count.setBeginnerCount(beginnerCount);
	    count.setBeginnerRanges(createRanges(beginnerCount));

	    // 中級の問題数と出題範囲を設定
	    long intermediateCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.INTERMEDIATE,
	            sourceCondition
	    );
	    count.setIntermediateCount(intermediateCount);
	    count.setIntermediateRanges(createRanges(intermediateCount));

	    // 上級の問題数と出題範囲を設定
	    long advancedCount = countQuestions(
	            userId,
	            languageVariant,
	            Difficulty.ADVANCED,
	            sourceCondition
	    );
	    count.setAdvancedCount(advancedCount);
	    count.setAdvancedRanges(createRanges(advancedCount));

	    return count;
	}
	
	/**
	 * 未学習問題数を難易度別に取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @return 難易度別の未学習問題数
	 */
	public NewPracticeCountDto countNewPracticeQuestions(
	        Long userId,
	        LanguageVariant languageVariant) {

	    // 未学習問題数を保持するDTOを作成
	    NewPracticeCountDto count = new NewPracticeCountDto();

	    // 初級の未学習問題数を取得して設定
	    long beginnerCount = questionRepository.countUnlearnedQuestions(
	            userId,
	            languageVariant.name(),
	            Difficulty.BEGINNER.name());
	    count.setBeginnerCount(beginnerCount);

	    // 中級の未学習問題数を取得して設定
	    long intermediateCount = questionRepository.countUnlearnedQuestions(
	            userId,
	            languageVariant.name(),
	            Difficulty.INTERMEDIATE.name());
	    count.setIntermediateCount(intermediateCount);

	    // 上級の未学習問題数を取得して設定
	    long advancedCount = questionRepository.countUnlearnedQuestions(
	            userId,
	            languageVariant.name(),
	            Difficulty.ADVANCED.name());
	    count.setAdvancedCount(advancedCount);

	    return count;
	}

	/**
	 * 問題数から通常学習の出題範囲を作成する。
	 * 1つの範囲につき最大50問として分割する。
	 *
	 * @param count 問題数
	 * @return 出題範囲の一覧
	 */
	private List<Range> createRanges(long count) {

	    // 出題範囲の一覧を作成
	    List<Range> ranges = new ArrayList<>();

	    // 一定件数ごとに出題範囲を作成
	    for (long start = 1; start <= count; start += PRACTICE_RANGE_SIZE) {

	        // 範囲の上限が問題数を超えない場合
	        if (start + (PRACTICE_RANGE_SIZE - 1) <= count) {
	            ranges.add(new Range(
	                    start,
	                    start + (PRACTICE_RANGE_SIZE - 1)));

	        // 範囲の上限が問題数を超える場合
	        } else {
	            ranges.add(new Range(start, count));
	        }
	    }

	    return ranges;
	}
	
	/**
	 * 指定した難易度に一致する未学習問題を取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @return 未学習問題の一覧
	 */
	public List<Question> getNewQuestions(
			Long userId, 
			LanguageVariant languageVariant,
			List<Difficulty> difficulty) {
		
		   return questionRepository
		            .findUnlearnedQuestions(
		                    userId,
		                    languageVariant.name(),
		                    searchConditionConverter.convertDifficulty(difficulty));
		}
	
	/**
	 * 指定した問題リストに登録されている通常学習対象の問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return 通常学習対象の問題数
	 */
	public long countPracticeQuestionsByList(
	        Long userId,
	        Long listId,
	        LanguageVariant languageVariant) {

	    return questionRepository.countPracticeQuestionsByList(
	            userId,
	            listId,
	            languageVariant.name());
	}
	
	/**
	 * 指定した問題リストに登録されている通常学習対象の問題を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return 通常学習対象の問題一覧
	 */
	public List<Question> getPracticeQuestionsByList(
	        Long userId,
	        Long listId,
	        LanguageVariant languageVariant) {

	    return questionRepository.findPracticeQuestionsByList(
	            userId,
	            listId,
	            languageVariant.name());
	}
	
	/**
	 * 指定した条件に一致する通常学習の問題数を取得する。
	 * ログイン状態に応じて取得対象を切り替える。
	 *
	 * @param userId ユーザーID。非ログインの場合はnull
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @param sourceCondition 問題の生成元
	 * @return 条件に一致する問題数
	 */
	private long countQuestions(
	        Long userId,
	        LanguageVariant languageVariant,
	        Difficulty difficulty,
	        QuestionSourceCondition sourceCondition) {

		// 非ログインの場合はオリジナル問題のみ集計
	    if (userId == null) {
	        return questionRepository.countAnonymousPracticeQuestions(
	                languageVariant.name(),
	                difficulty.name()
	        );
	    }
	    
	    return questionRepository.countPracticeQuestions(
	            userId,
	            languageVariant.name(),
	            difficulty.name(),
	            sourceCondition.name()
	    );
	}
	
}
