# 029-01 問題リスト機能①｜DB設計と基本機能の実装【学習ログ】

## 概要

今回の問題リスト機能では、ユーザーが任意のリストを作成し、1つの問題を複数のリストに登録できるようにした。

従来のお気に入り機能は問題ごとの登録・未登録というON/OFF状態だったが、問題リスト機能では、リストと問題の多対多の関係を扱う必要がある。

029-01では、**中間テーブルと複合主キーによるデータ設計、JPAでの関連付け、画面表示用DTO、更新日時の扱い**について、実装を通して学んだことをまとめる。チェックボックスや`fetch()`を利用した操作については029-02、詳細画面の再設計については029-03で扱う。

---

## 1. 中間テーブルを使ったリストと問題の関係

最初に理解する必要があったのが、リストと問題の関係だった。

1つのリストには複数の問題を登録できる。

同時に、1つの問題についても、

```text
問題A
 ├─ HSK6
 ├─ 苦手問題
 └─ 後で復習
```

のように複数のリストへ登録できる。

そのため、

```text
QUESTION_LIST
      1
      |
      N
QUESTION_LIST_ITEM
      N
      |
      1
QUESTION
```

という中間テーブルを利用する構成にした。

`question_list_item`では、

```text
list_id
question_id
```

を組み合わせた複合主キーとした。

これによって、

```text
list_id = 1
question_id = 100
```

という組み合わせは1行しか存在できない。

つまり、同じ問題を同じリストへ二重登録できないことをDB側でも保証できる。

今回の実装から、**多対多の関係では単純にどちらか一方へ外部キーを持たせるのではなく、両方のIDを持つ中間テーブルによって関係そのものを保存できる**ことを確認できた。

---

## 2. `@EmbeddedId`と`@MapsId`

`question_list_item`は、

```text
list_id + question_id
```

を主キーとするため、Entityでも通常の単一`@Id`ではなく複合主キーを扱う必要があった。

そこで、

```java
@EmbeddedId
private QuestionListItemKey id;
```

として、`QuestionListItemKey`に、

```java
private Long listId;
private Long questionId;
```

を持たせた。

ただし、これだけではJPA上で、

```text
listId
    → QuestionList

questionId
    → Question
```

というEntity同士の関連までは表現できない。

そこで、

```java
@ManyToOne
@MapsId("listId")
@JoinColumn(name = "list_id")
private QuestionList questionList;

@ManyToOne
@MapsId("questionId")
@JoinColumn(name = "question_id")
private Question question;
```

とした。

ここで`@MapsId("listId")`は、

```text
QuestionListItemKey.listId
        ↕
QuestionList.listId
```

を対応付ける役割を持つ。

同じように、

```text
QuestionListItemKey.questionId
        ↕
Question.questionId
```

も対応付ける。

今回の実装で、**`@EmbeddedId`は複合主キーそのものを表し、`@MapsId`はその主キーの値と関連Entityを結び付けるために利用できる**ことを学んだ。

---

## 3. Entityをそのまま画面へ返さずDTOへまとめる

リスト機能の最初の段階では、リストに登録されている問題を`List<Question>`として取得していた。

しかし、実際の問題リスト画面では、

```text
中国語
日本語
難易度
理解度
お気に入り
生成元
ピンイン
注音
別解
構文
```

などを表示する。

ここで問題になったのが、これらすべての情報を`Question`が持っているわけではないことだった。

例えば、

```text
Question
    → 問題本文、難易度、発音、生成元など

StudyHistory
    → 理解度

Favorite
    → お気に入り登録状態

Structure
    → 構文名
```

というように情報が複数のテーブルへ分かれている。

そこで`QuestionListItemDto`を作成し、RepositoryのSQLで必要な情報をまとめて取得するようにした。

```java
public interface QuestionListItemDto {

    Long getQuestionId();

    String getChineseText();

    String getJapaneseText();

    Difficulty getDifficulty();

    Evaluation getEvaluation();

    boolean isFavorite();

    boolean isAiGenerated();

    String getPinyin();

    String getZhuyin();

    String getAlternativeAnswer();

    String getStructureName();
}
```

SQLでは、

```sql
FROM question_list_item qli

JOIN question q
    ON qli.question_id = q.question_id

JOIN structure s
    ON q.structure_id = s.structure_id

LEFT JOIN study_history sh
    ON q.question_id = sh.question_id
    AND sh.user_id = :userId

LEFT JOIN favorite f
    ON q.question_id = f.question_id
    AND f.user_id = :userId
```

のように複数のテーブルをJOINする。

これによって、画面側ではデータがどのテーブルから来たのかを意識せず、

```javascript
question.chineseText
question.evaluation
question.favorite
question.structureName
```

のように1つのDTOとして扱える。

今回の実装から、**画面が必要とするデータとEntityの構造は必ずしも一致しないため、複数のEntityにまたがる表示用データはDTOへまとめる方法がある**ことを確認できた。

---

## 4. `@UpdateTimestamp`は関連テーブルの変更では更新されない

リストを更新日時の降順で表示するため、`QuestionList`には、

```java
@UpdateTimestamp
private LocalDateTime updatedAt;
```

を設定していた。

しかし、問題をリストへ追加・削除しても`updatedAt`が更新されなかった。

原因は、問題の追加・削除で変更されているのは、

```text
question_list_item
```

であって、

```text
question_list
```

そのものではないためだった。

つまり、

```text
question_listのlist_name変更
    → QuestionList UPDATE
    → @UpdateTimestampが更新される

question_list_itemへ問題追加
    → QuestionList自体はUPDATEされない
    → @UpdateTimestampは更新されない
```

という違いがある。

そこで問題を追加・削除した際には、リスト側についても更新されたことが分かるように`updatedAt`を更新する処理を追加した。

今回の実装から、**`@UpdateTimestamp`は関連するデータが何か変更されたら自動的に更新される仕組みではなく、そのEntity自身がUPDATEされた場合に働くもの**だと理解できた。

---

## まとめ

029-01では、問題リスト機能のデータ構造と、DBから画面表示に必要な情報を取得する方法を中心に学習した。

- リストと問題の多対多の関係を、中間テーブル`question_list_item`で表現すること
- `list_id`と`question_id`の組み合わせを複合主キーにして、同じリストへの二重登録を防ぐこと
- `@EmbeddedId`で複合主キーを表し、`@MapsId`で関連Entityと対応付けること
- 複数テーブルに分かれた情報を、画面表示用の`QuestionListItemDto`にまとめること
- `@UpdateTimestamp`は関連テーブルの変更だけでは更新されず、更新対象のEntityを意識する必要があること

特に、**問題そのもののデータと「どのリストに登録されているか」という関係のデータを分けて管理する**ことが、今回の設計の基本になった。

次の029-02では、このDB上の登録状態をJavaScriptのチェックボックスに反映し、非同期で追加・削除する仕組みを扱う。
