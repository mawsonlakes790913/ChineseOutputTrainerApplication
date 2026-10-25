# 025 AOPによる共通ログの導入と既存ログの整理

## 1. 概要

Controller・Serviceに共通するメソッド開始・正常終了のログをAOP（Aspect-Oriented Programming）へ集約し、既存の業務固有ログも見直した。

今回の目的は、ログを単純に増やすことではなく、**処理の流れを調査するためのログ**と、**業務上の状態変更を記録するログ**の役割を分け、必要な情報を適切なレベルで出力できるようにすることである。

主な変更は以下のとおり。

- `LogAspect`によるController・Serviceの開始／正常終了ログの共通化
- 業務固有ログのINFO・DEBUGの見直しと不足分の追加
- Entity・Form全体の出力を廃止し、必要な識別情報のみを記録
- `userId`と`loginId`の用途整理、削除処理時の識別情報保持
- Controller・Service間の重複ログ削除と不要な`@Slf4j`の削除
- AI生成処理の`System.out.println()`をDEBUGログへ変更

## 2. 共通ログをAOPへ集約

### 2.1 `LogAspect`の追加

Controller・Serviceの各メソッドへ同じ形式の開始・終了ログを個別に記述する代わりに、`LogAspect`を追加した。

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

`@Pointcut`でControllerとServiceを対象として定義し、`@Before`で開始、`@AfterReturning`で正常終了を記録する。`@After`を使わなかったのは、例外によって終了した処理まで「正常終了」と表示しないためである。メソッドの識別には`JoinPoint#getSignature()`を利用した。

想定するログの流れは次のとおり。

```text
Controller開始: PracticeController.getPracticeQuestion(...)
Service開始: PracticeService.getPracticeQuestions(...)
Service正常終了: PracticeService.getPracticeQuestions(...)
Controller正常終了: PracticeController.getPracticeQuestion(...)
```

これらは業務イベントではなく開発・調査時の実行経路を追う情報であるため、ログレベルは**DEBUG**とした。

### 2.2 共通ログと業務固有ログの分担

AOPに集約するのは、メソッド開始・正常終了のように一律に記録できる情報だけとした。

一方、問題登録、ユーザー凍結、パスワード変更などは、処理ごとに記録すべき情報が異なる。これらをAOPへ集約すると、メソッド引数やEntityの構造をAOP側で個別に判定する必要が生じるため、**業務上の成功・変更内容は原則として各Serviceに記録**する方針とした。

Controllerはリクエスト受付・入力・Session・画面遷移を担当し、状態変更が成功したことを示すログは、その処理を担当するServiceに置く。

## 3. AI生成処理の計測ログを変更

`AiPracticeService.generateQuestions()`では、AI生成処理の所要時間を以下の工程に分けて計測していた。

- AIへ送信する入力の作成
- AI APIによる問題生成
- DTO変換・AI生成履歴更新
- 全工程の合計

従来は`System.out.println()`でコンソールへ出力していたが、SLF4JのDEBUGログに変更した。

```java
log.debug("AI生成 入力作成時間: {} ms",
        inputCompletedTime - startTime);
log.debug("AI生成 API処理時間: {} ms",
        apiCompletedTime - inputCompletedTime);
log.debug("AI生成 DTO変換・履歴更新時間: {} ms",
        conversionCompletedTime - apiCompletedTime);
log.debug("AI生成 合計処理時間: {} ms",
        conversionCompletedTime - startTime);
```

メソッド全体の時間計測であれば`@Around`によるAOP化も考えられるが、現時点で計測対象はこのメソッドのみであり、工程別の区間もAI生成処理固有である。そのため、今回は共通化せず`AiPracticeService`に残した。

## 4. 業務固有ログの重要度を整理

### 4.1 INFOとDEBUGの使い分け

後から重要な状態変更を確認する必要がある操作を**INFO**、頻繁に発生する通常操作や内部処理の調査用情報を**DEBUG**とした。

| INFO：重要な状態変更 | DEBUG：頻繁な操作・内部処理 |
| --- | --- |
| 問題の登録・更新・削除 | 理解度の変更 |
| 文法・構造の登録・更新・削除 | お気に入りの追加・解除 |
| ユーザー登録・凍結・凍結解除 | AI生成履歴の保存・上限超過時の自動削除 |
| ログインID・パスワードの変更 | 学習対象言語・表示発音記号の変更 |
| 退会・ユーザー削除 | AI生成処理時間 |
| AI生成問題の正式保存・所有問題の削除 | Controller・Serviceの開始／正常終了 |

例えば`EvaluationService.updateEvaluation()`の理解度更新は学習中に何度も発生するため、既存のINFOログをDEBUGへ変更した。

```java
log.debug(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(), questionId, evaluation);
```

`FavoriteService`の追加・解除、`AiGenerationHistoryService`の履歴保存・自動削除も同様にDEBUGとした。

### 4.2 不足していたINFOログの追加

重要な状態変更にもかかわらずログがなかった箇所にはINFOログを追加した。

- `AdminStructureService`：文法・構造の登録・更新・削除
- `AdminUserService`：ユーザー凍結・凍結解除
- `AiPracticeService.saveGeneratedQuestion()`：AI生成問題の正式保存

例えば、AIが一時的に問題を生成した段階ではなく、生成結果を`question`テーブルへ正式保存した段階で記録する。

```java
log.info(
        "AI生成問題保存完了 userId={}, questionId={}, sourceQuestionId={}",
        user.getId(),
        savedQuestionResult.getQuestionId(),
        sourceQuestion.getQuestionId());
```

文法・構造の登録・更新・削除では`structureId`と`name`を記録し、操作対象を追跡できるようにした。

## 5. ログへ記録する情報を限定

### 5.1 Entity・Form全体の出力を廃止

`AdminQuestionService.updateOneQuestion()`では、更新前後の`Question` EntityをそのままINFOログへ渡していた。

```java
log.info("問題更新前 {}", question);
// 更新・保存処理
log.info("問題更新後 {}", question);
```

この形式では出力内容がEntityの`toString()`に依存し、問題本文や発音、テンプレートなど不要な情報まで記録される可能性がある。今回必要なのは「どの問題を更新したか」であるため、次の形式へ変更した。

```java
log.info("問題更新完了 questionId={}", questionId);
```

問題登録・更新・削除も`questionId`を中心とする形式に揃えた。`AdminQuestionController`にあった`log.info("問題登録 {}", form)`や`log.info("問題更新 {}", form)`は、Form全体の出力であるうえService側の成功ログと重複するため削除した。

### 5.2 `userId`と`loginId`の使い分け

内部データ間の関係を追うAI生成履歴・理解度・お気に入りなどは、DB上で安定した識別子である`userId`を使う。

一方、ユーザー登録、凍結、ログインID変更、パスワード変更、退会などのアカウント操作では、`userId`と`loginId`の両方を記録する方針とした。

ログインID変更時は、変更前後の値も区別する。

```java
log.info(
        "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
        user.getId(), currentLoginId, newLoginId);
```

パスワード変更のログには`userId`と`loginId`のみを記録し、**平文パスワード、新しいパスワード、ハッシュ値は出力しない**。

### 5.3 削除対象の識別情報を事前に保持

退会や管理者によるユーザー削除では、対象の`Users`自体が削除されるため、削除前に識別情報を取得する構成とした。

```java
Long userId = user.getId();
String deletedLoginId = user.getLoginId();

deleteUserData(user);

log.info("退会完了 userId={}, loginId={}",
        userId, deletedLoginId);
```

管理者によるユーザー削除も同様に`userId`と削除前の`loginId`を記録する。`UserQuestionService.deleteOwnedQuestion()`では`userId`と`questionId`を記録し、削除した所有問題を追跡できるようにした。

## 6. 重複ログと不要なログ設定を削除

状態変更の成功ログをServiceへ集約したため、Controllerにあった問題登録・更新ログ、ユーザー登録開始ログ、パスワード変更開始ログなどを削除した。

メソッド開始・正常終了はAOPのDEBUGログで確認できるため、同じ目的のログをControllerに個別に記述する必要はない。

これに伴い、個別のログ出力がなくなった`AdminQuestionController`、`SignupController`、`UserProfileController`などから`@Slf4j`を削除した。

また、`@Slf4j`の有無にかかわらずController・Serviceを確認し、検索、件数取得、ページング、画面表示、Session操作など、状態変更を伴わない処理には業務固有ログを無理に追加しなかった。

対象として確認したクラスには、`PaginationService`、`PracticeService`、`ReviewService`、`AiPromptService`、`AiPronunciationService`、`UserDetailsServiceImpl`、各学習モードのControllerなどが含まれる。これらもAOPの対象であれば、開始・正常終了はDEBUGログで記録される。

## 7. 既存のSpring AOP利用箇所を確認

独自の`LogAspect`を追加したことにあわせて、既存の`@Transactional`と`@PreAuthorize`についても、SpringのAOP／メソッドインターセプトの仕組みを利用していることを確認した。

- `@Transactional`：Proxyを介してトランザクション開始・COMMIT・ROLLBACKなどを制御する。
- `@PreAuthorize("isAuthenticated()")`：`PracticeController.getPracticeCount()`などでメソッド実行前に認可条件を確認する。

これらは**今回新たに実装した機能ではない**ため、既存の設定を維持した。Spring Security全体の見直しや認証・認可ログの追加は、この章の対象外とした。

## 8. 変更後の構成と確認範囲

ログの役割を次のように整理した。

```text
ログ
├── 共通ログ：LogAspect / DEBUG
│   ├── Controller・Service開始
│   └── Controller・Service正常終了
└── 業務固有ログ：各Service
    ├── INFO：重要な状態変更
    └── DEBUG：頻繁な通常操作・内部処理
```

今回の変更によって、共通的な実行経路と業務上の状態変更を区別し、ControllerとServiceの二重記録を減らした。また、ログへ渡す情報をEntity・Form全体から、目的に応じた`questionId`、`structureId`、`userId`、`loginId`などへ限定した。

**動作確認について：** 元の実装記録には、実際のログ出力結果やテスト実施結果の記載がないため、ここでは設定・コードの変更内容のみを記録する。AOPの適用範囲やログ出力順序、例外発生時の出力について、実機で確認済みとは断定しない。
