# データ設計書

Chinese Output Forgeの**現行のデータ管理方針**を説明する。テーブルのカラム、主キー・外部キー、リレーションの詳細は[ER図](er-diagram.md)、開発途中の変更理由は[データベース設計変更履歴](database-design-history.md)を参照。

## 1. 概要

本システムは、大陸普通話（`MAINLAND`）と台湾華語（`TAIWAN`）の中国語学習問題、ユーザー設定、学習履歴、AI生成履歴を管理する。

両者は語彙・表現・模範解答が異なる**独立した問題データ**として扱う。一方、テーブル構造は共通とし、`QUESTION.language_variant`で区別する。言語ごとに問題・お気に入り・学習履歴のテーブルを分けない。

## 2. テーブル一覧

| テーブル | 役割 |
| --- | --- |
| `users` | ログイン情報、権限、アカウント状態、学習対象言語・発音表記の設定 |
| `question` | 通常問題と保存済みAI生成問題を共通管理 |
| `structure` | 文法・構造の分類と、大陸普通話・台湾華語向けの説明 |
| `favorite` | ユーザーごとのお気に入り問題 |
| `study_history` | ユーザー・問題ごとの最新の理解度評価 |
| `ai_generation_history` | AI生成時の重複抑制に用いる過去の生成文 |
| `question_list` | ユーザーが作成する問題リスト |
| `question_list_item` | 問題リストに含まれる問題 |
| `password_reset_token` | パスワード再設定用の一時トークンと有効期限 |

## 3. 主要な設計方針

### 3.1 学習対象言語と発音表記

- `users.language_variant`：ユーザーが**現在学習する言語**（`MAINLAND` / `TAIWAN`）。
- `question.language_variant`：**その問題が属する言語**。通常学習・復習等の問題取得時に利用する。
- `users.pronunciation_type`：発音表記（`PINYIN` / `ZHUYIN` / `NONE`）。学習対象言語とは独立して選択できる。
- ユーザー設定はDBに永続化し、ログアウト後も保持する。設計上の初期値は学習対象言語が`MAINLAND`、発音表記が`PINYIN`。
- サイトのUI表記言語は、学習対象言語とは別の概念であり、問題の言語分類を変更しない。

### 3.2 問題と文法・構造

`question`には日本語問題文、中国語模範解答、拼音・注音、別解、難易度、解答条件などを保持する。文法・構造は`structure`でマスタ管理し、問題側の`structure_id`から参照する。`structure`は言語別の説明（`description_zh_cn` / `description_zh_tw`）を保持し、学習対象言語に応じて表示を切り替える。

`question.structure_id`は**NULLを許可**するため、構文が未設定の問題も存在できる。`condition`は文法分類とは異なり、出題者が設定する解答条件・ヒントを表す。

### 3.3 AI生成問題の保存と所有者

AI生成直後の問題は一時的なデータとして扱い、ユーザーが保存した場合に`question`へ登録する。独立した`ai_generated_question`テーブルは使用しない。

- `ai_generated = false`：通常問題。
- `ai_generated = true`：ユーザーが保存したAI生成由来の問題。
- `owner_user_id`：問題の所有ユーザー。**NULLを許可**する。AI生成問題の保存者だけでなく、ADMINが作成した通常問題にも設定される場合がある。
- 保存されたAI生成由来の問題は所有ユーザー向けに扱い、通常学習・復習・お気に入りなどの既存機能と共通のデータモデルを使用する。

AI生成の対象可否は`allow_ai_variation`、生成時に変更可能な範囲は`template`で管理する。固定部分とプレースホルダを組み合わせて生成内容を制御する。

### 3.4 学習履歴・お気に入り・問題リスト

`favorite`と`study_history`は、いずれも`user_id`と`question_id`の**複合主キー**を使用する。学習履歴は同じユーザー・問題の組み合わせにつき1件とし、最新の理解度（例：`HARD` / `GOOD` / `EASY`）と評価更新日時を管理する。

`question_list`はユーザーが作成したリスト、`question_list_item`はリストと問題を結び付ける中間テーブルである。`question_list_item`の主キーは`list_id`と`question_id`の組み合わせ。

### 3.5 AI生成履歴

`ai_generation_history`は**生成元の問題とユーザー**に紐付けて生成済み中国語文を保持し、次回のAI生成時の重複・過度な類似を抑制するために参照する。設計上、同一ユーザー・生成元問題の組み合わせにつき直近5件まで保持する。

これは、ユーザーが学習用に保存したAI生成問題（`question`）や、理解度を記録する`study_history`とは目的が異なる。生成履歴に言語・構文情報を重複保存せず、生成元の`question`から取得する。

### 3.6 ユーザー管理とパスワードリセット

`users`はログインIDとメールアドレスにUNIQUE制約を持つ。`account_locked`はアカウント凍結状態を表し、凍結時も関連する学習データは保持する設計とする。

`password_reset_token`は`users`に紐付き、`user_id`にUNIQUE制約があるため、ユーザー1人につき同時に最大1レコードを保持する。トークンには有効期限を設定し、再設定完了後に削除する。

## 4. DB制約と設計上の注意

- `question.owner_user_id`と`question.structure_id`はNULL許可。
- `favorite`、`study_history`、`question_list_item`は複合主キー。
- `study_history.user_id`および`study_history.question_id`には、確認時点のDBに**外部キー制約がない**。アプリケーション上の論理的な関連と物理DB制約を区別する。
- この資料は現在のデータモデルの概要を示す。変更前の設計や廃止済みカラムについては[設計変更履歴](database-design-history.md)を参照する。
