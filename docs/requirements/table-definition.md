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
- AI_GENERATION_HISTORY

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

大陸普通話・台湾華語のマスタ問題、およびAI生成に使用するテンプレート・生成可否情報を管理するテーブル。

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
- `template` は問題内容に応じてNULLを許容する。
- AI生成時の変更可能範囲は `template` によって定義する。
- AIによる変更を許可する部分は `template` 内のプレースホルダとして表現する。
- AIによる変更を許可しない部分は `template` の固定部分として保持する。
- 主語の種類など問題ごとに異なる生成制約は、可能な限りプレースホルダの種類によって表現する。
- `subject_type` および `verb_variation` のような独立したAI生成制御カラムは保持しない。
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

## 7.1 AI_GENERATION_HISTORY

### 概要

AI問題生成時に、同一ユーザー・同一生成元問題から同一または過度に類似した問題が短期間に繰り返し生成されることを抑制するため、過去のAI生成結果を管理するテーブル。

AI_GENERATED_QUESTIONがユーザーの学習データとして保存されたAI生成問題を管理するのに対し、AI_GENERATION_HISTORYは次回以降のAI問題生成を制御するための内部的な生成履歴を管理する。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| id | AI生成履歴ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 内部ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |
| chinese_text | 生成された中国語文 | - | - | TEXT | ○ | - | AIによって生成された中国語文 |
| created_at | 生成日時 | - | - | TIMESTAMP | ○ | - | AI生成履歴を保存した日時 |

### 主キー

AI_GENERATION_HISTORYでは、

```text
id
```

を主キーとする。

同一ユーザー・同一生成元問題について複数の生成履歴を保持する必要があるため、

```text
(user_id, question_id)
```

を主キーとはしない。

例えば、

```text
AI_GENERATION_HISTORY

id = 101
user_id = 1
question_id = 10
chinese_text = 服務台在哪裡？

id = 102
user_id = 1
question_id = 10
chinese_text = 電梯在哪裡？

id = 103
user_id = 1
question_id = 10
chinese_text = 捷運站在哪裡？
```

のように、同じ `user_id` と `question_id` の組み合わせについて複数レコードを保持できるものとする。

### USERとの関係

`user_id` は、

```text
USER.id
```

を参照する外部キーとする。

```text
USER
  │
  │ id
  ↓
AI_GENERATION_HISTORY
  │
  └── user_id
```

1人のユーザーは複数のAI生成履歴を持つことができる。

AI生成履歴はユーザーごとに独立して管理し、他のユーザーの生成履歴をAI問題生成時に使用しない。

### QUESTIONとの関係

`question_id` は、

```text
QUESTION.question_id
```

を参照する外部キーとする。

```text
QUESTION
   │
   │ question_id
   ↓
AI_GENERATION_HISTORY
   │
   └── question_id
```

`question_id` には、AIによって生成された問題そのもののIDではなく、**AI生成の元となったマスタQUESTIONのID**を保存する。

これにより、

```text
USER
  │
  │ 1:N
  ↓
AI_GENERATION_HISTORY
  ↑
  │ N:1
QUESTION
```

という関係で、ユーザー・生成元問題ごとのAI生成履歴を管理する。

### `chinese_text`

`chinese_text` には、AIによって新しく生成された中国語文を保存する。

例えば、生成元QUESTIONが、

```text
QUESTION

question_id  = 10
chinese_text = 請問，洗手間在哪裡？
template     = 請問，{noun}在哪裡？
```

であり、AIが、

```text
請問，服務台在哪裡？
```

を生成した場合、

```text
AI_GENERATION_HISTORY

user_id      = 1
question_id  = 10
chinese_text = 請問，服務台在哪裡？
```

として保存する。

プレースホルダへ代入された語句だけではなく、**生成された中国語文全体を保存する。**

### `created_at`

`created_at` には、そのAI生成履歴を保存した日時を保持する。

AI問題生成時には `created_at` の新しい順に履歴を取得する。

概念的には、

```text
ORDER BY created_at DESC
```

として扱う。

また、保持件数が上限に達した場合には、`created_at` が最も古い履歴を削除対象とする。

### 履歴の保持件数

AI_GENERATION_HISTORYは、同一 `user_id`・同一 `question_id` の組み合わせについて、**直近5件まで**保持する。

```text
user_id = 1
question_id = 10

        created_at
            ↓

履歴1    最新
履歴2
履歴3
履歴4
履歴5    最古
```

新しいAI生成結果を保存する際、同一 `user_id`・`question_id` の履歴が5件未満であれば、そのままINSERTする。

すでに5件存在する場合は、最も古い履歴をDELETEしてから、新しい履歴をINSERTする。

```text
既存履歴 = 5件
      ↓
最も古い履歴をDELETE
      ↓
新しい履歴をINSERT
      ↓
5件を維持
```

これにより、AI問題生成時に参照する履歴を直近5件に限定する。

### AI問題生成時の利用

AI問題生成時には、

```text
user_id
+
question_id
```

を条件としてAI_GENERATION_HISTORYを検索する。

さらに、

```text
created_at DESC
```

で新しい順に並べ、最大5件を取得する。

取得した `chinese_text` を過去の生成結果としてAIへ渡す。

```text
USER
+
生成元QUESTION
      ↓
AI_GENERATION_HISTORY検索
      ↓
直近5件のchinese_text
      ↓
AIへ入力
      ↓
新しい問題を生成
```

これにより、AIが直近の生成結果を考慮し、同一または過度に類似した問題が短期間に繰り返し生成されることを抑制する。

### AI_GENERATED_QUESTIONとの違い

AI_GENERATION_HISTORYとAI_GENERATED_QUESTIONは、保存目的と保存タイミングが異なる。

| テーブル | 目的 | 保存タイミング |
|---|---|---|
| AI_GENERATION_HISTORY | 次回以降のAI生成時の重複抑制 | AI問題の生成が正常に完了した時点 |
| AI_GENERATED_QUESTION | ユーザーが学習したAI生成問題の保存 | HARD / GOOD / EASYのいずれかが選択された場合 |

そのため、AI_GENERATION_HISTORYには、

```text
evaluation
pinyin
zhuyin
japanese_text
```

など、学習問題として必要な情報は保持しない。

AI_GENERATION_HISTORYでは、次回のAI問題生成に必要となる、

```text
user_id
question_id
chinese_text
created_at
```

のみを保持する。

### 学習対象言語・文法・構造

AI_GENERATION_HISTORY自身には、

```text
language_variant
structure_id
```

を保持しない。

これらは生成元QUESTIONから取得する。

```text
AI_GENERATION_HISTORY
        │
        │ question_id
        ↓
     QUESTION
        │
        ├── language_variant
        │
        └── structure_id
                │
                ▼
            STRUCTURE
```

これにより、生成元QUESTIONと同じ情報をAI_GENERATION_HISTORYへ重複して保存することを避ける。

### 補足

- AI_GENERATION_HISTORYは大陸普通話・台湾華語でテーブルを分離しない。
- `user_id` にはUSERの内部IDである `USER.id` を保存する。
- `question_id` には生成元となった `QUESTION.question_id` を保存する。
- `chinese_text` にはAIによって生成された中国語文全体を保存する。
- AI問題の生成が正常に完了した時点で履歴を保存する。
- ユーザーによる理解度評価の有無は、AI_GENERATION_HISTORYの保存条件としない。
- 同一ユーザー・同一生成元問題について直近5件まで保持する。
- 6件目を保存する場合は最も古い履歴を削除する。
- AI問題生成時には生成日時の新しい順に最大5件取得する。
- AI_GENERATION_HISTORYはSTUDY_HISTORYとは分離して管理する。
- AI_GENERATION_HISTORYはAI_GENERATED_QUESTIONとは分離して管理する。
- AI_GENERATION_HISTORY自身には `language_variant` を保持しない。
- AI_GENERATION_HISTORY自身には `structure_id` を保持しない。

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
| AI_GENERATION_HISTORY | user_id | USER.id |
| AI_GENERATION_HISTORY | question_id | QUESTION.question_id |

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
| AI_GENERATION_HISTORY | id |

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

のように変更した場合でも、Favorite、StudyHistory、AiGeneratedQuestion、AiGenerationHistoryなどの関連データを変更する必要がない。

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

Favorite、StudyHistory、AiGeneratedQuestion、AiGenerationHistoryについては、QUESTIONとの関連から学習対象言語を判定できるため、それぞれに `language_variant` を重複して保持しない。

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
- AI生成時の変更可能範囲はQUESTIONの `template` によって定義する。
- AIによる変更を許可する部分は `template` 内のプレースホルダとして表現する。
- AIによる変更を許可しない部分は `template` の固定部分として保持する。
- 主語の種類など問題ごとに異なる生成制約は、可能な限りプレースホルダの種類によって表現する。
- QUESTIONには `subject_type` を保持しない。
- QUESTIONには `verb_variation` を保持しない。
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
- AI_GENERATION_HISTORYは大陸普通話・台湾華語で分離しない。
- AI_GENERATION_HISTORYはAI問題生成時の重複抑制に使用する。
- AI_GENERATION_HISTORYの主キーは `id` とする。
- AI_GENERATION_HISTORYの `user_id` は `USER.id` を参照する。
- AI_GENERATION_HISTORYの `question_id` は生成元となる `QUESTION.question_id` を参照する。
- AI_GENERATION_HISTORYにはAIによって生成された中国語文全体を `chinese_text` として保持する。
- AI_GENERATION_HISTORYには生成日時を `created_at` として保持する。
- AI_GENERATION_HISTORYは同一ユーザー・同一生成元問題について直近5件まで保持する。
- 6件目を保存する場合は、同一ユーザー・同一生成元問題について最も古い履歴を削除する。
- AI_GENERATION_HISTORYはAI問題が正常に生成された時点で更新する。
- AI_GENERATION_HISTORYの保存はユーザーの理解度評価とは独立して行う。
- AI_GENERATION_HISTORYはSTUDY_HISTORYとは分離して管理する。
- AI_GENERATION_HISTORYはAI_GENERATED_QUESTIONとは分離して管理する。
- AI_GENERATION_HISTORY自身には `language_variant` を保持しない。
- AI_GENERATION_HISTORY自身には `structure_id` を保持しない。
- AI_GENERATION_HISTORYの学習対象言語および文法・構造は生成元QUESTIONから判定する。

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

## 18.6 AI生成履歴テーブルの追加

**追加日：2026年9月4日**

AI問題生成を繰り返した際に、同一ユーザー・同一生成元問題から同一または過度に類似した問題が短期間に繰り返し生成されることを抑制するため、AI生成結果の履歴をDBへ保存する設計を追加した。

新たに、

```text
AI_GENERATION_HISTORY
```

テーブルを追加する。

テーブル構成は以下とする。

| カラム名 | 意味 | PK | FK | データ型 | NOT NULL | UNIQUE | 備考 |
|---|---|---|---|---|---|---|---|
| id | AI生成履歴ID | ○ | - | BIGINT | ○ | ○ | 自動採番 |
| user_id | 内部ユーザーID | - | ○ | BIGINT | ○ | - | USER.id参照 |
| question_id | 生成元問題ID | - | ○ | BIGINT | ○ | - | QUESTION.question_id参照 |
| chinese_text | 生成された中国語文 | - | - | TEXT | ○ | - | AIによって生成された中国語文 |
| created_at | 生成日時 | - | - | TIMESTAMP | ○ | - | AI生成履歴を保存した日時 |

AI_GENERATION_HISTORYは、

```text
USER
  │
  │ 1:N
  ↓
AI_GENERATION_HISTORY
  ↑
  │ N:1
QUESTION
```

という関係とする。

`user_id` によって生成を行ったユーザーを識別し、`question_id` によって生成元となったマスタ問題を識別する。

同一 `user_id`・同一 `question_id` の組み合わせについて、直近5件までの生成履歴を保持する。

```text
USER
+
QUESTION
    ↓
AI_GENERATION_HISTORY
    ↓
直近5件
```

すでに5件存在する状態で新しい生成結果を保存する場合は、最も古い履歴を削除してから新しい履歴を保存する。

AI問題生成時には、対象となる `user_id`・`question_id` の履歴を `created_at` の新しい順に最大5件取得し、過去の生成結果としてAIへ渡す。

これにより、直近に生成された語句や内容が短期間に繰り返されることを抑制する。

AI_GENERATION_HISTORYはAI_GENERATED_QUESTIONとは目的を分離する。

```text
AI_GENERATION_HISTORY
└── 次回以降のAI問題生成を制御するための履歴

AI_GENERATED_QUESTION
└── ユーザーの学習データとして保存するAI生成問題
```

AI_GENERATION_HISTORYはAI問題の生成が正常に完了した時点で更新する。

一方、AI_GENERATED_QUESTIONはユーザーが `HARD / GOOD / EASY` のいずれかの理解度を与えた場合に保存する。

そのため、AI_GENERATION_HISTORYの保存はユーザーの理解度評価とは独立して行う。

また、AI_GENERATION_HISTORY自身には `language_variant` および `structure_id` を保持しない。

これらの情報が必要な場合は、

```text
AI_GENERATION_HISTORY
        ↓
    question_id
        ↓
     QUESTION
        ├── language_variant
        └── structure_id
                ↓
            STRUCTURE
```

として生成元QUESTIONから取得する。

---

## 18.7 AI生成制御属性の廃止

**変更日：2026年9月4日**

AI問題生成では当初、QUESTIONに以下のカラムを保持し、問題ごとのAI生成内容を制御する設計としていた。

```text
subject_type
verb_variation
```

`subject_type` は、AIによって主語を変更する際に、

```text
PRONOUN
NON_PRONOUN
ALL
```

などの値によって生成可能な主語の種類を指定するために使用していた。

また、`verb_variation` は、

```text
FIXED
FLEXIBLE
```

などの値によって、動詞に関する変更可否を指定するために使用していた。

しかし、AI生成用テンプレートの設計を進めた結果、これらの生成制約はQUESTIONの独立したカラムとして保持するのではなく、テンプレートおよびプレースホルダによって表現する方針へ変更した。

主語については、例えば、

```text
{subject}
{subject_non_pronoun}
{subject_all}
```

のようにプレースホルダの種類を分けることで、その位置に生成可能な主語の種類を指定する。

これにより、

```text
subject_type
+
template
```

のように複数のカラムを組み合わせて生成条件を判断する必要がなくなり、テンプレート自体から生成条件を判断できる。

また、動詞の時間関係・アスペクト・モダリティなど、AI生成後も維持する必要がある要素については、原則としてテンプレートの固定部分として保持する。

例えば、

```text
我去過{noun}。
```

の場合、`過` はプレースホルダの外側に存在するため、AIによる変更対象とはしない。

AIによる変更を許可する部分のみをプレースホルダとして表現することで、`verb_variation` のような独立したカラムを使用せずに変更範囲を制御する。

そのため、QUESTIONから以下のカラムを削除する。

```text
subject_type
verb_variation
```

変更前：

```text
QUESTION
│
├── allow_ai_variation
├── template
├── subject_type
└── verb_variation
```

変更後：

```text
QUESTION
│
├── allow_ai_variation
└── template
```

変更後、QUESTIONが保持するAI生成に直接関係する情報は、基本的に以下とする。

```text
allow_ai_variation
template
```

`allow_ai_variation` によってAI生成対象であるかを判定し、`template` によってAIが変更可能な範囲および生成条件を定義する。

具体的な生成制約は、必要に応じてテンプレート内のプレースホルダの種類によって表現する。

これにより、AI生成に関する制御情報を複数のQUESTIONカラムへ分散させず、テンプレートを中心として管理する。

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
├── 1:N AI_GENERATED_QUESTION
└── 1:N AI_GENERATION_HISTORY


STRUCTURE
    │
    │ 1:N
    ↓
QUESTION
    │
    ├── 1:N FAVORITE
    ├── 1:N STUDY_HISTORY
    ├── 1:N AI_GENERATED_QUESTION
    │       └── source_question_id
    │
    └── 1:N AI_GENERATION_HISTORY
            └── question_id
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

AI_GENERATION_HISTORYは、

```text
USER
+
生成元QUESTION
+
過去にAIによって生成された中国語文
```

を管理する。

AI_GENERATION_HISTORYは、同一USER・同一生成元QUESTIONについて直近5件まで保持する。

保持された生成履歴は、次回のAI問題生成時に参照し、同じ語句や内容が短期間に繰り返し生成されることを抑制するために使用する。

これにより、

```text
ユーザー設定
問題データ
文法・構造
学習履歴
お気に入り
AI生成問題
AI生成履歴
```

をそれぞれ役割の異なるデータとして管理する。

---

## 18.8 AI生成用`_reusable`プレースホルダの廃止

**変更日：2026年9月4日**

当初のAI問題生成では、同一の生成元QUESTIONから問題を繰り返し生成した場合に、生成される語彙や表現のバリエーションが狭くなりやすいことを想定していた。

そのため、通常のプレースホルダとは別に、生成元問題で使用されている語彙の再利用を許可するプレースホルダとして、`_reusable`を付与したプレースホルダを使用する設計としていた。

例えば、

```text
{subject_reusable}
{noun_reusable}
```

のように定義し、通常のプレースホルダと`_reusable`プレースホルダを使い分けることで、生成元問題の語彙を再利用できるかどうかをプレースホルダ単位で制御する設計としていた。

しかし、その後AI_GENERATION_HISTORYを導入し、同一ユーザー・同一生成元QUESTIONについて直近のAI生成結果を保持する設計を追加した。

AI問題生成時には、

```text
user_id
+
question_id
```

を条件としてAI_GENERATION_HISTORYから直近5件の生成結果を取得し、それらの`chinese_text`を`generationHistory`としてAIへ渡す。

これにより、AIは直近に生成した問題を考慮しながら新しい問題を生成できるようになり、同一または過度に類似した問題の生成を抑制しながら、生成内容のバリエーションを広げることができる。

そのため、当初のように生成結果のバリエーションの狭さを生成元問題の語彙の再利用によって補う必要がなくなった。

以上の理由から、`_reusable`を付与したプレースホルダを廃止し、生成元問題の語彙を再利用できるかどうかをプレースホルダの種類によって個別に制御しない設計へ変更する。

例えば、

```text
{noun_reusable}
```

は廃止し、

```text
{noun}
```

へ統一する。

同様に、

```text
{subject_reusable}
```

も廃止する。

主語については、語彙の再利用可否ではなく、その位置に生成できる主語の種類を表現するため、以下のプレースホルダを使用する。

```text
{subject}
{subject_pronoun}
{subject_non_pronoun}
{subject_family}
```

それぞれの役割は以下のとおりとする。

- `{subject}`  
  代名詞または代名詞以外の自然な主語を生成できる。ただし、家族関係を表す語は生成しない。

- `{subject_pronoun}`  
  代名詞のみを生成できる。

- `{subject_non_pronoun}`  
  代名詞以外の自然な主語を生成できる。ただし、家族関係を表す語は生成しない。

- `{subject_family}`  
  代名詞、代名詞以外の主語、および家族関係を表す語を生成できる。

すべての主語プレースホルダについて、人名および愛称は生成しない。

変更後のAI生成用プレースホルダでは、`_reusable`の有無によって語句の再利用可否を表現せず、その位置に生成可能な内容の種類を表現する。

AI_GENERATION_HISTORYおよび`generationHistory`は、プレースホルダ単位で語句の再利用可否を制御するためではなく、直近の生成結果をAIへ提供し、同一または過度に類似した問題が短期間に繰り返し生成されることを抑制するために使用する。