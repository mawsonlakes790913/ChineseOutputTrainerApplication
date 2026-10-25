# 030-02 問題リスト機能⑤｜問題一覧への検索条件追加 - 学習ログ

## はじめに

030-01では、通常学習・AI生成学習において、リストを独立した検索の入口として扱う設計を整理した。一方、問題一覧は問題を探して管理する画面であるため、リスト指定を難易度・理解度・お気に入りなどの既存条件と組み合わせる。

本章では、この違いを出発点に、`INNER JOIN`・`LEFT JOIN`・`EXISTS`の動作と選択理由、相関サブクエリ、`countQuery`、ページネーションの検索条件維持について整理する。元の030学習ログの第3〜11項を、説明とコード例を省略せずに収録した。

---

# 1. 同じ「リスト検索」でも、画面の目的によって検索条件の扱い方が変わる

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

---

# 2. INNER JOINを追加するだけでは「条件を指定しない」という状態を表現できないことがある

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

---

# 3. LEFT JOINとORを組み合わせると「条件未指定なら全部通す」を表現できる

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

---

# 4. LEFT JOINには「1問が複数行になる」という問題がある

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

---

# 5. EXISTSは「別テーブルの情報が欲しい」のではなく「存在するかだけ知りたい」ときに使える

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

---

# 6. EXISTSを使うと、検索結果の行数を増やさずに関連テーブルの存在確認ができる

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

---

# 7. EXISTSのサブクエリは、メインQueryの現在行を参照できる

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

---

# 8. countQueryにも同じ検索条件を入れなければページングが壊れる

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

---

# 9. ControllerだけでなくページネーションURLにも検索条件を引き継ぐ必要がある

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

---

# まとめ

問題一覧へのリスト検索条件追加では、単にSQLのWHERE句を増やすだけでなく、**未指定時に全問題を残すこと、複数リストに属する問題を重複表示しないこと、他ユーザーのリストを参照できないこと**を同時に満たす必要があった。

`INNER JOIN`では未登録問題が除外され、`LEFT JOIN`では複数リストに属する問題が重複する可能性がある。今回必要なのは関連テーブルの値そのものではなく、条件を満たす行の存在確認なので、`EXISTS`を用いる設計が適していた。

また、検索条件を追加するときはRepositoryの取得Queryだけでなく、**`countQuery`とページネーションの各リンクにも同じ条件を反映する**必要がある。SQLの正しさと画面遷移時の検索状態の維持を、一続きの実装として考えることが重要だった。

次の030-03では、既存APIの再利用、JavaScriptによるリスト登録・解除、モーダルと問題一覧のUI改善について整理する。
