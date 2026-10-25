# 022 Admin管理機能の実装｜学習ログ

> このログは、Admin管理機能の実装を通じて新しく理解したこと、設計時に考えたこと、実際につまずいた点を振り返るものです。実装の詳細やコードの変更履歴は、022-01〜022-10の実装ログを参照してください。

## はじめに

Adminメニュー、問題管理、ユーザー管理、文法・構造管理を実装した。画面の表示や一般的なCRUD処理には既存機能を流用できる部分が多かった一方、管理者がアプリケーション全体のデータを扱うようになったことで、検索対象の違い、データ間の参照関係、更新・削除の安全性をこれまで以上に意識する必要があった。

特に勉強になったのは、**Interface-based Projection、JOINの使い分け、JPA Auditing、複数フィールドにまたがるバリデーション、派生削除クエリと一括DELETEの違い、参照されているマスターデータの削除設計**である。

## 1. 問題一覧の実装で学んだこと

### 1.1 Interface-based Projectionを初めて使用した

Admin用問題一覧では、通常問題だけでなく、全ユーザーのAI生成由来の問題も表示し、そのOwnerのログインIDも取得する必要があった。そこで、`Question` Entityをそのまま返すのではなく、一覧に必要な値だけを受け取る`AdminQuestionListDto`をInterface-based Projectionとして定義した。

```java
public interface AdminQuestionListDto {
    Long getQuestionId();
    LanguageVariant getLanguageVariant();
    String getChineseText();
    String getJapaneseText();
    Difficulty getDifficulty();
    String getStructureName();
    boolean isAiGenerated();
    String getOwnerLoginId();
}
```

Native Queryで取得したカラムに、たとえば`AS questionId`、`AS structureName`、`AS ownerLoginId`という別名を付けると、対応するgetterを通じて結果を参照できる。

DTOクラスに値を詰め替える方法しか意識していなかったため、**インターフェースをRepositoryの戻り値に指定して、必要な項目を取得できる**ことが新しい発見だった。実装クラスやコンストラクタを自分で用意せずに済む点も理解できた。

### 1.2 JOINとLEFT JOINを使い分ける理由が明確になった

一覧では文法・構造名を取得するために`structure`を、OwnerのログインIDを取得するために`users`を結合した。

```sql
JOIN structure s
    ON q.structure_id = s.structure_id
LEFT JOIN users u
    ON q.owner_user_id = u.id
```

`Question`には必ず文法・構造が設定されているため、`structure`は`JOIN`で取得できる。一方、通常問題にはOwnerが存在しないため、`users`を通常の`JOIN`にすると、その問題が結果から除外されてしまう。

**関連先が必ず存在するか、存在しない場合もあるか**によって結合方法を選ぶ必要があることを、具体的なデータの違いから理解できた。

### 1.3 同じ一覧画面でも利用目的によって検索条件が変わる

User用問題一覧では、学習履歴やお気に入りなど、そのユーザー個人の情報を検索条件に利用していた。しかしAdmin用問題一覧では、問題自体を管理することが目的なので、そうした条件は不要になる。

また、User用では自分が所有するAI生成問題に限定していたが、Admin用ではOwnerを限定せずに取得する。普通話と國語をまとめて検索できる点も異なる。

既存画面を流用する場合でも、単純にコピーするのではなく、**誰が何のために利用する機能なのか**を基準に、取得対象や条件を見直す必要があると分かった。

## 2. 一覧画面の操作後に状態を維持する工夫

問題削除では、削除処理そのものよりも、**削除後に検索条件とページ番号を維持すること**を工夫した。

検索結果の3ページ目で問題を削除した後、固定URLへ戻すだけでは検索条件が失われる。そこで、一覧表示時に`getRequestURI()`と`getQueryString()`から現在のURLを組み立て、削除フォームの`returnUrl`として渡した。

```java
String currentUrl = request.getRequestURI();
if (request.getQueryString() != null) {
    currentUrl += "?" + request.getQueryString();
}
model.addAttribute("currentUrl", currentUrl);
```

これにより、検索条件を一つずつhidden項目として管理せずに、元の一覧状態へ戻れるようにした。

今回、一覧画面の操作では、処理の成否だけでなく**操作後にユーザーをどの画面状態へ戻すか**も重要だと学んだ。

なお、当時の実装では送信された`returnUrl`を`"redirect:" + returnUrl`として使用していた。これは入力値をリダイレクト先に使用する設計なので、遷移先をアプリ内の許可されたパスに制限するなどの検証が必要である。ここでは当時の実装を記録しており、安全性を確認済みという意味ではない。

## 3. JPA Auditingと既存データの移行

### 3.1 「登録順」では編集した問題を見つけにくかった

当初は`question_id DESC`で新しい問題を先頭に表示していた。しかし、既存問題を編集してもIDは変わらないため、編集直後の問題を一覧で探しにくいことに気付いた。

そこで、`Question`に`createdAt`と`updatedAt`を追加し、最終更新日時が新しい順をデフォルトにした。

作成日時と更新日時を1つの値で兼用する案も考えたが、**いつ作成されたかと、いつ最後に変更されたかは別の情報**なので、フィールドを分けることにした。

### 3.2 データが存在するテーブルにNOT NULLカラムを追加する

今回の`question`テーブルにはすでにデータが存在していたため、最初から必須カラムを追加するのではなく、次の順序で変更した。

```sql
ALTER TABLE question
ADD COLUMN created_at TIMESTAMP,
ADD COLUMN updated_at TIMESTAMP;

UPDATE question
SET created_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE question
ALTER COLUMN created_at SET NOT NULL,
ALTER COLUMN updated_at SET NOT NULL;
```

**既存レコードをどのように埋めるかを考えてからNOT NULL制約を設定する**という、DBスキーマ変更時の考え方を学んだ。過去の実際の作成日時は復元できないため、既存データには移行時点の日時を設定した。

### 3.3 日時更新をServiceに書かずに自動化できた

`@EnableJpaAuditing`でAuditingを有効にし、Entityに`@EntityListeners(AuditingEntityListener.class)`を指定したうえで、`@CreatedDate`と`@LastModifiedDate`を使用した。

```java
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;

@LastModifiedDate
@Column(name = "updated_at", nullable = false)
private LocalDateTime updatedAt;
```

これまでのように各Serviceで日時を設定するのではなく、**Entityの保存・更新に合わせて自動的に日時を記録できる**ことを初めて学んだ。新規追加時には両方が設定され、編集時には`createdAt`を維持して`updatedAt`だけが変わることもDBで確認した。

### 3.4 ORDER BYにもCASEを使える

並び順の選択には`AdminQuestionSortCondition`を用意し、Native Queryの`ORDER BY`を`CASE`で切り替えた。

```sql
ORDER BY
    CASE WHEN :sortCondition = 'UPDATED_DESC'
         THEN q.updated_at END DESC,
    CASE WHEN :sortCondition = 'QUESTION_ID_ASC'
         THEN q.question_id END ASC,
    q.question_id DESC
```

これまで`CASE`は検索条件などで意識することが多かったが、**並び替えの対象列を条件によって切り替える用途にも使える**ことが分かった。同じ更新日時の問題がある場合に備えて、最後にIDによる並び順も指定した。

## 4. 問題追加・編集で学んだFormとDTOの役割

### 4.1 FormとEntityは同じ形でなくてよい

`QuestionForm`では文法・構造を`structureId`というIDで受け取るが、`Question` Entityでは`Structure`との関連として保持している。登録時にはIDから`Structure`を取得してEntityへ設定する必要があった。

ここで、**Formは画面との受け渡し、EntityはDBとの対応に適した形で設計すればよい**と理解できた。

また、別解がある場合だけ別解関連の入力欄を使い、AI生成を許可した場合だけTemplateを入力するなど、複数項目の状態が連動するフォームも実装した。JavaScriptで入力欄の有効・無効を切り替える経験になった。

### 4.2 編集前の値と編集後の入力値を分けた

編集画面では、保存済みの内容を見ながら新しい内容を入力できるようにしたかった。当初は既存の`Question`を`QuestionForm`に詰めて初期値として表示する案も考えたが、今回は次の役割に分けた。

- `Question`：DBに保存されたデータ
- `OriginalQuestionDTO`：変更前の内容を画面に表示するためのデータ
- `QuestionForm`：変更後の入力を受け取るデータ

**同じ問題に関するデータでも、表示用と入力用では目的が異なる**ため、DTOやFormを分けてよいと分かった。

追加では新しい`Question`を作成して保存するが、編集では既存の`Question`を取得して値を変更する。この違いも整理できた。

AI生成由来の問題については閲覧のみを許可し、編集欄や更新ボタンを無効化した。ここでは**閲覧と変更を別の操作として考える**ことが重要だった。ただし、画面でボタンを無効化するだけではサーバー側の更新を防げないため、更新処理側の制限も別途確認が必要である。

## 5. JavaScriptによる入力補助で学んだこと

Templateのプレースホルダは種類が多く、手入力では間違えやすかったため、ボタンからカーソル位置に挿入できるようにした。

ボタンに`data-placeholder`を設定し、JavaScriptでは`button.dataset.placeholder`で取得する。**HTMLの`data-*`属性に、画面表示とは別の処理用データを持たせられる**ことを学んだ。

特に新しかったのは、`textarea`のカーソル位置と選択範囲を操作する方法である。

```javascript
const start = template.selectionStart;
const end = template.selectionEnd;

template.value =
    template.value.substring(0, start)
    + placeholder
    + template.value.substring(end);
```

`selectionStart`と`selectionEnd`を使えば、カーソル位置への挿入と選択範囲の置換を同じ処理で実現できる。さらに`setSelectionRange()`と`focus()`で挿入直後の位置にカーソルを戻し、そのまま入力を続けられるようにした。

AI生成を許可していない場合はTemplate自体を無効化するため、プレースホルダ挿入もできないようにし、既存の入力制御との整合性を保った。

## 6. AIによる拼音・注音生成で再確認したこと

拼音・注音の手入力負担を減らすため、問題登録時にAIへ発音情報の生成を依頼するようにした。

AI問題生成機能でStructured Outputを扱った経験があったため、API連携そのものに新しく学ぶ点は多くなかった。一方、**AIに渡すデータとAIから受け取るデータを用途別のDTOに分ける**考え方を再確認できた。

`QuestionForm`から必要な言語変種、中国語本文、別解を`AiPronunciationRequestDto`へ渡し、結果は`AiPronunciationResponseDto`で受け取る。

以前のAI問題生成では複数問を受け取るため配列を含むSchemaが必要だったが、今回は1問分の発音情報なので、単一のOBJECTでよい。**同じStructured Outputでも、必要な結果の件数と構造に合わせてSchemaを設計する**ことを理解した。

また、普通話と國語ではプロンプトを分け、中国語本文の変更はAIに任せず、発音情報の生成だけに役割を限定した。AIに何でも判断させるのではなく、**アプリケーション側で条件を決め、AIに任せる範囲を絞る**設計を意識した。

自動生成導入直後は、手入力欄をすぐに削除せずコメントアウトして残した。問題が起きた場合に元の方式へ戻せるよう、段階的に切り替えるためである。

## 7. 条件付きバリデーションで理解が深まったこと

### 7.1 複数フィールドを組み合わせて検証する

Templateには「AI生成を許可する場合だけ必須」という条件がある。`template`に単純な`@NotBlank`を付けるだけでは、この条件を表現できない。

そこで、`QuestionForm`に`@ValidQuestionTemplate`を付け、`ValidQuestionTemplateValidator`で`allowAiVariation`と`template`の組み合わせを検証した。

```java
if (!form.isAllowAiVariation()) {
    return true;
}
if (form.getTemplate() != null
        && !form.getTemplate().isBlank()) {
    return true;
}
```

以前作成した`PasswordMatchValidator`は複数のFormで使えるよう汎用化したが、今回のルールは`QuestionForm`固有であるため、専用Validatorにした。**汎用化するかどうかは、複数フィールドを参照するかではなく、その検証ルールを再利用するかで判断する**と整理できた。

### 7.2 クラスレベルのエラーをフィールドへ紐付けられる

今回最も勉強になったのは、Form全体を検証していても、エラーを特定の入力欄に表示できることだった。

```java
context.disableDefaultConstraintViolation();
context.buildConstraintViolationWithTemplate(
        context.getDefaultConstraintMessageTemplate())
    .addPropertyNode("template")
    .addConstraintViolation();
```

これによって、クラスレベルで判定したエラーを`template`フィールドのエラーとして扱い、Thymeleafの`th:errors="*{template}"`で表示できる。

**検証に使うデータの範囲と、エラーを表示するフィールドは別々に設定できる**ことが分かった。

### 7.3 `th:object`と`#fields`の関係

フォーム外側の画面上部にもエラーを表示するため、`th:object="${questionForm}"`を設定した。

`#fields`は`th:object`で指定されたオブジェクトのバリデーション結果を参照する。今回の修正で、**`th:object`は入力値のバインドだけでなく、`#fields`の参照対象も決める**ことを理解できた。

## 8. ユーザー管理で考えた安全性とデータ設計

### 8.1 既存データがあるテーブルに初期値付きのカラムを追加する

ユーザーの凍結状態を表す`account_locked`は、既存ユーザーを凍結しない状態から開始するため、`BOOLEAN NOT NULL DEFAULT FALSE`で追加した。

問題の日時カラム追加時には、一度NULL許可で追加して値を埋めたが、**既存レコードへ設定する初期値が明確ならDEFAULTを利用する方法もある**と分かった。

### 8.2 UIで操作できなくするだけでは足りない

Admin自身を凍結しないよう、画面では操作ボタンを表示しないことに加え、Serviceでも管理者への凍結を禁止した。

```java
if (user.getRole() == Role.ADMIN) {
    throw new IllegalStateException(
        "管理者ユーザーは凍結できません。");
}
```

画面上の制限だけでは、直接リクエストを送信される可能性がある。**重要な制約はサーバー側でも検証する必要がある**と改めて意識した。

ユーザー検索では`AdminUserSearchDto`と`AccountStatus` Enumを用意し、ページ移動時に検索条件を引き継いだ。これは問題一覧で学んだ、一覧画面の状態を維持する考え方の応用だった。

## 9. ユーザー削除時の外部キー制約違反とJPAの調査

今回のAdmin機能で、**最も苦労し、JPAの動作について深く調べた部分**である。

### 9.1 実際に発生した現象

ユーザー削除では、関連データを先に削除してから、そのユーザーが所有するAI生成問題と`Users`を削除する必要があった。

当時は、次の順序で処理していた。

```text
Favorite
  ↓
AiGenerationHistory
  ↓
StudyHistory
  ↓
所有するAI生成由来Question
  ↓
Users
```

ところが、`Question`の削除時に次の外部キー制約違反が発生した。

```text
Key (question_id)=(83) is still referenced from table "favorite"
```

コード上では先にFavoriteの削除メソッドを呼び出していたのに、QuestionをDELETEする時点では参照するFavoriteがDB上に残っていた。**Javaの処理を記述した順番だけを見ても、DBで実際に何が起きるかは十分に理解できない**と感じた。

### 9.2 派生削除クエリと一括DELETEは動作が異なる

調査の中で、次の派生削除クエリと、明示的なDELETEの違いを学んだ。

```java
void deleteByFavoriteKeyUserId(Long userId);
```

```java
@Modifying
@Query("""
    DELETE FROM Favorite f
    WHERE f.favoriteKey.userId = :userId
    """)
void deleteByFavoriteKeyUserId(@Param("userId") Long userId);
```

派生削除クエリでは、対象を検索してEntityとして読み込み、各Entityを削除する。一方、`@Modifying + @Query`による明示的なDELETEは、対象行をまとめて削除するクエリを実行する。

派生削除では`@PreRemove`などのEntityライフサイクルコールバックを利用できる一方、一括DELETEはEntityを1件ずつ読み込む方式ではない。

これまではどちらも「条件に合う行を削除する」という点でほぼ同じものと考えていたが、**同じ結果を目指すRepositoryメソッドでも、その実行方法は異なる**と理解できた。

### 9.3 原因について確認できたことと、断定できないこと

ここは今回の振り返りで重要な点である。

**実際に確認できた事実**は、QuestionのDELETEが実行された時点で、そのQuestionを参照するFavoriteがDB上に残っていたことだった。

派生削除クエリの動作が影響した可能性は考えたが、実際に発行されたSQLの順番やHibernate内部の状態まで確認したわけではない。そのため、**派生削除クエリが直接の原因だったとは断定できない**。

また、FavoriteだけでなくAiGenerationHistoryやStudyHistoryの削除にも派生削除クエリを使用していたため、どの処理がどのように影響したかまでは特定できなかった。

エラー調査では、**観測した現象、仕組みについて調べて分かったこと、原因に関する仮説**を区別して記録する必要があると学んだ。

### 9.4 明示的なDELETEへ統一した理由

今回のユーザー削除では、Entityごとの削除イベントを利用することよりも、外部キーの依存関係に沿って関連データを削除することが目的だった。

そこで、原因が完全には特定できていない状態でFavoriteだけを変更するのではなく、関連データの削除を`@Modifying + @Query`による明示的なDELETEへ統一した。

この経験を通じて、**原因を推測だけで断定せず、処理の目的に合った明確な実装を選ぶ**という姿勢を学んだ。

### 9.5 共通の削除処理をどこに置くか

ユーザーと関連データの削除は、Adminによる削除だけでなく、一般ユーザー自身の退会でも必要になる。

そのため、削除処理を`AdminUserService`だけに置くのではなく、`UserAccountService`にまとめた。

**処理を呼び出す画面ではなく、その処理の責務と再利用先を基準にServiceを分ける**ことが重要だと分かった。

## 10. 文法・構造管理で学んだこと

### 10.1 DBの一意制約と画面側の重複チェックは役割が違う

`Structure.name`には一意制約があるが、それだけでは登録時にDBの制約違反になる。そこで追加前に`existsByName()`で重複を確認し、画面に分かりやすいエラーを表示するようにした。

**DBの制約はデータ整合性の最終的な保証、事前チェックは利用者への適切なフィードバック**という役割の違いを意識した。

### 10.2 編集時には自分自身を重複判定から除外する

追加時と同じ`existsByName()`を編集時に使うと、名前を変えずに保存した場合も、編集対象自身が重複として検出されてしまう。

そこで次の派生クエリを使用した。

```java
boolean existsByNameAndStructureIdNot(
        String name,
        Long structureId);
```

**新規登録と更新では、同じ「重複チェック」でも条件が異なる**ことを学んだ。また、派生クエリ名の`Not`で「自身以外」という条件を表現できることも理解した。

### 10.3 参照されているマスターデータは単純に削除できない

`Question`にとって`Structure`は必須なので、使用中の文法・構造をそのまま削除すると参照整合性の問題が生じる。

そこで、削除対象を参照している問題を先にStructure ID `23`の「その他」へ変更してから、対象のStructureを削除することにした。「その他」自体は削除できないようにした。

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

問題を1件ずつ取得する代わりに一括UPDATEを利用し、参照先の変更とStructureの削除を`@Transactional`でまとめた。

**参照されているマスターデータの削除では、削除対象だけでなく、それを使っているデータの移行先も設計しなければならない**ことを学んだ。

## 11. Admin管理機能全体を振り返って

今回の実装は、当初考えていたよりも大規模になった。ただし、難しかったのはAdmin専用画面を増やすこと自体ではない。これまで作ってきた複数の機能やテーブルを、管理者の操作によって安全に扱えるようにすることだった。

特に印象に残ったのは、**「画面で操作できること」と「関連するデータまで正しく扱えること」は別の問題**だという点である。

ユーザー削除ではFavorite、StudyHistory、AI生成履歴、所有するQuestionの参照関係を考えなければならなかった。文法・構造の削除では、それを参照するQuestionを「その他」へ移す必要があった。どちらも、1つのデータを変更・削除するときに、他のデータへ及ぶ影響まで考える経験になった。

また、独自Validatorでは複数フィールドの関係を検証し、そのエラーを特定フィールドへ表示する方法を学んだ。Interface-based ProjectionやJPA Auditing、Structured Outputの応用など、以前学んだ仕組みを別の場面で使えるようになった点も大きい。

今回の中で最も重要だった学びは、**不具合の原因をすぐに決めつけず、確認できた事実と仮説を分けて考えること**だった。外部キー制約違反の直接原因は特定できなかったが、その調査によって派生削除クエリと一括DELETEの動作の違いを理解し、処理の目的に合う実装へ変更できた。

Controller、Service、Repository、Entity、Form、DTO、Validation、Thymeleaf、JavaScript、DBの制約を個別に使う段階から、**それぞれがアプリケーション全体でどう関係するかを考える段階へ進めた**と感じる。

将来AWS上で実際に運用することを考えても、DBを直接操作するのではなく、Admin画面から管理できる仕組みを整える意義は大きい。単なるCRUDの追加にとどまらず、運用を意識したデータ管理を考える機会になったチャプターだった。
