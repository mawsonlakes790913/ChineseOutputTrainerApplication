# 009 復習モードの実装 その2 - 出題機能の実装

## 1. 今回学んだこと

今回は、復習メニューで指定した条件をもとに問題セットを取得し、実際に復習を開始できるところまで実装した。

復習モードでも、

- 問題セットをSessionに保存する
- 現在のページをSessionで管理する
- 問題を1問ずつ表示する
- 中断した位置を保存する
- Sessionに残っている問題セットから再開する
- 最後まで進んだら完了する
- 途中でやめた場合はSessionを削除する

という基本的な流れは通常学習とほとんど同じだった。:contentReference[oaicite:0]{index=0} :contentReference[oaicite:1]{index=1}

一方で、**どの問題を問題セットに含めるか**という部分は通常学習とは異なり、復習モード独自の処理が必要になった。

---

## 2. 復習対象の問題を取得する

復習では、ユーザーの学習履歴をもとに、

- 理解度
- 難易度
- お気に入り状態

を組み合わせて問題を取得する。

そのため、`study_history` を起点として `question` と `favorite` をJOINするクエリを使用した。:contentReference[oaicite:2]{index=2}

```sql
SELECT q.*
FROM study_history sh
JOIN question q
  ON sh.question_id = q.question_id
LEFT JOIN favorite f
  ON sh.user_id = f.user_id
 AND sh.question_id = f.question_id
WHERE sh.user_id = :userId
  AND sh.evaluation IN (:evaluations)
  AND q.difficulty IN (:difficulties)
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

この仕組み自体は、前回実装した**復習メニューの問題数取得処理と同じ**である。

違うのは、

```sql
SELECT COUNT(*)
```

で件数を取得するのではなく、

```sql
SELECT q.*
```

として実際の問題を取得する点である。

前回問題数取得の仕組みを作っていたため、今回はそれをそのまま問題取得へ応用できた。

### EnumをRepositoryへそのまま渡さない

Serviceでは、Repositoryへ渡す前に、

```java
searchConditionConverter.convertEvaluation(evaluations)
searchConditionConverter.convertDifficulty(difficulties)
searchConditionConverter.convertFavoriteCondition(favoriteCondition)
```

を通している。:contentReference[oaicite:3]{index=3}

RepositoryのNative QueryへEnumをそのまま渡すのではなく、DBに保存されている値と比較できる文字列へ変換してから渡すためである。

また、ランダム出題が選択されている場合は、

```java
if (random) {
    Collections.shuffle(extractedQuestions);
}
```

として、取得後の問題セットをシャッフルする。

---

## 3. 「問題セットを作る処理」と「1問を表示する処理」を分ける

Controllerでは、

```text
getReviewStart()
↓
復習条件から問題セットを新しく作る

getReviewQuestion()
↓
作成済みの問題セットから指定された1問を表示する
```

という役割分担にした。:contentReference[oaicite:4]{index=4}

`getReviewStart()` では、新しい復習を始める前に既存の復習Sessionを削除し、検索条件から問題セットを取得する。

取得した問題セットと初期ページを、

```java
session.setAttribute("reviewQuestions", questions);
session.setAttribute("reviewCurrentPage", 0);
```

としてSessionへ保存する。:contentReference[oaicite:5]{index=5}

一方、`getReviewQuestion()` では問題をDBから取得し直さず、

```java
List<Question> questions =
        (List<Question>) session.getAttribute("reviewQuestions");
```

として、開始時に作成した問題セットを使用する。

さらに、

```java
session.setAttribute("reviewCurrentPage", page);
```

として現在位置を更新することで、中断・再開にも同じ問題セットとページ情報を利用できる。:contentReference[oaicite:6]{index=6}

このように、

**「問題セットの作成」と「問題セット内の現在位置の移動」は別の処理**

として考えると、復習モード全体の流れを整理しやすかった。

---

## 4. 復習中でも理解度を変更できるようにする

復習画面でも解答後に、

- HARD
- GOOD
- EASY

を選択できるようにした。

選択された理解度は `/review/evaluation` へPOSTし、

```java
evaluationService.updateEvaluation(
        user,
        questionId,
        evaluation);
```

で既存の学習履歴を更新する。:contentReference[oaicite:7]{index=7}

ここで重要なのは、**復習は過去の理解度を見るだけの機能ではない**という点だった。

例えば、以前 `HARD` だった問題を復習して理解できるようになれば、そこで `GOOD` や `EASY` に変更できる。

つまり復習によって理解度が変われば、その結果が次回の復習対象にも反映されることになる。

---

## 5. QuestionModelUtilの重複処理を整理した

今回の実装中に `QuestionModelUtil#setQuestionModel()` を確認したところ、発音表記を設定する `switch` が二重に存在していることに気づいた。:contentReference[oaicite:8]{index=8}

後から追加した処理だけで、

```text
pronunciation
alternativePronunciation
```

の両方を設定できていたため、以前の `switch` は不要だった。

そこで重複していた処理を削除した。

新しい機能を追加していく際には、以前の処理を拡張したつもりでも、古い処理がそのまま残って重複することがある。

今回のように共通Utilを再利用するときには、**「使えるかどうか」だけではなく「不要な処理が残っていないか」も確認する必要がある**と感じた。

---

## 6. 通常学習と同じ `random` に統一した

当初、復習メニューでは出題方法を、

```text
SEQUENTIAL
RANDOM
```

という `order` として送信する作りにしていた。

しかし通常学習では、すでに、

```java
boolean random
```

によってランダム出題を管理している。

そこで復習だけ別の表現を使う必要はないと考え、

```text
順番に出題
→ random=false

ランダムに出題
→ random=true
```

へ変更した。:contentReference[oaicite:9]{index=9}

同じ意味を持つ値については、機能ごとに異なる名前や表現を増やすより、既存の実装に合わせた方がControllerやServiceの処理も理解しやすい。

---

## 7. 通常学習と復習のController・Serviceを分けることにした

今回特に設計面で考えたのが、**通常学習と復習でControllerやServiceを共通化するかどうか**だった。

通常学習と復習では、

- トレーニングを中断してSessionに残す
- Sessionから問題セットを取得して再開する
- 完了後に完了画面へ遷移する
- トレーニングをやめてホームへ戻る

といった処理はほぼ同じである。

そのため、共通Controllerにまとめることもできる。:contentReference[oaicite:10]{index=10}

しかし今回は、あえて、

```text
通常学習
→ PracticeController / PracticeService

復習
→ ReviewController / ReviewService
```

と分けることにした。

理由の一つは、Session自体が、

```text
practiceQuestions
reviewQuestions
```

のように別々だからである。

さらに、通常学習と復習では**問題セットを作るまでの処理が異なる**。

復習では、ユーザーの学習履歴や理解度、お気に入り状態をもとに問題を抽出する必要があるため、入口の処理からすでに通常学習とは役割が違う。

確かにControllerを分けると、中断・再開・終了など似たコードが重複する。

ただ、無理に共通化すると、

```text
これは通常学習か？
復習か？
どちらのSessionを使うか？
どのServiceから問題を取得するか？
```

といった分岐が共通Controllerの中に増える可能性がある。

今回は、多少の重複があっても、**通常学習と復習を独立した処理として追える分かりやすさを優先した**。

「同じ処理があるから必ず共通化する」のではなく、機能ごとの責務や今後の変更のしやすさまで考えて、あえて分ける選択肢もあることを学んだ。

---

## 8. 全体的な所感

今回の復習出題機能は、基本的な流れについてはすでに実装した通常学習をかなり参考にできたため、一から考える部分はそれほど多くなかった。

特に、

```text
問題セットをSessionへ保存
↓
pageで現在位置を管理
↓
中断時はSessionを残す
↓
再開時はSessionから復元
```

という仕組みは通常学習とほぼ同じだった。

一方で、復習では**問題セットをどのように作るか**が大きく異なった。

また、既存コードを流用する中で `QuestionModelUtil` の重複処理や `order` と `random` の違いなども見つかり、単純にコピーするのではなく、現在のChinese Output Forgeの設計に合わせて確認しながら実装する必要があった。

今回特に勉強になったのは、通常学習と復習の共通部分が多いからといって、必ずしもControllerやServiceまで共通化する必要はないという点である。

処理の重複だけを見るのではなく、**それぞれの機能の入口・Session・問題取得方法・責務の違いまで含めて設計を考えることが重要**だと感じた。

## 9. 次やること

- ユーザーメニューの実装