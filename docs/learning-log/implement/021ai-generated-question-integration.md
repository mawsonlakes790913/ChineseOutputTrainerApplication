# 021 AI生成問題の既存学習機能への統合 学習ログ

このチャプターでは、前チャプターで`Question`テーブルへ保存できるようになったAI生成由来の問題を、既存の学習機能へ統合した。

対象となったのは、

1. 通常学習
2. 未学習問題
3. 復習
4. ユーザーメニューの問題一覧

である。

AI生成由来の問題は通常問題と同じ`Question`テーブルに保存される一方で、保存したユーザー本人だけが利用できる問題である。

そのため今回は、既存機能を大きく作り直すのではなく、既存の検索処理に、

```text
通常問題
+
ログインユーザー自身が所有するAI生成問題
```

という考え方を組み込んでいった。

---

# 1. 通常学習へAI生成由来の問題を統合

## 1-1. ログイン状態によって利用可能な問題を切り替える

通常学習では、最初にメニュー画面で難易度ごとの収録問題数を取得している。

AI生成問題を保存できるようになったことで、ここで表示すべき問題数がユーザーによって異なるようになった。

非ログインユーザーの場合は、

```sql
AND ai_generated = false
```

として通常問題だけを対象にする。

一方、ログインユーザーの場合は、

```sql
WHERE ai_generated = false
OR (
    ai_generated = true
    AND owner_user_id = :userId
)
```

として、

```text
通常問題
+
自分が所有するAI生成問題
```

を対象にした。

Controllerではログインしている場合のみユーザーIDを取得し、

```java
Long userId = null;

if (loginUser != null) {
    Users user = getLoginUser(loginUser);
    userId = user.getId();
}
```

Serviceへ渡す。

Serviceでは`userId`が`null`かどうかによって、非ログイン用とログイン用のRepositoryメソッドを呼び分けるようにした。

### 工夫した点

AI生成問題も同じ`Question`テーブルに存在するからといって、単純にすべての問題を取得しないようにした。

特に、

```sql
ai_generated = true
AND owner_user_id = :userId
```

をセットで条件にすることで、他のユーザーが保存したAI生成問題が出題されないようにした。

また、非ログインユーザーには`userId`自体が存在しないため、1つのQueryへ無理にまとめず、

```text
非ログイン
→ 通常問題のみを取得するQuery

ログイン
→ 所有者まで考慮するQuery
```

と処理を分けた。

### 勉強になった点

同じテーブルに保存されているデータであっても、

```text
DB上に存在するデータ
```

と、

```text
現在のユーザーが利用可能なデータ
```

は同じではない。

今回のようにユーザーが所有するデータを既存テーブルへ追加した場合、既存のSELECT文についても「誰がこのデータを利用できるのか」という観点から見直す必要があると分かった。

---

## 1-2. 問題の生成元による絞り込みを追加する

ログインユーザーは通常問題と自分のAI生成問題の両方を利用できるため、通常学習メニューから生成元を絞り込めるようにした。

最初は通常学習用として、

```java
public enum PracticeSearchCondition {

    ALL,
    ORIGINAL_ONLY,
    GENERATED_ONLY
}
```

を作成した。

Repositoryでは、

```sql
AND (
    (:searchCondition = 'ALL'
        AND (
            (ai_generated = true AND owner_user_id = :userId)
            OR ai_generated = false
        )
    )
    OR (:searchCondition = 'ORIGINAL_ONLY'
        AND ai_generated = false
    )
    OR (:searchCondition = 'GENERATED_ONLY'
        AND (
            ai_generated = true
            AND owner_user_id = :userId
        )
    )
)
```

として生成元を絞り込んだ。

### 工夫した点

特に注意したのが`ALL`の扱いだった。

`ALL`だからといって条件をなくしてしまうと、他ユーザーが所有するAI生成問題まで対象になってしまう。

そのため今回の`ALL`は、

```text
DB上のすべてのQuestion
```

ではなく、

```text
現在のユーザーが利用できるすべてのQuestion
```

という意味にした。

つまり、

```text
ALL
→ 通常問題 + 自分のAI生成問題

ORIGINAL_ONLY
→ 通常問題のみ

GENERATED_ONLY
→ 自分のAI生成問題のみ
```

となる。

### 勉強になった点

このQuery自体は、これまで問題一覧などで実装した、

```sql
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
```

や、

```sql
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
```

と同じ考え方で書くことができた。

以前であればこのような複数条件を持つQueryを書くだけでもかなり時間がかかったと思うが、既存の検索機能で同じ形式を何度も実装していたため、今回は短時間で組み立てることができた。

SQLそのものだけでなく、**過去に実装した検索条件のパターンを別の検索条件へ応用できるようになったこと**が今回の大きな進歩だった。

---

## 1-3. 生成元を変更したときに問題数を動的に更新する

生成元条件を変更すると利用可能な問題数も変化する。

そこで、

```java
@GetMapping("/practice/count")
@ResponseBody
```

のAPIを用意し、JavaScriptから現在選択されている`sourceCondition`を送信して問題数を再取得するようにした。

```javascript
const response =
    await fetch(
        `/practice/count?${params.toString()}`
    );

const data = await response.json();
```

取得した結果から、

```javascript
document.getElementById("beginnerCount")
    .textContent = data.beginnerCount;
```

のように難易度ごとの件数を更新した。

### 工夫した点

画面全体を再読み込みするのではなく、生成元を変更したときに必要な問題数だけを取得するようにした。

また、このAPIはログインユーザーだけが利用するため、

```java
@PreAuthorize("isAuthenticated()")
```

を付与した。

そのためにSecurityConfigでは、

```java
@EnableMethodSecurity
```

も有効にした。

### 勉強になった点

`@ResponseBody`を付けたControllerメソッドでは、通常のThymeleaf画面を返すのではなく、戻り値そのものをHTTPレスポンスBodyとして返せる。

今回の場合は`PracticeMenuDto`がJSONへ変換され、

```javascript
const data = await response.json();
```

としてJavaScript側から利用できる。

これによって、Spring MVCのControllerは画面遷移だけではなく、画面の一部分を更新するためのAPIとしても利用できることを確認できた。

---

## 1-4. 生成元変更時に出題範囲も更新する

最初の実装では生成元を変更すると問題数は正しく変化したが、

```text
1-10
11-20
21-30
```

などの出題範囲は変更前の状態のまま残っていた。

そこで、

```javascript
function updateRanges(selectId, ranges) {

    const select =
        document.getElementById(selectId);

    while (select.options.length > 1) {
        select.remove(1);
    }

    ranges.forEach(range => {

        const option =
            document.createElement("option");

        option.value = range.start;
        option.textContent = range.displayText;

        select.appendChild(option);
    });

    select.selectedIndex = 0;
}
```

を追加し、件数更新時に、

```javascript
updateRanges(
    "beginnerRange",
    data.beginnerRanges
);
```

として出題範囲も作り直すようにした。

### 工夫した点

単に問題数を変更するだけでなく、その問題数を元に作られている出題範囲も一緒に更新した。

また、既存の選択肢を残したまま追加すると古い範囲が混在するため、

```javascript
while (select.options.length > 1) {
    select.remove(1);
}
```

で「選択してください」以外を一度削除してから、新しい範囲を追加した。

### 勉強になった点

Ajaxで画面の値を変更するときは、その値だけを見るのではなく、

```text
その値を元に作られている別のUIはないか
```

まで確認する必要がある。

今回は、

```text
問題数
↓
出題範囲
```

という依存関係を見落としていたことで不整合が発生した。

バックエンドから新しい値を取得できているだけでは十分ではなく、フロントエンド側の関連する状態も合わせて更新する必要があると分かった。

---

## 1-5. 実際の問題取得にも同じ生成元条件を適用する

メニュー上の問題数だけでなく、実際に通常学習を開始したときの問題取得にも同じ条件を追加した。

ログインユーザーでは、

```sql
AND (
    (:sourceCondition = 'ALL'
        AND (
            (ai_generated = true AND owner_user_id = :userId)
            OR ai_generated = false
        )
    )
    OR (:sourceCondition = 'ORIGINAL_ONLY'
        AND ai_generated = false
    )
    OR (:sourceCondition = 'GENERATED_ONLY'
        AND (
            ai_generated = true
            AND owner_user_id = :userId
        )
    )
)
```

を使って問題を取得する。

非ログインユーザーでは、

```sql
AND ai_generated = false
```

としてAI生成問題を除外した。

### 工夫した点

件数取得用Queryと実際の問題取得用Queryで同じ条件を使用した。

もし件数取得だけ修正して問題取得を修正しなければ、

```text
メニューには10問と表示
↓
実際には別の件数・別の対象から出題
```

という不整合が発生する。

そのため、

```text
count
find
```

の両方をセットで確認した。

### 勉強になった点

検索条件を追加するときは、そのRepositoryメソッドだけを見るのではなく、

```text
同じ対象を数える処理
同じ対象を取得する処理
```

を探す必要がある。

特にページングや件数表示のある機能では、`countQuery`と実データ取得Queryの条件を一致させることが重要だと再確認した。

---

# 2. 未学習問題へAI生成由来の問題を統合

## 2-1. 自分が所有するAI生成問題も未学習対象にする

未学習問題についても、

```sql
AND (
    q.ai_generated = false
    OR (
        q.ai_generated = true
        AND owner_user_id = :userId
    )
)
```

を追加した。

その上で既存の、

```sql
AND sh.question_id IS NULL
```

を使用することで、

```text
現在のユーザーが利用可能
AND
まだ学習履歴が存在しない
```

問題を取得する。

### 工夫した点

通常学習とは異なり、未学習モードには生成元フィルターを追加しなかった。

未学習モードの目的は、

```text
まだ学習していない利用可能な問題を学習する
```

ことなので、通常問題とAI生成問題をさらに分ける必要性は低いと判断した。

また、既存Queryで不足していた`languageVariant`による絞り込みもこのタイミングで追加した。

### 勉強になった点

新しい検索条件を作ったからといって、すべての機能へ同じUIを追加する必要はない。

通常学習では生成元をユーザーが選択する意味があるが、未学習モードでは、

```text
利用可能か
未学習か
```

だけで十分である。

共通機能を増やすことよりも、その画面の目的に必要な条件だけを持たせることが重要だと分かった。

---

# 3. 問題の生成元を表すEnumを共通化

## 3-1. `PracticeSearchCondition`を`QuestionSourceCondition`へ変更する

当初は通常学習で使用するため、

```java
PracticeSearchCondition
```

という名前にしていた。

しかし、その後、

```text
通常学習
復習
問題一覧
```

のすべてで同じ生成元条件を使用することになった。

そこで、

```java
public enum QuestionSourceCondition {

    ALL,
    ORIGINAL_ONLY,
    GENERATED_ONLY
}
```

へ名称変更した。

変数名についても、

```java
searchCondition
```

から、

```java
sourceCondition
```

へ変更した。

### 工夫した点

既に動いている名前をそのまま使い回すのではなく、利用範囲が広がった時点で実際の役割に合った名前へ変更した。

`PracticeSearchCondition`では、

```text
通常学習専用の検索条件
```

に見えるが、`QuestionSourceCondition`なら、

```text
Questionの生成元を表す条件
```

であることが分かる。

### 勉強になった点

クラスやEnumの名前は「最初にどこで使ったか」ではなく、

```text
そのクラスが何を表しているか
```

で決める方がよい。

実装を進める中で責務が広がった場合は、既存の名前に引きずられずリファクタリングした方が、その後のコードを理解しやすくなると分かった。

---

# 4. 復習へAI生成由来の問題を統合

## 4-1. 復習の検索条件へ問題の生成元を追加する

復習では既に、

```text
理解度
難易度
お気に入り
構文
言語
```

など複数の条件を使用している。

ここへ、

```java
QuestionSourceCondition sourceCondition
```

を追加した。

Repositoryの件数取得と問題取得の両方へ、

```sql
AND (
    (:sourceCondition = 'ALL'
        AND (
            (q.ai_generated = true AND q.owner_user_id = :userId)
            OR q.ai_generated = false
        )
    )
    OR (:sourceCondition = 'ORIGINAL_ONLY'
        AND q.ai_generated = false
    )
    OR (:sourceCondition = 'GENERATED_ONLY'
        AND (
            q.ai_generated = true
            AND q.owner_user_id = :userId
        )
    )
)
```

を追加した。

### 工夫した点

通常学習で作成した`QuestionSourceCondition`をそのまま再利用し、生成元条件の意味が画面によって変わらないようにした。

また、復習では件数表示をJavaScriptで動的に更新しているため、

```javascript
"input[name='sourceCondition'], "
```

を監視対象へ追加し、

```javascript
params.append(
    "sourceCondition",
    document.querySelector(
        "input[name='sourceCondition']:checked"
    ).value
);
```

として既存の件数取得処理へ組み込んだ。

### 勉強になった点

今回の復習への追加は、既に構文・お気に入り・言語など複数の検索条件を実装していたため、それらと同じ流れで追加できた。

```text
HTMLで条件を入力
↓
JavaScriptで取得
↓
Controllerで@RequestParam
↓
Service
↓
Repository
```

という検索条件追加の流れが自分の中でかなり定型化されてきた。

以前は1つ検索条件を追加するだけでもどこを変更すべきか迷っていたが、今回は影響箇所を予測しながら実装できた。

---

## 4-2. AI生成由来の問題へAIバッジを表示する

通常学習・復習では、AI生成由来の問題に、

```html
<span class="badge bg-black"
      th:if="${question.aiGenerated}">
    AI
</span>
```

を表示するようにした。

### 工夫した点

AI生成問題も通常の`Question`として扱っているため、専用画面や専用DTOを新しく作るのではなく、既存の画面上で`aiGenerated`だけを確認して表示を変えた。

### 勉強になった点

バックエンド上でデータを区別できるだけでは、ユーザーにはその違いが分からない。

今回のようにデータの生成元に意味がある場合は、

```text
内部的な区別
+
画面上の区別
```

の両方が必要になる。

一方で、既存Entityに判定用フィールドが存在していれば、Thymeleafの`th:if`だけで簡単に表示を切り替えられることも確認できた。

---

# 5. 問題一覧へAI生成由来の問題を統合

## 5-1. 問題一覧でAI生成問題を表示・絞り込みできるようにする

問題一覧についても、

```text
すべて
AI生成由来の問題を除外する
AI生成由来の問題のみ
```

から絞り込めるようにした。

RepositoryのSELECT句には、

```sql
q.ai_generated AS aiGenerated
```

を追加し、Projectionへ、

```java
boolean isAiGenerated();
```

を追加した。

さらに検索Queryと`countQuery`の両方へ`sourceCondition`を追加した。

画面には、

```html
<select class="form-select"
        name="sourceCondition">
```

を追加し、表にも、

```text
生成元
```

列を追加した。

AI生成問題の場合のみ、

```html
<span class="badge bg-black"
      th:if="${question.aiGenerated}">
    AI
</span>
```

を表示する。

### 工夫した点

問題一覧では通常学習・復習のようなラジオボタンではなく、既存の、

```text
学習状況
お気に入り
```

と同じ`select`形式にした。

これによって、

```text
学習状況 | お気に入り | 問題の生成元
```

を同じレイアウトで並べられる。

また、通常問題について「オリジナル」などの文字を表示するのではなく、AI生成問題だけにAIバッジを付け、通常問題は空セルとした。

### 勉強になった点

Projectionを使用している場合、Entityへフィールドが存在するだけでは一覧画面から利用できない。

今回、

```sql
q.ai_generated AS aiGenerated
```

と、

```java
boolean isAiGenerated();
```

の両方を追加する必要があった。

Native QueryのSELECT結果とProjectionのgetterがどのように対応しているのかを再確認できた。

---

## 5-2. AI生成由来の問題を削除できるようにする

AI生成問題はユーザー自身が保存した問題なので、不要になった場合は問題一覧から削除できるようにした。

Serviceでは、

```java
@Transactional
public void deleteOneQuestion(
        Long userId,
        Long questionId) {

    Question question =
            questionRepository.findById(questionId)
                    .orElseThrow();

    if (question.isAiGenerated()
            && question.getOwner()
                    .getId()
                    .equals(userId)) {

        favoriteRepository
                .deleteByQuestionQuestionId(questionId);

        studyHistoryRepository
                .deleteByStudyHistoryKeyQuestionId(questionId);

        questionRepository.deleteById(questionId);
    }
}
```

とした。

### 工夫した点

画面では、

```html
<form th:if="${question.aiGenerated}">
```

としてAI生成問題にだけ削除ボタンを表示する。

しかし、画面でボタンを非表示にするだけでは削除可否の保証にはならない。

そのためServiceでも、

```java
question.isAiGenerated()
&& question.getOwner().getId().equals(userId)
```

を確認し、

```text
AI生成由来
AND
ログインユーザー本人が所有
```

する場合だけ削除するようにした。

### 勉強になった点

画面上で操作できないようにすることと、サーバー側で操作を許可しないことは別である。

HTMLを変更したりHTTPリクエストを直接送ったりすることは可能なので、データを変更する処理ではService側でも条件を確認する必要がある。

また、

```java
question.isAiGenerated()
        && question.getOwner().getId().equals(userId)
```

ではJavaの`&&`による短絡評価も使われている。

通常問題では、

```java
question.isAiGenerated()
```

が`false`になった時点で条件全体が`false`と確定するため、

```java
question.getOwner()
```

以降は評価されない。

そのため通常問題の`owner`が`null`でも、この条件では`NullPointerException`にならない。

`&&`は単なるAND演算ではなく、左側から順番に評価され、結果が確定すると右側を評価しないことを改めて確認できた。

---

## 5-3. 外部キーを考慮して関連データから先に削除する

AI生成問題には既に、

```text
study_history
favorite
```

が存在する可能性がある。

これらは`question`を外部キー参照しており、DBを確認すると削除時の動作は`NO ACTION`だった。

そのため、関連データが存在する状態で、

```java
questionRepository.deleteById(questionId);
```

を実行することはできない。

そこで、

```java
favoriteRepository
        .deleteByQuestionQuestionId(questionId);

studyHistoryRepository
        .deleteByStudyHistoryKeyQuestionId(questionId);

questionRepository.deleteById(questionId);
```

とした。

### 工夫した点

DB側の外部キー制約を変更して`ON DELETE CASCADE`にするのではなく、今回はService側で削除対象を明示した。

これによって、

```text
favorite
↓
study_history
↓
question
```

と、どの関連データを削除しているのかコード上でも分かるようにした。

また、これらはすべて「1問を削除する」という1つの処理なので、

```java
@Transactional
```

を付けた。

### 勉強になった点

`NO ACTION`は、親レコードを削除したときに子レコードも自動削除する設定ではない。

子レコードが親を参照している状態で親を削除しようとすると、外部キー制約によって削除できない。

そのため、

```text
子を削除
↓
親を削除
```

という順番が必要になる。

また、今回のように、

```text
favorite削除
study_history削除
question削除
```

という複数のDB更新を1つの処理として扱う場合、途中で失敗して一部だけ削除された状態を残さないために`@Transactional`が重要になる。

```text
すべて成功
→ COMMIT

途中で失敗
→ ROLLBACK
```

というトランザクションの必要性を、実際の複数テーブル削除で理解できた。

---

## 5-4. 削除後も元の検索条件へ戻れるようにする

問題一覧では、

```text
難易度
学習状況
お気に入り
生成元
構文
言語
キーワード
ページ
```

など多数の検索条件を使用している。

そのため、削除後に、

```java
return "redirect:/user/question/list";
```

とすると、それまで指定していた検索条件がすべて失われてしまう。

そこで、GET時に現在のURLを取得した。

```java
String currentUrl = request.getRequestURI();

if (request.getQueryString() != null) {
    currentUrl += "?" + request.getQueryString();
}

model.addAttribute(
        "currentUrl",
        currentUrl
);
```

HTMLでは、

```html
<input type="hidden"
       name="returnUrl"
       th:value="${currentUrl}">
```

としてPOSTへ渡す。

削除後は、

```java
return "redirect:" + returnUrl;
```

とすることで、削除前と同じ検索結果へ戻るようにした。

### 工夫した点

今回の削除処理で特に工夫が必要だった部分の1つだった。

削除そのものは既存のRepositoryを利用すれば実装できるが、

```text
削除した後にユーザーをどこへ戻すか
```

まで考える必要があった。

検索条件を個別にPOSTへ渡して再構築するのではなく、現在のURLそのものを`returnUrl`として保持することで、既存の検索条件が増減しても戻り先を組み立て直す必要がないようにした。

### 勉強になった点

`HttpServletRequest`の、

```java
request.getRequestURI()
```

では、ブラウザに表示されているURL全体ではなく、

```text
/user/question/list
```

のようなURI部分を取得する。

一方、

```java
request.getQueryString()
```

では、

```text
difficulties=BEGINNER&page=2
```

のような`?`以降のQuery Stringを取得できる。

そのため、

```java
String currentUrl = request.getRequestURI();

if (request.getQueryString() != null) {
    currentUrl += "?" + request.getQueryString();
}
```

とすることで、

```text
/user/question/list?difficulties=BEGINNER&page=2
```

を復元できる。

コード自体はそこまで複雑ではなかったが、今回初めて使う方法だったため、検索条件を保持したまま削除前の画面へ戻す方法を考える部分では工夫が必要だった。

結果として、Query Stringをそのまま利用することで、元の検索状態をきれいに復元できるようになった。

---

# 6. このチャプターを通して

今回の実装は、新しい仕組みを一から作るというよりも、これまで実装してきた通常学習・復習・問題一覧に、

```text
AI生成由来か
誰が所有しているか
```

という新しい状態を扱えるようにする作業が中心だった。

そのため実装全体のボリューム自体はそれほど大きくなかった。

特に今回の中心となったのは、

```sql
AND (
    (:sourceCondition = 'ALL'
        AND (
            (q.ai_generated = true AND q.owner_user_id = :userId)
            OR q.ai_generated = false
        )
    )
    OR (:sourceCondition = 'ORIGINAL_ONLY'
        AND q.ai_generated = false
    )
    OR (:sourceCondition = 'GENERATED_ONLY'
        AND (
            q.ai_generated = true
            AND q.owner_user_id = :userId
        )
    )
)
```

という生成元による絞り込みと、

```java
String currentUrl = request.getRequestURI();

if (request.getQueryString() != null) {
    currentUrl += "?" + request.getQueryString();
}

model.addAttribute("currentUrl", currentUrl);
```

によって削除後も元の検索結果へ戻す処理だった。

前者については、これまで`studyCondition`や`favoriteCondition`などの複数条件を持つ検索Queryを何度も実装してきた経験があったため、今回はかなり短時間で書くことができた。:contentReference[oaicite:0]{index=0}

一方、後者は今回初めて扱う方法だった。

`HttpServletRequest`からURIとQuery Stringを取得し、それらを組み合わせて元の検索状態を保持する方法を理解できたことで、今後も一覧画面から更新・削除処理を行う際に応用できる。

今回のチャプターでは、これまで個別に実装してきた、

```text
検索条件
ユーザーごとのデータ
Ajaxによる件数更新
外部キー
トランザクション
一覧画面
リダイレクト
```

といった知識を組み合わせて、AI生成問題を既存機能へ自然に統合することができた。