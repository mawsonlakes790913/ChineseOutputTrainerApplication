# テーブル定義書

## 1. 概要

Chinese Output Forgeで使用するデータベーステーブルを定義する。

本システムでは、大陸普通話と台湾華語（國語）を単なる文字体系の違いとして扱わず、それぞれ独立した問題データとして管理する。

ただし、両者のデータ構造は共通しているため、

- Question
- Favorite
- StudyHistory
- AiGeneratedQuestion

について、大陸普通話用と台湾華語用で別テーブルを持つのではなく、共通テーブルで管理する。

大陸普通話と台湾華語の識別は、QUESTIONの `language_variant` によって行う。

想定する値は以下のとおり。

```text
MAINLAND
TAIWAN
```

ユーザー情報についても両方の学習対象言語で共通のUSERテーブルを使用する。

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

# 3. QUESTION

## 概要

大陸普通話・台湾華語のマスタ問題、およびAI生成に必要な問題単位の制御情報を管理するテーブル。

両方の学習対象言語を同一テーブルで管理し、`language_variant` によって識別する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| question_id | 問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| language_variant | 学習対象言語 | - | - | VARCHAR(20) | ○ | - | MAINLAND / TAIWAN |
| japanese_text | 日本語文 | - | - | TEXT | ○ | - | 問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | 学習対象に対応した中国語 |
| alternative_answer | 別解 | - | - | TEXT | - | - | 任意入力（NULL可） |
| condition | 条件 | - | - | VARCHAR(100) | - | - | 文法・構文・表現条件など |
| difficulty | 難易度 | - | - | VARCHAR(20) | ○ | - | 難易度区分 |
| allow_ai_variation | AI生成可否 | - | - | BOOLEAN | ○ | - | AI生成対象かどうか |
| template | AI生成用テンプレート | - | - | TEXT | - | - | AI生成対象外の場合はNULL可 |
| subject_type | 主語生成タイプ | - | - | VARCHAR(20) | - | - | PRONOUN / NON_PRONOUN / ALL |
| verb_variation | 動詞生成タイプ | - | - | VARCHAR(20) | - | - | FIXED / FLEXIBLE |

## `language_variant`

学習対象となる中国語を識別する。

| 値 | 意味 |
|---|---|
| MAINLAND | 大陸普通話 |
| TAIWAN | 台湾華語（國語） |

実装時にはEnumとして定義することを想定する。

## 補足

- 本テーブルは全ユーザー共通のマスタ問題を保持する。
- 大陸普通話と台湾華語の問題を共通のQUESTIONテーブルで管理する。
- 大陸普通話と台湾華語は問題データとしては独立して扱う。
- 問題IDはQUESTION全体で一意とする。
- 大陸普通話と台湾華語の問題IDに対応関係は持たせない。
- 通常学習では現在の学習対象言語に対応する問題を出題する。
- AI生成学習では `allow_ai_variation = true` の問題をAI生成元として使用できる。
- `allow_ai_variation = false` の問題は固定問題としてのみ使用する。
- AIによって生成された問題そのものを本テーブルへ追加しない。
- `template`、`subject_type`、`verb_variation` は問題内容に応じてNULLを許容する。

---

# 4. FAVORITE

## 概要

ユーザーによるマスタ問題のお気に入り登録情報を管理するテーブル。

大陸普通話・台湾華語の双方を共通のFAVORITEテーブルで管理する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |

## 主キー

```text
(user_id, question_id)
```

## 補足

- USERとQUESTIONの多対多関係を管理する中間テーブルである。
- お気に入り登録された問題のみレコードを保持する。
- お気に入り未登録の場合はレコードを作成しない。
- お気に入り登録時にINSERTする。
- お気に入り解除時は対象レコードをDELETEする。
- FAVORITE自身には `language_variant` を保持しない。
- 学習対象言語は関連するQUESTIONの `language_variant` から判定する。

```text
FAVORITE
    ↓
QUESTION
    ↓
language_variant
```

---

# 5. STUDY_HISTORY

## 概要

マスタ問題に対するユーザーの学習履歴および自己評価を管理するテーブル。

大陸普通話・台湾華語の双方を共通のSTUDY_HISTORYテーブルで管理する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| user_id | 内部ユーザーID | ○ | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 問題ID | ○ | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |
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
- STUDY_HISTORY自身には `language_variant` を保持しない。
- 学習対象言語は関連するQUESTIONの `language_variant` から判定する。

```text
STUDY_HISTORY
      ↓
   QUESTION
      ↓
language_variant
```

---

# 6. AI_GENERATED_QUESTION

## 概要

AI生成学習によって生成された問題のうち、ユーザーが理解度を登録した問題を保存するテーブル。

大陸普通話・台湾華語のAI生成問題を共通のAI_GENERATED_QUESTIONテーブルで管理する。

保存されたAI生成問題は、生成したユーザー専用の復習対象として扱う。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| generated_question_id | AI生成問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 所有ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| source_question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |
| japanese_text | 日本語問題文 | - | - | TEXT | ○ | - | AI生成された問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | AI生成された中国語 |
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
- AI_GENERATED_QUESTION自身には原則として `language_variant` を保持しない。
- 学習対象言語は生成元となったQUESTIONから判定する。

```text
AI_GENERATED_QUESTION
        ↓
source_question_id
        ↓
QUESTION
        ↓
language_variant
```

生成時点の学習対象言語をAI_GENERATED_QUESTION自身にも保存する必要があるかについては、詳細実装時に必要性を再検討する。

---

# 7. 外部キー一覧

| テーブル | カラム | 参照先 |
|---|---|---|
| FAVORITE | user_id | USER.id |
| FAVORITE | question_id | QUESTION.question_id |
| STUDY_HISTORY | user_id | USER.id |
| STUDY_HISTORY | question_id | QUESTION.question_id |
| AI_GENERATED_QUESTION | user_id | USER.id |
| AI_GENERATED_QUESTION | source_question_id | QUESTION.question_id |

---

# 8. 主キー一覧

| テーブル | 主キー |
|---|---|
| USER | id |
| QUESTION | question_id |
| FAVORITE | user_id + question_id |
| STUDY_HISTORY | user_id + question_id |
| AI_GENERATED_QUESTION | generated_question_id |

---

# 9. ユーザーIDの管理方針

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

# 10. AI生成問題の保存方針

AI生成問題については、生成されたすべての問題をDBへ保存しない。

基本的な処理は以下とする。

```text
現在の学習対象言語
    ↓
対応するマスタQUESTION
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
マスタQUESTION
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

また、AI生成問題をマスタQUESTIONへ追加しないことで、

**あるユーザーのために生成された問題が、別のユーザーの通常学習に混入することを防止する。**

---

# 11. 大陸普通話・台湾華語の管理方針

大陸普通話と台湾華語は、単純な文字変換による同一問題として扱わない。

両者では、

- 使用文字
- 語彙
- 言い回し
- 慣用表現
- 模範解答
- 別解
- AI生成テンプレート
- AI生成時に適切な語彙

などが異なる可能性がある。

そのため、**問題データそのものは独立したデータとして扱う。**

ただし、データ構造は共通しているため、テーブルを分離するのではなく、

```text
QUESTION
```

として共通管理する。

QUESTIONに、

```text
language_variant
```

を持たせ、

```text
MAINLAND
TAIWAN
```

によって学習対象言語を識別する。

例えば、

```text
QUESTION

question_id      = 100
language_variant = MAINLAND
```

と、

```text
QUESTION

question_id      = 101
language_variant = TAIWAN
```

は、それぞれ独立した問題である。

両者が同じ日本語文・文法・構文を扱っていることは保証しない。

したがって、

```text
大陸普通話の問題
        ↓
文字変換
        ↓
台湾華語の問題
```

という関係にはしない。

あくまで、

```text
QUESTION
│
├── MAINLAND
│   └── 大陸普通話として作成された問題
│
└── TAIWAN
    └── 台湾華語として作成された問題
```

として管理する。

Favorite、StudyHistory、AiGeneratedQuestionについては、QUESTIONとの関連から学習対象言語を判定できるため、それぞれに `language_variant` を重複して保持しない。

---

# 12. 学習対象言語によるデータ取得方針

通常学習、復習、AI生成学習などでは、現在ユーザーが選択している学習対象言語に応じてQUESTIONを絞り込む。

大陸普通話の場合：

```text
language_variant = MAINLAND
```

台湾華語の場合：

```text
language_variant = TAIWAN
```

概念的には以下のような検索となる。

```sql
SELECT *
FROM question
WHERE language_variant = 'MAINLAND';
```

または、

```sql
SELECT *
FROM question
WHERE language_variant = 'TAIWAN';
```

FavoriteやStudyHistoryを利用する場合も、QUESTIONとのJOINによって現在の学習対象言語に対応するデータを取得する。

例えば概念的には、

```text
STUDY_HISTORY
      ↓
   QUESTION
      ↓
language_variant = TAIWAN
```

のように絞り込む。

これにより、テーブル自体を分離しなくても、大陸普通話・台湾華語の学習データを論理的に分離できる。

---

# 13. サイト表記言語との関係

学習対象言語とサイト表記言語は別の設定として扱う。

QUESTIONの `language_variant` は、

```text
どちらの中国語を学習するか
```

を表す。

一方、サイト表記言語は、

```text
サイトのUIをどの言語で表示するか
```

を表す。

サイト表記言語として以下を想定する。

```text
日本語
English
简体中文
繁體中文
```

例えば、

```text
サイト表記言語 = 日本語
学習対象言語   = TAIWAN
```

や、

```text
サイト表記言語 = 简体中文
学習対象言語   = TAIWAN
```

といった組み合わせを可能とする。

したがって、サイト表記言語を変更してもQUESTIONの取得条件には影響しない。

サイト表記言語および現在の学習対象言語をDBへ保存するか、Session等で管理するかについては別途詳細設計で決定する。

---

# 14. 設計上の補足

- USERは大陸普通話・台湾華語で共通とする。
- USERは内部管理用の `id` を主キーとして持つ。
- ユーザーがログイン時に使用するIDは `login_id` とする。
- `login_id` はUNIQUEとする。
- 他テーブルからユーザーを参照する場合は `USER.id` を使用する。
- `login_id` を外部キーとして使用しない。
- ユーザーが `login_id` を変更しても関連テーブルへの影響は発生しない。
- 大陸普通話と台湾華語は異なる問題データとして扱う。
- QUESTIONは大陸普通話・台湾華語で分離しない。
- QUESTIONに `language_variant` を持たせる。
- `language_variant` は `MAINLAND / TAIWAN` を想定する。
- QUESTIONの問題IDは全体で一意とする。
- 大陸普通話と台湾華語の問題IDに対応関係は持たせない。
- FAVORITEは大陸普通話・台湾華語で分離しない。
- FAVORITEはお気に入り登録された問題のみレコードを保持する。
- お気に入り登録時はINSERT、お気に入り解除時はDELETEする。
- FAVORITEの学習対象言語はQUESTIONから判定する。
- STUDY_HISTORYは大陸普通話・台湾華語で分離しない。
- STUDY_HISTORYは同一ユーザー・同一問題につき1レコードとする。
- STUDY_HISTORYには各マスタ問題に対する最新の理解度を保持する。
- STUDY_HISTORYの学習対象言語はQUESTIONから判定する。
- `evaluation` は `HARD / GOOD / EASY` のいずれかとする。
- AI_GENERATED_QUESTIONは大陸普通話・台湾華語で分離しない。
- AI生成問題は生成された時点ではDBへ保存しない。
- AI生成問題はユーザーが理解度を登録した場合に限り保存する。
- AI生成問題には所有ユーザーの内部IDを保持する。
- AI生成問題は所有ユーザー専用のデータとして扱う。
- 他ユーザーのAI生成問題を通常学習・復習・問題一覧へ表示しない。
- AI生成問題をQUESTIONへ追加しない。
- AI生成問題は復習メニューから明示的に選択された場合に再出題できる。
- AI生成問題の最新理解度はAI_GENERATED_QUESTION自身に保持する。
- `source_question_id` によってAI生成元のQUESTIONを追跡する。
- AI生成問題の学習対象言語は生成元QUESTIONから判定する。
- `allow_ai_variation = true` の問題のみAI生成元として使用する。
- `subject_type` は `PRONOUN / NON_PRONOUN / ALL` を想定する。
- `verb_variation` は `FIXED / FLEXIBLE` を想定する。
- USER.passwordはBCrypt等によってハッシュ化して保存する。
- `role = ADMIN` のユーザーのみ管理者用機能へアクセスできる。
- 学習対象言語とサイト表記言語は別の設定として扱う。
- AiSettingについては保存方式が未確定のため、本テーブル定義書には含めない。DB管理を採用する場合は別途追加する。