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

public interface QuestionRepository extends JpaRepository<Question, Long> {

	// ==================================================
	// 単純検索
	// ==================================================

	// 指定した中国語本文と同じ問題が存在するか確認
	boolean existsByChineseText(String chineseText);

	// 指定したユーザーが所有する問題を中国語本文から取得
	Optional<Question> findByOwnerIdAndChineseText(
	        Long userId,
	        String chineseText);
	
	// 指定したIDから取得
	Optional<Question> findByQuestionId(Long QuestionId);


	// ==================================================
	// アクセス確認
	// ==================================================

	// 指定した問題がログインユーザーからアクセス可能か確認
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

	// 非ログインユーザー用問題数取得(デフォルト)
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

	// ログインユーザー用問題数取得(デフォルト)
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

	// 非ログインユーザー用問題取得
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

	// ログインユーザー用問題取得
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

	// 未学習問題の数を取得
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

	// 未学習問題を取得
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
	
	// 指定したリストに登録されている問題数を取得
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
	
	// 指定したリストに登録されている問題を取得
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

	// 検索条件に一致するユーザー用問題一覧を取得
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

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("studyCondition")
	        String studyCondition,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("sourceCondition")
	        String sourceCondition,

	        @Param("structureIds")
	        List<Long> structureIds,

	        @Param("languageVariants")
	        List<String> languageVariants,

	        @Param("japaneseKeyword")
	        String japaneseKeyword,

	        @Param("chineseKeyword")
	        String chineseKeyword,

	        Pageable pageable
	);


	// ==================================================
	// Admin用問題一覧
	// ==================================================

	// 検索条件に一致するAdmin用問題一覧を取得
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
	Page<AdminQuestionListDto> findUserQuestionList(
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

	// AI生成元として利用可能な問題数を取得
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

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("structureIds")
	        List<Long> structureIds,

	        @Param("languageVariant")
	        String languageVariant
	);

	// AI生成元として利用可能な問題をランダムに最大50件取得
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

	        @Param("userId")
	        long userId,

	        @Param("difficulties")
	        List<String> difficulties,

	        @Param("evaluations")
	        List<String> evaluations,

	        @Param("favoriteCondition")
	        String favoriteCondition,

	        @Param("structureIds")
	        List<Long> structureIds,

	        @Param("languageVariant")
	        String languageVariant
	);


	// ==================================================
	// 更新・削除
	// ==================================================

	// 指定したユーザーが所有するAI生成由来の問題をすべて削除
	@Modifying
	@Query(value = """
	        DELETE FROM question
	        WHERE owner_user_id = :userId
	        AND ai_generated = true
	        """, nativeQuery = true)
	void deleteByOwnerId(
	        @Param("userId") Long userId);

	// 指定した構文を使用する問題の構文を別の構文へ一括置換
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