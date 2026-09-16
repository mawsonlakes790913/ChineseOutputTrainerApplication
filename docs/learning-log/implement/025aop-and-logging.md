# 025 AOP・ログ

## 1. AOPを使ってController・Serviceの共通ログを実装した

今回はAOPを使い、すべてのControllerとServiceについて、メソッドの開始と正常終了をDEBUGログへ出力する`LogAspect`を実装した。

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

工夫した点は、Controller・Serviceの各クラスに同じような開始・終了ログを書くのではなく、共通処理としてAOPへ分離したことである。

`@Pointcut`によってControllerとServiceを対象として指定し、`@Before`で実行前、`@AfterReturning`で正常終了後に処理を差し込んだ。

また、終了処理には`@After`ではなく`@AfterReturning`を使用した。これによって、例外によって処理が終了した場合まで「正常終了」と記録してしまうことを防いでいる。

今回の実装で、AOPとは単にログ専用の機能ではなく、本来の業務ロジックへ直接コードを書かずに、その前後などへ共通処理を追加する仕組みであることが分かった。

さらに`JoinPoint`を使えば、AOPが割り込んだ対象メソッドの情報を取得でき、`jp.getSignature()`によって実行されたController・Serviceのメソッドを共通処理側から特定できることも勉強になった。

---

## 2. 共通ログと業務固有ログを分けた

AOPを導入する際、既存のログをすべてAOPへ移すのではなく、「共通ログ」と「業務固有ログ」を分けることにした。

例えば、

```text
Controller開始
Service開始
Service正常終了
Controller正常終了
```

というログは、どの処理でも同じ形式で出力できるためAOPに向いている。

一方、

```java
log.info(
        "問題更新完了 questionId={}",
        questionId);
```

や、

```java
log.info(
        "ユーザー凍結完了 userId={}, loginId={}",
        user.getId(),
        user.getLoginId());
```

では、処理によって記録したい情報が異なる。

工夫した点は、このような業務固有ログまで無理にAOPへ集約しなかったことである。

AOPだから何でも共通化すればよいわけではなく、共通化することで逆に条件分岐や引数の判定が複雑になる場合は、個々のServiceへ残した方が分かりやすい。

今回、

```text
共通的な処理
→ AOP

業務ごとに意味が異なる処理
→ 各Service
```

という責務の分け方を実際のコードで確認できたことが勉強になった。

---

## 3. INFOとDEBUGの役割を整理した

既存ログを確認すると、重要な状態変更と頻繁に発生する通常操作の両方がINFOとして記録されていた。

そこで今回、ログレベルについても整理した。

例えば問題登録やユーザー登録、パスワード変更などは、後から確認する価値の高い状態変更なのでINFOとした。

一方、理解度変更は学習中に問題ごとに何度も発生する。

変更前は、

```java
log.info(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

だったが、

```java
log.debug(
        "評価更新 userId={}, questionId={}, evaluation={}",
        user.getId(),
        questionId,
        evaluation);
```

へ変更した。

お気に入り追加・解除、AI生成履歴の保存・自動削除、学習対象言語や表示発音記号の変更なども同様にDEBUGとした。

工夫した点は、「DBをUPDATEしたからINFO」というように処理方式だけで決めるのではなく、そのイベントを通常運用時のログとして継続的に残す価値があるかで判断したことである。

INFOとDEBUGの違いは単なる重要度だけではなく、発生頻度や運用時に必要な情報量も考えて決める必要があることが勉強になった。

---

## 4. EntityやFormをそのままログへ渡さないようにした

`AdminQuestionService.updateOneQuestion()`では、以前は、

```java
log.info("問題更新前 {}", question);
log.info("問題更新後 {}", question);
```

としていた。

しかしSLF4Jの`{}`へEntityをそのまま渡した場合、基本的にはそのオブジェクトの`toString()`によって内容が出力される。

そのため、Entityにどのフィールドが存在するか、`toString()`がどのように定義されているかによって、ログへ出力される情報まで変わってしまう。

そこで、

```java
log.info(
        "問題更新完了 questionId={}",
        questionId);
```

とし、必要な情報だけを明示的に指定するように変更した。

Controllerにあった、

```java
log.info("問題登録 {}", form);
```

のようなForm全体を渡すログについても削除した。

工夫した点は、ログを単なる「変数の中身を見る場所」として扱うのではなく、「後から何を確認したいのか」を先に決め、その目的に必要な項目だけを記録するようにしたことである。

開発中にオブジェクト全体を確認するDEBUG用途と、運用時に記録として残すINFOログでは、必要な情報の選び方が異なることが勉強になった。

---

## 5. userIdとloginIdを目的によって使い分けた

既存ログでは、ユーザーの識別に`userId`を使う場合と`loginId`を使う場合が混在していたため、今回基準を整理した。

`userId`はDB内部で使用する安定した識別子なので、評価、お気に入り、AI生成履歴など、内部データの関係を追跡するときに使用する。

例えば、

```java
log.debug(
        "お気に入り追加 userId={}, questionId={}",
        user.getId(),
        questionId);
```

のようにした。

一方、ユーザー登録、凍結、パスワード変更、退会など、アカウント自体に関する重要な操作では、人間がログを見たときにも対象を把握しやすいように`userId`と`loginId`の両方を残すようにした。

特にログインID変更では、

```java
log.info(
        "ログインID変更完了 userId={}, oldLoginId={}, newLoginId={}",
        user.getId(),
        currentLoginId,
        newLoginId);
```

とした。

工夫した点は、すべてのログへ機械的に`userId`と`loginId`の両方を入れるのではなく、そのログが内部データ追跡用なのか、アカウント操作の確認用なのかによって選択したことである。

同じユーザーを表す値でも用途が異なり、ログでは「何を追跡したいのか」に応じて識別子を選ぶ必要があることが勉強になった。

---

## 6. パスワードなどログへ残してはいけない情報を確認した

`UserAccountService.updatePassword()`では、パスワード変更自体は重要なアカウント操作なのでINFOログとして残すことにした。

```java
log.info(
        "パスワード変更完了 userId={}, loginId={}",
        user.getId(),
        user.getLoginId());
```

ただし、

```text
currentPassword
newPassword
ハッシュ化されたpassword
```

についてはログへ出力しない。

工夫した点は、「パスワード変更が行われた」という事実と、「どのパスワードへ変更したのか」という情報を明確に分けたことである。

ログにはデバッグに便利だからという理由だけで値を出力するのではなく、ログファイル自体が後から閲覧される情報であることを考え、機密情報を残さない設計が必要であることを確認できた。

---

## 7. ControllerとServiceで同じログを重複させないようにした

以前はController側にも、

```java
log.debug(
        "ユーザー登録開始 loginId={}",
        form.getLoginId());
```

や、

```java
log.debug(
        "パスワード変更開始 loginId={}",
        loginUser.getUsername());
```

などのログが存在していた。

しかし今回AOPを導入したことで、Controller・Serviceへの入口は共通DEBUGログから確認できる。

さらに処理成功後にはService側で、

```java
log.info(
        "ユーザー登録完了 userId={}, loginId={}",
        savedUser.getId(),
        savedUser.getLoginId());
```

などの業務固有ログを記録する。

そのためController独自の開始ログを削除した。

工夫した点は、「ログが多いほど詳しく分かる」と考えるのではなく、同じ出来事を複数の場所から重複して出力しないようにしたことである。

```text
Controller・Serviceへの出入り
→ AOP

処理が成功して状態が変わったこと
→ Service
```

と責務を分けることで、必要な情報を残しながらログの重複を減らせることが勉強になった。

---

## 8. ログがなかったServiceについても必要性を確認した

今回は既存ログを修正するだけではなく、`@Slf4j`が付いていないServiceについても確認し、業務固有ログが不足していないかを調べた。

その結果、`AdminStructureService`の文法・構造登録・更新・削除や、`AdminUserService`のユーザー凍結・凍結解除などにはINFOログを追加した。

一方、

```text
PaginationService
PracticeService
ReviewService
AiPromptService
AiPronunciationService
```

などについては、検索、件数取得、ページング、AI処理の補助などが中心であり、新しい業務固有ログは追加しなかった。

工夫した点は、`@Slf4j`がないこと自体を問題とは考えず、そのクラスに記録すべき業務イベントが存在するかを確認したことである。

ログを使う予定がないクラスへ形式的に`@Slf4j`を追加する必要はなく、必要なクラスだけがログを持てばよいことが分かった。

---

## 9. AI生成処理のSystem.out.printlnをDEBUGログへ変更した

`AiPracticeService.generateQuestions()`では、AI生成処理の性能確認のために各工程の時間を計測していたが、結果を`System.out.println()`で出力していた。

今回これを、

```java
log.debug(
        "AI生成 API処理時間: {} ms",
        apiCompletedTime - inputCompletedTime);
```

などのDEBUGログへ変更した。

AI生成処理では、

```text
入力作成
AI API
DTO変換・履歴更新
合計
```

をそれぞれ計測している。

工夫した点は、処理時間計測をすべてAOPへ移さなかったことである。

メソッド全体の時間であれば`@Around`を利用して外側から計測できるが、AI APIだけの時間などは`generateQuestions()`内部の処理構造を知らなければ計測できない。

また、現在詳細な時間計測が必要なのはこのメソッドだけなので、共通化しても重複コード削減のメリットがない。

AOPで実現できることと、実際にAOPへ移す価値があることは別であり、共通化できるからといって必ず共通化する必要はないことが勉強になった。

---

## 10. @TransactionalもAOPを利用していることを確認した

今回AOPについて確認したことで、以前から使用していた`@Transactional`もAOPの仕組みを利用していることが分かった。

```java
@Transactional
public void updateQuestion(...) {
    ...
}
```

とすると、メソッド本体へトランザクション開始やCOMMITなどを直接書かなくても、SpringがProxyを介してメソッドの前後へトランザクション処理を追加する。

概念的には、

```text
メソッド呼び出し
    ↓
Spring Proxy
    ↓
トランザクション開始
    ↓
対象メソッド
    ↓
COMMIT / ROLLBACK
```

となる。

今回自分で実装した`LogAspect`と処理内容は異なるが、「本来のメソッドの外側から別の処理を追加する」という点では同じAOPの考え方を利用している。

これまで`@Transactional`をDB更新を安全に行うためのアノテーションとして使用していたが、その裏側の仕組みと今回実装したAOPがつながったことが勉強になった。

---

## 11. @PreAuthorizeにもAOPが利用されていることを確認した

`PracticeController.getPracticeCount()`では以前から、

```java
@PreAuthorize("isAuthenticated()")
```

を使用している。

これは認証済みユーザーだけが対象メソッドを実行できるようにするもので、対象メソッドが実行される前にSpring Securityが条件を確認する。

```text
メソッド呼び出し
    ↓
認可条件を確認
    ↓
条件を満たす
    ↓
対象メソッドを実行
```

という形になり、これにもAOPの仕組みが利用されている。

これまでは`@PreAuthorize`を「ログイン済みユーザーだけ実行可能にするアノテーション」として使用していたが、これも対象メソッドの前に別の処理を差し込む仕組みであることが分かった。

今回の025ではAOPとの関係を確認するだけに留め、認証成功・失敗やアクセス拒否などSpring Security固有のログについては追加しなかった。

セキュリティ全体については、次のセキュリティ実装で改めて整理する。

---

## 12. 今回の実装で整理できたこと

今回の実装では、単に`@Aspect`を使ってログを出力するだけではなく、アプリケーション全体のログについて、

```text
共通ログ
→ AOPでDEBUG

重要な業務上の状態変更
→ 各ServiceでINFO

頻繁に発生する通常操作・内部処理
→ 各ServiceでDEBUG
```

という基本的な役割を整理できた。

また、

```text
どのクラスにもログを追加すればよいわけではない
すべてのDB更新をINFOにすればよいわけではない
すべてのユーザーログにloginIdを入れればよいわけではない
共通化できる処理をすべてAOPへ移せばよいわけではない
```

という点も今回の実装を通して確認できた。

ログは単に処理途中の値を表示するためのものではなく、「後から何を確認するための情報なのか」を考えて、ログレベル、出力場所、記録する項目を決める必要がある。

また、AOPについても、今回初めて`@Aspect`や`@Pointcut`を自分で実装した一方で、`@Transactional`や`@PreAuthorize`を通してSpringのAOP自体は以前から利用していたことが分かった。

今回の025によって、AOPの基本的な仕組みを確認するとともに、Chinese Output Forge全体のログの役割と記録方針を整理することができた。
