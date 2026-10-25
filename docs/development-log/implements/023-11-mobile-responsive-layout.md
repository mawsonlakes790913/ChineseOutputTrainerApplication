# 023-11 スマートフォン向けレスポンシブ対応

## はじめに

これまでは主にPCのブラウザで動作確認していたが、スマートフォンの実機で主要機能を確認すると、PC向けの横並びレイアウトが原因で、見出し・ラベル・問題文が不自然に細かく折り返されることが分かった。

特に、共通ヘッダー、通常学習・AI生成学習のメニュー、問題一覧に問題があった。一方、問題出題画面の長い文章が自然に複数行へ折り返されることは問題としない。

そこで、**PC向けの表示は維持し、画面幅が小さい場合に限って要素の配置や表示形式を切り替える**方針とした。Bootstrap 5のFlexbox、Display Utility、Spacing Utilityを中心に対応する。

## 1. 共通ヘッダーの2段表示

```bash
git commit -m "Improve header layout for mobile devices"
```

スマートフォンではアプリ名や「学習言語」が極端に狭い領域に押し込まれ、1文字単位で折り返されていた。

**修正前**

![](../../images/0023-48.png)

### ヘッダー全体の配置方向を切り替える

修正前は常に横並びだった。

```html
<div class="container d-flex justify-content-between align-items-center">
```

修正後は、`md`（768px）未満では縦方向、`md`以上では横方向へ配置する。

```html
<div class="container d-flex flex-column flex-md-row
            justify-content-between align-items-md-center gap-3">
```

スマートフォンではアプリ名を上段、言語選択やユーザー操作を下段へ配置し、PCでは従来どおり横一列とする。

### テキストの折り返しと余白を調整する

アプリ名は途中で改行されないようにする。

```html
<h1 class="h4 m-0 text-nowrap">
```

「学習言語」を含むグループにも`text-nowrap`を指定する。

```html
<div class="d-flex align-items-center gap-2 text-nowrap">
```

操作グループの右寄せはPC表示時だけ適用する。

```html
<div class="d-flex gap-3 align-items-center ms-md-auto">
```

さらに、スマートフォンでは言語選択とユーザー操作の間に余白を確保し、PCではその追加余白を解除するため、`me-5 me-md-0`と`ms-5 ms-md-0`を使い分けた。

**修正後**

![](../../images/0023-49.png)

## 2. 通常学習メニューの見出し・説明文を縦並びにする

```bash
git commit -m "Improve practice menu layout for mobile devices"
```

通常学習メニューでは、見出しと説明文を横に並べていたため、スマートフォンでそれぞれの幅が不足していた。

**修正前**

![](../../images/0023-50.png)

**修正前**

```html
<div class="d-flex align-items-center gap-3 mb-2">
```

**修正後**

```html
<div class="d-flex flex-column flex-md-row
            align-items-start align-items-md-center gap-3 mb-2">
```

スマートフォンでは見出しと説明文を上下に配置し、`md`以上では横並びを維持する。

また「範囲を指定してトレーニング」の末尾1文字だけが折り返される問題については、HTMLの見出し階層を変えずに見た目の文字サイズだけ変更した。

```html
<!-- 修正前 -->
<h3 class="mb-0">

<!-- 修正後：見出しレベルはh3のまま -->
<h3 class="h4 mb-0">
```

追加調整として、「未学習の問題をトレーニング」を「未学習問題をトレーニング」へ短縮し、未ログイン時の一部メニューでは左右のアイコンが競合しないよう、リスト・星のアイコンを削除した。

**修正後**

![](../../images/0023-51.png)

### 未学習問題の難易度選択も縦並びにする

```bash
git commit -m "Improve unstudied training layout for mobile devices"
```

「初級：53問」「中級：57問」「上級：112問」の3項目をスマートフォンでも横並びにすると、難易度名が1文字ずつ折り返されていた。

当初は`text-nowrap`を試したが、文字列を折り返さなくすると画面外へはみ出した。そのため、テキストだけでなく親要素の配置を変更した。

```html
<!-- 修正前 -->
<div class="d-flex gap-3 mb-3 fs-4">

<!-- 修正後 -->
<div class="d-flex flex-column flex-md-row gap-3 mb-3 fs-4">
```

各選択肢の`label`に指定した`bg-primary-subtle rounded px-3 py-3 flex-fill`などは変更せず、スマートフォンでは3項目を縦に並べ、PCでは横並びに戻す。

**修正後**

![](../../images/0023-52.png)

## 3. ログイン後のホーム画面下部に余白を確保する

スマートフォンではログイン成功メッセージ（「おかえりなさい、ユーザー名さん」）が表示されると、ホーム画面最下部の「このアプリについて」ボタンがブラウザ下部のUIに近接していた。

**修正前**

![](../../images/0023-53.png)

メニュー全体を囲む親要素に`mb-4`を追加した。

```html
<!-- 修正前 -->
<div class="d-grid gap-3 col-md-4 mx-auto mt-5">

<!-- 修正後 -->
<div class="d-grid gap-3 col-md-4 mx-auto mt-5 mb-4">
```

最後のボタンではなく親要素に余白を付けることで、ログイン状態によって表示されるメニューが変わっても、ページ下部の余白を確保できる。

## 4. AI生成学習メニューの折り返しを改善する

```bash
git commit -m "Improve AI practice menu layout for mobile devices"
```

AI生成学習メニューでも、「検索条件からAI問題を生成」「リストからAI問題を生成」の見出しと説明文が横並びで幅を取り合い、難易度の「初級」「中級」「上級」も2行に分かれていた。

**修正前**

![](../../images/0023-54.png)

見出しと説明文は、通常学習メニューと同様に画面幅でFlexboxの方向を切り替えた。

```html
<!-- 修正前 -->
<div class="d-flex align-items-center gap-3 mb-3">

<!-- 修正後 -->
<div class="d-flex flex-column flex-md-row
            align-items-start align-items-md-center gap-3 mb-3">
```

難易度の選択項目もスマートフォンでは縦並びにした。

```html
<!-- 修正前 -->
<div class="d-flex gap-3">

<!-- 修正後 -->
<div class="d-flex flex-column flex-md-row gap-3">
```

「理解度」「問題難易度」「お気に入り」の検索条件全体には、すでにBootstrap Gridによるレスポンシブ配置が適用されていたため、今回は問題のある見出し・説明文と難易度選択に限定して修正した。

**修正後**

![](../../images/0023-55.png)

## 5. 問題一覧をPC用テーブル・スマートフォン用カードに切り替える

```bash
git commit -m "Improve question list layout for mobile devices"
```

問題一覧は、問題番号、日本語、中国語、文法・構造、難易度、生成元、詳細、理解度、お気に入りなどを1つのテーブルで表示していた。

スマートフォンではカラム幅が不足し、問題文が数文字単位で折り返されるうえ、全項目の確認には横スクロールが必要だった。

**修正前**

![](../../images/0023-56.png)

![](../../images/0023-57.png)

文字サイズや列幅だけでは解決しにくいため、**768px未満は1問1カード、768px以上は既存のテーブル**という表示形式を採用した。

### BootstrapのDisplay Utilityで表示を切り替える

対象テンプレートは`/user/question/list.html`。

```html
<!-- PC・タブレット向けテーブル -->
<div class="table-responsive d-none d-md-block">
    <table class="table table-hover align-middle">
        <!-- 既存の問題一覧 -->
    </table>
</div>

<!-- スマートフォン向けカード -->
<div class="d-md-none">
    <div th:each="question : ${questionList}"
         class="card mb-3">
        <div class="card-body">
            <!-- 1問分の情報と操作ボタン -->
        </div>
    </div>
</div>
```

`d-none d-md-block`は`md`未満で非表示、`md`以上で表示する。`d-md-none`はその逆であり、スマートフォン側だけにカードを表示する。

### カード内に問題情報を縦方向へ表示する

カードでは問題番号、日本語、中国語、文法・構造、難易度、生成元を縦方向に配置する。Thymeleafの`th:each`でPC版と同じ`${questionList}`を繰り返すため、Controller側で別データを用意する必要はない。

代表的な記述は以下のとおり。

```html
<div th:each="question : ${questionList}" class="card mb-3">
    <div class="card-body">
        <div class="mb-3">
            <div class="fw-bold mb-1"
                 th:text="#{user.question.list.japanese}">日本語</div>
            <div th:text="${question.japaneseText}"></div>
        </div>
        <div class="mb-3">
            <div class="fw-bold mb-1"
                 th:text="#{user.question.list.chinese}">中国語</div>
            <div th:text="${question.chineseText}"></div>
        </div>
        <!-- 文法・構造、難易度、生成元、各操作を続ける -->
    </div>
</div>
```

文法・構造のツールチップ、難易度別の表示、AI生成か既存問題かの区別もPC版と同じ情報を利用する。

### 操作ボタンとJavaScriptは既存処理を共用する

スマートフォン用カードにも「詳細」「理解度」「お気に入り」「リスト」「削除」の操作を配置した。

既存JavaScriptで使用する以下のクラス名はPC版と共通にする。

```text
detailButton
evaluationButton
favoriteButton
questionListButton
```

さらに`data-bs-target`や`th:data-question-id`などの属性も維持し、既存のモーダルとイベント処理を再利用する。削除も従来のフォーム送信を使用する。

表示形式だけを分け、操作処理は共通化することで、PC版とスマートフォン版で機能が分岐しないようにした。

### 表示確認

スマートフォンのブラウザで、問題がカードごとに表示され、日本語・中国語の文章が細かく折り返されなくなったことを確認した。各操作も横スクロールせずに利用できる配置となった。

**修正後**

![](../../images/0023-58.png)

なお、元の記録で具体的なカード実装と表示確認が示されているのは`/user/question/list.html`である。冒頭で挙げた問題リスト内の一覧については、同様の問題があることは記録されているものの、本節には個別の修正コードや確認結果は残っていないため、対応完了とは記載しない。

## まとめ

今回の調整では、スマートフォンで発生していた不自然な折り返しや横スクロールの問題に対し、表示領域の狭さに応じてレイアウトを切り替えた。

- 共通ヘッダー：スマートフォンでは2段、PCでは1段に変更
- 通常学習・AI生成学習メニュー：見出し・説明文・難易度選択の配置を画面幅に応じて切り替え
- ホーム画面：ログイン成功メッセージ表示時も下部余白を確保
- 問題一覧：PCはテーブル、スマートフォンはカードで表示し、既存の操作処理を共用

Bootstrapの`md`ブレークポイント（768px）を基準とすることで、PC向けの一覧性を残しながら、スマートフォンでも主要画面を読みやすく操作できるようにした。

これで「023 UI・機能の最終調整」から分割した**023-01〜023-11**の実装ログが揃った。
