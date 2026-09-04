# ER図

## 1. 概要

Chinese Output Forge で使用する主要エンティティと、そのリレーションを示す。

本システムでは、以下の主要データを管理する。

- User
- Question
- Structure
- Favorite
- StudyHistory
- AiGeneratedQuestion
- AiGenerationHistory

Userは、認証・権限に関する情報だけでなく、ユーザーごとの設定として以下の情報を保持する。

- 学習対象言語
- 表示する発音表記

学習対象言語として、以下の2種類を扱う。

- `MAINLAND`：大陸普通話
- `TAIWAN`：台湾華語（國語）

表示する発音表記として、以下の3種類を扱う。

- `PINYIN`：拼音
- `ZHUYIN`：注音
- `NONE`：表示なし

これらはユーザーごとの設定としてUserに保持し、DBへ永続化する。

これにより、ログアウトによってセッションが破棄された場合でも設定値は失われず、再ログイン時にはUserに保存されている設定値を引き続き使用できる。

学習対象言語のデフォルト値は `MAINLAND`、発音表記のデフォルト値は `PINYIN` とする。

また、大陸普通話と台湾華語は、単なる簡体字・繁体字の違いとして扱うのではなく、**それぞれ異なる問題データとして管理する**。

ただし、両者のデータ構造は共通しているため、

```text
SIMPLIFIED_QUESTION
TRADITIONAL_QUESTION
```

のようにテーブル自体を分離するのではなく、

```text
QUESTION
```

として共通管理する。

QUESTIONに学習対象言語を識別する `language_variant` を持たせ、

```text
MAINLAND
TAIWAN
```

によって大陸普通話と台湾華語を区別する。

Favorite、StudyHistory、AiGeneratedQuestion、AiGenerationHistoryについても、大陸普通話用・台湾華語用には分離せず共通テーブルとして管理する。

AI生成問題は通常のマスタ問題とは別エンティティとし、生成したユーザーにのみ紐づく個人用データとして管理する。

---

## 2. ER図

```mermaid
erDiagram

    USER ||--o{ FAVORITE : has
    QUESTION ||--o{ FAVORITE : has

    USER ||--o{ STUDY_HISTORY : has
    QUESTION ||--o{ STUDY_HISTORY : has

    USER ||--o{ AI_GENERATED_QUESTION : owns
    QUESTION ||--o{ AI_GENERATED_QUESTION : generates

    USER ||--o{ AI_GENERATION_HISTORY : has
    QUESTION ||--o{ AI_GENERATION_HISTORY : source

    STRUCTURE ||--o{ QUESTION : classifies


    USER {
        bigint id PK
        string login_id
        string password
        string role
        string language_variant
        string pronunciation_type
    }


    STRUCTURE {
        bigint structure_id PK
        string name
        text description_zh_cn
        text description_zh_tw
    }


    QUESTION {
        bigint question_id PK
        string language_variant
        text japanese_text
        text chinese_text
        text pinyin
        text zhuyin
        text alternative_answer
        text alternative_answer_pinyin
        text alternative_answer_zhuyin
        string condition
        bigint structure_id FK
        string difficulty
        boolean allow_ai_variation
        text template
        string subject_type
        string verb_variation
    }


    FAVORITE {
        bigint user_id PK, FK
        bigint question_id PK, FK
    }


    STUDY_HISTORY {
        bigint user_id PK, FK
        bigint question_id PK, FK
        string evaluation
        datetime first_studied_at
        datetime evaluation_updated_at
    }


    AI_GENERATED_QUESTION {
        bigint generated_question_id PK
        string user_id FK
        bigint source_question_id FK
        text japanese_text
        text chinese_text
        text pinyin
        text zhuyin
        string evaluation
        datetime created_at
        datetime evaluation_updated_at
    }


    AI_GENERATION_HISTORY {
        bigint id PK
        bigint user_id FK
        bigint question_id FK
        text chinese_text
        datetime created_at
    }
```

---

## 3. 全体のリレーション

本システムでは、Questionを大陸普通話・台湾華語で分離せず、共通のマスタ問題として管理する。

全体の関係は以下のようになる。

```text
USER
  │
  ├── FAVORITE
  │       │
  │       └── QUESTION
  │              │
  │              └── STRUCTURE
  │
  ├── STUDY_HISTORY
  │       │
  │       └── QUESTION
  │              │
  │              └── STRUCTURE
  │
  ├── AI_GENERATED_QUESTION
  │       │
  │       └── QUESTION
  │              │
  │              └── STRUCTURE
  │           （生成元）
  │
  └── AI_GENERATION_HISTORY
          │
          └── QUESTION
                 │
                 └── STRUCTURE
              （生成元）
```

USER自身は、ユーザーごとの設定として以下の属性を保持する。

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

`USER.language_variant` は、そのユーザーが現在どちらの中国語を学習対象としているかを表す。

一方、QUESTIONにも、

```text
language_variant
```

を持たせ、

```text
MAINLAND
TAIWAN
```

によって、その問題が大陸普通話・台湾華語のどちらに属するかを識別する。

したがって、

```text
USER.language_variant
```

と、

```text
QUESTION.language_variant
```

は同じ種類の値を扱うが、役割は異なる。

```text
USER.language_variant
└── ユーザーが現在学習対象としている言語

QUESTION.language_variant
└── その問題が対象としている言語
```

通常学習や復習などでは、`USER.language_variant` に対応する `QUESTION.language_variant` を持つ問題を取得する。

また、QUESTIONには、

```text
structure_id
```

を外部キーとして持たせ、対応するSTRUCTUREを参照する。

```text
QUESTION
    ↓
structure_id
    ↓
STRUCTURE
```

これにより、QUESTIONの文法・構造を判定する。

FavoriteやStudyHistoryなどに学習対象言語やStructureを重複して保持する必要はなく、関連するQuestionを参照することで判定できる。

AI_GENERATED_QUESTIONについても、生成元QUESTIONを経由して学習対象言語およびStructureを判定する。

AI_GENERATION_HISTORY自身には `language_variant` および `structure_id` を保持しない。

---

## 4. Userとユーザー設定

### 学習対象言語

USERは、ユーザーごとの学習対象言語として、

```text
language_variant
```

を保持する。

値は以下の2種類とする。

```text
MAINLAND
TAIWAN
```

`MAINLAND` は大陸普通話、`TAIWAN` は台湾華語（國語）を表す。

デフォルト値は、

```text
MAINLAND
```

とする。

ユーザーが学習対象言語を変更した場合は、USERの `language_variant` を更新する。

例えば、

```text
USER
--------------------------------
user_id           = 1
language_variant  = TAIWAN
--------------------------------
```

の場合、このユーザーの現在の学習対象言語は台湾華語となる。

学習対象言語はDBへ永続化するため、ログアウトしても設定値は失われない。

そのため、

```text
language_variant = TAIWAN

        ↓
    ログアウト
        ↓
    再ログイン

language_variant = TAIWAN
```

となり、前回の設定を引き続き使用できる。

---

### 発音表記

USERは、ユーザーが問題画面で表示する発音表記として、

```text
pronunciation_type
```

を保持する。

値は以下の3種類とする。

```text
PINYIN
ZHUYIN
NONE
```

それぞれ、

| 値 | 表示 |
| --- | --- |
| `PINYIN` | 拼音 |
| `ZHUYIN` | 注音 |
| `NONE` | 表示なし |

を表す。

デフォルト値は、

```text
PINYIN
```

とする。

発音表記についてもDBへ永続化する。

例えば、

```text
USER
--------------------------------
user_id             = 1
pronunciation_type  = ZHUYIN
--------------------------------
```

の場合、問題画面では注音を表示する。

ログアウト後も、

```text
pronunciation_type = ZHUYIN

        ↓
    ログアウト
        ↓
    再ログイン

pronunciation_type = ZHUYIN
```

となり、前回の設定を引き続き使用できる。

---

### 学習対象言語と発音表記の独立

`language_variant` と `pronunciation_type` は独立した設定として扱う。

そのため、

```text
language_variant    = MAINLAND
pronunciation_type  = ZHUYIN
```

や、

```text
language_variant    = TAIWAN
pronunciation_type  = PINYIN
```

といった組み合わせも可能とする。

学習対象言語を変更しても発音表記は自動的に変更しない。

同様に、発音表記を変更しても学習対象言語は変更しない。

---

## 5. Questionと学習対象言語

Questionは、大陸普通話・台湾華語双方のマスタ問題を管理する。

例えば以下のように保存する。

```text
QUESTION
------------------------------------------------
question_id       = 100
language_variant  = MAINLAND
chinese_text      = 大陸普通話の問題
------------------------------------------------

QUESTION
------------------------------------------------
question_id       = 101
language_variant  = TAIWAN
chinese_text      = 台湾華語の問題
------------------------------------------------
```

大陸普通話と台湾華語は同じQUESTIONテーブルに保存するが、**問題データとしては独立して扱う。**

したがって、

```text
MAINLANDの問題100
=
TAIWANの問題100
```

のような対応関係は持たせない。

問題IDはQUESTION全体で一意とする。

通常学習や復習では、USERに設定されている学習対象言語に応じてQUESTIONを絞り込む。

例えば、

```text
USER.language_variant = MAINLAND
```

の場合は、

```text
QUESTION.language_variant = MAINLAND
```

の問題を対象とする。

台湾華語の場合は、

```text
USER.language_variant = TAIWAN
```

に対して、

```text
QUESTION.language_variant = TAIWAN
```

の問題を対象とする。

概念的には、

```text
USER
│
└── language_variant = TAIWAN
            │
            ▼
QUESTION
│
└── language_variant = TAIWAN
```

となる。

---

## 6. QuestionとStructure

### リレーション

StructureとQuestionは1対多の関係とする。

```text
STRUCTURE
   │
   │ 1:N
   ↓
QUESTION
```

1つのStructureには複数のQuestionを関連付けることができる。

一方、1つのQuestionが参照するStructureは1つのみとする。

例えば、

```text
STRUCTURE
structure_id       = 1
name               = 可能補語
description_zh_cn  = 動作や結果が実現できるか、できないかを表す形式。
description_zh_tw  = 動作や結果が実現できるか、できないかを表す形式。
        │
        │ 1:N
        ↓
QUESTION
├── 这么多菜，我们吃不完。
├── 这个箱子太重了，我搬不动。
└── 他说得太快，我听不懂。
```

のような関係となる。

QUESTIONには、

```text
structure_id
```

を外部キーとして保持し、対応するSTRUCTUREを参照する。

```text
QUESTION
    ↓
structure_id
    ↓
STRUCTURE
├── name
├── description_zh_cn
└── description_zh_tw
```

すべてのQuestionには文法・構造上の分類が存在するため、QuestionからStructureへの関連は必須とする。

一方、StructureはQuestionがまだ登録されていない状態でもマスタデータとして存在できるものとする。

そのため、

```text
STRUCTURE 1 : 0..N QUESTION
QUESTION  N : 1 STRUCTURE
```

の関係とする。

---

### Structureの説明と学習対象言語

STRUCTUREは、

```text
description_zh_cn
description_zh_tw
```

の2種類の説明を保持する。

どちらを使用するかは、USERに設定されている `language_variant` によって決定する。

```text
USER.language_variant

├── MAINLAND
│   └── description_zh_cn
│
└── TAIWAN
    └── description_zh_tw
```

例えば、

```text
USER.language_variant = MAINLAND
```

の場合は、

```text
STRUCTURE.description_zh_cn
```

を使用する。

一方、

```text
USER.language_variant = TAIWAN
```

の場合は、

```text
STRUCTURE.description_zh_tw
```

を使用する。

この切り替えはサイト表記言語とは独立して扱う。

---

## 7. UserとFavorite

### リレーション

UserとFavoriteは1対多の関係とする。

```text
USER
  │
  │ 1:N
  ↓
FAVORITE
```

1人のユーザーは複数の問題をお気に入り登録できる。

Favoriteには、お気に入り登録された問題のみレコードを保持する。

### QuestionとFavorite

QuestionとFavoriteも1対多の関係とする。

```text
QUESTION
   │
   │ 1:N
   ↓
FAVORITE
```

1つのマスタ問題は複数ユーザーからお気に入り登録される可能性がある。

そのため、UserとQuestionはFavoriteを介して多対多の関係となる。

```text
USER
  │
  │ 1:N
  ↓
FAVORITE
  ↑
  │ N:1
QUESTION
```

Favoriteの主キーは、

```text
(user_id, question_id)
```

の複合主キーとする。

Favorite自身には `language_variant` を保持しない。

```text
FAVORITE
    ↓
QUESTION
    ↓
language_variant
```

と参照することで、そのお気に入りが大陸普通話・台湾華語のどちらに属するか判定する。

---

## 8. UserとStudyHistory

### リレーション

UserとStudyHistoryは1対多の関係とする。

```text
USER
  │
  │ 1:N
  ↓
STUDY_HISTORY
```

1人のユーザーは複数のマスタ問題について学習履歴を持つことができる。

### QuestionとStudyHistory

QuestionとStudyHistoryも1対多の関係とする。

```text
QUESTION
   │
   │ 1:N
   ↓
STUDY_HISTORY
```

1つのマスタ問題は複数ユーザーによって学習される。

そのため、UserとQuestionはStudyHistoryを介して多対多の関係となる。

```text
USER
  │
  │ 1:N
  ↓
STUDY_HISTORY
  ↑
  │ N:1
QUESTION
```

StudyHistoryの主キーは、

```text
(user_id, question_id)
```

の複合主キーとする。

同一ユーザー・同一問題について1レコードを保持し、

- `evaluation`
- `first_studied_at`
- `evaluation_updated_at`

を管理する。

StudyHistory自身には `language_variant` を保持しない。

```text
STUDY_HISTORY
      ↓
   QUESTION
      ↓
language_variant
```

と参照することで、その学習履歴が大陸普通話・台湾華語のどちらに属するか判定する。

---

## 9. AI生成問題の位置付け

AI生成問題はマスタ問題とは明確に分離する。

```text
QUESTION
   │
   │ AI生成
   ↓
AI_GENERATED_QUESTION
   ↑
   │ owns
 USER
```

QUESTIONには、

```text
language_variant
```

が存在するため、生成元となったQuestionからAI生成問題の学習対象言語を判定できる。

例えば、

```text
AI_GENERATED_QUESTION
        ↓
source_question_id
        ↓
QUESTION
        ↓
language_variant = TAIWAN
```

であれば、そのAI生成問題は台湾華語の問題として扱う。

AI生成問題は以下の条件を満たす場合のみDBへ保存する。

- AI生成学習中に問題が生成される
- ユーザーがその問題に対して `HARD / GOOD / EASY` のいずれかを選択する

理解度が与えられなかった問題は永続化しない。

また、AI生成問題をマスタ問題であるQUESTIONへ追加することはしない。

これにより、特定ユーザー向けに生成された問題が他ユーザーの通常学習へ混入することを防ぐ。

---

## 10. UserとAiGeneratedQuestion

UserとAiGeneratedQuestionは1対多の関係とする。

```text
USER
  │
  │ 1:N
  ↓
AI_GENERATED_QUESTION
```

1人のユーザーは複数のAI生成問題を所有できる。

AI生成問題には必ず `user_id` を保持し、そのユーザー専用の問題として管理する。

他のユーザーからは参照・出題しない。

---

## 11. QuestionとAiGeneratedQuestion

QuestionとAiGeneratedQuestionは1対多の関係とする。

```text
QUESTION
   │
   │ 1:N
   ↓
AI_GENERATED_QUESTION
```

1つのマスタ問題を基に、複数のAI生成問題が作成される可能性がある。

AiGeneratedQuestionは、

```text
source_question_id
```

によって生成元となったQuestionを参照する。

これにより、

- どのマスタ問題から生成されたか
- 大陸普通話・台湾華語のどちらの問題か
- どの文法・構造に分類される問題か

を判定できる。

---

## 12. AI生成問題とStudyHistoryの違い

マスタ問題では、問題そのものが全ユーザーで共有される。

そのため、

```text
USER
  ↓
STUDY_HISTORY
  ↓
QUESTION
```

という中間エンティティによって、ユーザーごとの理解度を管理する。

一方、AI生成問題は問題そのものが最初から特定ユーザーに所属する。

```text
USER
  ↓
AI_GENERATED_QUESTION
```

そのため、AI生成問題については別途StudyHistoryを作成せず、AI生成問題自身に、

- `evaluation`
- `created_at`
- `evaluation_updated_at`

を保持する。

---

### 12.1 AI生成履歴

AI_GENERATION_HISTORYは、AI問題生成時に同じ語彙や表現が短期間に繰り返し生成されることを抑制するため、過去に生成された中国語文を保持する。

AI_GENERATION_HISTORYはUSERおよび生成元QUESTIONの両方と関連付ける。

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

1人のユーザーは複数のAI生成履歴を持つことができる。

また、1つのQUESTIONから複数のAI生成履歴が作成される可能性がある。

AI_GENERATION_HISTORYは、

```text
user_id
question_id
```

によって、どのユーザーがどのマスタ問題から生成した履歴であるかを識別する。

例えば、

```text
USER
user_id = 1
    │
    ▼
AI_GENERATION_HISTORY
user_id     = 1
question_id = 10
chinese_text = 過去に生成された中国語文
    │
    ▼
QUESTION
question_id = 10
```

という関係となる。

同一USER・同一QUESTIONについて保持するAI生成履歴は、直近5件までとする。

```text
USER 1
  │
  └── QUESTION 10
        │
        ├── 履歴1（最新）
        ├── 履歴2
        ├── 履歴3
        ├── 履歴4
        └── 履歴5（最古）
```

新しいAI生成結果を保存する際、すでに同一USER・同一QUESTIONについて5件の履歴が存在する場合は、最も古い履歴を削除してから新しい履歴を保存する。

AI問題生成時には、対象となるUSER・QUESTIONに対応するAI_GENERATION_HISTORYを生成日時の新しい順に取得し、直近の生成結果をAIへ渡す。

AI_GENERATION_HISTORYは、AI_GENERATED_QUESTIONとは異なる目的を持つ。

```text
AI_GENERATED_QUESTION
└── ユーザーの学習データとして保存するAI生成問題

AI_GENERATION_HISTORY
└── 次回のAI問題生成時に参照する生成履歴
```

AI_GENERATED_QUESTIONはユーザーが生成問題に対して理解度を与えた場合に保存する。

一方、AI_GENERATION_HISTORYは理解度評価とは関係なく、AI問題が生成された時点で更新する。

また、AI_GENERATION_HISTORY自身には `language_variant` および `structure_id` を保持しない。

```text
AI_GENERATION_HISTORY
        │
        │ question_id
        ▼
     QUESTION
        │
        ├── language_variant
        │
        └── structure_id
                │
                ▼
            STRUCTURE
```

学習対象言語および文法・構造が必要な場合は、生成元QUESTIONを経由して取得する。

---

## 13. 大陸普通話・台湾華語の管理

大陸普通話と台湾華語は、**論理的には異なる問題データとして扱うが、物理的なテーブルは共通化する。**

旧設計では、

```text
SIMPLIFIED_QUESTION
TRADITIONAL_QUESTION

SIMPLIFIED_FAVORITE
TRADITIONAL_FAVORITE

SIMPLIFIED_STUDY_HISTORY
TRADITIONAL_STUDY_HISTORY

SIMPLIFIED_AI_GENERATED_QUESTION
TRADITIONAL_AI_GENERATED_QUESTION
```

のように、それぞれ別テーブルとして管理することを想定していた。

新しい設計では、これらを、

```text
QUESTION
FAVORITE
STUDY_HISTORY
AI_GENERATED_QUESTION
AI_GENERATION_HISTORY
```

へ統合する。

大陸普通話・台湾華語の識別は、QUESTIONの、

```text
language_variant
```

によって行う。

```text
QUESTION
   │
   ├── MAINLAND
   │     └── 大陸普通話
   │
   └── TAIWAN
         └── 台湾華語
```

この設計により、データ構造の重複を避けながら、大陸普通話・台湾華語の問題を独立して管理できる。

なお、USERにも `language_variant` を保持するが、これは問題自体の分類ではなく、**そのユーザーが現在どちらを学習対象としているかを表す設定値**である。

---

## 14. AiSettingについて

AI問題生成に関する共通設定としてAiSettingを想定するが、現時点ではDBテーブルとして管理することを確定していない。

候補となる情報は以下のとおり。

- 使用するAIモデル
- 共通プロンプト
- 大陸普通話向けLanguage Profile
- 台湾華語向けLanguage Profile
- その他AI生成共通設定

これらは、

- DB
- `application.yml`
- Properties
- JSON
- `resources/prompts/`

などで管理することが考えられる。

保存方式が未確定であるため、現時点のER図にはAiSettingを含めない。

---

## 15. 設計上の補足

- USERは大陸普通話・台湾華語で共通とする。
- USERはユーザーごとの学習対象言語を `language_variant` として保持する。
- USERの `language_variant` は `MAINLAND / TAIWAN` を想定する。
- USERの `language_variant` のデフォルト値は `MAINLAND` とする。
- USERはユーザーごとの発音表記を `pronunciation_type` として保持する。
- USERの `pronunciation_type` は `PINYIN / ZHUYIN / NONE` を想定する。
- USERの `pronunciation_type` のデフォルト値は `PINYIN` とする。
- USERの `language_variant` と `pronunciation_type` はDBへ永続化する。
- USERの設定はログアウト後も保持する。
- USERの `language_variant` と `pronunciation_type` は独立した設定として扱う。
- USERの `language_variant` とQUESTIONの `language_variant` は役割が異なる。
- 大陸普通話と台湾華語は異なる問題データとして扱う。
- QUESTIONは大陸普通話・台湾華語で分離しない。
- QUESTIONに `language_variant` を持たせる。
- QUESTIONの `language_variant` は `MAINLAND / TAIWAN` を想定する。
- QUESTIONの問題IDは全体で一意とする。
- 大陸普通話と台湾華語の問題IDに対応関係は持たせない。
- FAVORITEは大陸普通話・台湾華語で分離しない。
- FAVORITEはお気に入り登録された場合のみレコードを作成する。
- FAVORITEの学習対象言語はQUESTIONから判定する。
- STUDY_HISTORYは大陸普通話・台湾華語で分離しない。
- STUDY_HISTORYはユーザーとマスタ問題の組み合わせごとに1レコードを保持する。
- STUDY_HISTORYには最新の理解度を保持する。
- STUDY_HISTORYの学習対象言語はQUESTIONから判定する。
- AI_GENERATED_QUESTIONは大陸普通話・台湾華語で分離しない。
- AI生成問題は理解度を与えられた場合のみ保存する。
- AI生成問題には必ず所有ユーザーを設定する。
- AI生成問題は通常学習用のQUESTIONへ追加しない。
- AI生成問題は生成したユーザー自身の復習でのみ再利用する。
- AI生成問題は生成元となったQUESTIONを外部キーで保持する。
- AI生成問題の学習対象言語は生成元QUESTIONから判定する。
- AI生成問題には専用のStudyHistoryを設けず、問題自身に理解度を保持する。
- AiSettingは保存方式が確定するまでER図には含めない。
- AI_GENERATION_HISTORYは大陸普通話・台湾華語で分離しない。
- AI_GENERATION_HISTORYはAI問題生成時の重複抑制に使用する。
- AI_GENERATION_HISTORYには必ず生成したUSERを設定する。
- AI_GENERATION_HISTORYには必ず生成元QUESTIONを設定する。
- AI_GENERATION_HISTORYはUSERとQUESTIONの組み合わせごとに管理する。
- 同一USER・同一QUESTIONについて保持するAI生成履歴は直近5件までとする。
- AI_GENERATION_HISTORYには生成された中国語文を保持する。
- AI_GENERATION_HISTORYには生成日時を保持する。
- AI_GENERATION_HISTORYは理解度評価とは独立して、AI問題が生成された時点で更新する。
- AI_GENERATION_HISTORYはAI_GENERATED_QUESTIONとは分離して管理する。
- AI_GENERATION_HISTORYはSTUDY_HISTORYとは分離して管理する。
- AI_GENERATION_HISTORYの学習対象言語は生成元QUESTIONから判定する。
- AI_GENERATION_HISTORYのStructureは生成元QUESTIONから判定する。
- AI_GENERATION_HISTORY自身には `language_variant` および `structure_id` を重複して保持しない。
- 文法・構造は独立したSTRUCTUREとして管理する。
- STRUCTUREは文法・構造ID、文法・構造名、大陸普通話向けの説明、台湾華語向けの説明を保持する。
- 文法・構造そのものの分類は大陸普通話と台湾華語で共通して管理する。
- STRUCTUREの説明は、大陸普通話向けと台湾華語向けに分けて保持する。
- STRUCTUREの説明を表示する際は、USERに設定されている現在の学習対象言語に応じて使用する説明を切り替える。
- STRUCTUREの説明の切り替えはサイト表記言語とは独立して扱う。
- STRUCTUREとQUESTIONは1対多の関係とする。
- QUESTIONには `structure_id` を外部キーとして保持する。
- 1つのQUESTIONにつき1つのSTRUCTUREを設定する。
- QUESTIONからSTRUCTUREへの関連は必須とする。
- QUESTION自身には文法・構造名や説明を重複して保持しない。
- AI生成問題のStructureは生成元QUESTIONから判定し、AI_GENERATED_QUESTIONには `structure_id` を重複して保持しない。

---

## 16. 開発途中で追加した設計

### 16.1 拼音・注音への対応

**追加日：2026年8月15日**

当初のER図では、QUESTIONおよびAI_GENERATED_QUESTIONに中国語本文のみを保持し、発音表記は保持しない設計としていた。

その後、学習時に中国語の発音を確認できるようにするため、QUESTIONおよびAI_GENERATED_QUESTIONに以下の属性を追加する。

```text
pinyin
zhuyin
```

QUESTIONおよびAI_GENERATED_QUESTIONは、大陸普通話・台湾華語のどちらの問題についても拼音・注音の両方を保持する。

発音表記は `language_variant` と固定的に対応させない。

そのため、

```text
MAINLAND + PINYIN
MAINLAND + ZHUYIN
TAIWAN   + PINYIN
TAIWAN   + ZHUYIN
```

のすべての組み合わせで発音表記を利用できる。

発音表記の選択はユーザー側の設定として扱う。

当初、この設定をどのように保持するかは確定していなかったが、その後の設計変更により、USERの `pronunciation_type` としてDBへ永続化することとした。

QUESTIONおよびAI_GENERATED_QUESTIONとの新たなリレーションは発生しない。

---

### 16.2 別解の拼音・注音への対応

**追加日：2026年8月16日**

`16.1 拼音・注音への対応` では、QUESTIONの中国語模範解答に対応する発音情報として、

```text
pinyin
zhuyin
```

を追加した。

その後、別解が存在する問題についても発音を確認できるようにする必要があるため、QUESTIONに以下の属性を追加する。

```text
alternative_answer_pinyin
alternative_answer_zhuyin
```

これにより、QUESTIONの中国語解答に関する属性は以下の構成となる。

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

`alternative_answer_pinyin` は別解に対応する拼音、`alternative_answer_zhuyin` は別解に対応する注音を保持する。

今回の変更はQUESTIONが保持する属性の追加であり、新しいエンティティや外部キーは発生しない。

したがって、既存エンティティ間のリレーションは変更しない。

---

### 16.3 問題の文法・構造（structure）の追加

**追加日：2026年8月23日**

問題を文法・構造によって客観的に分類し、復習時の出題条件や問題検索に利用できるようにするため、当初QUESTIONに以下の属性を追加した。

```text
structure
```

`structure` は、模範解答となる中国語文を文法・構造上分析した際に、**その文章の根幹となる文法・構造を客観的に分類するための属性**とした。

例えば、

```text
QUESTION
│
├── chinese_text = 他笑着跟我说话。
├── structure    = 動態助詞（着）
└── condition    = 「着」を使う
```

の場合、

- `structure` は、中国語文そのものの根幹となる文法・構造を表す。
- `condition` は、開発者・出題者がその問題に設定した解答条件・ヒントを表す。

したがって、`structure` と `condition` は役割が異なる。

```text
structure
└── 中国語文そのものの根幹となる文法・構造
    └── 客観的な分類

condition
└── その問題をどのように解答させるか
    └── 開発者・出題者が設定する主観的な条件・ヒント
```

1つの中国語文に複数の文法的要素が含まれている場合でも、文章の根幹となる文法・構造を1つ選択する。

一方、`condition` は特定の解答条件を必要としない問題も存在するため、NULLを許可する。

その後、`structure` は文字列属性ではなく独立したSTRUCTUREとして管理する設計へ変更した。

---

### 16.4 Structureのマスタテーブル化

**設計変更日：2026年8月24日**

`16.3 問題の文法・構造（structure）の追加` では、QUESTION自身が文法・構造名を文字列として保持する設計としていた。

その後、文法・構造名だけでなく、その文法・構造についての説明を管理する必要が生じた。

例えば、

```text
可能補語

動作や結果が実現できるか、できないかを表す形式。
```

のような情報である。

これにより、`structure` は単なるQUESTIONの分類用文字列ではなく、

```text
文法・構造
├── 文法・構造を識別するID
├── 文法・構造名
├── 大陸普通話向けの説明
└── 台湾華語向けの説明
```

という独立した情報を持つデータとして扱う方が適切であると判断した。

そこで、QUESTIONの `structure` 文字列属性として管理する設計を変更し、文法・構造を独立したSTRUCTUREとして管理する。

#### STRUCTUREの追加

STRUCTUREは以下の情報を保持する。

```text
STRUCTURE
│
├── structure_id
├── name
├── description_zh_cn
└── description_zh_tw
```

`structure_id` は文法・構造を識別する主キーとする。

`name` には、

```text
可能補語
把構文
比較構文
条件複文
譲歩複文
```

などの文法・構造名を保持する。

文法・構造そのものの分類は、大陸普通話と台湾華語で共通して管理する。

一方、説明内で使用する中国語については、簡体字・繁体字の違いや、大陸普通話・台湾華語で一般的に使用される表現の違いが生じる場合がある。

そのため、

```text
description_zh_cn
└── 大陸普通話向けの説明

description_zh_tw
└── 台湾華語向けの説明
```

として、2種類の説明を保持する。

どちらの説明を表示するかは、USERに保存されている現在の学習対象言語によって決定する。

```text
USER.language_variant

├── MAINLAND
│   └── description_zh_cn
│
└── TAIWAN
    └── description_zh_tw
```

この切り替えはサイト表記言語とは独立して扱う。

#### QUESTIONとのリレーション

これまでQUESTIONが直接保持していた、

```text
structure
```

は廃止する。

代わりにQUESTIONは、

```text
structure_id
```

を外部キーとして保持し、STRUCTUREを参照する。

```text
STRUCTURE
    │
    │ 1:N
    ↓
QUESTION
```

1つのSTRUCTUREには複数のQUESTIONを関連付けることができる。

一方、1つのQUESTIONが参照するSTRUCTUREは1つのみとする。

すべてのQUESTIONには分類対象となる文法・構造が存在するため、QUESTIONからSTRUCTUREへの関連は必須とする。

したがって、QUESTIONの `structure_id` はNULLを許可しない。

一方、STRUCTUREは将来使用する文法・構造を事前に登録することもできるため、QUESTIONが1件も関連付けられていないSTRUCTUREの存在は許可する。

```text
STRUCTURE 1 : 0..N QUESTION
QUESTION N : 1 STRUCTURE
```

#### conditionとの関係

STRUCTUREを独立したエンティティとして管理するようになっても、`condition` との役割の違いは変更しない。

```text
STRUCTURE
└── 中国語文そのものの根幹となる文法・構造
    └── 客観的な分類

condition
└── その問題をどのように解答させるか
    └── 開発者・出題者が設定する主観的な条件・ヒント
```

STRUCTUREとの関連はすべてのQUESTIONに必須とするが、`condition` は特定の解答条件を必要とする問題にのみ設定するため、NULLを許可する。

#### AI_GENERATED_QUESTIONとの関係

AI_GENERATED_QUESTIONには、STRUCTUREとの外部キーを追加しない。

AI生成問題は、

```text
source_question_id
```

によって生成元QUESTIONを参照しているため、

```text
AI_GENERATED_QUESTION
        │
        │ source_question_id
        ↓
     QUESTION
        │
        │ structure_id
        ↓
    STRUCTURE
```

という関係から文法・構造を取得する。

AI生成問題は生成元QUESTIONの文法・構造を維持したバリエーションとして生成するため、原則として生成元QUESTIONと同じSTRUCTUREに分類される。

そのため、AI_GENERATED_QUESTION自身に `structure_id` を重複して保持しない。

---

### 16.5 ユーザー設定の永続化

**設計変更日：2026年8月27日**

これまで、学習対象言語および発音表記は、アプリケーション利用中の現在の設定として扱っていた。

しかし、設定値をセッション上でのみ管理する場合、ログアウトによってセッションが破棄されると設定値も失われる。

その結果、再ログイン時には、

```text
学習対象言語 = MAINLAND
発音表記     = PINYIN
```

というデフォルト値へ戻ってしまう。

そこで、USERに以下の属性を追加する。

```text
USER
│
├── language_variant
└── pronunciation_type
```

`language_variant` はユーザーが現在学習対象としている言語を保持する。

```text
MAINLAND
TAIWAN
```

の2種類を扱い、デフォルト値は `MAINLAND` とする。

`pronunciation_type` はユーザーが表示する発音表記を保持する。

```text
PINYIN
ZHUYIN
NONE
```

の3種類を扱い、デフォルト値は `PINYIN` とする。

これらの設定値をUSERに保持してDBへ永続化することで、ログアウト後も設定を維持できる。

```text
USER
├── language_variant   = TAIWAN
└── pronunciation_type = ZHUYIN

        ↓
    ログアウト
        ↓
    再ログイン

USER
├── language_variant   = TAIWAN
└── pronunciation_type = ZHUYIN
```

なお、この変更はUSERが保持する属性の追加であり、新しいエンティティは追加しない。

そのため、

```text
USER ── LANGUAGE_VARIANT
USER ── PRONUNCIATION_TYPE
```

のような新しいリレーションは発生しない。

また、USERの `language_variant` とQUESTIONの `language_variant` は役割が異なる。

```text
USER.language_variant
└── ユーザーが現在学習対象としている言語

QUESTION.language_variant
└── その問題が対象としている言語
```

通常学習や復習などでは、USERに保存されている `language_variant` を基準として、対応する `QUESTION.language_variant` の問題を取得する。

発音表示では、USERに保存されている `pronunciation_type` を基準として、QUESTIONまたはAI_GENERATED_QUESTIONが保持する拼音・注音のどちらを表示するか、または発音表記を表示しないかを決定する。

---

### 16.6 AI生成履歴の追加

**設計変更日：2026年9月4日**

AI問題生成では、同一の生成元QUESTIONから繰り返し問題を生成した場合、過去に生成された問題と同じ語句や似た内容が短期間に繰り返し生成されることがある。

AIは、それまでに生成した問題を自動的に把握しているわけではないため、過去の生成結果を次回の生成時に参照できる仕組みが必要となる。

そこで、ユーザーごと・生成元QUESTIONごとに直近のAI生成結果を保持するため、新たに `AI_GENERATION_HISTORY` を追加する。

#### AI_GENERATION_HISTORYの追加

AI_GENERATION_HISTORYは以下の情報を保持する。

```text
AI_GENERATION_HISTORY
│
├── id
├── user_id
├── question_id
├── chinese_text
└── created_at
```

各属性の役割は以下のとおりとする。

| 属性 | 役割 |
| --- | --- |
| `id` | AI生成履歴を一意に識別するID |
| `user_id` | AI問題を生成したUSER |
| `question_id` | 生成元となったQUESTION |
| `chinese_text` | AIによって生成された中国語文 |
| `created_at` | AI生成履歴が作成された日時 |

`id` をAI_GENERATION_HISTORYの主キーとする。

`user_id` はUSERを参照する外部キーとする。

`question_id` は生成元となったQUESTIONを参照する外部キーとする。

#### USERとのリレーション

USERとAI_GENERATION_HISTORYは1対多の関係とする。

```text
USER
  │
  │ 1:N
  ↓
AI_GENERATION_HISTORY
```

1人のユーザーは、複数のAI生成履歴を持つことができる。

AI生成履歴はユーザーごとに独立して管理する。

そのため、同じQUESTIONからAI問題を生成した場合でも、異なるユーザーの生成履歴を共有しない。

例えば、

```text
USER A
  │
  └── QUESTION 10
        └── USER Aの生成履歴

USER B
  │
  └── QUESTION 10
        └── USER Bの生成履歴
```

のように、それぞれ独立した履歴として扱う。

#### QUESTIONとのリレーション

QUESTIONとAI_GENERATION_HISTORYも1対多の関係とする。

```text
QUESTION
   │
   │ 1:N
   ↓
AI_GENERATION_HISTORY
```

1つのQUESTIONを生成元として、複数のAI生成履歴が作成される可能性がある。

AI_GENERATION_HISTORYは `question_id` によって生成元となったQUESTIONを参照する。

したがって、USERとQUESTIONはAI_GENERATION_HISTORYを介して以下の関係となる。

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

AI生成履歴は、

```text
USER × QUESTION
```

の組み合わせごとに管理する。

#### 直近5件の生成履歴を保持する

AI_GENERATION_HISTORYは、同一USER・同一QUESTIONについて無制限に保存するのではなく、**直近5件まで**保持する。

例えば、

```text
USER 1
  │
  └── QUESTION 10
        │
        ├── 履歴1  ← 最新
        ├── 履歴2
        ├── 履歴3
        ├── 履歴4
        └── 履歴5  ← 最古
```

という状態とする。

新しいAI問題が生成された際、同一USER・同一QUESTIONについて保存されている履歴が5件未満の場合は、そのまま新しい履歴を追加する。

すでに5件の履歴が存在する場合は、`created_at` が最も古い履歴を削除したうえで、新しい履歴を追加する。

```text
生成前

履歴1  ← 最新
履歴2
履歴3
履歴4
履歴5  ← 最古

        ↓ 新しい問題を生成

履歴5を削除

        ↓

新しい履歴 ← 最新
履歴1
履歴2
履歴3
履歴4       ← 最古
```

これにより、同一USER・同一QUESTIONについて常に直近5件の生成結果を参照できるようにする。

#### AI問題生成時の利用

AI問題を生成する際には、対象となるUSERと生成元QUESTIONに対応するAI_GENERATION_HISTORYを、`created_at` の新しい順に最大5件取得する。

```text
USER
  │
  └── QUESTION
        │
        └── AI_GENERATION_HISTORY
              │
              ├── 直近の生成結果
              ├── 2件前の生成結果
              ├── 3件前の生成結果
              ├── 4件前の生成結果
              └── 5件前の生成結果
                        │
                        ▼
                       AI
                        │
                        ▼
                  新しい問題を生成
```

取得した過去の中国語文をAIへ渡し、直近の生成結果を考慮したうえで新しい問題を生成させる。

これにより、同一の生成元QUESTIONから問題を繰り返し生成した場合でも、直近に使用された語句や内容が短期間に繰り返し生成されることを抑制する。

ただし、AI_GENERATION_HISTORYは過去に使用した語句を永久に使用禁止とするためのものではない。

保持する履歴を直近5件に限定することで、短期間の重複を抑制しつつ、過去に使用された自然な語彙や表現を将来的に再び使用できるものとする。

#### AI_GENERATED_QUESTIONとの違い

AI_GENERATION_HISTORYとAI_GENERATED_QUESTIONは、どちらもAIによる生成結果に関係するが、目的が異なる。

```text
AI_GENERATED_QUESTION
└── ユーザーが学習したAI生成問題を保存する

AI_GENERATION_HISTORY
└── 次回のAI問題生成時に過去の生成結果を参照する
```

AI_GENERATED_QUESTIONは、AIによって問題が生成されただけでは永続化せず、ユーザーがその問題に対して `HARD / GOOD / EASY` のいずれかの理解度を与えた場合に保存する。

一方、AI_GENERATION_HISTORYはユーザーの理解度評価を管理するためのものではない。

AI問題の生成が正常に完了した時点で生成された中国語文を記録し、次回以降のAI問題生成に利用する。

したがって、同じAI生成結果について、

```text
AI問題を生成
      │
      ├── AI_GENERATION_HISTORY
      │     └── 生成時点で記録
      │
      └── AI_GENERATED_QUESTION
            └── HARD / GOOD / EASYが
                選択された場合に保存
```

という異なるタイミング・目的でデータを管理する。

#### STUDY_HISTORYとの違い

STUDY_HISTORYは、USERとマスタQUESTIONの組み合わせについて、ユーザーの学習状態や最新の理解度を管理するためのエンティティである。

一方、AI_GENERATION_HISTORYは学習状態や理解度を管理しない。

```text
STUDY_HISTORY
└── USER × QUESTIONの学習状態・理解度を管理

AI_GENERATION_HISTORY
└── USER × QUESTIONの直近のAI生成結果を管理
```

そのため、AI_GENERATION_HISTORYはSTUDY_HISTORYとは分離して管理する。

また、STUDY_HISTORYは同一USER・同一QUESTIONについて1レコードを保持するのに対し、AI_GENERATION_HISTORYは同一USER・同一QUESTIONについて直近5件までのレコードを保持する。

#### 学習対象言語・Structureとの関係

AI_GENERATION_HISTORY自身には、`language_variant` および `structure_id` を保持しない。

AI_GENERATION_HISTORYは生成元QUESTIONを参照しているため、

```text
AI_GENERATION_HISTORY
        │
        │ question_id
        ▼
     QUESTION
        │
        ├── language_variant
        │
        └── structure_id
                │
                ▼
            STRUCTURE
```

という関係から、生成元問題の学習対象言語および文法・構造を判定できる。

そのため、AI_GENERATION_HISTORYにこれらの情報を重複して保持しない。

今回の変更により、以下のリレーションをER図へ追加する。

```text
USER 1 : 0..N AI_GENERATION_HISTORY
QUESTION 1 : 0..N AI_GENERATION_HISTORY
```

AI_GENERATION_HISTORYはAI問題生成のための内部的な生成履歴として扱い、通常の学習履歴およびユーザー専用のAI生成問題とは明確に分離して管理する。