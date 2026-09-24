# 030 リスト機能その2 - 学習ログ

# 1. 既存機能へ新しい検索方法を追加するときは、すべてを1つの検索処理に詰め込む必要はない

今回、作成済みのリストを通常学習とAI生成学習でも利用できるようにした。

最初に考える必要があったのは、

```text
既存の検索条件へlistIdを追加するか
```

それとも、

```text
通常検索
リスト検索
```

を別々の入口にするか、という点だった。

通常学習の場合、既存検索は、

```text
難易度
生成元
その他の条件
↓
問題セットを取得
```

という目的を持っている。

一方、リスト検索は、

```text
ユーザーがあらかじめ選んだリスト
↓
そのリストに登録されている問題
```

という、かなり性質の違う検索になる。

そのため今回は、

```text
通常検索
    ↓
既存Repository
    ↓
問題セット

リスト検索
    ↓
リスト用Repository
    ↓
問題セット

-----------------

ここから先は共通

問題セット
    ↓
セッションへ保存
    ↓
question画面
```

という構成にした。

ここで重要なのは、**入口が違っても、その後の処理まで分ける必要はない**ということだった。

例えば通常学習なら、

```java
List<Question> questions
```

さえ取得できれば、その後は既存の、

```text
セッションへ保存
↓
currentPageを初期化
↓
question画面へredirect
```

という処理をそのまま使える。

新しい機能を追加するときに既存処理全体を書き換えるのではなく、

```text
どこまでが新機能固有なのか
どこから既存処理へ合流できるのか
```

を考えることが重要だと分かった。

---

# 2. listIdだけを条件にしても、そのリストを操作してよいユーザーかは保証できない

リストに登録されている問題を取得するだけなら、

```sql
SELECT q.*
FROM question q

JOIN question_list_item qli
ON q.question_id = qli.question_id

WHERE qli.list_id = :listId
```

でも取得できる。

しかし、これでは、

```text
ログインユーザー
userId = 10

他人のリスト
listId = 50
```

という組み合わせでも、`listId=50`をリクエストへ直接指定すれば検索できてしまう。

そこで、

```sql
JOIN question_list ql
ON qli.list_id = ql.list_id
```

まで行い、

```sql
AND ql.user_id = :userId
```

を条件にする必要がある。

つまり、

```text
listIdが存在する
```

だけでは不十分で、

```text
そのlistIdを
現在のuserIdが所有している
```

ところまで確認する必要がある。

これは今後ほかの機能でも、

```text
URLやRequestParamからIDを受け取る
```

場合に共通する考え方になる。

IDが正しい形式で存在していることと、**現在のユーザーがそのデータへアクセスする権限を持っていることは別問題**である。

---

# 3. 同じ「リスト検索」でも、画面の目的によって検索条件の扱い方が変わる

通常学習では、

```text
通常検索
```

と、

```text
リスト検索
```

を分離した。

しかし問題一覧画面では逆に、

```text
リスト
+
難易度
+
理解度
+
お気に入り
+
文法・構造
+
キーワード
```

を組み合わせられるようにした。

一見すると同じリスト機能なのに、実装方針が異なる。

これは画面の目的が違うためだった。

通常学習では、

```text
このリストの問題をトレーニングしたい
```

という目的なので、リストそのものが問題集合になる。

一方、問題一覧は、

```text
問題を探す
問題を確認する
問題を管理する
```

ための画面なので、

```text
台湾旅行リスト
↓
その中の上級だけ
↓
さらにHARDだけ
```

という検索ができた方が便利である。

そのため、

```text
同じデータを扱う機能
=
同じ検索方法にする
```

とは限らない。

**その画面でユーザーが何をするのかによって、検索条件の組み合わせ方を決める必要がある。**

---

# 4. INNER JOINを追加するだけでは「条件を指定しない」という状態を表現できないことがある

問題一覧へリスト条件を追加するとき、最初に考えられるのは、

```sql
JOIN question_list_item qli
ON q.question_id = qli.question_id
```

だった。

しかしINNER JOINすると、

```text
question
↓
question_list_itemにも存在する問題だけ残る
```

ため、どのリストにも登録されていない問題が消えてしまう。

例えば、

```text
問題A → リスト1
問題B → リスト未登録
問題C → リスト2
```

なら、

```sql
JOIN question_list_item
```

した時点で、

```text
問題A
問題C
```

しか残らない。

しかし今回必要なのは、

```text
listId未指定
→ A・B・Cすべて検索

listId=1
→ Aだけ検索
```

という動作だった。

したがって、単純なINNER JOINでは実現できない。

SQLを書くときは、

```text
必要なテーブルをJOINすればよい
```

だけではなく、

**JOINした瞬間にどのレコードが検索対象から消えるのか**

まで考える必要がある。

---

# 5. LEFT JOINとORを組み合わせると「条件未指定なら全部通す」を表現できる

INNER JOINではなくLEFT JOINを使えば、

```sql
LEFT JOIN question_list_item qli
ON q.question_id = qli.question_id
```

として、リスト未登録問題も残せる。

さらに、

```sql
AND (
    :listId IS NULL
    OR qli.list_id = :listId
)
```

とすると、

```text
listIdがNULL
→ 前半がTRUE
→ TRUE OR ...
→ 全部通る
```

となる。

例えば、

```text
問題A | list_id=10
問題B | list_id=NULL
問題C | list_id=20
```

に対して`listId=NULL`なら、

```text
問題A
TRUE OR ...
→ TRUE

問題B
TRUE OR ...
→ TRUE

問題C
TRUE OR ...
→ TRUE
```

になる。

この考え方はJavaの論理演算と同じで、

```java
true || 何か
```

は必ず`true`になる。

SQLでも、

```sql
:param IS NULL
OR 条件
```

とすることで、

```text
パラメータ未指定
→ 条件を実質無効化

パラメータ指定
→ 後半の条件を適用
```

というOptionalな検索条件を作れる。

---

# 6. LEFT JOINには「1問が複数行になる」という問題がある

LEFT JOINを使えばリスト未登録問題も残せるが、新しい問題が発生する。

例えば問題Aが、

```text
リスト10
リスト20
```

の両方に登録されている場合、

```text
問題A | 10
問題A | 20
```

という2行になる。

`listId=NULL`なら、

```sql
:listId IS NULL
```

がTRUEなので両方とも検索結果へ残る。

結果として、

```text
問題A
問題A
問題B
問題C
```

のように重複する可能性がある。

これに対して、

```sql
SELECT DISTINCT
```

を使えば重複排除できる。

つまり、

```text
LEFT JOIN
+
:listId IS NULL OR ...
+
DISTINCT
```

でも今回の検索は実現できる。

ただし今回は、そもそもリスト情報を検索結果として必要としていない。

そこで`EXISTS`の方が目的に合っていた。

---

# 7. EXISTSは「別テーブルの情報が欲しい」のではなく「存在するかだけ知りたい」ときに使える

今回一番理解が進んだSQLが`EXISTS`だった。

最終的な条件は、

```sql
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
```

となった。

最初は、

```sql
SELECT 1
```

が何を意味しているのか分かりにくかった。

しかし`EXISTS`ではSELECTした値そのものを使うわけではない。

見ているのは、

```text
条件に一致する行が存在するか
```

だけである。

例えばメインQueryが問題100を処理しているとき、

```text
question_id = 100
list_id = 5
user_id = 現在のユーザー
```

を満たす`question_list_item`が存在するか調べる。

存在すれば、

```text
EXISTS = TRUE
```

存在しなければ、

```text
EXISTS = FALSE
```

になる。

つまり、

```sql
AND EXISTS (...)
```

は、

```sql
AND q.difficulty = ...
```

などと同じように、最終的にはTRUE/FALSEの条件として働く。

---

# 8. EXISTSを使うと、検索結果の行数を増やさずに関連テーブルの存在確認ができる

今回LEFT JOINではなくEXISTSを採用した最大の理由はここだった。

LEFT JOINすると、

```text
question
×
question_list_item
```

の組み合わせによって内部的に行が増える。

その後、

```sql
DISTINCT
```

で戻す必要がある。

一方EXISTSなら、

```text
問題A
↓
リスト5に存在する？
↓
YES / NO
```

だけを判定する。

リスト情報そのものをメインQueryへ持ってこないので、

```text
問題A | リスト10
問題A | リスト20
```

のような行増加が発生しない。

今回欲しいのは、

```text
リスト名
登録日時
listId
```

などではなく、

```text
この問題は指定リストに入っているか
```

だけだった。

そのため、

```text
関連テーブルのデータも取得したい
→ JOIN

関連テーブルに条件を満たす行があるかだけ知りたい
→ EXISTS
```

という判断ができることを理解した。

---

# 9. EXISTSのサブクエリは、メインQueryの現在行を参照できる

今回のEXISTSには、

```sql
WHERE qli.question_id = q.question_id
```

がある。

ここで、

```text
qli
→ EXISTS内部のquestion_list_item

q
→ 外側のメインQueryのquestion
```

である。

つまりサブクエリ側から、

```sql
q.question_id
```

として外側のQueryで現在判定中の問題を参照している。

イメージとしては、

```text
メインQuery

問題100を見る
    ↓
EXISTSへ
    ↓
question_list_itemに
question_id=100かつlist_id=5はある？
    ↓
TRUE/FALSE

問題101を見る
    ↓
EXISTSへ
    ↓
question_list_itemに
question_id=101かつlist_id=5はある？
    ↓
TRUE/FALSE
```

となる。

サブクエリが完全に独立して動いているのではなく、**外側の現在行と関連付けて判定できる**。

---

# 10. countQueryにも同じ検索条件を入れなければページングが壊れる

問題一覧はSpring Data JPAの`Page`を使っている。

そのためRepositoryには、

```text
実際のデータを取得するQuery
```

だけでなく、

```text
検索結果の総件数を取得するcountQuery
```

も存在する。

リスト条件を本体Queryにだけ追加して、

```sql
AND (
    :listId IS NULL
    OR EXISTS (...)
)
```

を`countQuery`へ追加しなければ、

```text
実際の検索結果
→ リスト1の9問

countQuery
→ リスト条件なしの500問
```

のような食い違いが発生する。

すると、

```text
総ページ数
次へ
最終ページ
```

などが正しく計算されない。

ページング検索を変更するときは、

```text
SELECT側を変更した
↓
countQueryも同じWHERE条件になっているか確認
```

が必要である。

---

# 11. ControllerだけでなくページネーションURLにも検索条件を引き継ぐ必要がある

`listId`をControllerで受け取って検索できるようにしただけでは不十分だった。

例えば、

```text
リスト1を選択
↓
1ページ目
↓
次へ
```

と進んだとき、「次へ」のURLに`listId`がなければ2ページ目からリスト条件が消えてしまう。

そのため、

```text
前へ
1ページ目
中央ページ
最終ページ
次へ
```

のすべてに、

```text
listId=${selectedListId}
```

を追加する必要があった。

検索画面へ条件を追加するときは、

```text
検索フォーム
Controller
Service
Repository
```

だけではなく、

```text
検索状態を保持したまま移動するリンク
```

も確認する必要がある。

---

# 12. 同じバックエンドAPIは別画面から再利用できる

問題一覧からリスト登録状態を変更する機能では、新しいバックエンド処理をほとんど作らなかった。

すでにquestion画面用に、

```text
GET /user/question-list/selection
```

で現在の登録状態を取得し、

```text
POST /user/question-list/item/update
```

で登録状態を更新し、

```text
POST /user/question-list/create-modal
```

で新しいリストを作成できるようになっていた。

問題一覧画面でも必要な操作は同じだったため、このAPIをそのまま利用した。

つまり、

```text
practice/question
review/question
ai-practice/question
user/question/list
```

という異なる画面から、同じバックエンド処理を利用している。

これは、

```text
画面ごとにControllerを作る
```

のではなく、

```text
操作そのものをAPIとして考える
```

ことで再利用できた例だった。

---

# 13. UIが違っても、データ操作が同じなら処理を共通化できる

question画面ではリスト更新後、

```text
モーダルを閉じる
↓
そのまま問題を解き続ける
```

という動作だった。

一方、問題一覧では、

```javascript
location.reload();
```

を利用した。

これはリスト検索中に問題をそのリストから削除した場合、

```text
現在表示中の問題
↓
リストから外す
↓
現在の検索条件では対象外になる
```

ためである。

そこで保存後に現在のURLを再読み込みすれば、

```text
現在の検索条件を維持
↓
再検索
↓
対象外になった問題が消える
```

という自然な動作になる。

バックエンドAPIは同じでも、**API実行後にUIをどう更新するかは画面の目的に合わせて変えてよい**ということが分かった。

---

# 14. JavaScriptでは画面によって存在しない要素を考慮する必要がある

通常学習メニューでは、ログインユーザーだけリスト選択UIを操作できる。

そのためJavaScriptで、

```javascript
const practiceQuestionList =
    document.getElementById(
        "practiceQuestionList"
    );
```

としても、画面状態によっては対象要素が存在しない。

そのまま、

```javascript
practiceQuestionList.addEventListener(...)
```

とすると、

```text
null.addEventListener(...)
```

となりJavaScriptエラーになる。

そこで、

```javascript
if (practiceQuestionList) {

    practiceQuestionList.addEventListener(
        "change",
        ...
    );
}
```

とした。

ThymeleafやSpring Securityによって、

```html
sec:authorize
th:if
```

などでHTML要素そのものが出たり消えたりする場合、JavaScript側も、

```text
この要素は必ず存在する
```

と考えてはいけない。

---

# 15. URLSearchParamsを使うとform形式のPOSTデータをJavaScriptから組み立てられる

リスト登録状態の更新では、

```javascript
const params =
    new URLSearchParams();

params.append(
    "questionId",
    questionListQuestionId
);
```

として送信データを作った。

さらに複数のリストIDは、

```javascript
selectedListIds.forEach(
    function (listId) {

        params.append(
            "selectedListIds",
            listId
        );
    }
);
```

とする。

例えば、

```text
questionId = 100
selectedListIds = 1
selectedListIds = 3
selectedListIds = 5
```

という形式になる。

これを、

```javascript
fetch("/user/question-list/item/update", {

    method: "POST",

    headers: {
        "Content-Type":
            "application/x-www-form-urlencoded",
        [csrfHeader]:
            csrfToken
    },

    body:
        params.toString()
});
```

と送れば、Spring側では通常のフォーム送信と同じように受け取れる。

特に、

```text
同じパラメータ名を複数回append
↓
List<Long> selectedListIds
```

として扱える点が、チェックボックスの複数選択と相性がよい。

---

# 16. 非同期処理後に「イベントを擬似的に発生させる」より、処理を関数として切り出した方が分かりやすい

リスト操作モーダルでは、

```javascript
loadQuestionListSelection();
```

としてリスト一覧取得を関数化している。

これによって、

```text
モーダルを開いたとき
↓
loadQuestionListSelection()

新しいリストを作成したとき
↓
loadQuestionListSelection()
```

と同じ処理を再利用できる。

もし、

```javascript
questionListButton.click();
```

のようにクリックイベントを人工的に発生させて再取得しようとすると、

```text
本当にボタンを押したいのか
データを再取得したいだけなのか
```

が分かりにくくなる。

再利用したい処理そのものを、

```javascript
function loadQuestionListSelection() {
    ...
}
```

として分離しておけば、

```text
イベント
↓
必要な処理を呼ぶ
```

という構造になる。

---

# 17. AI生成では「ユーザーが明示的に選んだ集合」なら既存条件をそのまま適用する必要はない

通常のAI生成では、

```text
学習済み問題
```

を生成元にする。

これはユーザーが生成元問題を直接選択していないため、

```text
一度も見ていない問題が
勝手にAI生成元になる
```

ことを防ぐ意味がある。

しかしリストの場合は、

```text
ユーザー自身が問題をリストへ登録
↓
そのリストをAI生成元として指定
```

している。

したがって、さらに、

```text
学習済みであること
```

を要求する必要性は低い。

そのためリストAI生成では、

```text
指定リストに存在
AND
ユーザー所有リスト
AND
現在のLanguageVariant
AND
allow_ai_variation = true
```

を条件とした。

既存機能に新しい入口を追加するとき、

```text
既存条件だから全部そのまま適用する
```

のではなく、

```text
なぜその条件が存在するのか
新しい入口でもその理由は成立するのか
```

を考える必要がある。

---

# 18. LanguageVariantはリスト内の問題とは別に維持する必要がある

リストにはMAINLANDとTAIWANの問題を混在させることができる。

しかしアプリには、

```text
現在の学習対象言語
```

という設定がある。

そのため、リストを指定したからといって、

```text
リストに入っている問題を全部出す
```

とはしなかった。

Repositoryで、

```sql
AND q.language_variant = :languageVariant
```

を残した。

これによって、

```text
リスト
├─ MAINLAND 10問
└─ TAIWAN 5問
```

だった場合でも、現在MAINLANDを学習中なら、

```text
対象問題数 = 10
```

になる。

リストは「ユーザーがまとめた問題集合」ではあるが、アプリ全体の言語設定を上書きするものではない。

---

# 19. AI生成元の件数と実際に利用する件数は別の概念になる

AI生成では、

```text
リスト内のAI生成可能問題数
```

を表示する一方で、実際には、

```text
最大50問から生成
全問題から生成
```

を選択できるようにした。

そのため、

```text
count
全件取得
50件取得
```

はそれぞれ別処理になる。

例えばリストにAI生成可能問題が120問あれば、

```text
対象問題数表示
→ 120

最大50問モード
→ 50問を取得

全件モード
→ 120問を取得
```

となる。

UIに表示する「対象件数」と、バックエンドで実際に処理へ渡す「取得件数」は必ずしも同じではない。

---

# 20. 機能追加ではバックエンドだけでなく、UI上の情報量も調整する必要がある

問題一覧へリスト操作列を追加した結果、テーブルの列数が増えた。

そこで最終的に、

```text
別解列
```

を削除した。

別解そのものを削除したわけではなく、

```text
一覧
→ 主要情報だけ

詳細モーダル
→ 別解を含む詳細情報
```

と役割を分けた。

さらに、

```text
日本語 22% → 27%
中国語 22% → 27%
生成元 → 6%
```

と列幅も調整した。

機能を追加し続けると、

```text
表示できる情報は全部表示する
```

という方向になりやすい。

しかし、情報を増やした結果一覧性が落ちるのであれば、

```text
一覧に必要な情報
詳細画面にあればよい情報
```

を分ける必要がある。

---

# 21. disabledの操作を表示することで「機能がない」と「操作できない」を区別できる

以前の削除列では、

```text
AI生成問題
→ 削除ボタン表示

APP問題
→ 削除ボタン非表示
```

だった。

しかしこれでは、APP問題を見たユーザーからすると、

```text
なぜここには削除ボタンがないのか
```

が分かりにくい。

そこで、

```html
th:disabled="${!question.aiGenerated}"
```

を使い、

```text
AI
→ 削除可能

APP
→ 削除ボタンは存在するがdisabled
```

へ変更した。

さらにTooltipで、

```text
あなたが保存したAI生成由来の問題のみ削除できます
```

と説明する。

これによって、

```text
削除機能そのものが存在しない
```

のではなく、

```text
この問題は削除対象ではない
```

ことをUIから判断できるようになった。

---

# 22. 共通UIは専用CSSへ分離すると複数画面で同じ見た目を維持しやすい

リスト操作モーダルは、

```text
通常学習
復習
AI生成学習
問題一覧
```

など複数画面で利用する。

そこでチェックボックスの見た目を、

```css
#questionListSelectionArea .form-check-input {
    border: 2px solid #777;
}
```

として、

```text
question-list-modal.css
```

へ分離した。

画面ごとのCSSへ同じスタイルを書くと、

```text
practiceでは変更済み
reviewでは古いまま
```

のような差が生まれやすい。

同じ役割のUIを複数画面で使う場合、

```text
そのUI専用CSS
```

としてまとめることで変更箇所を1つにできる。

---

# まとめ

今回の実装では、リスト機能そのものよりも、**既存機能へ新しい条件や入口を追加するときの設計**について理解が進んだ。

特に重要だったのは、

```text
1. 新機能固有なのはどこまでかを考え、
   既存処理へ合流できる地点を探す

2. listIdだけではなくuserIdも使って
   所有権を確認する

3. INNER JOIN / LEFT JOIN / EXISTSでは
   検索結果の残り方が異なる

4. Optionalな検索条件は
   :param IS NULL OR ...
   で表現できる

5. 存在確認だけならEXISTSを使うことで
   不要なJOINと重複を避けられる

6. Pageを使う検索では
   本体QueryとcountQueryの条件を一致させる

7. 検索条件を追加したら
   ページネーションにも引き継ぐ

8. 同じAPIを複数画面から再利用し、
   UI更新方法だけ画面ごとに変えられる

9. 既存条件にはそれぞれ理由があり、
   新しい検索方法でも必要かを考える

10. 機能追加によってUIが複雑になった場合は、
    情報を増やすだけでなく減らす判断も必要
```

という点だった。

特に今回の、

```sql
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
```

は、

```text
検索条件が未指定なら制限しない
指定されていれば関連テーブル上の存在を確認する
さらに所有者も確認する
```

という複数の要件を1つのWHERE条件で表現している。

今回の実装の中では、SQLのJOIN・EXISTSと、既存機能を壊さずに新しい入口を追加する設計が特に重要な学習ポイントになった。
