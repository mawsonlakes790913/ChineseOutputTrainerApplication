# 022 Admin専用メニューの実装

このチャプターでは、アプリケーションの管理に必要な以下のAdmin専用機能を実装した。

1. Adminメニューページ
2. Admin用問題一覧ページ
3. 問題追加ページ
4. 問題編集ページ
5. ユーザー管理ページ
6. 文法・構造管理ページ

なお、Admin画面は日本語表示のみとし、基本的に`messages.properties`による多言語化は行わない。

# 準備

Admin機能の実装・動作確認用に、`ROLE_ADMIN`を持つユーザーを作成した。

# 1. Adminメニューページ

各Admin機能への入口となるAdminメニューページを実装した。

ユーザー管理、問題管理、問題追加などの各管理ページへ遷移できるようにした。

この部分は既存のControllerやThymeleafを利用した単純な画面遷移の実装であり、特に難しい点はなかった。

# 2. Admin用問題一覧ページ

Admin用問題一覧ページでは、User用問題一覧ページをベースにしつつ、管理者がすべての問題を確認できるようにした。

User用との主な違いは、全ユーザーが所有するAI生成由来の問題も対象とし、そのOwnerも表示する点である。一方、理解度やお気に入りなどのユーザー個人に紐づく学習情報は検索条件から除外した。また、普通話・國語の両方をまとめて検索できるようにした。

# 2-1. 問題一覧ページ及び検索機能

問題一覧では、難易度、生成元、文法・構造、言語、日本語・中国語キーワードによる検索機能を実装した。

基本的な検索処理や画面構成はUser用問題一覧を踏襲したが、Admin用では全ユーザーのAI生成問題を扱うため、問題だけでなくOwnerの情報も取得する必要があった。そこで、今回は`Question` Entityをそのまま返すのではなく、一覧表示に必要な情報だけを持つ`AdminQuestionListDto`を用意した。

## Interface-based Projection

今回初めて使用したのが、Interface-based ProjectionによるDTOである。

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

通常であればDTOクラスを作成して検索結果を詰め替える必要があるが、Spring Data JPAではRepositoryの戻り値をインターフェースにして、SQLの`AS`で付けた別名とgetter名を対応させることができる。

例えば、

```sql
SELECT
    q.question_id  AS questionId,
    s.name         AS structureName,
    u.login_id     AS ownerLoginId
```

とすると、それぞれ`getQuestionId()`、`getStructureName()`、`getOwnerLoginId()`に対応する。

この方法ではDTOの実装クラスやコンストラクタを自分で用意する必要がなく、一覧表示に必要な項目だけを取得できることを学んだ。

## JOINとLEFT JOINの使い分け

Repositoryでは`question`だけでは取得できない文法・構造名とOwnerのlogin IDを取得するため、`structure`と`users`を結合した。

ここで重要だったのが、`structure`には`JOIN`、`users`には`LEFT JOIN`を使用したことである。

```sql
JOIN structure s
    ON q.structure_id = s.structure_id

LEFT JOIN users u
    ON q.owner_user_id = u.id
```

問題には必ず文法・構造が設定されているため`structure`は通常の`JOIN`でよい。

一方、Ownerを持つのはAI生成由来の問題だけであり、通常問題の`owner_user_id`は存在しない。そのため`users`を通常の`JOIN`にすると、Ownerを持たない通常問題が検索結果から消えてしまう。

そこで`LEFT JOIN`を使用し、Ownerが存在しない問題も残したまま、AI生成問題についてのみOwner情報を取得するようにした。

これによって、**関連データが必ず存在する場合と、存在しない場合がある場合でJOINを使い分ける必要がある**ことを理解できた。

## User用問題一覧との違い

User用問題一覧では、ログインユーザー自身の学習状況を表示するため、`study_history`や`favorite`も利用している。

しかしAdmin用問題一覧の目的は問題そのものの管理なので、理解度やお気に入りといった個人の学習情報は必要ない。

また、User用ではAI生成問題を、

```sql
q.owner_user_id = :userId
```

によって自分が所有するものだけに限定していたが、Admin用ではOwnerによる制限を行わず、すべてのユーザーが所有するAI生成問題を取得するようにした。

同じ「問題一覧」であっても、**誰が何のために使う画面なのかによって必要な検索条件やJOIN対象が変わる**点が今回の実装で重要だった。

## Service・Controller・画面

Serviceでは、検索条件が未指定の場合に「全言語」「全難易度」「全文法・構造」「すべての生成元」として扱い、Repositoryへ渡す検索条件を整えるようにした。

ControllerやHTML、JavaScriptについてはUser用問題一覧の実装を大部分で再利用できたため、新しく学習する内容は少なかった。Admin用として不要な学習状況・お気に入りの条件を除き、Owner表示や普通話・國語の選択などを追加した。

## 実行

Admin用問題一覧にアクセスし、通常問題と各ユーザーが所有するAI生成問題が一覧に表示されることを確認した。

また、難易度、生成元、文法・構造、言語、日本語・中国語キーワードによる検索が機能することを確認した。

# 2-2. 削除機能の実装

Admin用問題一覧ページから、問題を直接削除できる機能を実装した。

問題を削除する処理自体は、ユーザーが自身のAI生成問題を削除する機能ですでに実装していたため、今回はその処理をAdmin用にも利用した。

## 関連データを先に削除する

`Question`を削除する前に、その問題を参照している`Favorite`と`StudyHistory`を削除する必要がある。

```java
@Transactional
public void deleteOneQuestion(Long questionId) {

    favoriteRepository.deleteByQuestionQuestionId(questionId);
    studyHistoryRepository.deleteByStudyHistoryKeyQuestionId(questionId);
    questionRepository.deleteById(questionId);
}
```

この処理は以前のAI生成問題の削除機能ですでに扱っているため、今回新しくRepositoryを実装する必要はなかった。

また、一連の削除処理には`@Transactional`を付け、途中で処理に失敗した場合に一部のデータだけが削除された状態にならないようにしている。

## 削除後も検索条件を維持する

今回工夫したのは、**問題を削除した後も検索条件やページ番号を維持すること**である。

例えば検索条件を指定して3ページ目を表示している状態で問題を削除した場合、単純に

```java
return "redirect:/admin/question/list";
```

とすると、検索条件やページ番号がすべて失われて最初の一覧へ戻ってしまう。

そこで、一覧ページを表示した時点で現在のURLを取得した。

```java
String currentUrl = request.getRequestURI();

if (request.getQueryString() != null) {
    currentUrl += "?" + request.getQueryString();
}

model.addAttribute("currentUrl", currentUrl);
```

`getRequestURI()`でパス部分を取得し、`getQueryString()`で検索条件やページ番号を含むクエリ文字列を取得する。

そして削除フォームから、そのURLを`returnUrl`として送信するようにした。

```html
<input type="hidden"
       name="returnUrl"
       th:value="${currentUrl}">
```

削除後は固定URLではなく、

```java
return "redirect:" + returnUrl;
```

とすることで、削除前と同じ条件の問題一覧ページへ戻れるようにした。

### 工夫した点・勉強になった点

検索機能のある一覧画面では、削除処理そのものだけでなく、**処理後にユーザーをどの状態へ戻すかも考える必要がある**と分かった。

また、URLのクエリパラメータには検索条件やページ番号が含まれているため、現在のURLを保持してリダイレクト先として利用すれば、個々の検索条件を一つずつhiddenで管理しなくても画面状態を維持できる。

## 削除結果の通知

削除に成功した場合は`RedirectAttributes`に成功メッセージを設定し、リダイレクト後の問題一覧ページに表示するようにした。

これにより、削除後も検索条件を維持したまま、処理が正常に完了したことをAdminユーザーへ通知できるようになった。

## 実行

検索条件を設定した状態で問題を削除し、問題が正常に削除されることを確認した。

また、削除後に成功メッセージが表示され、削除前に設定していた検索条件やページ情報も維持されることを確認した。

## 2-3. 追加修正 - 問題の表示順を見直す

問題追加機能を実装したことで、新しく追加した問題は`question_id`が最も大きくなるため、`ORDER BY question_id DESC`によって問題一覧の先頭に表示されていた。

しかし、問題編集では既存の`question_id`は変わらない。そのため、古い問題を編集しても一覧の後方に残り、編集結果をすぐに確認しにくいという問題があった。:contentReference[oaicite:0]{index=0}

そこで`Question`に作成日時と更新日時を追加し、Admin用問題一覧では最終更新日時が新しい問題から表示するように変更した。

### 作成日時・更新日時の追加

当初は「最後に追加または編集された日時」だけを持たせることも考えたが、作成日時と更新日時は意味が異なるため、

```java
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

の2つに分けて管理することにした。

これにより、新規追加では両方が現在日時となり、編集では`createdAt`を維持したまま`updatedAt`だけを更新できる。

### 既存テーブルへのNOT NULLカラム追加

今回はすでに`question`テーブルにデータが存在していたため、最初から`NOT NULL`でカラムを追加するのではなく、次の順番で変更した。

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

まずNULLを許可した状態でカラムを追加し、既存データを現在日時で埋めてから`NOT NULL`制約を付けた。

既存データがあるテーブルに必須カラムを追加する場合は、**既存レコードをどのように移行するかまで考える必要がある**ことを学んだ。

今回は過去の本当の作成日時・更新日時を復元できないため、既存データについては現在日時を初期値とした。

### JPA Auditing

作成日時と更新日時を保存・更新のたびに手動で設定するのではなく、Spring Data JPAの`JPA Auditing`を利用して自動的に記録するようにした。

```java
@EntityListeners(AuditingEntityListener.class)
public class Question {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

`@CreatedDate`は新規作成時、`@LastModifiedDate`は更新時の日時を自動的に設定する。

また、

```java
updatable = false
```

を`createdAt`に指定することで、作成日時がUPDATEの対象にならないようにした。

今回初めてJPA Auditingを使用し、日時を設定する処理を各Serviceに書かなくても、Entityのライフサイクルに合わせて自動的に管理できることを学んだ。

JPA Auditingを使用するには、メインクラス側でも有効化する必要がある。

```java
@SpringBootApplication
@EnableJpaAuditing
public class ChineseOutputForgeApplication {
    ...
}
```

今回の設定は、

```text
@EnableJpaAuditing
        ↓
アプリケーション全体でAuditingを有効化

@EntityListeners(AuditingEntityListener.class)
        ↓
QuestionでAuditingを使用

@CreatedDate / @LastModifiedDate
        ↓
日時を自動記録するフィールドを指定
```

という関係になっている。

実際に問題を新規追加・編集してDBを確認したところ、新規追加時には作成日時と更新日時が設定され、編集時には作成日時を維持したまま更新日時だけが変更されることを確認できた。

### 表示順の切り替え

日時を記録できるようになったため、Admin用問題一覧のデフォルト表示を`updated_at DESC`に変更した。

一方、問題を登録順に確認したい場合もあるため、「最終更新日時が新しい順」と「問題IDの昇順」を選択できるようにした。

並び順は`AdminQuestionSortCondition`としてEnumで管理した。

```java
public enum AdminQuestionSortCondition {

    UPDATED_DESC,
    QUESTION_ID_ASC

}
```

Repositoryでは、選択された値によって`ORDER BY`を切り替える。

```sql
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
```

ここでは`CASE`を使うことで、1つのNative Queryの中で動的に並び順を変更している。

`UPDATED_DESC`の場合は`updated_at DESC`が有効になり、`QUESTION_ID_ASC`の場合は`question_id ASC`が有効になる。条件に該当しない`CASE`は`NULL`となるため、その部分は並び順に影響しない。

また、更新日時が同じ問題が複数存在する可能性があるため、最後に`question_id DESC`を指定して表示順が一定になるようにした。

今回、**WHERE句による検索条件だけでなく、ORDER BYもCASEを使って条件によって切り替えられる**ことを学んだ。

### 実行

問題一覧では、デフォルトで最終更新日時が新しい順に表示され、「問題IDの昇順」に切り替えることもできるようになった。

また、古い問題を編集すると`updated_at`が更新され、編集完了後にその問題が一覧の先頭へ表示されることを確認した。

これにより、新規追加だけでなく編集した問題についても、変更結果を一覧画面ですぐに確認できるようになった。

# 3. 問題追加ページ

Adminが新しい問題を登録するための問題追加ページを実装した。

後に実装する問題編集ページでも同じ入力項目を扱うため、追加・編集で共通利用できる`QuestionForm`を作成した。

## FormとEntityのデータの持ち方

今回意識した点は、画面から受け取るFormとDBを表すEntityでは、必ずしもデータの持ち方が同じではないことである。

例えば文法・構造について、`QuestionForm`では選択された値を、

```java
private Long structureId;
```

としてIDだけで受け取る。

一方、`Question`では`Structure` Entityとの関連として保持しているため、登録時には`structureId`から対応する`Structure`を取得してQuestionへ設定する必要があった。

これにより、**Formは画面とのデータ受け渡しに適した形、EntityはDBとの対応に適した形で設計すればよく、両者を同じ構造にする必要はない**ことを理解できた。

## 入力項目同士に依存関係がある場合のUI

問題には、

- 別解がある場合のみ、別解・別解拼音・別解注音を入力する
- AI生成を許可する場合のみ、テンプレートを入力する

という項目間の依存関係がある。

そこでJavaScriptを使い、選択状態に応じて必要な入力欄だけを有効化するようにした。

これまでのように単純に値を入力するフォームだけでなく、**ある項目の状態によって別の入力項目の状態を変化させるフォーム**を実装できた。

なお、Controllerによる画面表示や登録処理、Bean Validationなどはこれまでにも使用してきた内容が中心であり、特に新しく学習した点は少なかった。

# 3-1. プレースホルダの入力を容易にする機能

テンプレートで使用するプレースホルダは種類が多く、それぞれ意味も異なるため、手入力では入力ミスや使い分けの間違いが起こりやすい。

そこで、使用可能なプレースホルダを意味付きのボタンとして表示し、クリックするとテンプレートのカーソル位置へ挿入できるようにした。

## data属性によるプレースホルダの管理

各ボタンには、表示する文字とは別に、実際に挿入するプレースホルダを`data-placeholder`として持たせた。

```html
<button type="button"
        class="placeholder-button"
        data-placeholder="{subject}">
    主語 {subject}
</button>
```

JavaScriptでは、

```javascript
button.dataset.placeholder
```

によって`data-placeholder`の値を取得できる。

今回、**HTML側にJavaScriptで利用するデータを持たせる方法として`data-*`属性を利用できる**ことを学んだ。

## カーソル位置への文字列挿入

今回特に勉強になったのは、`textarea`の任意のカーソル位置へ文字列を挿入する処理である。

```javascript
const start = template.selectionStart;
const end = template.selectionEnd;
```

`selectionStart`と`selectionEnd`を使うことで、現在のカーソル位置だけでなく、選択されている文字列の範囲も取得できる。

その位置を利用して、

```javascript
template.value =
    template.value.substring(0, start)
    + placeholder
    + template.value.substring(end);
```

とすることで、「選択範囲より前の文字列 + プレースホルダ + 選択範囲より後の文字列」と組み直している。

そのため、単純な末尾への文字追加ではなく、**カーソル位置への挿入と、選択中の文字列の置換を同じ処理で実現できる**ことが分かった。

さらに、

```javascript
template.setSelectionRange(
    cursorPosition,
    cursorPosition
);

template.focus();
```

によって、挿入後のカーソルをプレースホルダの直後へ移動し、そのまま入力を続けられるようにした。

`selectionStart`、`selectionEnd`、`setSelectionRange()`を組み合わせることで、JavaScriptからテキスト入力欄のカーソルや選択範囲まで操作できることが今回新しく学んだ点だった。

また、AI生成を許可しておらずテンプレート自体が無効な場合はプレースホルダも挿入できないようにし、既存の入力制御と矛盾しないようにした。

# 3-2. 拼音と注音はAIに入力させる

拼音と注音は手入力の負担が大きく、声調記号などの入力ミスも起こりやすい。一方、使用言語と中国語本文が分かっていればAIによる生成が可能であるため、問題登録時に発音情報を自動生成するようにした。

## AIに任せる処理の切り分け

今回の処理では、Adminが入力した中国語をそのままAIへ渡すのではなく、AIへの入力と出力をそれぞれ専用DTOに分けた。

```text
QuestionForm
    ↓
AiPronunciationRequestDto
    ↓
AI
    ↓
AiPronunciationResponseDto
    ↓
Question
```

`AiPronunciationRequestDto`には発音生成に必要な使用言語、中国語、別解だけを持たせ、レスポンス側には拼音・注音と別解の発音情報だけを持たせた。

これにより、**画面から受け取るデータ、AIに必要なデータ、AIから受け取るデータはそれぞれ役割が異なるため、DTOも用途ごとに分けた方が責務が明確になる**ことを改めて確認できた。

## Structured Outputを単一オブジェクトで扱う

AI APIを使ったJSON形式でのデータ取得自体はAI問題生成ですでに実装していたため、今回新しく学ぶ部分はそれほど多くなかった。

ただし、AI問題生成では複数の問題を返すため、

```text
OBJECT
  └─ questions
       └─ ARRAY
            └─ question
```

という構造が必要だったのに対し、今回は1問分の発音情報だけを取得する。

そのため、`questions`という配列を持つ外側のSchemaは必要なく、拼音・注音などを直接持つ1つのOBJECTとしてレスポンスSchemaを定義できた。

同じStructured Outputでも、**AIから何件のデータをどのような構造で受け取りたいかによってSchemaの構造を変える必要がある**ことが分かった。

## 普通話と國語でプロンプトを分離する

発音生成では、`LanguageVariant`に応じて普通話用と國語用のプロンプトを切り替えるようにした。

中国語本文そのものは変更せず、指定された言語変種を基準として拼音・注音だけを生成させる役割に限定した。

このようにAIへ何でも判断させるのではなく、**アプリケーション側で使用するルールを選択したうえで、AIには限定された処理だけを任せる**構成にした。

## AI機能導入時の既存機能の残し方

拼音・注音の手入力欄はすぐに削除せず、一時的にコメントアウトして残した。

AIによる自動生成を導入した直後だったため、問題が発生した場合にすぐ手入力方式へ戻せるようにするためである。

新しい仕組みに置き換える際、動作が十分確認できるまでは既存の仕組みを完全に削除せず、**切り戻しやすい状態で段階的に移行する**という進め方も今回工夫した点だった。

# 4. 問題編集ページ

既存の問題について、現在の登録内容を確認しながら内容を変更できる問題編集ページを実装した。

問題追加と似た処理が多いが、新規Entityを作成するのではなく既存の`Question`を取得して更新することと、**変更前の値と変更後の入力値を同時に扱う**点が大きな違いとなった。

## 変更前データと入力データを分ける

今回最も考えた点は、編集画面で扱うデータの分け方である。

当初は既存の`Question`を`QuestionForm`へ変換し、そのまま編集フォームの初期値として使用する方法を考えていた。

しかし今回は、

```text
中国語：原本不靈光的腦袋就變得更笨
[新しい内容の入力欄]
```

のように、現在DBに保存されている内容を確認しながら新しい内容を入力できる画面にしたかった。

そこで、

```text
Question
    ↓
OriginalQuestionDTO
    ↓
変更前データとして表示
```

と、

```text
QuestionForm
    ↓
変更後データを入力
    ↓
Questionへ反映
```

を分けて扱うことにした。

これにより、

- `Question`：DB上のデータを扱う
- `OriginalQuestionDTO`：変更前のデータを画面へ表示する
- `QuestionForm`：変更後の入力値を受け取る

という役割分担になった。

今回の実装から、**DTOやFormは単にEntityを画面へ渡すための入れ物ではなく、「何のためのデータなのか」に応じて分けることができる**という考え方を学んだ。

## 追加と編集におけるEntityの扱いの違い

問題追加では、

```text
new Question()
↓
入力値をSET
↓
save()
```

だったが、編集では、

```text
questionIdから既存Questionを取得
↓
入力値をSET
↓
save()
```

となる。

つまり同じ`save()`を使用していても、新規追加では新しいEntityを保存するのに対し、編集では既存Entityの内容を書き換えるという違いがある。

編集画面を実装したことで、**INSERTとUPDATEでは画面やFormが似ていても、その前段階でEntityをどのように用意するかが異なる**ことを整理できた。

## AI生成由来の問題を編集不可にする

AI生成由来の問題については詳細を確認できる一方、編集はできない仕様とした。

そのため`OriginalQuestionDTO`に`aiGenerated`を持たせ、画面側ではその値に応じて入力欄や更新ボタンを無効化した。

これは単に編集ページそのものへのアクセスを禁止するのではなく、**閲覧は許可するが変更操作だけを禁止する**というUIにするためである。

同じ画面でも「閲覧できること」と「操作できること」は別々に制御できるという点が、今回の画面設計で重要だった。

Controllerやバリデーション、AIによる発音生成、プレースホルダ入力などは問題追加ページですでに実装した仕組みを利用できたため、この部分について新しく学習した内容は少なかった。

## 4-1. 追加修正 - Templateの条件付きバリデーション

Templateは常に必須なのではなく、

- AI生成を許可する場合 → 必須
- AI生成を許可しない場合 → 未入力でもよい

という条件がある。

そのため`template`に単純な`@NotBlank`を付けることはできず、`allowAiVariation`と`template`の2つを組み合わせて判定する独自バリデーションを実装した。

### クラスレベルの独自バリデーション

今回のように、あるフィールドの妥当性が別のフィールドの値によって決まる場合、単一フィールドに付けるバリデーションだけでは判定できない。

そこで`QuestionForm`全体を検証する`@ValidQuestionTemplate`を作成した。

```java
@ValidQuestionTemplate
@Data
public class QuestionForm {
    ...
}
```

実際の判定は`ValidQuestionTemplateValidator`で行い、

```java
if (!form.isAllowAiVariation()) {
    return true;
}

if (form.getTemplate() != null
        && !form.getTemplate().isBlank()) {
    return true;
}
```

とすることで、`allowAiVariation == true`かつTemplateが空欄の場合だけエラーになるようにした。

以前実装した`PasswordMatchValidator`は複数のFormで再利用できるようフィールド名を外から指定する汎用的なValidatorだったが、今回は`QuestionForm`固有の仕様なので、`QuestionForm`を直接受け取る専用Validatorとした。

つまり、Validatorをどこまで汎用化するかは「複数フィールドを使うか」ではなく、**その検証ルールを他でも再利用するかどうか**で考えることができる。

### クラスレベルのエラーを特定フィールドに紐付ける

もう一つ新しく学んだのが、クラスレベルのバリデーションエラーを特定のフィールドへ紐付ける方法である。

`@ValidQuestionTemplate`は`QuestionForm`全体に付けているため、そのままではエラーもForm全体に対するものになる。

そこで、

```java
context.disableDefaultConstraintViolation();

context.buildConstraintViolationWithTemplate(
        context.getDefaultConstraintMessageTemplate())
    .addPropertyNode("template")
    .addConstraintViolation();
```

として、デフォルトのクラスレベルエラーを無効化したうえで、`template`フィールドのエラーとして登録し直した。

これによって、クラス全体を使って判定するバリデーションであっても、

```html
th:errors="*{template}"
```

でTemplate入力欄の直下にエラーメッセージを表示できる。

**「どの範囲のデータを使って検証するか」と「画面上でどのフィールドのエラーとして扱うか」は別々に設定できる**ことが分かった。

### `th:object`と`#fields`の参照範囲

バリデーションエラーが発生したことを画面上部にも表示するため、外側の`container`にも、

```html
th:object="${questionForm}"
```

を設定した。

`#fields`は`th:object`で指定されたオブジェクトを対象として入力値やバリデーションエラーを参照する。そのため、これまで`form`内で使用していた`#fields`を画面上部でも使用するには、その範囲でも`questionForm`を参照できるようにする必要があった。

今回の修正によって、**`th:object`は`th:field`とのバインドだけでなく、`#fields`がどのオブジェクトのバリデーション結果を参照するかを決める役割も持つ**ことを理解できた。

# 5. ユーザー管理ページ

Adminからユーザーの検索、凍結・凍結解除、削除を行えるユーザー管理ページを実装した。

## アカウントの凍結状態をDBで管理する

ユーザーを一時的に利用不可にするため、`Users`に`accountLocked`を追加した。

既存データが存在するテーブルへの追加だったため、DBでは、

```sql
account_locked BOOLEAN NOT NULL DEFAULT FALSE
```

とした。

`DEFAULT FALSE`を設定することで既存ユーザーにも値を設定でき、`NOT NULL`のカラムを安全に追加できた。

以前の`created_at`、`updated_at`追加では一度NULL許可で追加して既存データを更新してから`NOT NULL`にしたが、今回のように**既存レコードへ設定する初期値が明確なら、DEFAULTを利用して追加する方法もある**ことが分かった。

## 検索条件をDTOとEnumでまとめる

ユーザー数が増えた場合でも目的のユーザーを探せるように、ログインIDとアカウント状態による検索を追加した。

検索条件は、

```text
ログインID
アカウント状態：ALL / ACTIVE / LOCKED
```

とし、入力された条件を`AdminUserSearchDto`へまとめた。また、アカウント状態は`AccountStatus` Enumで管理した。

ページネーションでは、ページ番号だけをURLへ渡すと検索条件が失われてしまうため、

```text
loginId=abc
accountStatus=LOCKED
page=1
```

のように検索条件もページリンクへ引き継いだ。

これはAdmin問題一覧でも利用した考え方であり、**検索とページネーションを組み合わせる場合は、ページ移動時にも検索条件を維持する必要がある**ことを再確認できた。

## Admin自身を操作できないようにする

ユーザーの凍結・凍結解除機能では、Adminを誤って凍結できないようにした。

画面上ではAdminに操作ボタンを表示しないが、それだけでなくService側でも、

```java
if (user.getRole() == Role.ADMIN) {
    throw new IllegalStateException(
        "管理者ユーザーは凍結できません。");
}
```

としている。

画面側だけで操作を禁止しても、リクエストを直接送信されれば処理を呼び出せてしまう。そのため、**UIで操作させないことと、サーバー側で実際に禁止することは別であり、重要な制約はService側でも守る必要がある**ことを意識した。

## ユーザー削除と外部キー制約

ユーザー削除では、`Users`だけを削除するのではなく、そのユーザーに紐づくデータも削除する必要があった。

今回は、

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

の順番で削除した。

ここで実際に、

```text
Key (question_id)=(83) is still referenced from table "favorite"
```

という外部キー制約違反が発生した。

この経験から、**関連を持つデータを削除するときは、単に「最終的に全部消せばよい」のではなく、外部キーの参照関係を考えて削除順序を設計する必要がある**ことを学んだ。

## 派生削除クエリと明示的なDELETEの違い

今回のユーザー削除で特に苦労し、Spring Data JPAについて深く勉強することになったのが、**派生削除クエリと`@Modifying + @Query`による明示的なDELETEの違い**である。

当初、ユーザーに紐づく`Favorite`の削除には、

```java
void deleteByFavoriteKeyUserId(Long userId);
```

というメソッドを使用していた。

Spring Data JPAでは、このようにメソッド名から削除条件を生成するものを派生削除クエリとして利用できる。

一見すると、

```java
@Modifying
@Query("""
    DELETE FROM Favorite f
    WHERE f.favoriteKey.userId = :userId
    """)
void deleteByFavoriteKeyUserId(
        @Param("userId") Long userId);
```

と最終的に削除されるデータは同じに見える。

しかし、今回調べたことで、**この2つは削除結果が同じでも、削除に至るまでの処理方法が異なる**ことを知った。

### 派生削除クエリの場合

```java
void deleteByFavoriteKeyUserId(Long userId);
```

という派生削除クエリでは、条件に一致するデータに対して直接一括DELETEを発行するのではなく、まず削除対象を取得し、それぞれをEntityとして削除する。

イメージとしては、

```text
削除条件に一致するFavoriteを検索
        ↓
Favorite Entityとして読み込む
        ↓
それぞれのEntityを削除
```

となる。

例えば、

```text
user_id    question_id
10         81
10         82
10         83
20         84
```

というFavoriteが存在するとき、

```java
deleteByFavoriteKeyUserId(10L);
```

を実行すると、

```text
user_id = 10 のFavoriteを検索
        ↓
Favorite A（question_id = 81）
Favorite B（question_id = 82）
Favorite C（question_id = 83）
        ↓
それぞれをEntityとして削除
```

という処理になる。

この方式では、削除対象をEntityとして扱うため、`@PreRemove`などのEntityのライフサイクルコールバックを実行できるという特徴がある。

### `@Modifying + @Query`の場合

一方、

```java
@Modifying
@Query("""
    DELETE FROM Favorite f
    WHERE f.favoriteKey.userId = :userId
    """)
void deleteByFavoriteKeyUserId(
        @Param("userId") Long userId);
```

では、削除対象の`Favorite`を1件ずつEntityとして取得するのではなく、DBに対して明示的なDELETEを発行する。

イメージとしては、

```text
deleteByFavoriteKeyUserId(10L)
        ↓
DELETE FROM Favorite
WHERE userId = 10
        ↓
条件に一致するレコードをまとめて削除
```

となる。

したがって、両者の違いは、

```text
【派生削除クエリ】

削除対象を検索
        ↓
EntityとしてJava側に読み込む
        ↓
それぞれを削除


【@Modifying + @Query】

DELETEクエリをDBへ発行
        ↓
条件に一致するデータをまとめて削除
```

と整理できる。

これまでは`deleteBy...()`も`@Query`で書いたDELETEも、「条件に一致するデータを削除する」という意味ではほぼ同じものだと考えていた。

しかし今回、**Repositoryメソッドは最終的な結果だけでなく、JPAがその処理をどのような手順で実行するのかまで理解する必要がある**ことを学んだ。

### 実際に発生した外部キー制約違反

この違いを調べるきっかけになったのが、ユーザー削除時に実際に発生した外部キー制約違反だった。

ユーザー削除では、

```text
① Favorite
    ↓
② AiGenerationHistory
    ↓
③ StudyHistory
    ↓
④ ユーザー所有のAI生成由来Question
    ↓
⑤ Users
```

という順番で削除するようにしていた。

Javaのコード上では`Favorite`を先に削除しているため、④でQuestionを削除するときには、そのQuestionを参照するFavoriteはすでに存在しないはずだった。

しかし実際には、Questionを削除する段階で、

```text
Key (question_id)=(83) is still referenced from table "favorite"
```

という外部キー制約違反が発生した。

つまり、**コード上ではFavoriteの削除処理を先に呼び出しているにもかかわらず、QuestionのDELETEが実行された時点では、そのQuestionを参照するFavoriteがDB上に残っていた**ことになる。

ここが今回最もつまずいた部分だった。

### 原因を断定しなかった

調査する中で、Favoriteの削除に使用していた派生削除クエリが、

```text
対象を検索
    ↓
Entityとして取得
    ↓
それぞれ削除
```

という動作をすることが分かった。

そのため、この動作が今回の一連の削除処理に影響した可能性を考えた。

ただし、ここで重要なのは、**派生削除クエリが外部キー制約違反の直接的な原因だったと断定できたわけではない**ことである。

今回実際に確認できた事実は、

```text
QuestionをDELETEした時点で
そのQuestionを参照するFavoriteがDB上に残っていた
```

というところまでである。

実際に発行されたSQLの順番やHibernate内部の状態まで確認したわけではないため、

```java
void deleteByFavoriteKeyUserId(Long userId);
```

を使用していたことだけが原因だったとは断定できない。

さらに、

```java
aiGenerationHistoryRepository.deleteByUserId(userId);

studyHistoryRepository.deleteByStudyHistoryKeyUserId(userId);
```

についても派生削除クエリを使用していたため、Favoriteだけの問題だったのか、一連の派生削除処理が影響していたのかについても特定できなかった。

この経験から、**エラーの原因として考えられる仮説と、実際に確認できた事実は分けて考える必要がある**ことも勉強になった。

### 今回は明示的なDELETEへ統一した

今回のユーザー削除で重要なのは、Entityごとの削除処理を行うことではなく、

```text
Favorite
    ↓
AiGenerationHistory
    ↓
StudyHistory
    ↓
Question
    ↓
Users
```

という依存関係に沿った順序で関連データを削除することである。

そこで、原因が完全に特定できていない状態でFavoriteだけを変更するのではなく、関連データ削除に使用していた派生削除クエリを、

```java
@Modifying
@Query(...)
```

による明示的なDELETEへ統一した。

これによって、削除対象をEntityとして取得して1件ずつ削除する方法ではなく、**各段階で対象データに対するDELETEを明示的に実行する設計**に変更した。

今回のつまずきによって、

- 派生削除クエリと一括DELETEは同じものではない
- Repositoryのメソッド名だけでなく、内部でどのような処理になるのかも意識する
- 外部キーを持つ複数テーブルを削除するときは削除順序が重要
- エラー発生時には「確認できた事実」と「原因についての推測」を区別する
- 原因を完全に特定できない場合でも、処理の目的に合ったより明確な実装へ変更する

という点を学ぶことができた。

今回のAdmin機能実装の中でも、特にJPAとDBの動作について理解が深まった部分だった。

**エラー発生時に推測した原因と、実際に確認できた事実を区別することも重要**だと感じた。

## 共通処理をどのServiceに置くか

ユーザーと関連データを削除する処理は、Adminによるユーザー削除だけでなく、一般ユーザー自身の退会でも必要になる。

そのためAdmin専用の`AdminUserService`ではなく、共通の`UserAccountService`に削除処理をまとめた。

これにより、

```text
ユーザー自身の退会 ─┐
                    ├→ UserAccountService → 共通の削除処理
Adminによる削除 ────┘
```

という構成にできた。

**処理をどのServiceに置くかは、その処理を呼び出している画面ではなく、「その処理がどの責務に属し、どこから再利用されるか」で考える**ことが重要だと学んだ。

# 6. 文法・構造管理ページ

問題の追加・編集時に適切な文法・構造が存在しないケースに対応するため、Adminから文法・構造の追加・編集・削除を行える管理機能を実装した。

一覧表示やページネーションはこれまでのAdmin画面とほぼ同じ仕組みを利用したため、新しく学習した点は少なかった。

## 文法・構造の追加

文法・構造の追加では、同じ名前を重複して登録できないようにした。

`Structure.name`にはDB側ですでに`unique = true`が設定されているが、それだけでは登録を実行した後にDBの制約違反となる。

そこで登録前に、

```java
boolean existsByName(String name);
```

で同名データの存在を確認し、重複している場合は画面上でエラーを表示するようにした。

ここでは、**DBの制約はデータの整合性を最終的に保証するためのものであり、ユーザーへ適切なエラーを返すためのチェックとは役割が異なる**ことを意識した。

## 編集時の重複チェックでは自分自身を除外する

文法・構造の編集でも名前の重複を確認する必要があるが、追加時と同じ`existsByName()`は使用できない。

編集対象自身も同じ名前を持っているため、名前を変更せずに更新しただけでも重複と判定されてしまうからである。

そこで、

```java
boolean existsByNameAndStructureIdNot(
        String name,
        Long structureId);
```

を使用した。

これは、

```text
同じname
AND
structureId != 編集中のstructureId
```

となるデータが存在するかを確認する。

今回の実装で、**新規登録時の重複チェックと編集時の重複チェックでは、編集対象自身を除外する必要があるという違い**を理解できた。また、Spring Data JPAの派生クエリでは`Not`によって「〜ではない」という条件も表現できることを学んだ。

## 文法・構造をそのまま削除できない問題

削除処理が今回最も考える必要のある部分だった。

`Question`にとって`Structure`は必須であるため、問題から参照されている`Structure`をそのまま削除することはできない。

そこで削除前に、

```text
削除対象Structureを使用しているQuestion
            ↓
Structure ID 23「その他」へ変更
            ↓
元のStructureへの参照がなくなる
            ↓
Structureを削除
```

という処理にした。

Question側の変更には一括UPDATEを使用した。

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

これにより、削除対象の文法を使用している問題を1件ずつ取得して変更するのではなく、一括して「その他」へ移行できる。

また、移行先である「その他」自体は削除できないようにした。

今回の実装から、**他のデータから参照されているマスターデータを削除する場合は、単純にDELETEするのではなく、参照しているデータをどう扱うかまで含めて削除仕様を設計する必要がある**ことを学んだ。

さらに、Questionの「その他」への変更とStructureの削除を`@Transactional`で一つの処理にすることで、途中だけ成功して不整合な状態になることを防いだ。

# Admin機能の実装を終えて

これで、Adminメニューから問題・ユーザー・文法や構造を管理するための一通りの機能が完成した。

今回の実装は当初考えていたよりもかなり大規模になった。

単純にAdmin専用のCRUD画面を追加するだけではなく、実際に管理機能として使用できるようにしていく過程で、

- 通常問題とAI生成由来問題を考慮した問題管理
- AIによる拼音・注音の自動生成
- 複数フィールドの関係を扱う独自バリデーション
- ユーザーの検索、凍結、削除
- 外部キーの参照関係を考慮した関連データの削除
- 文法・構造の追加、編集、削除
- 使用中のマスターデータを削除する際の「その他」への退避

など、それまで個別に学んできたSpring Boot、Spring Data JPA、Thymeleaf、JavaScript、Spring Securityなどの知識を組み合わせて実装する必要があった。

特に印象に残ったのは、**「画面上で操作できるようにすること」と「実際のデータを安全に管理できるようにすること」は別の問題である**という点だった。

例えばユーザー削除では、削除ボタンを作って`Users`を削除するだけでは済まず、Favorite、StudyHistory、AI生成履歴、AI生成由来Questionなどの参照関係を考える必要があった。

実際に外部キー制約違反にも遭遇し、その調査から派生削除クエリと`@Modifying + @Query`による明示的なDELETEの違いまで調べることになった。

また、文法・構造の削除でも、Questionから参照されているStructureをそのまま削除することはできないため、削除対象を使用しているQuestionを「その他」へ移行してから削除する必要があった。

このような実装を通して、**DBを利用するアプリケーションでは、1つのデータを変更・削除したときに他のデータへどのような影響が出るのかまで考えて設計する必要がある**ことを強く実感した。

バリデーションについても理解が深まった。

これまでは`@NotBlank`や`@Length`など、基本的に1つのフィールドだけを検証するものが中心だったが、Templateでは、

```text
allowAiVariation = true
        ↓
templateを必須にする

という複数フィールドの関係を検証する必要があった。

そこでクラスレベルの独自Validatorを作成し、さらにそのエラーをtemplateフィールドへ紐付けるところまで実装したことで、Bean Validationを単純な入力チェック以上の用途でも利用できることが分かった。

また、Admin問題追加では既存のAI機能を管理機能にも組み込み、拼音・注音をAIに生成させた。AI問題生成機能を実装したときに作ったStructured Outputの知識を別の用途へ応用できたことで、以前実装した機能を再利用・発展させる経験にもなった。

今回のAdmin機能は、これまで作ってきた機能の中でも特に多くの既存機能やテーブルと関係する実装だった。

そのため、Controller、Service、Repository、Entity、Form、DTO、Validation、Thymeleaf、JavaScript、DBの外部キー制約などを個別に実装するだけではなく、それぞれがアプリケーション全体の中でどう関係しているのかを考えながら実装する機会が多かった。

将来的にアプリケーションをAWSへデプロイして実際に運用することを考えると、問題やユーザーをDBから直接操作するのではなく、Admin画面から安全に管理できる仕組みは必要になる。

想定以上に時間のかかるチャプターになったが、単に機能を増やしただけではなく、これまで学習してきたSpring Bootの各要素を組み合わせて、「実際に運用するWebアプリケーションの管理機能」を作る経験ができたチャプターだった。