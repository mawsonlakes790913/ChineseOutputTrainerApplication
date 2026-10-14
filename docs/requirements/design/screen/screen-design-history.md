# 画面設計変更履歴

元の画面設計書「24. 開発途中で追加した画面設計」の記録を移動した。拼音・注音、別解、Structure表示、復習フィルター、ユーザー設定永続化、管理画面変更など、設計変更時点の説明を原文に近い形で保存する。

> これは**設計変更の記録**であり、現行仕様の保証ではない。現行の画面概要は[画面設計書](screen-design.md)、画面別の詳細は[画面詳細設計書](screen-design-details.md)を参照。

# 24. 開発途中で追加した画面設計

## 24.1 拼音・注音表示への対応

**追加日：2026年8月15日**

当初の画面設計では、中国語の模範解答のみを表示することを想定していた。

その後、学習時に中国語の発音を確認できるようにするため、

- 拼音
- 注音

を表示できるようにする。

Questionは、拼音・注音の両方を保持する。

AI生成由来の問題についても、保存後はQuestionとして管理するため、
通常の問題と同様に拼音・注音を保持する。

表示時には、ユーザーの発音表記設定によってどちらを使用するかを決定する。

```text
PINYIN
└── 拼音

ZHUYIN
└── 注音
```

その後、発音表記を表示しない選択肢も必要であると判断し、

```text
NONE
```

を追加した。

最終的に、

```text
PINYIN
ZHUYIN
NONE
```

の3種類とする。

---

## 24.2 別解の拼音・注音表示

**追加日：2026年8月16日**

別解が存在する問題についても、模範解答と同様に発音を確認できるようにする。

Questionには、

```text
alternative_answer
alternative_answer_pinyin
alternative_answer_zhuyin
```

を保持する。

画面上では、現在の発音表記設定に応じて、

```text
PINYIN
└── alternative_answer_pinyin

ZHUYIN
└── alternative_answer_zhuyin

NONE
└── 発音表記を表示しない
```

と切り替える。

別解が存在しない場合は、

```text
別解：
```

などのラベル自体も表示しない。

---

## 24.3 問題の文法・構造表示

**追加日：2026年8月23日**

Questionには、模範解答となる中国語文の根幹となる文法・構造を設定する。

当初はQuestion自身が、

```text
structure
```

という文字列を保持することを想定していた。

その後、Structureを独立したマスタデータとして管理する設計へ変更した。

現在は、

```text
Question
    ↓
structure_id
    ↓
Structure
```

という関係で文法・構造を取得する。

Structureは、

```text
structure_id
name
description_zh_cn
description_zh_tw
```

を保持する。

復習メニューや問題一覧ではStructureを検索条件として利用する。

---

## 24.4 復習メニューへのStructureフィルター追加

**追加日：2026年8月24日**

復習対象を文法・構造によって絞り込めるようにするため、G06復習メニューにStructureの複数選択を追加する。

```text
文法・構造

[すべて選択] [すべて解除]

☑ 動態助詞
☑ 可能補語
☑ 結果補語
☑ 把構文
☑ 比較構文
...
```

Structureの一覧はDBから取得する。

Enumとして固定しない。

各選択肢にはStructureの説明を表示できるようにする。

説明は現在の学習対象言語に応じて、

```text
MAINLAND
└── description_zh_cn

TAIWAN
└── description_zh_tw
```

を切り替える。

---

## 24.5 Structure説明の学習対象言語対応

**追加日：2026年8月24日**

Structureの説明は、大陸普通話向けと台湾華語向けの2種類を保持する。

```text
Structure
│
├── description_zh_cn
└── description_zh_tw
```

どちらを表示するかは、現在の学習対象言語によって決定する。

ログインユーザーの場合は、

```text
Users.language_variant

├── MAINLAND
│   └── description_zh_cn
│
└── TAIWAN
    └── description_zh_tw
```

とする。

これはサイト表記言語とは独立した切り替えである。

例えば、

```text
サイト表記言語 = 日本語
Users.language_variant = TAIWAN
```

の場合、

```text
Structure.description_zh_tw
```

を表示する。

---

## 24.6 ユーザー設定の永続化

**追加日：2026年8月27日**

当初、学習対象言語および発音表記は、アプリケーション利用中に変更できる設定として実装していた。

しかし、設定値をセッション上でのみ保持した場合、

```text
ログイン
    ↓
学習対象言語をTAIWANへ変更
    ↓
台湾華語で学習
    ↓
ログアウト
    ↓
セッション破棄
    ↓
再ログイン
    ↓
MAINLANDへ戻る
```

という問題が発生する。

発音表記についても同様に、ユーザーが注音へ変更しても、ログアウト後にデフォルトの拼音へ戻ってしまう。

これらはユーザーが継続的に利用する設定であるため、セッションのみで管理するのは適切ではない。

そこで、Usersに、

```text
language_variant
pronunciation_type
```

を追加し、ユーザーごとの設定としてDBへ永続化する。

```text
Users
│
├── language_variant
│   ├── MAINLAND
│   └── TAIWAN
│
└── pronunciation_type
    ├── PINYIN
    ├── ZHUYIN
    └── NONE
```

デフォルト値は、

```text
language_variant   = MAINLAND
pronunciation_type = PINYIN
```

とする。

設定変更後は、

```text
G24 設定画面
    ↓
設定変更
    ↓
Usersを更新
    ↓
DBへ保存
```

という処理を行う。

ログイン時には、

```text
認証成功
    ↓
Usersを取得
    ↓
language_variantを取得
pronunciation_typeを取得
    ↓
現在のユーザー設定として使用
```

とする。

これにより、

```text
Users
├── language_variant   = TAIWAN
└── pronunciation_type = ZHUYIN

        ↓

ログアウト

        ↓

再ログイン

        ↓

Users
├── language_variant   = TAIWAN
└── pronunciation_type = ZHUYIN
```

となり、前回の設定を継続できる。

### セッションとの関係

この変更によって、セッションを一切使用しなくなるわけではない。

アプリケーション利用中の現在値として、

```text
languageVariant
pronunciationType
```

をセッションへ保持する実装は可能である。

ただし、

```text
セッション
└── 一時的な現在値

Users
└── 永続的なユーザー設定
```

と役割を明確に分ける。

ログアウトによってセッションが破棄されても、Usersに設定が保存されているため、再ログイン時に復元できる。

### G24設定画面

この設計変更に伴い、G24を正式なユーザー設定画面として定義する。

Version 1.0で扱う設定は、

```text
1. 学習対象言語
2. 発音表記
```

の2項目とする。

AI問題生成については、ユーザーごとの設定項目を現時点では設けない。

AI生成に使用するLanguage Profileは、

```text
Users.language_variant
```

によって自動的に決定する。

そのため、AI問題生成のためだけに、

```text
AI生成言語
AI生成方言
AI用発音設定
```

などをG24へ追加しない。

---

## 24.7 アドミン専用AI設定画面の廃止

**変更日：2026年9月11日**

当初は、管理者がAI問題生成に関する共通設定を管理するため、

G20 アドミン専用AI設定画面

を設けることを想定していた。

G20では、

- 使用するAIモデル
- 共通プロンプト
- 大陸普通話向けLanguage Profile
- 台湾華語向けLanguage Profile
- その他のAI生成共通設定

などを管理することを想定していた。

その後の実装では、AI問題生成に使用する共通プロンプトおよびLanguage Profileをresources配下のファイルとして管理する構成とした。

また、現時点ではこれらの設定をアプリケーション上から変更する必要性がないため、アドミン専用AI設定画面は実装しない方針とした。

そのため、

G20 アドミン専用AI設定画面

を画面設計から削除した。

なお、G20の廃止はAI生成機能そのものの廃止を意味するものではない。

AI生成時には引き続き、共通プロンプトおよび学習対象言語に対応するLanguage Profileを使用する。

---
