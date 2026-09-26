package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mawsonlakes790913.chineseoutputforge.dto.AdminQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.dto.UserQuestionListDto;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;
import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;

/**
 * 中国語学習で使用する問題情報のDB操作を行うRepository。
 */
public interface QuestionRepository extends JpaRepository<Question, Long> {

	// ==================================================
	// 単純検索
	// ==================================================

	/**
	 * 指定した中国語本文と同じ問題が存在するか確認する。
	 *
	 * @param chineseText 中国語本文
	 * @return 同じ中国語本文の問題が存在する場合はtrue
	 */
	boolean existsByChineseText(String chineseText);

	/**
	 * 指定したユーザーが所有する問題を中国語本文から取得する。
	 *
	 * @param userId ユーザーID
	 * @param chineseText 中国語本文
	 * @return 条件に一致する問題
	 */
	Optional<Question> findByOwnerIdAndChineseText(
	        Long userId,
	        String chineseText);
	
	/**
	 * 指定した問題IDから問題を取得する。
	 *
	 * @param questionId 問題ID
	 * @return 指定したIDの問題
	 */
	Optional<Question> findByQuestionId(Long QuestionId);


	// ==================================================
	// アクセス確認
	// ==================================================

	/**
	 * 指定した問題がログインユーザーからアクセス可能か確認する。
	 *
	 * @param questionId 問題ID
	 * @param userId ユーザーID
	 * @return アクセス可能な場合はtrue
	 */
	@Query(value = """
	        SELECT COUNT(*) > 0
	        FROM question
	        WHERE question_id = :questionId
	        AND (
	            owner_user_id IS NULL
	            OR owner_user_id = :userId
	        )
	        """, nativeQuery = true)
	boolean existsAccessibleQuestion(
	        @Param("questionId") Long questionId,
	        @Param("userId") Long userId);


	// ==================================================
	// 通常学習
	// ==================================================

	/**
	 * 非ログインユーザーが通常学習で利用できる問題数を取得する。
	 *
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @return 条件に一致する問題数
	 */
	@Query(value = """
	        SELECT COUNT (*)
	        FROM question
	        WHERE language_variant = :languageVariant
	        AND difficulty = :difficulty
	        AND ai_generated = false
	        AND owner_user_id IS NULL
	        """, nativeQuery = true)
	long countAnonymousPracticeQuestions(
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulty") String difficulty
	);

	/**
	 * ログインユーザーが通常学習で利用できる問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @param sourceCondition 問題の生成元の検索条件
	 * @return 条件に一致する問題数
	 */
	@Query(value = """
	        SELECT COUNT(*)
	        FROM question
	        WHERE language_variant = :languageVariant
	        AND difficulty = :difficulty
	        AND (
	            (:sourceCondition = 'ALL'
	                AND (
	                    (ai_generated = true AND owner_user_id = :userId)
	                    OR ai_generated = false AND (owner_user_id IS NULL OR owner_user_id = :userId)
	                )
	            )
	            OR (:sourceCondition = 'ORIGINAL_ONLY'
	                AND ai_generated = false AND (owner_user_id IS NULL OR owner_user_id = :userId)
	            )
	            OR (:sourceCondition = 'GENERATED_ONLY'
	                AND (ai_generated = true AND owner_user_id = :userId)
	            )
	        )
	        """, nativeQuery = true)
	long countPracticeQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulty") String difficulty,
	        @Param("sourceCondition") String sourceCondition
	);

	/**
	 * 非ログインユーザーが通常学習で利用できる問題を最大50件取得する。
	 *
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @param offset 取得開始位置
	 * @return 条件に一致する問題の一覧
	 */
	@Query(value = """
	        SELECT *
	        FROM question
	        WHERE language_variant = :languageVariant
	        AND difficulty = :difficulty
	        AND ai_generated = false
	        AND owner_user_id IS NULL
	        ORDER BY question_id
	        LIMIT 50 OFFSET :offset
	        """, nativeQuery = true)
	List<Question> findAnonymousPracticeQuestions(
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulty") String difficulty,
	        @Param("offset") int offset
	);

	/**
	 * ログインユーザーが通常学習で利用できる問題を最大50件取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @param difficulty 難易度
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param offset 取得開始位置
	 * @return 条件に一致する問題の一覧
	 */
	@Query(value = """
	        SELECT *
	        FROM question
	        WHERE language_variant = :languageVariant
	        AND difficulty = :difficulty
	        AND (
	            (:sourceCondition = 'ALL'
	                AND (
	                    (ai_generated = true AND owner_user_id = :userId)
	                    OR (
	                        ai_generated = false
	                        AND (owner_user_id IS NULL OR owner_user_id = :userId)
	                    )
	                )
	            )
	            OR (
	                :sourceCondition = 'ORIGINAL_ONLY'
	                AND ai_generated = false
	                AND (owner_user_id IS NULL OR owner_user_id = :userId)
	            )
	            OR (
	                :sourceCondition = 'GENERATED_ONLY'
	                AND (
	                    ai_generated = true
	                    AND owner_user_id = :userId
	                )
	            )
	        )
	        ORDER BY question_id
	        LIMIT 50 OFFSET :offset
	        """, nativeQuery = true)
	List<Question> findPracticeQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulty") String difficulty,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("offset") int offset
	);


	// ==================================================
	// 未学習問題
	// ==================================================

	/**
	 * 指定したユーザーの未学習問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @param difficulties 難易度の検索条件
	 * @return 条件に一致する未学習問題数
	 */
	@Query(value = """
	        SELECT COUNT(*)
	        FROM question q
	        LEFT JOIN study_history sh
	          ON q.question_id = sh.question_id
	         AND sh.user_id = :userId
	        WHERE language_variant = :languageVariant
	          AND q.difficulty IN (:difficulties)
	          AND (
	              q.owner_user_id IS NULL
	              OR q.owner_user_id = :userId
	          )
	          AND sh.question_id IS NULL
	        """, nativeQuery = true)
	long countUnlearnedQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulties") String difficulties
	);

	/**
	 * 指定したユーザーの未学習問題を取得する。
	 *
	 * @param userId ユーザーID
	 * @param languageVariant 学習対象言語
	 * @param difficulties 難易度の検索条件
	 * @return 条件に一致する未学習問題の一覧
	 */
	@Query(value = """
	        SELECT q.*
	        FROM question q
	        LEFT JOIN study_history sh
	          ON q.question_id = sh.question_id
	         AND sh.user_id = :userId
	        WHERE language_variant = :languageVariant
	          AND q.difficulty IN (:difficulties)
	          AND (
	              q.owner_user_id IS NULL
	              OR q.owner_user_id = :userId
	          )
	          AND sh.question_id IS NULL
	        """, nativeQuery = true)
	List<Question> findUnlearnedQuestions(
	        @Param("userId") Long userId,
	        @Param("languageVariant") String languageVariant,
	        @Param("difficulties") List<String> difficulties
	);
	
	// ==================================================
	// リストの問題
	// ==================================================
	
	/**
	 * 指定した問題リストに登録されている通常学習用の問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return 条件に一致する問題数
	 */
	@Query(value = """
	        SELECT COUNT(*)
	        FROM question q
	        JOIN question_list_item qli
	          ON q.question_id = qli.question_id
	        JOIN question_list ql
	          ON qli.list_id = ql.list_id
	        WHERE qli.list_id = :listId
	          AND ql.user_id = :userId
	          AND q.language_variant = :languageVariant
	        """, nativeQuery = true)
	long countPracticeQuestionsByList(
	        @Param("userId") Long userId,
	        @Param("listId") Long listId,
	        @Param("languageVariant") String languageVariant
	);
	
	/**
	 * 指定した問題リストに登録されている通常学習用の問題を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return 条件に一致する問題の一覧
	 */
	@Query(value = """
	        SELECT q.*
	        FROM question q
	        JOIN question_list_item qli
	          ON q.question_id = qli.question_id
	        JOIN question_list ql
	          ON qli.list_id = ql.list_id
	        WHERE qli.list_id = :listId
	          AND ql.user_id = :userId
	          AND q.language_variant = :languageVariant
	        ORDER BY q.question_id
	        """, nativeQuery = true)
	List<Question> findPracticeQuestionsByList(
	        @Param("userId") Long userId,
	        @Param("listId") Long listId,
	        @Param("languageVariant") String languageVariant
	);

	// ==================================================
	// ユーザー用問題一覧
	// ==================================================

	/**
	 * 指定された検索条件に一致するユーザー用問題一覧を取得する。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param studyCondition 学習状況の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariants 学習対象言語の検索条件
	 * @param listId 問題リストの検索条件
	 * @param japaneseKeyword 日本語の検索キーワード
	 * @param chineseKeyword 中国語の検索キーワード
	 * @param pageable ページング情報
	 * @return 条件に一致するユーザー用問題一覧
	 */
	@Query(value = """
	        SELECT
	            q.question_id         AS questionId,
	            q.japanese_text       AS japaneseText,
	            q.chinese_text        AS chineseText,
	            q.alternative_answer  AS alternativeAnswer,
	            s.name                AS structureName,
	            s.description_zh_cn   AS structureDescriptionZhCn,
	            s.description_zh_tw   AS structureDescriptionZhTw,
	            q.difficulty          AS difficulty,
	            sh.evaluation         AS evaluation,
	            q.ai_generated        AS aiGenerated,
	            CASE
	                WHEN f.question_id IS NOT NULL THEN TRUE
	                ELSE FALSE
	            END AS favorite,
	            q.pinyin                    AS pinyin,
	            q.zhuyin                    AS zhuyin,
	            q.alternative_answer_pinyin AS alternativeAnswerPinyin,
	            q.alternative_answer_zhuyin AS alternativeAnswerZhuyin
	        FROM question q
	        JOIN structure s
		        ON q.structure_id = s.structure_id
	        LEFT JOIN study_history sh
		        ON (
		            q.question_id = sh.question_id
		            AND sh.user_id = :userId
		        )
	        LEFT JOIN favorite f
		        ON (
		            q.question_id = f.question_id
		            AND f.user_id = :userId
		        )
	        WHERE q.difficulty IN (:difficulties)
	        AND (
	            :studyCondition = 'ALL'
	            OR (
	                :studyCondition = 'LEARNED_ONLY'
	                AND sh.question_id IS NOT NULL
	                AND sh.evaluation IN (:evaluations)
	            )
	            OR (
	                :studyCondition = 'UNLEARNED_ONLY'
	                AND sh.question_id IS NULL
	            )
	        )
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND (
	            (:sourceCondition = 'ALL'
	                AND (
	                    (q.ai_generated = true AND q.owner_user_id = :userId)
	                    OR (
	                        q.ai_generated = false
	                        AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId)
	                    )
	                )
	            )
	            OR (
	                :sourceCondition = 'ORIGINAL_ONLY'
	                AND q.ai_generated = false
	                AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId)
	            )
	            OR (
	                :sourceCondition = 'GENERATED_ONLY'
	                AND (
	                    q.ai_generated = true
	                    AND q.owner_user_id = :userId
	                )
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :listId IS NULL
	            OR EXISTS (
	                SELECT 1
	                FROM question_list_item qli
	                JOIN question_list ql
	                ON qli.list_id = ql.list_id
	                WHERE qli.question_id = q.question_id
	                AND qli.list_id = :listId
	                AND ql.user_id = :userId
	            )
	        )
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )
	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )
	        ORDER BY q.question_id ASC
	        """,
	        countQuery = """
	        SELECT COUNT(*)
	        FROM question q
	        JOIN structure s
		        ON q.structure_id = s.structure_id
	        LEFT JOIN study_history sh
		        ON (
		            q.question_id = sh.question_id
		            AND sh.user_id = :userId
		        )
	        LEFT JOIN favorite f
		        ON (
		            q.question_id = f.question_id
		            AND f.user_id = :userId
		        )
	        WHERE q.difficulty IN (:difficulties)
	        AND (
	            :studyCondition = 'ALL'
	            OR (
	                :studyCondition = 'LEARNED_ONLY'
	                AND sh.question_id IS NOT NULL
	                AND sh.evaluation IN (:evaluations)
	            )
	            OR (
	                :studyCondition = 'UNLEARNED_ONLY'
	                AND sh.question_id IS NULL
	            )
	        )
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND (
	            (:sourceCondition = 'ALL'
	                AND (
	                    (q.ai_generated = true AND q.owner_user_id = :userId)
	                    OR (
	                        q.ai_generated = false
	                        AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId)
	                    )
	                )
	            )
	            OR (
	                :sourceCondition = 'ORIGINAL_ONLY'
	                AND q.ai_generated = false
	                AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId)
	            )
	            OR (
	                :sourceCondition = 'GENERATED_ONLY'
	                AND (
	                    q.ai_generated = true
	                    AND q.owner_user_id = :userId
	                )
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :listId IS NULL
	            OR EXISTS (
	                SELECT 1
	                FROM question_list_item qli
	                JOIN question_list ql
	                ON qli.list_id = ql.list_id
	                WHERE qli.question_id = q.question_id
	                AND qli.list_id = :listId
	                AND ql.user_id = :userId
	            )
	        )
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )
	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )
	        """,
	        nativeQuery = true)
	Page<UserQuestionListDto> findUserQuestionList(
	        @Param("userId") long userId,
	        @Param("difficulties") List<String> difficulties,
	        @Param("evaluations") List<String> evaluations,
	        @Param("studyCondition") String studyCondition,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("structureIds") List<Long> structureIds,
	        @Param("languageVariants") List<String> languageVariants,
	        @Param("listId") Long listId,
	        @Param("japaneseKeyword") String japaneseKeyword,
	        @Param("chineseKeyword") String chineseKeyword,
	        Pageable pageable
	);

	// ==================================================
	// Admin用問題一覧
	// ==================================================

	/**
	 * 指定された検索条件に一致する管理者用問題一覧を取得する。
	 *
	 * @param difficulties 難易度の検索条件
	 * @param sourceCondition 問題の生成元の検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariants 学習対象言語の検索条件
	 * @param japaneseKeyword 日本語の検索キーワード
	 * @param chineseKeyword 中国語の検索キーワード
	 * @param sortCondition 並び順
	 * @param pageable ページング情報
	 * @return 条件に一致する管理者用問題一覧
	 */
	@Query(
	        value = """
	        SELECT
	            q.question_id      AS questionId,
	            q.language_variant AS languageVariant,
	            q.chinese_text     AS chineseText,
	            q.japanese_text    AS japaneseText,
	            q.difficulty       AS difficulty,
	            s.name             AS structureName,
	            q.ai_generated     AS aiGenerated,
	            u.login_id         AS ownerLoginId
	        FROM question q
	        JOIN structure s
	            ON q.structure_id = s.structure_id
	        LEFT JOIN users u
	            ON q.owner_user_id = u.id
	        WHERE q.difficulty IN (:difficulties)
	        AND (
	            :sourceCondition = 'ALL'
	            OR (:sourceCondition = 'ORIGINAL_ONLY' AND q.ai_generated = false)
	            OR (:sourceCondition = 'GENERATED_ONLY' AND q.ai_generated = true)
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )
	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )
	        ORDER BY
	            CASE
	                WHEN :sortCondition = 'UPDATED_DESC'
	                THEN q.updated_at
	            END DESC,
	            CASE
	                WHEN :sortCondition = 'QUESTION_ID_ASC'
	                THEN q.question_id
	            END ASC,
	            q.question_id DESC
	        """,
	        countQuery = """
	        SELECT COUNT(*)
	        FROM question q
	        WHERE q.difficulty IN (:difficulties)
	        AND (
	            :sourceCondition = 'ALL'
	            OR (:sourceCondition = 'ORIGINAL_ONLY' AND q.ai_generated = false)
	            OR (:sourceCondition = 'GENERATED_ONLY' AND q.ai_generated = true)
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant IN (:languageVariants)
	        AND (
	            :japaneseKeyword = ''
	            OR LOWER(q.japanese_text)
	                LIKE LOWER(CONCAT('%', :japaneseKeyword, '%'))
	        )
	        AND (
	            :chineseKeyword = ''
	            OR LOWER(q.chinese_text)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	            OR LOWER(q.alternative_answer)
	                LIKE LOWER(CONCAT('%', :chineseKeyword, '%'))
	        )
	        """,
	        nativeQuery = true
	)
	Page<AdminQuestionListDto> findAdminQuestionList(
	        @Param("difficulties") List<String> difficulties,
	        @Param("sourceCondition") String sourceCondition,
	        @Param("structureIds") List<Long> structureIds,
	        @Param("languageVariants") List<String> languageVariants,
	        @Param("japaneseKeyword") String japaneseKeyword,
	        @Param("chineseKeyword") String chineseKeyword,
	        @Param("sortCondition") String sortCondition,
	        Pageable pageable
	);


	// ==================================================
	// AI生成
	// ==================================================
	
	/**
	 * AI生成元として利用可能な問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題数
	 */
	@Query(value = """
	        SELECT COUNT (*)
	        FROM question q
	        JOIN study_history sh
		        ON (
		            q.question_id = sh.question_id
		            AND sh.user_id = :userId
		        )
	        LEFT JOIN favorite f
		        ON (
		            q.question_id = f.question_id
		            AND f.user_id = :userId
		        )
	        WHERE q.difficulty IN (:difficulties)
	        AND sh.evaluation IN (:evaluations)
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant = :languageVariant
	        AND q.allow_ai_variation = true
	        """,
	        nativeQuery = true)
	Long countAiGenerationSourceQuestions(
	        @Param("userId") long userId,
	        @Param("difficulties") List<String> difficulties,
	        @Param("evaluations") List<String> evaluations,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("structureIds") List<Long> structureIds,
	        @Param("languageVariant") String languageVariant
	);
	
	/**
	 * AI生成元として利用可能な問題をランダムに最大50件取得する。
	 *
	 * @param userId ユーザーID
	 * @param difficulties 難易度の検索条件
	 * @param evaluations 理解度の検索条件
	 * @param favoriteCondition お気に入りの検索条件
	 * @param structureIds 文法・構造の検索条件
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題の一覧
	 */
	@Query(value = """
	        SELECT q.*
	        FROM question q
	        JOIN study_history sh
		        ON (
		            q.question_id = sh.question_id
		            AND sh.user_id = :userId
		        )
	        LEFT JOIN favorite f
		        ON (
		            q.question_id = f.question_id
		            AND f.user_id = :userId
		        )
	        WHERE q.difficulty IN (:difficulties)
	        AND sh.evaluation IN (:evaluations)
	        AND (
	            :favoriteCondition = 'ALL'
	            OR (
	                :favoriteCondition = 'FAVORITED'
	                AND f.question_id IS NOT NULL
	            )
	            OR (
	                :favoriteCondition = 'NOT_FAVORITED'
	                AND f.question_id IS NULL
	            )
	        )
	        AND q.structure_id IN (:structureIds)
	        AND q.language_variant = :languageVariant
	        AND q.allow_ai_variation = true
	        ORDER BY RANDOM()
	        LIMIT 50
	        """,
	        nativeQuery = true)
	List<Question> findAiGenerationSourceQuestions(
	        @Param("userId") long userId,
	        @Param("difficulties") List<String> difficulties,
	        @Param("evaluations") List<String> evaluations,
	        @Param("favoriteCondition") String favoriteCondition,
	        @Param("structureIds") List<Long> structureIds,
	        @Param("languageVariant") String languageVariant
	);

	/**
	 * 指定した問題リストのAI生成元として利用可能な問題数を取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題数
	 */
	@Query(value = """
	        SELECT COUNT(*)
	        FROM question q
	        JOIN question_list_item qli
		        ON q.question_id = qli.question_id
	        JOIN question_list ql
				ON qli.list_id = ql.list_id
	        WHERE qli.list_id = :listId
	          AND ql.user_id = :userId
	          AND q.language_variant = :languageVariant
	          AND q.allow_ai_variation = true
	        """, nativeQuery = true)
	long countAiGenerationSourceQuestionsByList(
	        @Param("userId") Long userId,
	        @Param("listId") Long listId,
	        @Param("languageVariant") String languageVariant
	);

	/**
	 * 指定した問題リストからAI生成元として利用可能な問題をすべて取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題の一覧
	 */
	@Query(value = """
	        SELECT q.*
	        FROM question q
	        JOIN question_list_item qli
		          ON q.question_id = qli.question_id
	        JOIN question_list ql
		          ON qli.list_id = ql.list_id
	        WHERE qli.list_id = :listId
	          AND ql.user_id = :userId
	          AND q.language_variant = :languageVariant
	          AND q.allow_ai_variation = true
	        ORDER BY q.question_id
	        """, nativeQuery = true)
	List<Question> findAiGenerationSourceQuestionsByList(
	        @Param("userId") Long userId,
	        @Param("listId") Long listId,
	        @Param("languageVariant") String languageVariant
	);

	/**
	 * 指定した問題リストからAI生成元として利用可能な問題をランダムに最大50件取得する。
	 *
	 * @param userId ユーザーID
	 * @param listId 問題リストID
	 * @param languageVariant 学習対象言語
	 * @return AI生成元として利用可能な問題の一覧
	 */
	@Query(value = """
	        SELECT q.*
	        FROM question q
	        JOIN question_list_item qli
		          ON q.question_id = qli.question_id
	        JOIN question_list ql
		          ON qli.list_id = ql.list_id
	        WHERE qli.list_id = :listId
	          AND ql.user_id = :userId
	          AND q.language_variant = :languageVariant
	          AND q.allow_ai_variation = true
	        ORDER BY RANDOM()
	        LIMIT 50
	        """, nativeQuery = true)
	List<Question> findAiGenerationSourceQuestionsByListLimit50(
	        @Param("userId") Long userId,
	        @Param("listId") Long listId,
	        @Param("languageVariant") String languageVariant
	);


	// ==================================================
	// 更新・削除
	// ==================================================

	/**
	 * 指定したユーザーが所有するAI生成由来の問題をすべて削除する。
	 *
	 * @param userId ユーザーID
	 */
	@Modifying
	@Query(value = """
	        DELETE FROM question
	        WHERE owner_user_id = :userId
	        AND ai_generated = true
	        """, nativeQuery = true)
	void deleteByOwnerId(
	        @Param("userId") Long userId);

	/**
	 * 指定した文法・構造を使用する問題を別の文法・構造へ一括変更する。
	 *
	 * @param targetStructure 変更対象の文法・構造
	 * @param replacementStructure 変更後の文法・構造
	 */
	@Modifying
	@Query("""
	        UPDATE Question q
	        SET q.structure = :replacementStructure
	        WHERE q.structure = :targetStructure
	        """)
	void replaceStructure(
	        @Param("targetStructure") Structure targetStructure,
	        @Param("replacementStructure") Structure replacementStructure);

}