# 023-03 Home・学習画面のナビゲーション改善

## はじめに

Chinese Output Forgeの主要機能がそろった段階で、Home画面や学習完了画面から各機能へ移動しやすいようにリンクを追加した。また、通常学習・復習・AI生成モードなどのメニューに説明文を設け、機能の利用条件が伝わるように表示文言を見直した。

本章では、元の023「UI・機能の最終調整」の「4. Home画面の表示内容の拡充」「5. トレーニング完了画面の拡充」「8. 各モードのメニュー画面への説明文の追加」「15. 各画面の説明文と表示文言の修正」をまとめる。後から追加された機能に関する文言も、当時の調整記録として含めている。

## 1. Home画面から各機能へ移動できるようにする

```bash
git commit -m "feat: expand home page with navigation links"
```

### 1.1 修正の目的

従来のHome画面はアプリケーション名などを表示する最低限の構成だった。主要機能の実装が進んだため、通常学習、復習、AI生成モード、ユーザーメニュー、アプリ概要ページへ直接移動できるようにした。

**変更前のHome画面**

![](../../images/0023-05.png)

ログイン状態によって利用できる機能が異なるため、ThymeleafとSpring Securityの`sec:authorize`を使い、リンクの表示を切り替える。

### 1.2 `/home.html`の変更

主要なリンク部分を抜粋する。

```html
<div class="d-grid gap-3 col-md-4 mx-auto mt-5">
    <!-- 誰でも利用できる通常学習 -->
    <a th:href="@{/practice/menu}"
       class="btn btn-primary btn-lg"
       th:text="#{home.practice}">通常学習</a>

    <!-- ログインユーザー向け -->
    <a sec:authorize="isAuthenticated()"
       th:href="@{/review/menu}"
       class="btn btn-success btn-lg"
       th:text="#{home.review}">復習</a>

    <a sec:authorize="isAuthenticated()"
       th:href="@{/ai-practice/menu}"
       class="btn btn-warning btn-lg"
       th:text="#{home.aiPractice}">AI生成モード</a>

    <a sec:authorize="isAuthenticated()"
       th:href="@{/user/menu}"
       class="btn btn-secondary btn-lg"
       th:text="#{home.userMenu}">ユーザーメニュー</a>

    <!-- アプリの説明は非ログイン時にも公開 -->
    <a th:href="@{/about}"
       class="btn btn-secondary"
       th:text="#{home.about}">このアプリについて</a>

    <!-- 非ログインユーザー向け -->
    <a sec:authorize="isAnonymous()"
       th:href="@{/login}"
       class="btn btn-outline-primary"
       th:text="#{home.login}">ログイン</a>

    <a sec:authorize="isAnonymous()"
       th:href="@{/signup/signup}"
       class="btn btn-outline-success"
       th:text="#{home.signup}">新規登録</a>
</div>
```

`sec:authorize`は画面上のリンク表示を制御するものであり、URLへのアクセス制限そのものは`SecurityConfig`などの認可設定で行う必要がある。

表示文言は`messages.properties`に定義した。

```properties
home.description=瞬間中国語作文トレーニングアプリ
home.practice=通常学習
home.review=復習
home.aiPractice=AI生成モード
home.userMenu=ユーザーメニュー
home.about=このアプリについて
home.login=ログイン
home.signup=新規登録
```

### 1.3 動作確認

**ログイン時**

![](../../images/0023-10.png)

**非ログイン時**

![](../../images/0023-11.png)

ログイン状態に応じて、表示されるメニューが切り替わることを確認した。

## 2. トレーニング完了画面のリンクを拡充

```bash
git commit -m "feat: add navigation links to training complete page"
```

### 2.1 修正の目的

従来の完了画面にはHomeと通常学習メニューへ戻るリンクしかなかった。しかし、学習終了後には復習やAI生成モードへ進む場合もあるため、次の学習方法を選択できるようにした。

**変更前の完了画面**

![](../../images/0023-12.png)

### 2.2 `/complete.html`の変更

```html
<div class="d-grid gap-3 col-md-4 mx-auto mt-4">
    <a th:href="@{/practice/menu}"
       class="btn btn-primary"
       th:text="#{complete.backToPractice}">通常学習メニューに戻る</a>

    <a sec:authorize="isAuthenticated()"
       th:href="@{/review/menu}"
       class="btn btn-success"
       th:text="#{complete.review}">復習</a>

    <a sec:authorize="isAuthenticated()"
       th:href="@{/ai-practice/menu}"
       class="btn btn-warning"
       th:text="#{complete.aiPractice}">AI生成モード</a>

    <a th:href="@{/}"
       class="btn btn-outline-secondary"
       th:text="#{common.backToTop}">Topに戻る</a>
</div>
```

Home画面と同様、ログインが必要な機能のリンクには`sec:authorize="isAuthenticated()"`を付けた。ボタンの文言もメッセージ定義で管理する。

```properties
complete.pageTitle=学習完了 | Chinese Output Forge
complete.title=お疲れさまでした！
complete.message=この調子でトレーニングを続けていきましょう。
complete.backToPractice=通常学習メニューに戻る
complete.review=復習メニューに戻る
complete.aiPractice=AI生成モードメニューに戻る
```

### 2.3 動作確認

**ログイン時**

![](../../images/0023-13.png)

**非ログイン時**

![](../../images/0023-14.png)

完了画面でもログイン状態に応じたリンクを表示できることを確認した。

## 3. 各モードのメニューに説明文を追加

```bash
git commit -m "feat: add descriptions to mode menu screens"
```

### 3.1 修正の目的

「通常学習」「復習」「AI問題生成」といったタイトルだけでは、各機能で何ができるのかが十分に伝わらない。そのため、各メニュー画面のタイトル直下に短い説明文を追加した。

**変更前の例**

![](../../images/0023-19.png)

### 3.2 Thymeleafでの表示

例えば、通常学習メニューでは見出しの下に次の要素を追加した。

```html
<p class="text-muted mb-0"
   th:text="#{practice.menu.description}">
    アプリケーションに収録されている問題を使って、
    瞬間中国語作文トレーニングを行います。
</p>
```

同じ構成を`/review/menu.html`、`/ai-practice/menu.html`、`/user/question/list.html`にも適用した。

```properties
practice.menu.description=アプリケーションに収録されている問題を使って、瞬間中国語作文トレーニングを行います。
review.menu.description=これまでに学習した問題を、理解度や文法・構造などの条件で絞り込んで復習します。
aiPractice.menu.description=これまでに学習した問題をもとに、AIが内容を変化させた新しい問題を生成してトレーニングします。
user.question.list.description=アプリケーションに登録されている問題や、自分が保存したAI生成由来の問題を検索・確認できます。
```

### 3.3 動作確認

**通常学習**

![](../../images/0023-20.png)

**復習**

![](../../images/0023-21.png)

**AI生成モード**

![](../../images/0023-22.png)

**問題一覧**

![](../../images/0023-23.png)

## 4. 説明文を実際の出題条件に合わせて修正

```bash
git commit -m "Improve learning menu descriptions"
```

### 4.1 修正の背景

説明文を追加した後も、実際の出題対象や機能の制約が曖昧な箇所が残っていた。特に、保存したAI生成問題、リスト内の問題、理解度の記録有無などは、ユーザーが出題結果を予測するうえで重要である。

そこで、日本語・簡体字中国語・繁体字中国語の表示文言を見直した。以下は日本語の主な変更内容である。中国語の対応文言も元の記録に沿って併記する。

### 4.2 通常学習メニュー

**AI生成問題も出題対象に含まれることを明示**

> 難易度と出題範囲を選択して出題します。ユーザーが保存したAI生成由来の問題もこの中に含まれます。

- 簡体字：选择难度和出题范围进行练习。其中也包括用户保存的由 AI 生成的问题。
- 繁体字：選擇難度和出題範圍進行練習。其中也包括使用者儲存的由 AI 生成的問題。

**リストの出題対象を明確化**

> ユーザーがリストに登録した問題をトレーニングします。

- 簡体字：练习用户添加到列表中的问题。
- 繁体字：練習使用者加入清單中的問題。

**学習対象言語による絞り込みを明示**

> リスト内であっても現在設定している学習対象言語ではない問題は、出題対象外となります。

- 簡体字：即使问题已添加到列表中，如果不是当前设置的学习目标语言，也不会被列入出题范围。
- 繁体字：即使問題已加入清單中，如果不是目前設定的學習目標語言，也不會被列入出題範圍。

**「未学習」の判定条件を明示**

> ユーザーがまだ学習していない（理解度ボタンを押していない）問題のみを出題します。

- 簡体字：仅出题用户尚未学习（尚未点击理解度按钮）的问题。
- 繁体字：僅出題使用者尚未學習（尚未點擊理解度按鈕）的問題。

### 4.3 復習メニュー

復習対象が単に閲覧済みの問題ではなく、理解度を記録した問題であることを明確にした。

> これまでに学習した問題を、理解度や文法・構造などの条件で絞り込んで復習します。復習対象は、理解度ボタンを押した問題のみになります。

- 簡体字：将已学习过的问题按照理解度、语法・结构等条件进行筛选并复习。复习对象仅限于已点击理解度按钮的问题。
- 繁体字：將已學習過的問題按照理解度、文法・結構等條件進行篩選並複習。複習對象僅限於已點擊理解度按鈕的問題。

### 4.4 AI問題生成メニュー

当初の説明は「これまでに学習した問題をもとに」としていたが、生成対象の説明を修正し、アプリ内の一部の問題を生成元とすることを明示した。

> アプリに収録されている一部の問題の内容を、AIが変化させ新しい問題としてトレーニングします。

- 簡体字：AI会对应用中收录的部分问题内容进行变化，并将其作为新问题进行练习。
- 繁体字：AI會對應用程式中收錄的部分問題內容進行變化，並將其作為新問題進行練習。

また、生成数の上限を明記した。

> 学習履歴や問題条件を指定してAI問題を最大50問生成します。

- 簡体字：指定学习记录和问题条件，由 AI 最多生成 50 道问题。
- 繁体字：指定學習紀錄和問題條件，由 AI 最多生成 50 道問題。

さらに、理解度未記録の問題は生成対象にならないことを、目立つ注意書きとして追加した。

```html
<p class="mb-0 text-muted">
    <span th:text="#{aiPractice.menu.search.description}">
        学習履歴や問題条件を指定してAI問題を生成します。
    </span>
    <br>
    <small class="text-danger"
           th:text="#{aiPractice.menu.search.evaluationNote}">
        理解度ボタンを押していない問題はAI生成の対象になりません。
    </small>
</p>
```

ここでの説明は、元の実装記録に記載された当時の仕様・表示文言に基づく。後続の機能追加に伴う仕様変更があれば、その時点の記録と併せて確認する。

## 5. まとめ

今回の調整では、新しい学習処理を増やすのではなく、**必要な機能へ移動しやすくすること**と、**ユーザーが出題条件や利用可能な機能を正しく理解できるようにすること**を重視した。

Home画面と完了画面ではログイン状態に応じてリンクを切り替え、各メニューでは説明文を追加した。さらに、AI生成問題・リスト・理解度記録など、実際の出題条件に関わる説明を具体化した。

これにより、画面の見た目だけでなく、ユーザーが次の操作や出題結果を予測しやすい構成へ近づけた。
