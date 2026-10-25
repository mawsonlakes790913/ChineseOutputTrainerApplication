# 023-07 Repositoryのクエリとメソッド名の整理

## はじめに

機能追加を重ねた結果、各Repositoryのメソッド数が増え、長いクエリや、検索条件を列挙したメソッド名が目立つようになった。処理自体に明確な不具合があったわけではないが、**Repositoryの役割を把握しやすくし、今後の保守を容易にすること**を目的に整理した。

今回の主な変更は、メソッドへのコメント追加、JPQLからNative Queryへの一部変更、`@Query`を使用するメソッドの命名整理である。なお、当初検討項目に挙げた「復習問題の最大出題数」は、この節の記録に具体的な変更がないため、実装済みの変更としては扱わない。

## 1. Repositoryメソッドへのコメント追加

```bash
git commit -m "docs: add comments to repository methods"
```

クエリが長いメソッドは、SQLを最後まで読まないと用途が分かりにくい。そこで、各メソッドの直前に「何を取得・更新・削除するか」が分かるコメントを追加した。

特に、通常学習・復習・ユーザー管理など、似た条件の検索処理が複数あるRepositoryでは、メソッド名とコメントから用途を判断できるようにすることを重視した。

## 2. JPQLとNative Queryの使い分けを整理

```bash
git commit -m "refactor: replace JPQL queries with native queries"
```

Repository内ではJPQLとNative Queryが混在していた。開発時にNative Queryを使うことが多く、テーブル名・カラム名を直接確認できる形式に統一した方が、自分にとって読みやすいと判断した。

ただし、**すべてのJPQLを機械的に置き換える方針ではない**。変更によって呼び出し側が複雑になる箇所は、JPQLのまま残している。

### 2.1 削除クエリの変更

`AiGenerationHistoryRepository`では、Entityを対象とするJPQLの削除を、テーブルを直接指定するNative Queryへ変更した。

**変更前（JPQL）**

```java
// 指定したユーザーのAI生成履歴をすべて削除
@Modifying
@Query("""
        DELETE FROM AiGenerationHistory a
        WHERE a.user.id = :userId
        """)
void deleteByUserId(@Param("userId") Long userId);
```

**変更後（Native Query）**

```java
// 指定したユーザーのAI生成履歴をすべて削除
@Modifying
@Query(value = """
        DELETE FROM ai_generation_history
        WHERE user_id = :userId
        """, nativeQuery = true)
void deleteByUserId(@Param("userId") Long userId);
```

同じ方針で、次の削除処理も変更した。

| Repository | 対象テーブル | 条件 |
| --- | --- | --- |
| `FavoriteRepository` | `favorite` | `user_id = :userId` |
| `QuestionRepository` | `question` | `owner_user_id = :userId AND ai_generated = true` |
| `StudyHistoryRepository` | `study_history` | `user_id = :userId` |

これらはクエリの記述形式を変更したものであり、削除対象の条件そのものを変更することが目的ではない。

### 2.2 一覧取得クエリの変更

`StructureRepository.findStructures(...)`もNative Queryへ変更した。

```java
// 構文を構文IDの昇順でページング取得
@Query(value = """
        SELECT *
        FROM structure
        ORDER BY structure_id ASC
        """, nativeQuery = true)
Page<Structure> findStructures(Pageable pageable);
```

`UserRepository.findUsers(...)`では、ログインID・アカウント状態・メールアドレスによる絞り込みと、指定された並び順を維持したまま、Native Queryへ変更した。ページング用の`Page<Users>`を返すため、件数計算用の`countQuery`も明示した。

```java
@Query(
        value = """
                SELECT *
                FROM users
                WHERE (
                    :loginId IS NULL OR :loginId = ''
                    OR login_id LIKE CONCAT('%', :loginId, '%')
                )
                AND (
                    :accountStatus = 'ALL'
                    OR (:accountStatus = 'ACTIVE' AND account_locked = false)
                    OR (:accountStatus = 'LOCKED' AND account_locked = true)
                )
                AND (
                    :email IS NULL OR :email = ''
                    OR LOWER(email) LIKE LOWER(CONCAT('%', :email, '%'))
                )
                ORDER BY
                    CASE WHEN :sortCondition = 'LOGIN_ID_ASC'
                         THEN login_id END ASC,
                    CASE WHEN :sortCondition = 'EMAIL_ASC'
                         THEN email END ASC
                """,
        countQuery = """
                SELECT COUNT(*)
                FROM users
                WHERE (
                    :loginId IS NULL OR :loginId = ''
                    OR login_id LIKE CONCAT('%', :loginId, '%')
                )
                AND (
                    :accountStatus = 'ALL'
                    OR (:accountStatus = 'ACTIVE' AND account_locked = false)
                    OR (:accountStatus = 'LOCKED' AND account_locked = true)
                )
                AND (
                    :email IS NULL OR :email = ''
                    OR LOWER(email) LIKE LOWER(CONCAT('%', :email, '%'))
                )
                """,
        nativeQuery = true
)
Page<Users> findUsers(
        @Param("loginId") String loginId,
        @Param("accountStatus") String accountStatus,
        @Param("email") String email,
        @Param("sortCondition") String sortCondition,
        Pageable pageable);
```

`value`の検索条件と`countQuery`の検索条件が一致していることが重要である。件数計算では並び替えが不要なので、`ORDER BY`は含めていない。

### 2.3 `replaceStructure(...)`はJPQLのまま維持

構文を削除する際、関連する問題の構文を「その他」へ一括変更する`QuestionRepository.replaceStructure(...)`は、JPQLのまま残した。

```java
@Modifying
@Query("""
        UPDATE Question q
        SET q.structure = :replacementStructure
        WHERE q.structure = :targetStructure
        """)
void replaceStructure(
        @Param("targetStructure") Structure targetStructure,
        @Param("replacementStructure") Structure replacementStructure);
```

Native Queryにすると、`structure_id`を直接更新するため、引数を`Structure`から`Long`のIDへ変更することになる。呼び出し元の`AdminStructureService.deleteStructure()`にも変更が必要となり、今回の目的に対して修正範囲が広がる。

**記述形式を統一することよりも、既存コードの見通しと変更範囲を優先した。**

## 3. `@Query`を使用するRepositoryメソッド名の簡素化

```bash
git commit -m "refactor: simplify repository method names"
```

Spring Data JPAの派生クエリでは、メソッド名が検索条件を表す。一方、`@Query`でSQL・JPQLを明示している場合、検索条件をすべてメソッド名に列挙する必要はない。

そこで、長い条件列挙型の名前から、**メソッドの用途が分かる名前**へ変更した。

### 3.1 通常学習・未学習問題の取得

| 変更前 | 変更後 |
| --- | --- |
| `countByLanguageVariantAndDifficulty(...)` | `countAnonymousPracticeQuestions(...)` |
| `findQuestionsByLanguageVariantAndDifficulty(...)` | `findAnonymousPracticeQuestions(...)` |
| `findAvailableQuestionsByUserIdAndLanguageVariantAndDifficulty(...)` | `findPracticeQuestions(...)` |
| `findUnlearnedQuestionsByUserIdAndDifficulty(...)` | `findUnlearnedQuestions(...)` |

これにより、通常学習の取得・件数計算が次のような対応関係になった。

```text
非ログインユーザー
  countAnonymousPracticeQuestions(...)
  findAnonymousPracticeQuestions(...)

ログインユーザー
  countPracticeQuestions(...)
  findPracticeQuestions(...)
```

なお、ここでの変更は**メソッド名の整理**であり、問題の所有者判定などのアクセス条件は023-06で扱った変更を前提としている。

### 3.2 問題一覧・削除処理

| Repository | 変更前 | 変更後 |
| --- | --- | --- |
| `QuestionRepository` | `findFilteredUserQuestionList(...)` | `findUserQuestionList(...)` |
| `QuestionRepository` | `findFilteredAdminQuestionList(...)` | `findAdminQuestionList(...)` |
| `FavoriteRepository` | `deleteByFavoriteKeyUserId(...)` | `deleteByUserId(...)` |
| `StudyHistoryRepository` | `deleteByStudyHistoryKeyUserId(...)` | `deleteByUserId(...)` |

例えば`FavoriteRepository`の削除処理は、すでに`@Query`で削除条件を指定している。複合主キー`favoriteKey`の構造をメソッド名へ反映する必要がないため、`deleteByUserId(...)`へ変更した。

```java
// 指定したユーザーのお気に入りをすべて削除
@Modifying
@Query(value = """
        DELETE FROM favorite
        WHERE user_id = :userId
        """, nativeQuery = true)
void deleteByUserId(@Param("userId") Long userId);
```

メソッド名を変更した箇所では、呼び出し元も新しい名前に合わせる必要がある。

## 4. まとめ

今回の作業は、新機能の追加ではなく、増え続けたRepositoryを読みやすくするための整理だった。

- コメントを追加し、クエリの用途を把握しやすくした。
- 開発時に読み慣れているNative Queryへ一部を変更した。
- `@Query`で処理を定義しているメソッドは、条件の羅列より用途が伝わる名前へ変更した。
- `replaceStructure(...)`のように、統一によって変更範囲が広がる箇所はJPQLを維持した。

**技術的な統一そのものを目的にせず、保守性と修正範囲のバランスを考えて整理する**ことが、今回の判断の中心となった。

なお、元の記録には、この整理に対する個別の動作確認結果やスクリーンショットは記載されていないため、本節では確認済みとは記載しない。
