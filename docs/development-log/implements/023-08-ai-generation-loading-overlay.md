# 023-08 AI問題生成中のローディング表示

## はじめに

AI生成問題学習モードでは、既存問題をもとに新しい問題を生成するまで数秒、場合によってはさらに長い待機時間が発生する。生成中に画面が変化しないと、ユーザーが送信に失敗したと判断して再操作する可能性がある。

そこで、AI問題生成を開始した際にローディングオーバーレイを表示し、処理中であることを明示するようにした。併せて、関連画面で共用していたCSSの配置も整理した。

## 1. AI問題生成中のローディング表示

```bash
git commit -m "Add loading overlay during AI question generation"
```

### 1-1. HTMLにオーバーレイを追加

`/ai-practice/menu.html`に、通常時は非表示のオーバーレイを配置した。

```html
<!-- AI問題生成中のローディング表示 -->
<div id="loadingOverlay" class="loading-overlay d-none">
    <div class="loading-box text-center">
        <div class="spinner-border"></div>
        <div class="mt-2">Loading...</div>
    </div>
</div>
```

`d-none`はBootstrapの非表示用クラスである。JavaScriptからこのクラスを取り除くことで、生成開始時にオーバーレイを表示する。`spinner-border`は処理中であることを示す回転アニメーションである。

### 1-2. CSSで全画面表示と中央配置を設定

`/css/ai-practice/menu.css`に以下を追加した。

```css
.loading-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 9999;
    display: flex;
    justify-content: center;
    align-items: center;
}

.loading-box {
    background: white;
    padding: 30px 50px;
    border-radius: 8px;
}

.loading-overlay.d-none {
    display: none;
}
```

`position: fixed`と`inset: 0`で表示領域全体を覆い、半透明の背景と高い`z-index`によって待機中の表示を前面に配置する。Flexboxの`justify-content`と`align-items`を使って、メッセージとスピナーを画面中央に配置した。

最後の`.loading-overlay.d-none`は、オーバーレイ側の`display: flex`より非表示指定を優先させるためのルールである。

### 1-3. JavaScriptでフォーム送信時に表示

`/ai-practice/menu.js`では、DOMの読み込み後にオーバーレイを取得し、AI生成を開始する2種類のフォームに`submit`イベントを設定した。

```javascript
document.addEventListener("DOMContentLoaded", () => {
    const loadingOverlay =
        document.getElementById("loadingOverlay");

    const aiPracticeForms = document.querySelectorAll(
        "form[action='/ai-practice/start'], " +
        "form[action='/ai-practice/list/start']"
    );

    aiPracticeForms.forEach(form => {
        form.addEventListener("submit", () => {
            loadingOverlay.classList.remove("d-none");
        });
    });

    // 既存の画面操作処理は省略
});
```

対象は、検索条件から生成する`/ai-practice/start`と、リストから生成する`/ai-practice/list/start`である。両方に同じ処理を適用することで、生成元の選択方法にかかわらず待機状態を表示できる。

フォーム送信後はサーバー側で生成処理が行われ、レスポンスに応じて画面が遷移する構成のため、この実装ではJavaScriptから明示的にオーバーレイを閉じる処理を追加していない。ただし、送信が中断されて同じ画面にとどまる場合などの復帰処理は、ここでは扱っていない。

### 1-4. 動作確認

`http://localhost:8080/ai-practice/menu`から問題生成を開始すると、ローディングオーバーレイが表示されることを確認した。

![](../../images/0023-36.png)

## 2. 共通CSSの配置とファイル名の整理

```bash
git commit -m "Move shared question structure styles to common CSS"
```

ローディング表示用CSSを追加する際、`/ai-practice/menu.html`が`/css/review/menu.css`も読み込んでいることに気付いた。このファイルに記載されていたのは、復習専用のスタイルではなく、文法・構造一覧の折り畳みとツールチップの表示に関するスタイルだった。

```css
/* 文法・構造の折り畳み */
.structure-list {
    max-height: 100px;
    overflow: hidden;
}

.structure-list.expanded {
    max-height: none;
}

/* ツールチップ */
.tooltip-inner {
    max-width: 400px;
    padding: 12px 16px;
    font-size: 1rem;
    text-align: left;
}
```

これらは`/review/menu.html`と`/ai-practice/menu.html`の両方で使用していたため、用途に合わせて`/css/common/question-structure.css`へ移した。

```text
css/
├── common/
│   └── question-structure.css
├── review/
│   └── menu.css
└── ai-practice/
    └── menu.css
```

両画面のHTMLでは、共通スタイルを次のように参照する。

```html
<link rel="stylesheet"
      th:href="@{/css/common/question-structure.css}">
```

**画面をまたいで使用するスタイルは、最初に作成した画面の名前ではなく、スタイルの役割に基づいて管理する**構成にした。

## まとめ

AI問題生成中の待機状態をオーバーレイで明示し、処理が進行していることをユーザーに伝えられるようにした。また、関連画面のCSSを確認したことで、復習画面とAI生成画面で共用するスタイルを適切な場所へ移せた。

今回の変更は生成処理そのものではなく、**時間のかかる処理に対する操作上のフィードバック**と、**共通UIの保守性**を改善するものである。
