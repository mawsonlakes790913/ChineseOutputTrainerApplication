# 025 AOP・ログ — 学習ログ

## はじめに

今回のチャプターでは、Controller・Serviceのメソッド開始と正常終了を記録する`LogAspect`を実装し、既存のログの出力場所・レベル・内容を見直した。

取り組む前は、ログを「処理の途中で何が起きたかを確認するための出力」と捉えることが多かった。しかし今回、**誰が、いつ、何を確認するために読むログなのか**を考えることで、共通化する範囲や記録する情報が変わると分かった。

## 1. AOPによって業務処理の外側から共通処理を追加できる

今回、`@Aspect`を付けた`LogAspect`を作成し、`@Pointcut`でController・Serviceを対象に指定した。メソッドの実行前には`@Before`、正常終了後には`@AfterReturning`を使用した。

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

特に重要だったのは、終了ログに`@After`ではなく`@AfterReturning`を選んだことである。`@After`は例外終了時にも実行されるため、「正常終了」というメッセージには適さない。ログの文言とAdviceの実行条件を一致させる必要があると理解した。

また、`JoinPoint#getSignature()`によって対象メソッドの情報を取得できるため、各クラスへ同じログを記述しなくても、共通の形式で処理を追跡できる。

AOPはログ専用の仕組みではなく、**本来の業務ロジックに直接コードを追加せず、横断的な処理を適用するための仕組み**だと実感した。

なお、Spring AOPは通常、Springが管理するBeanへのプロキシ経由の呼び出しに適用される。そのため、同一クラス内で`this`を通じて呼び出すメソッドなど、プロキシを経由しない呼び出しまで必ず記録できるわけではない。この点は「すべてのメソッドが無条件に記録される」という理解と区別しておきたい。

## 2. 共通ログと業務固有ログは役割が違う

ControllerやServiceの開始・正常終了は、どの機能でも同じ形式で記録できる。一方、「問題を更新した」「ユーザーを凍結した」といったイベントでは、記録したい識別子や処理の意味が異なる。

```text
メソッドの開始・正常終了
    → LogAspectで共通のDEBUGログ

問題の更新・ユーザーの凍結など
    → 各Serviceで業務固有のログ
```

例えば問題更新時には`questionId`、ユーザー凍結時には`userId`や`loginId`を記録する。これらをすべてAOP側で処理しようとすると、対象メソッドごとの引数や意味を判別する必要が生じる。

今回学んだのは、**AOPで実装できることと、AOPへ移すべきことは同じではない**という点である。共通の形式を持つ処理はAOPに集約し、業務上の意味が異なるイベントは、それを把握しているServiceへ残す方が分かりやすい。

## 3. INFOとDEBUGは状態変更の有無だけで決めない

以前はDBの更新を伴う処理をINFOで記録していた箇所もあったが、今回、イベントの重要性に加えて発生頻度と運用時の必要性を考えて整理した。

問題登録・削除、ユーザー登録・凍結、パスワード変更、退会などは、後から確認する価値が高い状態変更としてINFOにした。一方、理解度変更、お気に入り追加・解除、AI生成履歴の保存などは、通常利用中に繰り返し発生するためDEBUGへ変更した。

例えば理解度変更は次のようにした。

```java
log.debug(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

**UPDATEを実行したからINFO、参照処理だからDEBUG**という単純な分類ではなく、通常運用で継続して記録する価値があるかを考える必要がある。

また、INFOとDEBUGはログ設定によって出力の有無が変わる。重要なイベントをDEBUGへ移す場合には、通常運用時にその記録が必要ないかも含めて判断することが大切だと分かった。

## 4. ログにはオブジェクト全体ではなく必要な項目を記録する

`AdminQuestionService.updateOneQuestion()`では、以前は`Question` Entityをそのままログへ渡していた。

```java
log.info("問題更新前 {}", question);
log.info("問題更新後 {}", question);
```

この書き方では、出力内容がEntityの`toString()`の実装に左右される。Entityに項目が追加されたり、`toString()`が変更されたりすると、ログの出力内容も意図せず変わる可能性がある。

今回必要なのは「どの問題が更新されたか」という情報だったため、次の形式へ変更した。

```java
log.info("問題更新完了 questionId={}", questionId);
```

Controllerにあった`log.info("問題登録 {}", form);`のようなForm全体の出力も削除した。

ここから、ログは単なる変数表示ではなく、**後から確認したい事実を、必要な情報に絞って残すもの**だと理解した。不要な情報の出力を抑えることは、読みやすさだけでなく情報漏えいリスクの低減にもつながる。

## 5. 識別子の選択と機密情報の扱いを考える

ユーザー関連ログでは、内部データの追跡には`userId`、アカウント操作の確認には必要に応じて`userId`と`loginId`の両方を使用する方針とした。

`userId`はDB上の安定した識別子であり、`loginId`は変更される可能性がある。特にログインID変更では、変更前後の値を区別して記録する必要がある。

```java
log.info(
        "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
        user.getId(),
        currentLoginId,
        newLoginId);
```

一方、パスワード変更では、変更が行われたという事実のみを記録する。

```java
log.info(
        "パスワード変更完了 userId={}, loginId={}",
        user.getId(),
        user.getLoginId());
```

現在のパスワード、新しいパスワード、ハッシュ化されたパスワードは出力しない。**操作の事実を記録することと、操作に使われた機密情報を記録することは別**だと整理できた。

また、`loginId`もユーザーを識別できる情報なので、必要性を考えずにあらゆるログへ含めるべきではない。ログ自体の閲覧権限や保管期間も、今後運用する際に考えるべき点である。

## 6. ログは多ければよいわけではなく、重複と不足の両方を確認する

AOP導入前は、Controllerに「ユーザー登録開始」「パスワード変更開始」などのログが存在していた。しかしメソッドへの入口はAOPのDEBUGログで確認できるため、同じ目的の開始ログを個別に残す必要性が低くなった。

また、状態変更が成功したことを表すログは原則としてService側へ置き、ControllerとServiceで同じイベントを重複して記録しないようにした。

一方、既存ログを減らすだけではなく、`AdminStructureService`の文法・構造登録・更新・削除や、`AdminUserService`の凍結・凍結解除など、重要なイベントに不足していたINFOログは追加した。

ログがなかったServiceについても確認したが、検索やページングなどが中心で、新たな業務固有ログを必要としないクラスには形式的に`@Slf4j`を追加しなかった。

今回の見直しで、ログ設計は「出力箇所を増やす作業」ではなく、**必要なイベントが記録され、同じ意味のログが重複しない状態を作る作業**だと分かった。

## 7. AI生成処理の時間計測はAOPへ移さなかった

`AiPracticeService.generateQuestions()`では、入力作成、AI API呼び出し、DTO変換・履歴更新、全工程の処理時間を計測していた。以前は`System.out.println()`で出力していたが、今回DEBUGログへ変更した。

```java
log.debug(
        "AI生成 API処理時間: {} ms",
        apiCompletedTime - inputCompletedTime);
```

メソッド全体の処理時間なら`@Around`によって外側から計測できる。しかし、AI APIの呼び出し部分だけの時間など、内部工程ごとの計測には`generateQuestions()`固有の知識が必要になる。

また、今回の記録では詳細な時間計測が必要なのはこのメソッドだけだったため、無理に共通化しなかった。

**共通化によって責務が明確になるのか、それとも処理の意味を隠してしまうのか**を判断することが重要であり、これは前章024のリファクタリングで学んだ考え方とも共通している。

## 8. `@Transactional`と`@PreAuthorize`もAOPとつながっていた

今回独自の`LogAspect`を作成したことで、以前から利用していたSpringのアノテーションも、同様にメソッドの外側から処理を適用する仕組みと関係していることが分かった。

`@Transactional`は、Springのトランザクション管理が有効な環境で、通常プロキシを通じてトランザクションの開始・完了・ロールバックなどを制御する。

```java
@Transactional
public void updateQuestion(...) {
    // DB更新処理
}
```

`@PreAuthorize("isAuthenticated()")`は、メソッドの実行前に認可条件を確認するために使用していた。

```java
@PreAuthorize("isAuthenticated()")
public ... getPracticeCount(...) {
    // 件数取得処理
}
```

これらは自作したログ用Aspectと同じ実装そのものではないが、対象メソッドの呼び出しに横断的な処理を適用するという点でつながっている。

ただし、Springのプロキシを利用する場合には、同一クラス内からの自己呼び出しなど、プロキシを経由しないケースに注意が必要である。また、トランザクションや認可の適用条件は、それぞれの設定や呼び出し方にも依存する。

これまで個別の便利なアノテーションとして使用していた機能を、**Springがメソッド呼び出しへ処理を追加する仕組み**として捉え直せたことが、今回の大きな学びだった。

## 9. まとめ

025では、AOPを使ってController・Serviceの共通ログを実装し、アプリケーション全体のログを見直した。

特に重要だったのは、ログの出力方法そのものよりも、**何を記録するか、どこで記録するか、どのレベルで記録するか**を目的に応じて判断することである。

共通のメソッド実行情報はAOPでDEBUGとして記録し、業務上重要な状態変更は各ServiceでINFOとして記録する。一方、頻繁な通常操作は必要に応じてDEBUGとし、EntityやForm全体、パスワードなどの不要・機密情報は出力しない。

AOPもログも、適用範囲を広げればよいわけではない。**業務処理の意味を明確にしながら、後から必要な情報を追跡できるようにすること**が、今回整理できた設計上の考え方である。
