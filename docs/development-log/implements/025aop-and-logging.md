# 025 AOP・ログ

## 概要

Controller・Serviceの各メソッドで共通して利用できるログ出力を、AOPを利用して一元管理する仕組みを追加した。

これまでは業務処理ごとに必要なログをControllerやServiceへ個別に記述していたが、メソッドの開始・正常終了のようにすべてのクラスで同じ形式となるログについては、個別に記述する必要がない。

そこで`LogAspect`を追加し、Controller・Serviceのメソッド実行前後にDEBUGログを出力するようにした。

また、AOPの導入にあわせて既存の業務固有ログも全体的に見直した。

主に以下を整理した。

* 共通ログと業務固有ログの役割分担
* INFOとDEBUGの使い分け
* `userId`と`loginId`の使い分け
* EntityやForm全体をログへ渡していた処理の修正
* ServiceとControllerで重複していたログの削除
* ログを使用しなくなったクラスからの`@Slf4j`削除
* AI生成処理の`System.out.println()`をDEBUGログへ変更

あわせて、既に使用していた`@Transactional`と`@PreAuthorize`についても、SpringのAOPを利用した機能であることを確認した。

---

## 1. Controller・Serviceの共通ログをAOP化

Controller・Serviceの各メソッドについて、実行開始と正常終了を共通して記録できるように`LogAspect`を追加した。

```java
@Aspect
@Component
@Slf4j
public class LogAspect {

    @Pointcut("@within(org.springframework.stereotype.Controller)")
    public void controllerMethods() {}

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceMethods() {}

    @Before("controllerMethods()")
    public void startControllerLog(JoinPoint jp) {
        log.debug("Controller開始: {}", jp.getSignature());
    }

    @Before("serviceMethods()")
    public void startServiceLog(JoinPoint jp) {
        log.debug("Service開始: {}", jp.getSignature());
    }

    @AfterReturning("controllerMethods()")
    public void endControllerLog(JoinPoint jp) {
        log.debug("Controller正常終了: {}", jp.getSignature());
    }

    @AfterReturning("serviceMethods()")
    public void endServiceLog(JoinPoint jp) {
        log.debug("Service正常終了: {}", jp.getSignature());
    }
}
```

`@Pointcut`によってControllerとServiceをそれぞれ対象として定義し、`@Before`でメソッド実行前、`@AfterReturning`で正常終了後にログを出力する。

終了ログには`@After`ではなく`@AfterReturning`を使用した。

これにより、例外が発生して処理が途中で終了した場合には「正常終了」というログが出力されず、実際に正常終了したメソッドのみを区別できる。

また、`JoinPoint#getSignature()`を利用することで、個々のController・Serviceにメソッド名を直接記述しなくても、実行されたメソッドをログから確認できる。

例えば1回のリクエストについて、

```text
Controller開始: PracticeController.getPracticeQuestion(...)
Service開始: PracticeService.getPracticeQuestions(...)
Service正常終了: PracticeService.getPracticeQuestions(...)
Controller正常終了: PracticeController.getPracticeQuestion(...)
```

のような流れを確認できる。

これらは業務上のイベントではなく、主に開発時に処理の流れを追跡するための情報である。

そのためINFOではなくDEBUGとし、通常運用時に共通ログが大量に出力されないようにした。

---

## 2. 共通ログと業務固有ログを分離

今回AOPへ移したのは、Controller・Serviceのメソッド開始・正常終了という、どのクラスでも同じ形式で記録できるログである。

一方、

```text
問題を登録した
ユーザーを凍結した
パスワードを変更した
AI生成問題を保存した
理解度を変更した
```

といったログは、それぞれ必要な情報が異なる。

例えば問題削除では`questionId`が必要だが、ユーザー凍結では`userId`と`loginId`が必要になる。

このような業務固有ログまでAOPへ集約すると、メソッドごとに異なる引数やEntityの内容をAOP側で判定する必要があり、かえって処理が複雑になる。

そのため、

```text
共通的な処理の流れ
    → AOP

業務処理の結果・変更内容
    → 各Service
```

という役割分担とした。

Controllerについては、HTTPリクエストの受付、入力値の受け取り、Session管理、画面遷移などを担当し、実際の状態変更が成功したことを表すログは原則としてService側へ置く。

---

## 3. AI生成処理の処理時間出力をDEBUGログへ変更

`AiPracticeService.generateQuestions()`では、AI生成処理の性能を確認するため、処理を複数の工程に分けて時間を計測している。

計測対象は、

```text
AIへ送信する入力の作成
AI APIによる問題生成
DTO変換・AI生成履歴更新
全工程
```

である。

これまでは計測結果を、

```java
System.out.println(
        "AI API: "
        + (apiCompletedTime - inputCompletedTime)
        + " ms");
```

のようにコンソールへ直接出力していた。

025ではログ出力を整理するため、これらをSLF4JによるDEBUGログへ変更した。

```java
log.debug(
        "AI生成 入力作成時間: {} ms",
        inputCompletedTime - startTime);

log.debug(
        "AI生成 API処理時間: {} ms",
        apiCompletedTime - inputCompletedTime);

log.debug(
        "AI生成 DTO変換・履歴更新時間: {} ms",
        conversionCompletedTime - apiCompletedTime);

log.debug(
        "AI生成 合計処理時間: {} ms",
        conversionCompletedTime - startTime);
```

メソッド全体の処理時間だけであれば`@Around`を使用してAOPへ移すことも可能だが、現在処理時間を計測しているのは`generateQuestions()`のみである。

また、入力作成、AI API、DTO変換といった区間別の計測は`AiPracticeService`固有の処理である。

そのため、現時点では無理にAOPへ共通化せず、`AiPracticeService`固有のDEBUGログとして残した。

---

## 4. 既存の業務固有ログを全体的に整理

AOPによる共通ログの追加後、既存のController・Serviceを確認し、業務固有ログの追加・削除・ログレベル変更を行った。

基本的な基準として、後から業務上の重要な状態変更を確認する必要があるものをINFOとした。

主なINFOログは以下である。

```text
問題の登録・更新・削除
文法・構造の登録・更新・削除
ユーザー登録
ユーザー凍結・凍結解除
ログインID変更
パスワード変更
退会・ユーザー削除
AI生成問題の正式保存
ユーザー所有問題の削除
```

一方、通常利用によって頻繁に発生し、主に処理内容を確認するために使用するものはDEBUGとした。

```text
AI生成履歴の保存
上限超過によるAI生成履歴の自動削除
理解度変更
お気に入り追加・解除
学習対象言語変更
表示発音記号変更
AI生成処理時間
```

例えば`EvaluationService.updateEvaluation()`は、以前INFOとしていた。

```java
log.info(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

理解度は学習中に問題ごとに何度も変更されるため、INFOでは通常の学習だけで大量のログが発生する。

そのため、

```java
log.debug(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

へ変更した。

`FavoriteService`の追加・解除、`AiGenerationHistoryService`の履歴保存・自動削除なども同じ基準でDEBUGへ変更した。

---

## 5. 不足していた重要な業務ログを追加

既存ログを確認した結果、重要な状態変更であるにもかかわらずログが存在しない処理についてはINFOログを追加した。

例えば`AdminStructureService`では、文法・構造の登録・更新・削除についてログが存在しなかったため、それぞれ追加した。

```java
log.info(
        "文法・構造登録完了 structureId={}, name={}",
        savedStructure.getStructureId(),
        savedStructure.getName());
```

```java
log.info(
        "文法・構造更新完了 structureId={}, name={}",
        structureId,
        structure.getName());
```

```java
log.info(
        "文法・構造削除完了 structureId={}, name={}",
        structureId,
        targetStructure.getName());
```

また、`AdminUserService`のユーザー凍結・凍結解除についても、アカウント状態を変更する重要な管理操作であるためINFOログを追加した。

AI生成問題についても、一時的にAIが問題を生成しただけではINFOを記録しないが、ユーザーが生成結果を正式に`question`テーブルへ保存した場合は永続的な状態変更となる。

そのため`AiPracticeService.saveGeneratedQuestion()`へ、

```java
log.info(
        "AI生成問題保存完了 userId={}, questionId={}, sourceQuestionId={}",
        user.getId(),
        savedQuestionResult.getQuestionId(),
        sourceQuestion.getQuestionId());
```

を追加した。

---

## 6. Entity・Form全体をログへ出力する処理を修正

`AdminQuestionService.updateOneQuestion()`では、変更前後の`Question` EntityそのものをINFOログへ渡していた。

```java
log.info("問題更新前 {}", question);

applyQuestionForm(question, form);

questionRepository.save(question);

log.info("問題更新後 {}", question);
```

SLF4Jの`{}`へEntityを渡した場合、実際の出力内容はEntityの`toString()`に依存する。

そのため、Entityのフィールドや`toString()`が将来変更された場合、ログ側を変更していなくても出力内容が変化する可能性がある。

また、問題本文、発音、テンプレートなど、INFOログとして必要のない情報までまとめて出力される可能性もある。

今回必要なのは「どの問題が更新されたか」という記録なので、明示的に`questionId`のみを残すように変更した。

```java
log.info(
        "問題更新完了 questionId={}",
        questionId);
```

これにより問題管理について、

```text
問題登録完了 questionId=123
問題更新完了 questionId=123
問題削除完了 questionId=123
```

という統一した形式にした。

`AdminQuestionController`に存在していた、

```java
log.info("問題登録 {}", form);
log.info("問題更新 {}", form);
```

についても、Form全体をログへ渡していることに加え、Service側の成功ログと重複するため削除した。

---

## 7. ログに使用するuserIdとloginIdを整理

既存ログでは、ユーザーを識別する情報として`userId`を使用している箇所と`loginId`を使用している箇所が混在していた。

今回、用途を次のように整理した。

```text
userId
→ DB内部でユーザーを安定して識別するID

loginId
→ ユーザーがログインに使用し、
  人間がログを確認するときにも識別しやすいID
```

AI生成履歴、評価、お気に入りなど、内部データ同士の関係を追跡する場合は`userId`を使用する。

例えば、

```java
log.debug(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

のように記録する。

一方、ユーザー登録、管理者による凍結、ログインID変更、パスワード変更、退会など、アカウントそのものに関する重要な処理では`userId`と`loginId`の両方を記録する。

特にログインID変更では、変更後に`loginId`が変わるため、

```java
log.info(
        "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
        user.getId(),
        currentLoginId,
        newLoginId);
```

として、内部IDと変更前後のloginIdを残すようにした。

パスワード変更については、

```java
log.info(
        "パスワード変更完了 userId={}, loginId={}",
        user.getId(),
        user.getLoginId());
```

とするが、現在のパスワード、新しいパスワード、ハッシュ化されたパスワードについては一切ログへ出力しない。

---

## 8. 削除処理のログを整理

ユーザー退会や管理者によるユーザー削除では、対象となる`Users`自体が削除される。

そのため、削除前にログへ必要な識別情報を取得する形にした。

退会では、

```java
Long userId = user.getId();
String deletedLoginId = user.getLoginId();

deleteUserData(user);

log.info(
        "退会完了 userId={}, loginId={}",
        userId,
        deletedLoginId);
```

とした。

管理者によるユーザー削除についても同様に、削除前に`loginId`を取得してから、

```java
log.info(
        "ユーザー削除完了 userId={}, loginId={}",
        userId,
        deletedLoginId);
```

を記録する。

また、ユーザー自身が保存したAI生成由来問題を削除する`UserQuestionService.deleteOwnedQuestion()`については、

```java
log.info(
        "所有問題削除完了 userId={}, questionId={}",
        userId,
        questionId);
```

として、どのユーザーが所有するどの問題を削除したのかを追跡できるようにした。

---

## 9. Controllerの重複ログを削除

状態変更に関するログは、実際に処理が成功したService側へ記録する方針とした。

そのため、Controllerに存在していた以下のログを削除した。

```java
log.info("問題登録 {}", form);

log.info("問題更新 {}", form);

log.debug(
        "ユーザー登録開始 loginId={}",
        form.getLoginId());

log.debug(
        "パスワード変更開始 loginId={}",
        loginUser.getUsername());
```

例えばユーザー登録では、

```text
SignupController
    ↓
SignupService.signup()
    ↓
DBへユーザー登録
    ↓
INFO ユーザー登録完了
```

という形になり、成功した業務イベントはService側のINFOログだけで確認できる。

ControllerやServiceへの入口と正常終了についてはAOPのDEBUGログで確認できるため、Controllerで「○○開始」というログを個別に記録する必要もなくなった。

ログを使用しなくなった`AdminQuestionController`、`SignupController`、`UserProfileController`などからは`@Slf4j`も削除した。

---

## 10. ログが存在しないController・Serviceも確認

今回の見直しでは、既に`@Slf4j`が付いているクラスだけではなく、ログを持っていないController・Serviceについても確認した。

検索、件数取得、ページング、画面表示、Session操作など、状態変更を伴わない処理については、業務固有ログを追加しないこととした。

例えば、

```text
PaginationService
PracticeService
ReviewService
AiPromptService
AiPronunciationService
UserDetailsServiceImpl

AdminUserController
AiPracticeController
FavoriteController
LanguageVariantController
PronunciationTypeController
PracticeController
ReviewController
UserQuestionController
```

などについては、新しい業務固有ログを追加する必要はないと判断した。

これらについてもメソッド開始・正常終了はAOPによってDEBUGログへ記録されるため、ログを追加する目的だけで`@Slf4j`を付けることはしない。

---

## 11. 既存のAOP利用箇所を確認

今回独自に`LogAspect`を実装したほか、既存コードで使用している`@Transactional`によるトランザクション管理も、SpringのAOPを利用した仕組みであることを確認した。

`@Transactional`を付けたメソッドでは、SpringのProxyを介してメソッドの前後にトランザクション開始、COMMIT、ROLLBACKなどの処理が追加される。

そのため、今回新しくトランザクション管理の仕組みを実装するのではなく、既存の`@Transactional`をそのまま利用する。

また、`PracticeController.getPracticeCount()`で既に使用している、

```java
@PreAuthorize("isAuthenticated()")
```

についても、メソッド実行前にSpring Securityが認可条件を確認する仕組みにAOPが利用されている。

今回はAOP・ログの整理を目的としているため、Spring Security全体の見直しや認証・認可ログについてはここでは追加せず、後続のセキュリティ実装で扱うこととした。

---

## 12. 変更後のログ方針

今回の変更により、アプリケーション全体のログの役割を以下のように整理した。

```text
ログ
│
├── 共通ログ
│   └── AOP / DEBUG
│       ├── Controller開始
│       ├── Controller正常終了
│       ├── Service開始
│       └── Service正常終了
│
└── 業務固有ログ
    └── 各Service
        │
        ├── INFO
        │   └── 後から確認する価値が高い重要な状態変更
        │
        └── DEBUG
            └── 頻繁に発生する通常操作・内部処理
```

これにより、共通的なメソッド実行状況と、業務上意味のある状態変更を分離した。

また、ControllerとServiceで同じイベントを二重に記録することを避け、ログに含める値についてもEntityやForm全体ではなく、目的に応じて`questionId`、`structureId`、`userId`、`loginId`など必要な情報を明示的に指定する構成とした。
