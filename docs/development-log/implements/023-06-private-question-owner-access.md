# 023-06 所有者付き非公開問題へのアクセス制限

## はじめに

開発者自身の学習用として、一般ユーザーには公開しない問題を登録できるようにした。本節では、問題の公開範囲を`ai_generated`ではなく`owner_user_id`に基づいて判定するよう、問題取得処理を見直した記録をまとめる。

```bash
git commit -m "fix: restrict access to questions by owner"
```

## 1. 修正前の問題点

従来は、`ai_generated = false`を共通問題とみなし、`ai_generated = true`の場合のみ`owner_user_id`を確認していた。そのため、次のように**AI生成由来ではないが所有者が設定された問題**を追加すると、他のユーザーにも取得される可能性があった。

```text
ai_generated = false
allow_ai_variation = false
owner_user_id = 開発者のuserId
```

`ai_generated`は問題の生成元を表す項目であり、公開・非公開を判断するための項目ではない。問題の取得可否を所有者で判定するよう変更する必要があった。

## 2. 問題の公開範囲と設計方針

| `owner_user_id` | 問題の扱い | アクセスできるユーザー |
| --- | --- | --- |
| `NULL` | 共通問題 | 全ユーザー |
| ログインユーザーのID | 所有者付き問題 | 所有者本人 |
| 他のユーザーのID | 他人の所有する問題 | アクセス不可 |

非ログインユーザーが取得できるのは、共通問題のうち既存の出題条件を満たすものに限定する。ログインユーザーの基本的なアクセス条件は次のとおりとした。

```sql
AND (
    owner_user_id IS NULL
    OR owner_user_id = :userId
)
```

この条件は、AI生成由来かどうかとは独立して適用する。

開発者専用の非公開問題について、専用の出題モードや「自分が所有する問題のみ」といった検索条件は設けない。所有者本人には通常の問題と同様に扱い、特定の問題をまとめて学習したい場合は既存のリスト機能を利用する方針とした。開発者専用の用途のために一般ユーザーの画面を複雑にしないことを優先した。

## 3. 通常学習の問題数・問題取得処理の修正

### 3.1 非ログインユーザー

`PracticeRepository`の`countByLanguageVariantAndDifficulty(...)`および`findQuestionsByLanguageVariantAndDifficulty(...)`は、従来の`ai_generated = false`だけでは所有者付きの非AI問題も対象としてしまう。両方のクエリに次の条件を追加した。

```sql
AND ai_generated = false
AND owner_user_id IS NULL
```

### 3.2 ログインユーザー

`countPracticeQuestions(...)`と`findAvailableQuestionsByUserIdAndLanguageVariantAndDifficulty(...)`では、既存の出題元条件（`ALL`、`ORIGINAL_ONLY`、`GENERATED_ONLY`）を維持しながら、所有者の判定を加えた。

```sql
(
    :sourceCondition = 'ALL'
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
    AND ai_generated = true
    AND owner_user_id = :userId
)
```

この条件は、問題の取得処理だけでなく件数取得処理にも適用する。件数表示と実際の出題対象が食い違わないようにするためである。

なお、アクセス条件と出題元条件を分離して、次のように記述する方法も検討した。

```sql
AND (owner_user_id IS NULL OR owner_user_id = :userId)
AND (
    :sourceCondition = 'ALL'
    OR (:sourceCondition = 'ORIGINAL_ONLY' AND ai_generated = false)
    OR (:sourceCondition = 'GENERATED_ONLY' AND ai_generated = true)
)
```

こちらは重複が少ない。一方、今回は既存SQLとの差分を追いやすくするため、元の分岐構造を残した修正を採用した。上記は**設計上の比較案**であり、採用した実装とは区別する。

### 3.3 未学習問題

`countUnlearnedQuestions(...)`と`findUnlearnedQuestionsByUserIdAndDifficulty(...)`にも所有者条件を適用した。従来の「非AI問題は全件取得できる」という前提を取り除き、アクセス可能な問題のうち`study_history`に存在しないものを未学習問題として扱う。

## 4. 評価・お気に入り操作のアクセスチェック

問題一覧や通常学習から非公開問題を除外するだけでは不十分である。問題IDを直接指定した場合でも、評価やお気に入り登録の処理を通過できないようにする必要がある。

従来の`existsAccessibleQuestion(...)`は、次のように`aiGenerated = false`であれば所有者を問わずアクセス可能と判定していた。

```java
@Query("""
        SELECT COUNT(q) > 0
        FROM Question q
        WHERE q.questionId = :questionId
          AND (
              q.aiGenerated = false
              OR q.owner.id = :userId
          )
        """)
boolean existsAccessibleQuestion(
        @Param("questionId") Long questionId,
        @Param("userId") Long userId);
```

これを、`owner_user_id`だけでアクセス可否を判断するネイティブクエリへ変更した。

```java
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
```

当初は`allow_ai_variation = true/false`を分けて記述する案も検討したが、同列が`NOT NULL`であるため、どちらの場合も`owner_user_id = :userId`にまとめられる。`allow_ai_variation`はAI生成元として利用できるかを示すもので、所有者によるアクセス制御とは別の関心事である。

## 5. 復習・問題一覧への適用

### 5.1 復習（`StudyHistoryRepository`）

`countReviewQuestions(...)`と`findReviewQuestions(...)`にも所有者条件を追加した。通常学習で取得を制限していても、復習処理は`study_history`から`question`をJOINして取得するため、過去のデータやDBから直接登録されたデータに他人の所有する問題が含まれている可能性を考慮する必要がある。

```sql
AND (
    (:sourceCondition = 'ALL'
        AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId))
    OR (:sourceCondition = 'ORIGINAL_ONLY'
        AND q.ai_generated = false
        AND (q.owner_user_id IS NULL OR q.owner_user_id = :userId))
    OR (:sourceCondition = 'GENERATED_ONLY'
        AND q.ai_generated = true
        AND q.owner_user_id = :userId)
)
```

これは単なる画面側の二重チェックではなく、**問題を取得するクエリ自身でアクセス範囲を制限する**ための修正である。

### 5.2 ユーザー用問題一覧（`QuestionRepository`）

`findFilteredUserQuestionList(...)`の一覧取得クエリと件数取得クエリの両方に、通常学習と同様の所有者条件を適用した。これにより、他のユーザーが所有する非AI問題が検索結果に表示されないようにした。

### 5.3 Admin用問題一覧

`findFilteredAdminQuestionList(...)`は、Adminが所有者を問わず全問題を管理するための一覧である。所有者のログインIDも取得する設計になっているため、今回の一般ユーザー向けアクセス制限は適用しない。

## 6. 動作確認

### 6.1 非公開問題追加前

非ログイン状態では初級・中級・上級ともに110問だった。

![](../../images/0023-30.png)

開発者アカウントでログインすると、初級112問・中級111問・上級112問だった。共通問題330問に加え、開発者所有のAI生成由来問題5問が含まれていた。

![](../../images/0023-31.png)

### 6.2 開発者所有の非AI問題を10問追加

`ai_generated = false`、`owner_user_id = 7`として、初級3問・中級4問・上級3問の計10問を追加した。追加した問題は、例えば次のような内容である。

| 難易度 | 日本語 | 中国語（繁体字） |
| --- | --- | --- |
| 初級 | 冷蔵庫を開けたら、プリンが私を見つめていました。 | 我打開冰箱的時候，布丁正在盯著我看。 |
| 中級 | 財布を忘れたことに、レジで気づきました。 | 我到了櫃檯才發現自己忘了帶錢包。 |
| 上級 | 早く寝ようと思っていたのに、気づいたら午前三時にWikipediaを読んでいました。 | 我本來想早點睡，回過神來已經凌晨三點了，我還在看維基百科。 |

追加後、開発者アカウントの通常学習メニューでは初級・中級・上級ともに115問、合計345問になった。

![](../../images/0023-32.png)

別のユーザーでログインすると、開発者とは異なる問題数が表示された。問題数は各ユーザーが所有するAI生成由来問題などの数によっても異なる。

![](../../images/0023-33.png)

### 6.3 ユーザー用問題一覧

開発者のユーザー用問題一覧には、追加した10問（問題ID 691〜700）が表示された。

![](../../images/0023-34.png)

一方、別のユーザーの問題一覧では追加した10問は表示されず、末尾は問題ID 689だった（690は欠番）。

![](../../images/0023-35.png)

これらの確認により、**所有者本人には非公開問題が通常の問題と同様に表示され、他のユーザーには通常学習やユーザー用問題一覧で表示されないこと**を確認した。なお、このスクリーンショットによる検証は主に件数と一覧表示を確認したものであり、評価・お気に入り操作への直接ID指定や復習の異常データに関する個別テストまで実施したことを示すものではない。

## 7. まとめ

今回の修正では、`ai_generated`と`owner_user_id`の役割を分離し、問題の公開範囲を所有者情報で判断するようにした。通常学習の件数・問題取得、未学習問題、復習、ユーザー用問題一覧、評価・お気に入り操作のアクセスチェックを横断的に見直した。

**画面に表示しないことと、データへアクセスできないことは別である。** 問題IDを指定する操作や別経路のRepository検索も考慮し、問題を取得・操作する処理でアクセス制限を確認する構成とした。
