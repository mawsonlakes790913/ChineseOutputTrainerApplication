# テーブル定義書

## 1. 概要

Chinese Output Forgeで使用するデータベーステーブルを定義する。

本システムでは、大陸普通話と台湾華語（國語）を単なる文字体系の違いとして扱わず、それぞれ独立した問題データとして管理する。

ただし、両者のデータ構造は共通しているため、

- USER
- QUESTION
- STRUCTURE
- FAVORITE
- STUDY_HISTORY
- AI_GENERATED_QUESTION

について、大陸普通話用と台湾華語用で別テーブルを持つのではなく、共通テーブルで管理する。

大陸普通話と台湾華語の識別は、QUESTIONの `language_variant` によって行う。

想定する値は以下のとおり。

```text
MAINLAND
TAIWAN
```

ユーザー情報についても両方の学習対象言語で共通のUSERテーブルを使用する。

また、USERには認証・権限情報だけでなく、ユーザーが継続的に使用する以下の設定も保持する。

```text
language_variant
pronunciation_type
```

これにより、ユーザーが学習対象言語や発音表記を変更した場合、その設定をログアウト後も維持できるようにする。

---

# 2. USER

## 概要

ユーザー情報、認証情報、権限、およびユーザーごとの学習・表示設定を管理するテーブル。

ユーザーを内部的に識別するための `id` を主キーとし、ユーザーがログイン時に使用するIDは `login_id` として別途管理する。

また、

- 学習対象言語
- 発音表記

についてはユーザーごとの設定としてUSERに保存する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| id | 内部ユーザーID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| login_id | ログインID | - | - | VARCHAR(20) | ○ | ○ | ユーザーがログイン時に使用 |
| password | パスワード | - | - | VARCHAR(255) | ○ | - | ハッシュ化して保存 |
| role | 権限 | - | - | VARCHAR(20) | ○ | - | USER / ADMIN |
| language_variant | 学習対象言語 | - | - | VARCHAR(20) | ○ | - | MAINLAND / TAIWAN |
| pronunciation_type | 発音表記 | - | - | VARCHAR(20) | ○ | - | PINYIN / ZHUYIN / NONE |

## `language_variant`

ユーザーが現在学習対象として設定している中国語を表す。

| 値 | 意味 |
|---|---|
| MAINLAND | 大陸普通話 |
| TAIWAN | 台湾華語（國語） |

実装時にはEnumとして定義する。

新規ユーザーのデフォルト値は、

```text
MAINLAND
```

とする。

## `pronunciation_type`

ユーザーが中国語の解答とともに表示する発音表記を表す。

| 値 | 意味 |
|---|---|
| PINYIN | 拼音を表示する |
| ZHUYIN | 注音を表示する |
| NONE | 発音表記を表示しない |

実装時にはEnumとして定義する。

新規ユーザーのデフォルト値は、

```text
PINYIN
```

とする。

## 補足

- `id` はアプリケーション内部およびDB上でユーザーを識別するために使用する。
- `login_id` はユーザーがログイン時に使用する。
- `login_id` は重複を許可しない。
- ユーザーが `login_id` を変更しても内部IDである `id` は変更しない。
- 他テーブルからUSERを参照する場合は `login_id` ではなく `id` を外部キーとして使用する。
- パスワードは平文では保存しない。
- `role = ADMIN` のユーザーのみ管理者用機能へアクセスできる。
- `language_variant` はユーザーが現在使用する学習対象言語を表す。
- `pronunciation_type` はユーザーが現在使用する発音表記を表す。
- `language_variant` と `pronunciation_type` は独立した設定として扱う。
- ユーザーが設定画面で設定を変更した場合はUSERを更新する。
- これらの設定はDBへ永続化し、ログアウト後も維持する。

例えば、

```text
USER

id                   = 1001
login_id             = naoki
role                 = USER
language_variant     = TAIWAN
pronunciation_type   = ZHUYIN
```

の場合、このユーザーは台湾華語を学習対象とし、発音表記として注音を使用する。

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
| pinyin | 拼音 | - | - | TEXT | ○ | - | 中国語本文に対応する拼音 |
| zhuyin | 注音 | - | - | TEXT | ○ | - | 中国語本文に対応する注音符號 |
| alternative_answer | 別解 | - | - | TEXT | - | - | 任意入力（NULL可） |
| alternative_answer_pinyin | 別解の拼音 | - | - | TEXT | - | - | 別解に対応する拼音（NULL可） |
| alternative_answer_zhuyin | 別解の注音 | - | - | TEXT | - | - | 別解に対応する注音符號（NULL可） |
| condition | 条件 | - | - | VARCHAR(100) | - | - | 解答条件・ヒント（NULL可） |
| structure_id | 文法・構造ID | - | ○ | BIGINT | ○ | - | STRUCTURE.structure_id参照 |
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

実装時にはEnumとして定義する。

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
- QUESTIONは `structure_id` によってSTRUCTUREを参照する。
- 1つのQUESTIONにつき1つのSTRUCTUREを関連付ける。
- STRUCTUREとの関連はすべてのQUESTIONで必須とし、`structure_id` はNULLを許可しない。
- 文法・構造名および説明はQUESTION自身には保持せず、関連するSTRUCTUREから取得する。
- `condition` は開発者・出題者が設定する解答条件・ヒントであり、条件を必要としない問題ではNULLを許可する。
- `pinyin` と `zhuyin` はユーザーの現在の発音表記設定にかかわらず両方保持する。
- 別解が存在する場合は、別解用の拼音・注音も保持できる。

---

# 4. STRUCTURE

## 概要

問題の文法・構造を管理するマスタテーブル。

QUESTIONの模範解答となる中国語文を文法・構造上分析した際の、文章の根幹となる文法・構造を管理する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| structure_id | 文法・構造ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| name | 文法・構造名 | - | - | VARCHAR(50) | ○ | ○ | 可能補語、把構文、比較構文など |
| description_zh_cn | 大陸普通話向け説明 | - | - | TEXT | ○ | - | 大陸普通話向けの文法・構造の説明 |
| description_zh_tw | 台湾華語向け説明 | - | - | TEXT | ○ | - | 台湾華語向けの文法・構造の説明 |

## 補足

- STRUCTUREは文法・構造を一元管理するマスタテーブルとする。
- `structure_id` は文法・構造を内部的に識別するために使用する。
- `name` には「可能補語」「把構文」「比較構文」などの文法・構造名を保持する。
- 文法・構造名の重複を防ぐため、`name` はUNIQUEとする。
- 文法・構造そのものの分類は、大陸普通話と台湾華語で共通して管理する。
- `description_zh_cn` には、大陸普通話向けの文法・構造についての簡潔な説明を保持する。
- `description_zh_tw` には、台湾華語向けの文法・構造についての簡潔な説明を保持する。
- 説明には、必要に応じて代表的な中国語表現を含める。
- ログインユーザーの場合、どちらの説明を使用するかは `USER.language_variant` によって決定する。
- `USER.language_variant = MAINLAND` の場合は `description_zh_cn` を使用する。
- `USER.language_variant = TAIWAN` の場合は `description_zh_tw` を使用する。
- 説明の切り替えはサイト表記言語とは独立して扱う。
- 1つのSTRUCTUREには複数のQUESTIONを関連付けることができる。
- QUESTIONが1件も関連付けられていないSTRUCTUREの存在も許可する。
- QUESTIONからSTRUCTUREへの関連は必須とする。
- 文法・構造はEnumでは管理せず、STRUCTUREのレコードとして追加・変更できるものとする。

```text
STRUCTURE
    │
    │ 1:N
    ↓
QUESTION
```

例えば、

```text
STRUCTURE

structure_id = 1
name         = 因果複文

description_zh_cn =
原因・理由と、それによって生じる結果を表す複文。
「因为～所以～」「既然～就～」など。

description_zh_tw =
原因・理由と、それによって生じる結果を表す複文。
「因為～所以～」「既然～就～」など。

        │
        │ 1:N
        ↓

QUESTION
├── 因为下雨，所以我没出去。
├── 既然决定了，就不要放弃。
└── 因为太累了，所以他早早就睡了。
```

のように管理する。

---

# 5. FAVORITE

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

# 6. STUDY_HISTORY

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

# 7. AI_GENERATED_QUESTION

## 概要

AI生成学習によって生成された問題のうち、保存対象となった問題を管理するテーブル。

大陸普通話・台湾華語のAI生成問題を共通のAI_GENERATED_QUESTIONテーブルで管理する。

保存されたAI生成問題は、生成したユーザー専用のデータとして扱う。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| generated_question_id | AI生成問題ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 所有ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| source_question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |
| japanese_text | 日本語問題文 | - | - | TEXT | ○ | - | AI生成された問題文 |
| chinese_text | 模範解答 | - | - | TEXT | ○ | - | AI生成された中国語 |
| pinyin | 拼音 | - | - | TEXT | ○ | - | 中国語本文に対応する拼音 |
| zhuyin | 注音 | - | - | TEXT | ○ | - | 中国語本文に対応する注音符號 |
| alternative_answer | 別解 | - | - | TEXT | - | - | 任意入力（NULL可） |
| alternative_answer_pinyin | 別解の拼音 | - | - | TEXT | - | - | 別解に対応する拼音（NULL可） |
| alternative_answer_zhuyin | 別解の注音 | - | - | TEXT | - | - | 別解に対応する注音符號（NULL可） |
| evaluation | 学習結果 | - | - | VARCHAR(10) | ○ | - | HARD / GOOD / EASY |
| created_at | 作成日時 | - | - | TIMESTAMP | ○ | - | DBへ初めて保存した日時 |
| evaluation_updated_at | 評価更新日時 | - | - | TIMESTAMP | ○ | - | 最後に理解度を更新した日時 |

## 補足

- AIが問題を生成した時点ではレコードを作成しない。
- 保存条件を満たした場合にAI_GENERATED_QUESTIONへINSERTする。
- `user_id` によってAI生成問題の所有ユーザーを特定する。
- AI生成問題は所有ユーザーのみ参照できる。
- 他ユーザーの通常学習や復習には使用しない。
- 通常学習では本テーブルから出題しない。
- 保存されたAI生成問題は、対応する機能から再度参照できる。
- AI生成問題専用のStudyHistoryテーブルは作成せず、本テーブル自身に最新の評価を保持する。
- `source_question_id` により生成元となったマスタ問題を追跡する。
- AI_GENERATED_QUESTION自身には原則として `language_variant` を保持しない。
- 学習対象言語は生成元となったQUESTIONから判定する。
- 文法・構造についても生成元QUESTIONを経由してSTRUCTUREから判定する。
- AI生成問題にも本解答・別解それぞれの拼音・注音を保持できる。

```text
AI_GENERATED_QUESTION
        ↓
source_question_id
        ↓
QUESTION
        ├── language_variant
        │
        └── structure_id
                ↓
            STRUCTURE
```

---

# 8. 外部キー一覧

| テーブル | カラム | 参照先 |
|---|---|---|
| QUESTION | structure_id | STRUCTURE.structure_id |
| FAVORITE | user_id | USER.id |
| FAVORITE | question_id | QUESTION.question_id |
| STUDY_HISTORY | user_id | USER.id |
| STUDY_HISTORY | question_id | QUESTION.question_id |
| AI_GENERATED_QUESTION | user_id | USER.id |
| AI_GENERATED_QUESTION | source_question_id | QUESTION.question_id |

---

# 9. 主キー一覧

| テーブル | 主キー |
|---|---|
| USER | id |
| QUESTION | question_id |
| STRUCTURE | structure_id |
| FAVORITE | user_id + question_id |
| STUDY_HISTORY | user_id + question_id |
| AI_GENERATED_QUESTION | generated_question_id |

---

# 10. ユーザーIDの管理方針

USERでは、内部管理用IDとログインIDを分離する。

```text
USER

id       ← DB内部で使用する不変のID
login_id ← ユーザーが使用するログインID
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

# 11. ユーザー設定の管理方針

ユーザーが継続的に使用する、

```text
学習対象言語
発音表記
```

はUSERに保存する。

```text
USER
│
├── language_variant
└── pronunciation_type
```

## 学習対象言語

```text
language_variant

├── MAINLAND
│   └── 大陸普通話
│
└── TAIWAN
    └── 台湾華語（國語）
```

デフォルト値は、

```text
MAINLAND
```

とする。

## 発音表記

```text
pronunciation_type

├── PINYIN
│   └── 拼音
│
├── ZHUYIN
│   └── 注音
│
└── NONE
    └── 表示なし
```

デフォルト値は、

```text
PINYIN
```

とする。

## 永続化

ユーザーが設定画面で設定を変更した場合はUSERを更新する。

```text
設定変更
    ↓
USER更新
    ↓
DBへ保存
```

例えば、

```text
language_variant   = TAIWAN
pronunciation_type = ZHUYIN
```

に変更した場合、

```text
ログアウト
    ↓
セッション破棄
    ↓
再ログイン
    ↓
USERから設定を取得
    ↓
TAIWAN / ZHUYIN を継続
```

となる。

セッションはアプリケーション利用中の現在値を一時的に保持するために利用できるが、ユーザー設定の永続的な保存先はUSERとする。

---

# 12. AI生成問題の保存方針

AI生成問題については、生成されたすべての問題をDBへ保存しない。

基本的な処理は以下とする。

```text
USER.language_variant
    ↓
対応するマスタQUESTION
    ↓
AIによる問題生成
    ↓
ユーザーへ出題
    ↓
保存対象となる操作
    ↓
AI_GENERATED_QUESTIONへINSERT
```

保存されなかったAI生成問題については、学習終了後に破棄する。

これにより、表示されただけのAI生成問題がDBへ大量に蓄積されることを防ぐ。

また、AI生成問題をマスタQUESTIONへ追加しないことで、

**あるユーザーのために生成された問題が、別のユーザーの通常学習に混入することを防止する。**

AI生成問題の学習対象言語は、

```text
AI_GENERATED_QUESTION
    ↓
source_question_id
    ↓
QUESTION.language_variant
```

によって判定する。

AI生成時に使用するLanguage Profileについても、現在の `USER.language_variant` に対応するものを使用する。

---

# 13. 大陸普通話・台湾華語の管理方針

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

question_id       = 100
language_variant  = MAINLAND
```

と、

```text
QUESTION

question_id       = 101
language_variant  = TAIWAN
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

# 14. 学習対象言語によるデータ取得方針

通常学習、復習、AI生成学習、ユーザー用問題一覧などでは、ログインユーザーの場合、

```text
USER.language_variant
```

を現在の学習対象言語として使用する。

大陸普通話の場合：

```text
USER.language_variant = MAINLAND
```

に対応して、

```text
QUESTION.language_variant = MAINLAND
```

の問題を取得する。

台湾華語の場合：

```text
USER.language_variant = TAIWAN
```

に対応して、

```text
QUESTION.language_variant = TAIWAN
```

の問題を取得する。

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

例えば、

```text
USER.language_variant = TAIWAN

        ↓

STUDY_HISTORY
      ↓
   QUESTION
      ↓
language_variant = TAIWAN
```

のように絞り込む。

これにより、テーブル自体を分離しなくても、大陸普通話・台湾華語の学習データを論理的に分離できる。

---

# 15. 発音表記による表示方針

QUESTIONおよびAI_GENERATED_QUESTIONは、ユーザーの現在の設定にかかわらず、

```text
pinyin
zhuyin
```

の両方を保持する。

別解についても、

```text
alternative_answer_pinyin
alternative_answer_zhuyin
```

の両方を保持できる。

実際にどちらを表示するかは、

```text
USER.pronunciation_type
```

によって決定する。

```text
USER.pronunciation_type = PINYIN

chinese_text
└── pinyin

alternative_answer
└── alternative_answer_pinyin
```

```text
USER.pronunciation_type = ZHUYIN

chinese_text
└── zhuyin

alternative_answer
└── alternative_answer_zhuyin
```

```text
USER.pronunciation_type = NONE

chinese_text
└── 発音表記なし

alternative_answer
└── 発音表記なし
```

学習対象言語と発音表記は独立して扱う。

したがって、

```text
MAINLAND + PINYIN
MAINLAND + ZHUYIN
MAINLAND + NONE

TAIWAN + PINYIN
TAIWAN + ZHUYIN
TAIWAN + NONE
```

のすべての組み合わせを許可する。

---

# 16. サイト表記言語との関係

学習対象言語、発音表記、サイト表記言語はそれぞれ異なる設定として扱う。

## 学習対象言語

```text
USER.language_variant
```

は、

```text
どちらの中国語を学習するか
```

を表す。

## 発音表記

```text
USER.pronunciation_type
```

は、

```text
中国語の発音をどの形式で表示するか
```

を表す。

## サイト表記言語

サイト表記言語は、

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
発音表記       = PINYIN
```

や、

```text
サイト表記言語 = 简体中文
学習対象言語   = MAINLAND
発音表記       = ZHUYIN
```

といった組み合わせを可能とする。

したがって、

- サイト表記言語を変更してもQUESTIONの取得条件には影響しない。
- 学習対象言語を変更してもサイト表記言語は自動的に変更しない。
- 発音表記を変更しても学習対象言語は変更しない。
- 学習対象言語を変更しても発音表記は変更しない。

---

# 17. 設計上の補足

- USERは大陸普通話・台湾華語で共通とする。
- USERは内部管理用の `id` を主キーとして持つ。
- ユーザーがログイン時に使用するIDは `login_id` とする。
- `login_id` はUNIQUEとする。
- 他テーブルからユーザーを参照する場合は `USER.id` を使用する。
- `login_id` を外部キーとして使用しない。
- ユーザーが `login_id` を変更しても関連テーブルへの影響は発生しない。
- USERには `language_variant` を保持する。
- USERには `pronunciation_type` を保持する。
- `language_variant` は `MAINLAND / TAIWAN` とする。
- `pronunciation_type` は `PINYIN / ZHUYIN / NONE` とする。
- 新規ユーザーの `language_variant` のデフォルト値は `MAINLAND` とする。
- 新規ユーザーの `pronunciation_type` のデフォルト値は `PINYIN` とする。
- 学習対象言語および発音表記はDBへ永続化する。
- ログアウト後も学習対象言語および発音表記を維持する。
- 大陸普通話と台湾華語は異なる問題データとして扱う。
- QUESTIONは大陸普通話・台湾華語で分離しない。
- QUESTIONに `language_variant` を持たせる。
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
- AI生成問題には所有ユーザーの内部IDを保持する。
- AI生成問題は所有ユーザー専用のデータとして扱う。
- 他ユーザーのAI生成問題を通常学習・復習・問題一覧へ表示しない。
- AI生成問題をQUESTIONへ追加しない。
- AI生成問題の最新理解度はAI_GENERATED_QUESTION自身に保持する。
- `source_question_id` によってAI生成元のQUESTIONを追跡する。
- AI生成問題の学習対象言語は生成元QUESTIONから判定する。
- AI生成問題の文法・構造は生成元QUESTIONから判定する。
- `allow_ai_variation = true` の問題のみAI生成元として使用する。
- `subject_type` は `PRONOUN / NON_PRONOUN / ALL` を想定する。
- `verb_variation` は `FIXED / FLEXIBLE` を想定する。
- USER.passwordはBCrypt等によってハッシュ化して保存する。
- `role = ADMIN` のユーザーのみ管理者用機能へアクセスできる。
- 学習対象言語、発音表記、サイト表記言語はそれぞれ独立した設定として扱う。
- AiSettingについては保存方式が未確定のため、本テーブル定義書には含めない。DB管理を採用する場合は別途追加する。
- STRUCTUREは文法・構造を管理するマスタテーブルとする。
- STRUCTUREは `structure_id`、文法・構造名、大陸普通話向けの説明、台湾華語向けの説明を保持する。
- 文法・構造そのものの分類は大陸普通話と台湾華語で共通して管理する。
- `description_zh_cn` には大陸普通話向けの説明を保持する。
- `description_zh_tw` には台湾華語向けの説明を保持する。
- ログインユーザーについて、STRUCTUREの説明を表示する際は `USER.language_variant` に応じて使用する説明を切り替える。
- `USER.language_variant = MAINLAND` の場合は `description_zh_cn` を使用する。
- `USER.language_variant = TAIWAN` の場合は `description_zh_tw` を使用する。
- STRUCTUREの説明の切り替えはサイト表記言語とは独立して扱う。
- STRUCTUREとQUESTIONは1対多の関係とする。
- QUESTIONは `structure_id` によってSTRUCTUREを参照する。
- 1つのQUESTIONにつき1つのSTRUCTUREを関連付ける。
- QUESTIONからSTRUCTUREへの関連は必須とし、`structure_id` はNULLを許可しない。
- 文法・構造名はSTRUCTUREで一元管理し、QUESTIONには重複して保持しない。
- `condition` は開発者・出題者が設定する解答条件・ヒントとして扱い、NULLを許可する。
- AI_GENERATED_QUESTIONには `structure_id` を重複して保持しない。

---

# 18. 開発途中で追加・変更したテーブル設計

## 18.1 拼音・注音への対応

**追加日：2026年8月15日**

当初のテーブル設計では、QUESTIONおよびAI_GENERATED_QUESTIONに中国語本文のみを保存し、発音表記は保存しない設計としていた。

その後、学習時に中国語の発音を確認できるようにするため、

```text
pinyin
zhuyin
```

を追加した。

QUESTIONおよびAI_GENERATED_QUESTIONでは、大陸普通話・台湾華語のどちらについても拼音・注音の両方を保持する。

```text
QUESTION
│
├── language_variant
├── chinese_text
├── pinyin
└── zhuyin
```

表示する発音表記は、

```text
USER.pronunciation_type
```

によって決定する。

```text
PINYIN
↓
pinyin

ZHUYIN
↓
zhuyin

NONE
↓
表示しない
```

発音表記は学習対象言語とは独立して扱う。

---

## 18.2 別解の拼音・注音への対応

**追加日：2026年8月16日**

別解が存在する問題についても発音を確認できるようにするため、

```text
alternative_answer_pinyin
alternative_answer_zhuyin
```

を追加した。

QUESTIONでは、

```text
QUESTION
│
├── chinese_text
│   ├── pinyin
│   └── zhuyin
│
└── alternative_answer
    ├── alternative_answer_pinyin
    └── alternative_answer_zhuyin
```

として保持する。

AI_GENERATED_QUESTIONについても、

```text
alternative_answer
alternative_answer_pinyin
alternative_answer_zhuyin
```

を保持できる設計とする。

本解答と別解には同じ `USER.pronunciation_type` を適用する。

```text
PINYIN
├── chinese_text        → pinyin
└── alternative_answer  → alternative_answer_pinyin

ZHUYIN
├── chinese_text        → zhuyin
└── alternative_answer  → alternative_answer_zhuyin

NONE
├── chinese_text        → 発音表記なし
└── alternative_answer  → 発音表記なし
```

別解専用の発音表記設定は設けない。

---

## 18.3 問題の文法・構造の追加

**追加日：2026年8月23日**

問題を文法・構造によって客観的に分類し、復習時の出題条件や問題検索に利用できるようにするため、QUESTIONへ文法・構造情報を追加する設計とした。

当初は、

```text
QUESTION
└── structure VARCHAR(50)
```

としてQUESTION自身に文字列で保存する設計とした。

`structure` は、中国語文そのものの根幹となる文法・構造を客観的に分類する情報として定義した。

一方、既存の `condition` は、

```text
structure
└── 中国語文そのものの根幹となる文法・構造
    └── 客観的な分類

condition
└── その問題をどのように解答させるか
    └── 開発者・出題者が設定する主観的な条件・ヒント
```

という違いを持つ。

例えば、

```text
japanese_text = 彼は笑いながら私に話した。
chinese_text  = 他笑着跟我说话。
structure     = 動態助詞（着）
condition     = 「着」を使う
```

のように扱う。

その後、文法・構造をマスタテーブル化する設計へ変更したため、QUESTION自身に文字列の `structure` を保持する設計は廃止した。

---

## 18.4 STRUCTUREのマスタテーブル化

**変更日：2026年8月24日**

文法・構造ごとに、

- 文法・構造名
- 大陸普通話向け説明
- 台湾華語向け説明

を保持する必要が生じたため、STRUCTUREを独立したマスタテーブルとして管理する設計へ変更した。

```text
STRUCTURE
│
├── structure_id
├── name
├── description_zh_cn
└── description_zh_tw
```

QUESTIONの、

```text
structure VARCHAR(50) NOT NULL
```

を廃止し、

```text
structure_id BIGINT NOT NULL
```

へ変更する。

`structure_id` は、

```text
STRUCTURE.structure_id
```

を参照する外部キーとする。

```text
STRUCTURE
    │
    │ 1:N
    ↓
QUESTION
    │
    └── structure_id
```

1つのQUESTIONには必ず1つのSTRUCTUREを関連付ける。

AI_GENERATED_QUESTIONには `structure_id` を追加せず、

```text
AI_GENERATED_QUESTION
        ↓
source_question_id
        ↓
QUESTION
        ↓
structure_id
        ↓
STRUCTURE
```

として取得する。

---

## 18.5 ユーザー設定の永続化

**変更日：2026年8月27日**

当初、学習対象言語および発音表記については、

```text
Session等で管理するか
USERへ保存するか
```

を詳細設計時に決定する方針としていた。

しかし、Sessionのみで管理した場合、

```text
ログイン
    ↓
設定変更
    ↓
学習
    ↓
ログアウト
    ↓
Session破棄
    ↓
再ログイン
    ↓
デフォルト設定へ戻る
```

という問題が発生する。

例えば、

```text
MAINLAND
↓
TAIWANへ変更
↓
ログアウト
↓
再ログイン
↓
MAINLAND
```

となってしまう。

発音表記についても同様に、

```text
PINYIN
↓
ZHUYINへ変更
↓
ログアウト
↓
再ログイン
↓
PINYIN
```

となってしまう。

学習対象言語と発音表記はユーザーが継続的に使用する設定であるため、USERへ保存する設計に変更する。

USERへ、

```text
language_variant
pronunciation_type
```

を追加する。

```text
USER
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

初期値は、

```text
language_variant   = MAINLAND
pronunciation_type = PINYIN
```

とする。

設定変更時は、

```text
設定画面
    ↓
USER更新
    ↓
DBへ保存
```

とする。

再ログイン時は、

```text
ログイン
    ↓
USER取得
    ↓
language_variant取得
pronunciation_type取得
    ↓
保存済み設定を使用
```

とする。

これにより、

```text
USER
├── language_variant   = TAIWAN
└── pronunciation_type = ZHUYIN

        ↓

ログアウト

        ↓

再ログイン

        ↓

TAIWAN / ZHUYINを継続
```

できる。

Sessionはアプリケーション利用中の一時的な現在値として使用できるが、永続的なユーザー設定の保存先とはしない。

---

# 19. 現在の主要テーブル構成

最終的な主要テーブルの関係は以下とする。

```text
USER
│
├── language_variant
├── pronunciation_type
│
├── 1:N FAVORITE
├── 1:N STUDY_HISTORY
└── 1:N AI_GENERATED_QUESTION


STRUCTURE
    │
    │ 1:N
    ↓
QUESTION
    │
    ├── 1:N FAVORITE
    ├── 1:N STUDY_HISTORY
    │
    └── 1:N AI_GENERATED_QUESTION
            ↑
            │ source_question_id
```

USERは、

```text
誰が利用しているか
+
そのユーザーがどの設定を使用しているか
```

を管理する。

QUESTIONは、

```text
どの学習対象言語の
どのマスタ問題であるか
```

を管理する。

STRUCTUREは、

```text
QUESTIONがどの文法・構造に属するか
```

を管理する。

FAVORITEおよびSTUDY_HISTORYは、

```text
USER
+
QUESTION
```

の関係を管理する。

AI_GENERATED_QUESTIONは、

```text
USER
+
生成元QUESTION
+
AIによって生成された問題
```

を管理する。

これにより、

```text
ユーザー設定
問題データ
文法・構造
学習履歴
お気に入り
AI生成問題
```

をそれぞれ役割の異なるデータとして管理する。