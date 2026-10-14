# テーブル定義書

Chinese Output Forgeの**現行DB構造**をまとめた資料です。設計方針は[data-design.md](data-design.md)、関係図は[er-diagram.md](er-diagram.md)、設計変更の経緯は[database-design-history.md](database-design-history.md)を参照してください。

## 表記

- **PK**：主キー（複数列の場合は複合主キー）
- **FK**：DB上に外部キー制約が存在
- **NN**：NOT NULL、**UQ**：UNIQUE、**NULL**：NULL可
- PK列はNOT NULL。データ型は現行DBの型を表し、VARCHARの最大文字数は本資料では省略。

## テーブル定義

### `users`

ユーザー認証・権限・学習設定

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | BIGINT | PK | 内部ユーザーID |
| `login_id` | VARCHAR | NN, UQ | ログインID |
| `email` | VARCHAR | NN, UQ | メールアドレス |
| `password` | VARCHAR | NN | ハッシュ化されたパスワード |
| `role` | VARCHAR | NN | USER / ADMIN |
| `account_locked` | BOOLEAN | NN | アカウント凍結状態 |
| `language_variant` | VARCHAR | NN | MAINLAND / TAIWAN |
| `pronunciation_type` | VARCHAR | NN | PINYIN / ZHUYIN / NONE |

### `structure`

文法・構造のマスタ

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `structure_id` | BIGINT | PK | 文法・構造ID |
| `name` | VARCHAR | NN, UQ | 文法・構造名 |
| `description_zh_cn` | VARCHAR | NN | 大陸普通話向け説明 |
| `description_zh_tw` | VARCHAR | NN | 台湾華語向け説明 |

### `question`

通常問題・保存済みAI生成問題

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `question_id` | BIGINT | PK | 問題ID |
| `language_variant` | VARCHAR | NN | MAINLAND / TAIWAN |
| `japanese_text` | VARCHAR | NN | 日本語問題文 |
| `chinese_text` | VARCHAR | NN | 中国語模範解答 |
| `pinyin` | TEXT | NULL | 拼音 |
| `zhuyin` | TEXT | NULL | 注音 |
| `alternative_answer` | VARCHAR | NULL | 別解 |
| `alternative_answer_pinyin` | VARCHAR | NULL | 別解の拼音 |
| `alternative_answer_zhuyin` | VARCHAR | NULL | 別解の注音 |
| `condition` | VARCHAR | NULL | 解答条件・ヒント |
| `structure_id` | BIGINT | FK, NULL | structure.structure_id |
| `difficulty` | VARCHAR | NN | 難易度 |
| `allow_ai_variation` | BOOLEAN | NN | AI改変元として使用可能か |
| `template` | VARCHAR | NULL | AI生成用テンプレート |
| `ai_generated` | BOOLEAN | NN | AI生成由来か |
| `owner_user_id` | BIGINT | FK, NULL | users.id。通常問題でも所有者設定可能 |
| `created_at` | TIMESTAMP | NN | 作成日時 |
| `updated_at` | TIMESTAMP | NN | 更新日時 |

### `favorite`

ユーザー別のお気に入り

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `user_id` | BIGINT | PK, FK | users.id |
| `question_id` | BIGINT | PK, FK | question.question_id |

### `study_history`

ユーザー別・問題別の最新の自己評価

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `user_id` | BIGINT | PK | ユーザーID（論理的にusers.idに対応） |
| `question_id` | BIGINT | PK | 問題ID（論理的にquestion.question_idに対応） |
| `evaluation` | VARCHAR | NN | HARD / GOOD / EASY |
| `evaluation_updated_at` | TIMESTAMP | NN | 最終評価日時 |

### `ai_generation_history`

AI生成の重複抑制用履歴

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | BIGINT | PK | 履歴ID |
| `user_id` | BIGINT | NN, FK | users.id |
| `question_id` | BIGINT | NN, FK | 生成元question.question_id |
| `chinese_text` | VARCHAR | NN | 生成された中国語文 |
| `created_at` | TIMESTAMP | NN | 生成日時 |

### `password_reset_token`

パスワード再設定用トークン

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `token_id` | BIGINT | PK | トークンID |
| `user_id` | BIGINT | NN, FK, UQ | users.id（1ユーザーにつき最大1件） |
| `token` | VARCHAR | NN, UQ | 再設定トークン |
| `expires_at` | TIMESTAMP | NN | 有効期限 |

### `question_list`

ユーザーが作成する問題リスト

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `list_id` | BIGINT | PK | リストID |
| `user_id` | BIGINT | NN, FK | users.id |
| `list_name` | VARCHAR | NN | リスト名 |
| `created_at` | TIMESTAMP | NN | 作成日時 |
| `updated_at` | TIMESTAMP | NN | 更新日時 |

### `question_list_item`

問題リストへの問題登録

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| `list_id` | BIGINT | PK, FK | question_list.list_id |
| `question_id` | BIGINT | PK, FK | question.question_id |
| `added_at` | TIMESTAMP | NN | 追加日時 |

## 補足：データの扱い

- 大陸普通話と台湾華語の問題は`question.language_variant`で区別し、別レコードとして管理する。
- `question.ai_generated`はAI生成由来かどうかを示す。`owner_user_id`はAI生成由来だけでなく通常問題にも設定できる。
- `question.structure_id`はNULL可。文法・構造を設定しない問題も登録できる。
- AI生成の一時的な履歴は`ai_generation_history`、ユーザーが保存した生成問題は`question`に保持する。
- `study_history`の`user_id`と`question_id`は複合主キーだが、現行DBに外部キー制約はない。アプリケーション上では各テーブルのIDに対応する。
- `favorite`、`study_history`、`question_list_item`はそれぞれ2列の複合主キーを持つ。
- `password_reset_token.user_id`はUNIQUEであり、ユーザーごとに同時に保持できるトークンは最大1件。

## DB上の外部キー

| 参照元 | 参照先 |
|---|---|
| `question.structure_id` | `structure.structure_id` |
| `question.owner_user_id` | `users.id` |
| `favorite.user_id` | `users.id` |
| `favorite.question_id` | `question.question_id` |
| `ai_generation_history.user_id` | `users.id` |
| `ai_generation_history.question_id` | `question.question_id` |
| `password_reset_token.user_id` | `users.id` |
| `question_list.user_id` | `users.id` |
| `question_list_item.list_id` | `question_list.list_id` |
| `question_list_item.question_id` | `question.question_id` |

> 注：上記は確認済みのDB定義を基準としたものです。旧資料に記載されていた`study_history.first_studied_at`や`question.source_question_id`は現行テーブルには存在しません。
