# テーブル定義書

## 1. 概要

Chinese Output Trainerで使用するデータベーステーブルを定義する。

本システムでは、簡体中文（普通話）と繁體中文（台湾華語・國語）を独立した問題データとして管理する。

そのため、

- Question
- Favorite
- StudyHistory
- AiGeneratedQuestion

については、簡体中文用と繁體中文用でそれぞれ別テーブルを持つ。

ユーザー情報については両言語で共通のUSERテーブルを使用する。

また、ユーザーをDB内部で識別するための数値IDと、ユーザーがログイン時に使用するログインIDを分離する。

---

# 2. USER

## 概要

ユーザー情報、認証情報、権限を管理するテーブル。

ユーザーを内部的に識別するための `id` を主キーとし、ユーザーがログイン時に使用するIDは `login_id` として別途管理する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| id | 内部ユーザーID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| login_id | ログインID | - | - | VARCHAR(20) | ○ | ○ | ユーザーがログイン時に使用 |
| password | パスワード | - | - | VARCHAR(255) | ○ | - | ハッシュ化して保存 |
| role | 権限 | - | - | VARCHAR(20) | ○ | - | USER / ADMIN |

## 補足

- `id` はアプリケーション内部およびDB上でユーザーを識別するために使用する。
- `login_id` はユーザーがログイン時に使用する。
- `login_id` は重複を許可しない。
- ユーザーが `login_id` を変更しても内部IDである `id` は変更しない。
- 他テーブルからUSERを参照する場合は `login_id` ではなく `id` を外部キーとして使用する。
- パスワードは平文では保存しない。
- `role = ADMIN` のユーザーのみ管理者用機能へアクセスできる。

---

# 3. SIMPLIFIED_QUESTION

## 概要

簡体中文（普通話）のマスタ問題およびAI生成に必要な問題単位の制御情報を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| question_id | 問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| japanese_text | 日本語文 | - | - | TEXT | ○ | - | 問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | 簡体中文 |
| alternative_answer | 別解 | - | - | TEXT | - | - | 任意入力（NULL可） |
| condition | 条件 | - | - | VARCHAR(100) | - | - | 文法・構文・表現条件など |
| difficulty | 難易度 | - | - | VARCHAR(20) | ○ | - | 難易度区分 |
| allow_ai_variation | AI生成可否 | - | - | BOOLEAN | ○ | - | AI生成対象かどうか |
| template | AI生成用テンプレート | - | - | TEXT | - | - | AI生成対象外の場合はNULL可 |
| subject_type | 主語生成タイプ | - | - | VARCHAR(20) | - | - | PRONOUN / NON_PRONOUN / ALL |
| verb_variation | 動詞生成タイプ | - | - | VARCHAR(20) | - | - | FIXED / FLEXIBLE |

## 補足

- 本テーブルは全ユーザー共通のマスタ問題を保持する。
- 通常学習では本テーブルの問題を出題する。
- AI生成学習では `allow_ai_variation = true` の問題をAI生成元として使用できる。
- `allow_ai_variation = false` の問題は固定問題としてのみ使用する。
- AIによって生成された問題そのものを本テーブルへ追加しない。
- `template`、`subject_type`、`verb_variation` は問題内容に応じてNULLを許容する。

---

# 4. TRADITIONAL_QUESTION

## 概要

繁體中文（台湾華語・國語）のマスタ問題およびAI生成に必要な問題単位の制御情報を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| question_id | 問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| japanese_text | 日本語文 | - | - | TEXT | ○ | - | 問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | 繁體中文 |
| alternative_answer | 別解 | - | - | TEXT | - | - | 任意入力（NULL可） |
| condition | 条件 | - | - | VARCHAR(100) | - | - | 文法・構文・表現条件など |
| difficulty | 難易度 | - | - | VARCHAR(20) | ○ | - | 難易度区分 |
| allow_ai_variation | AI生成可否 | - | - | BOOLEAN | ○ | - | AI生成対象かどうか |
| template | AI生成用テンプレート | - | - | TEXT | - | - | AI生成対象外の場合はNULL可 |
| subject_type | 主語生成タイプ | - | - | VARCHAR(20) | - | - | PRONOUN / NON_PRONOUN / ALL |
| verb_variation | 動詞生成タイプ | - | - | VARCHAR(20) | - | - | FIXED / FLEXIBLE |

## 補足

- 本テーブルは全ユーザー共通のマスタ問題を保持する。
- 通常学習では本テーブルの問題を出題する。
- AI生成学習では `allow_ai_variation = true` の問題をAI生成元として使用できる。
- `allow_ai_variation = false` の問題は固定問題としてのみ使用する。
- AIによって生成された問題そのものを本テーブルへ追加しない。
- SIMPLIFIED_QUESTIONとの間に問題ID上の対応関係は持たせない。
- `template`、`subject_type`、`verb_variation` は問題内容に応じてNULLを許容する。

---

# 5. SIMPLIFIED_FAVORITE

## 概要

ユーザーによる簡体中文マスタ問題のお気に入り登録情報を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | SIMPLIFIED_QUESTION.question_id参照 |

## 主キー

```text
(user_id, question_id)
```

## 補足

- USERとSIMPLIFIED_QUESTIONの多対多関係を管理する中間テーブルである。
- お気に入り登録された問題のみレコードを保持する。
- お気に入り未登録の場合はレコードを作成しない。
- お気に入り登録時にINSERTする。
- お気に入り解除時は対象レコードをDELETEする。

---

# 6. TRADITIONAL_FAVORITE

## 概要

ユーザーによる繁體中文マスタ問題のお気に入り登録情報を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | TRADITIONAL_QUESTION.question_id参照 |

## 主キー

```text
(user_id, question_id)
```

## 補足

- USERとTRADITIONAL_QUESTIONの多対多関係を管理する中間テーブルである。
- お気に入り登録された問題のみレコードを保持する。
- お気に入り未登録の場合はレコードを作成しない。
- お気に入り登録時にINSERTする。
- お気に入り解除時は対象レコードをDELETEする。

---

# 7. SIMPLIFIED_STUDY_HISTORY

## 概要

簡体中文マスタ問題に対するユーザーの学習履歴および自己評価を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | SIMPLIFIED_QUESTION.question_id参照 |
| evaluation | 学習結果 | - | - | VARCHAR(10) | ○ | - | HARD / GOOD / EASY |
| first_studied_at | 初回学習日時 | - | - | TIMESTAMP | ○ | - | 初めて評価を登録した日時 |
| evaluation_updated_at | 評価更新日時 | - | - | TIMESTAMP | ○ | - | 最後に理解度を更新した日時 |

## 主キー

```text
(user_id, question_id)
```

## 補足

- 同一ユーザー・同一問題について1レコードを保持する。
- 初回学習時にレコードをINSERTする。
- 再学習時は `evaluation` および `evaluation_updated_at` を更新する。
- `first_studied_at` は初回登録後は変更しない。
- 復習機能では本テーブルの理解度等を利用して対象問題を取得する。

---

# 8. TRADITIONAL_STUDY_HISTORY

## 概要

繁體中文マスタ問題に対するユーザーの学習履歴および自己評価を管理するテーブル。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | TRADITIONAL_QUESTION.question_id参照 |
| evaluation | 学習結果 | - | - | VARCHAR(10) | ○ | - | HARD / GOOD / EASY |
| first_studied_at | 初回学習日時 | - | - | TIMESTAMP | ○ | - | 初めて評価を登録した日時 |
| evaluation_updated_at | 評価更新日時 | - | - | TIMESTAMP | ○ | - | 最後に理解度を更新した日時 |

## 主キー

```text
(user_id, question_id)
```

## 補足

- 同一ユーザー・同一問題について1レコードを保持する。
- 初回学習時にレコードをINSERTする。
- 再学習時は `evaluation` および `evaluation_updated_at` を更新する。
- `first_studied_at` は初回登録後は変更しない。
- 復習機能では本テーブルの理解度等を利用して対象問題を取得する。

---

# 9. SIMPLIFIED_AI_GENERATED_QUESTION

## 概要

AI生成学習によって生成された簡体中文問題のうち、ユーザーが理解度を登録した問題を保存するテーブル。

保存されたAI生成問題は、生成したユーザー専用の復習対象として扱う。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| generated_question_id | AI生成問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 所有ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| source_question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | SIMPLIFIED_QUESTION.question_id参照 |
| japanese_text | 日本語問題文 | - | - | TEXT | ○ | - | AI生成された問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | AI生成された簡体中文 |
| evaluation | 学習結果 | - | - | VARCHAR(10) | ○ | - | HARD / GOOD / EASY |
| created_at | 作成日時 | - | - | TIMESTAMP | ○ | - | DBへ初めて保存した日時 |
| evaluation_updated_at | 評価更新日時 | - | - | TIMESTAMP | ○ | - | 最後に理解度を更新した日時 |

## 補足

- AIが問題を生成した時点ではレコードを作成しない。
- ユーザーが `HARD / GOOD / EASY` のいずれかを選択した場合に初めてINSERTする。
- `user_id` によってAI生成問題の所有ユーザーを特定する。
- AI生成問題は所有ユーザーのみ参照・復習できる。
- 他ユーザーの通常学習や復習には使用しない。
- 通常学習では本テーブルから出題しない。
- 復習メニューでAI生成問題を対象として選択した場合に再出題できる。
- AI生成問題専用のStudyHistoryテーブルは作成せず、本テーブル自身に最新の評価を保持する。
- `source_question_id` により生成元となったマスタ問題を追跡する。

---

# 10. TRADITIONAL_AI_GENERATED_QUESTION

## 概要

AI生成学習によって生成された繁體中文問題のうち、ユーザーが理解度を登録した問題を保存するテーブル。

保存されたAI生成問題は、生成したユーザー専用の復習対象として扱う。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| generated_question_id | AI生成問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 所有ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| source_question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | TRADITIONAL_QUESTION.question_id参照 |
| japanese_text | 日本語問題文 | - | - | TEXT | ○ | - | AI生成された問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | AI生成された繁體中文 |
| evaluation | 学習結果 | - | - | VARCHAR(10) | ○ | - | HARD / GOOD / EASY |
| created_at | 作成日時 | - | - | TIMESTAMP | ○ | - | DBへ初めて保存した日時 |
| evaluation_updated_at | 評価更新日時 | - | - | TIMESTAMP | ○ | - | 最後に理解度を更新した日時 |

## 補足

- AIが問題を生成した時点ではレコードを作成しない。
- ユーザーが `HARD / GOOD / EASY` のいずれかを選択した場合に初めてINSERTする。
- `user_id` によってAI生成問題の所有ユーザーを特定する。
- AI生成問題は所有ユーザーのみ参照・復習できる。
- 他ユーザーの通常学習や復習には使用しない。
- 通常学習では本テーブルから出題しない。
- 復習メニューでAI生成問題を対象として選択した場合に再出題できる。
- AI生成問題専用のStudyHistoryテーブルは作成せず、本テーブル自身に最新の評価を保持する。
- `source_question_id` により生成元となったマスタ問題を追跡する。

---

# 11. 外部キー一覧

| テーブル | カラム | 参照先 |
|---|---|---|
| SIMPLIFIED_FAVORITE | user_id | USER.id |
| SIMPLIFIED_FAVORITE | question_id | SIMPLIFIED_QUESTION.question_id |
| TRADITIONAL_FAVORITE | user_id | USER.id |
| TRADITIONAL_FAVORITE | question_id | TRADITIONAL_QUESTION.question_id |
| SIMPLIFIED_STUDY_HISTORY | user_id | USER.id |
| SIMPLIFIED_STUDY_HISTORY | question_id | SIMPLIFIED_QUESTION.question_id |
| TRADITIONAL_STUDY_HISTORY | user_id | USER.id |
| TRADITIONAL_STUDY_HISTORY | question_id | TRADITIONAL_QUESTION.question_id |
| SIMPLIFIED_AI_GENERATED_QUESTION | user_id | USER.id |
| SIMPLIFIED_AI_GENERATED_QUESTION | source_question_id | SIMPLIFIED_QUESTION.question_id |
| TRADITIONAL_AI_GENERATED_QUESTION | user_id | USER.id |
| TRADITIONAL_AI_GENERATED_QUESTION | source_question_id | TRADITIONAL_QUESTION.question_id |

---

# 12. 主キー一覧

| テーブル | 主キー |
|---|---|
| USER | id |
| SIMPLIFIED_QUESTION | question_id |
| TRADITIONAL_QUESTION | question_id |
| SIMPLIFIED_FAVORITE | user_id + question_id |
| TRADITIONAL_FAVORITE | user_id + question_id |
| SIMPLIFIED_STUDY_HISTORY | user_id + question_id |
| TRADITIONAL_STUDY_HISTORY | user_id + question_id |
| SIMPLIFIED_AI_GENERATED_QUESTION | generated_question_id |
| TRADITIONAL_AI_GENERATED_QUESTION | generated_question_id |

---

# 13. ユーザーIDの管理方針

USERでは、内部管理用IDとログインIDを分離する。

```text
USER

id          ← DB内部で使用する不変のID
login_id    ← ユーザーが使用するログインID
```

例えば以下のユーザーが存在するとする。

```text
id       = 1001
login_id = naoki
```

他テーブルでは、

```text
user_id = 1001
```

を保存し、

```text
user_id = "naoki"
```

とはしない。

これにより、ユーザーがログインIDを、

```text
naoki
↓
naoki2026
```

のように変更した場合でも、Favorite、StudyHistory、AiGeneratedQuestionなどの関連データを変更する必要がない。

内部的なユーザー識別は常に `USER.id` によって行う。

---

# 14. AI生成問題の保存方針

AI生成問題については、生成されたすべての問題をDBへ保存しない。

基本的な処理は以下とする。

```text
マスタ問題
    ↓
AIによる問題生成
    ↓
ユーザーへ出題
    ↓
回答表示
    ↓
Hard / Good / Easy
    ↓
AI_GENERATED_QUESTIONへINSERT
```

一方、理解度を登録せず終了した場合は、

```text
マスタ問題
    ↓
AIによる問題生成
    ↓
ユーザーへ出題
    ↓
学習終了
    ↓
保存しない
```

とする。

これにより、表示されただけのAI生成問題がDBへ大量に蓄積されることを防ぐ。

また、AI生成問題をマスタQuestionへ追加しないことで、

**あるユーザーのために生成された問題が、別のユーザーの通常学習に混入することを防止する。**

---

# 15. 簡体中文・繁體中文の分離方針

簡体中文と繁體中文は、単純な文字変換による同一問題として扱わない。

普通話と台湾華語では、

- 使用文字
- 語彙
- 言い回し
- 慣用表現
- 模範解答
- 別解
- AI生成テンプレート
- AI生成時に適切な語彙

などが異なる可能性がある。

そのため、

```text
SIMPLIFIED_QUESTION
TRADITIONAL_QUESTION
```

を独立して管理する。

同様に、

```text
SIMPLIFIED_FAVORITE
TRADITIONAL_FAVORITE

SIMPLIFIED_STUDY_HISTORY
TRADITIONAL_STUDY_HISTORY

SIMPLIFIED_AI_GENERATED_QUESTION
TRADITIONAL_AI_GENERATED_QUESTION
```

についても分離する。

SIMPLIFIED_QUESTIONとTRADITIONAL_QUESTIONの同一 `question_id` に対応関係が存在することは保証しない。

---

# 16. 設計上の補足

- USERは簡体中文・繁體中文で共通とする。
- USERは内部管理用の `id` を主キーとして持つ。
- ユーザーがログイン時に使用するIDは `login_id` とする。
- `login_id` はUNIQUEとする。
- 他テーブルからユーザーを参照する場合は `USER.id` を使用する。
- `login_id` を外部キーとして使用しない。
- ユーザーが `login_id` を変更しても関連テーブルへの影響は発生しない。
- SIMPLIFIED_QUESTIONとTRADITIONAL_QUESTIONは独立した問題マスタとして扱う。
- SIMPLIFIED_QUESTIONとTRADITIONAL_QUESTIONの問題IDに対応関係は持たせない。
- Favorite系テーブルは、お気に入り登録された問題のみレコードを保持する。
- お気に入り登録時はINSERT、お気に入り解除時はDELETEする。
- StudyHistory系テーブルは同一ユーザー・同一問題につき1レコードとする。
- StudyHistory系テーブルには各マスタ問題に対する最新の理解度を保持する。
- `evaluation` は `HARD / GOOD / EASY` のいずれかとする。
- AI生成問題は生成された時点ではDBへ保存しない。
- AI生成問題はユーザーが理解度を登録した場合に限り保存する。
- AI生成問題には所有ユーザーの内部IDを保持する。
- AI生成問題は所有ユーザー専用のデータとして扱う。
- 他ユーザーのAI生成問題を通常学習・復習・問題一覧へ表示しない。
- AI生成問題をSIMPLIFIED_QUESTIONまたはTRADITIONAL_QUESTIONへ追加しない。
- AI生成問題は復習メニューから明示的に選択された場合に再出題できる。
- AI生成問題の最新理解度はAI_GENERATED_QUESTION系テーブル自身に保持する。
- `source_question_id` によってAI生成元のマスタ問題を追跡する。
- `allow_ai_variation = true` の問題のみAI生成元として使用する。
- `subject_type` は `PRONOUN / NON_PRONOUN / ALL` を想定する。
- `verb_variation` は `FIXED / FLEXIBLE` を想定する。
- USER.passwordはBCrypt等によってハッシュ化して保存する。
- `role = ADMIN` のユーザーのみ管理者用機能へアクセスできる。
- AiSettingについては保存方式が未確定のため、本テーブル定義書には含めない。DB管理を採用する場合は別途追加する。