# 003 言語設定機能の実装

ここでは、以下の2種類の言語設定機能の土台を実装する。

- **サイト表示言語**
  - サイト上の説明文やボタン、メニューなどに表示する言語を設定する。
- **学習対象言語**
  - 実際に学習する中国語を、大陸普通話または台湾華語（國語）から設定する。

この2つはどちらも「言語設定」ではあるが、それぞれ独立した役割を持つ。

---

# 1. なぜ言語設定機能を先に実装するのか

## 1.1 サイト表示言語

今後の開発では、多くのHTMLファイルを作成することになる。

先に単一言語のみを前提として画面を作成してしまうと、後から多言語表示に対応する際、各HTMLファイルに直接記述したテキストを多言語対応の形式へ修正する必要が生じる。

サイト表示言語の切り替え自体は比較的早い段階で導入できるため、

**画面が増える前に多言語対応の仕組みを構築し、以降の画面を最初から多言語対応を前提として実装する。**

## 1.2 学習対象言語

今後、通常学習や復習などを実装する際には、DBから問題データを取得する必要がある。

本アプリケーションでは、大陸普通話と台湾華語の問題を同一のQuestionテーブルで管理し、学習対象言語によって取得する問題を切り替える予定である。

そのため、問題取得処理を実装する前に、

- 大陸普通話
- 台湾華語

のどちらを現在の学習対象としているのかを管理する仕組みが必要になる。

学習対象言語を表す値や、その選択状態を管理する仕組みを先に定義しておくことで、今後のController・Service・Repositoryなどを最初から学習対象言語の切り替えを前提として実装できる。

以上の理由から、通常学習などの具体的な学習機能を実装する前に、サイト表示言語と学習対象言語を管理するための共通基盤を構築する。

```text
003 言語設定機能の実装
    ├─ サイト表示言語
    │   ├─ 日本語
    │   ├─ English
    │   ├─ 简体中文
    │   └─ 繁體中文
    │
    └─ 学習対象言語
        ├─ MAINLAND
        └─ TAIWAN
```

---

# 2. サイト表示言語設定

```bash
git commit -m "feat: add multilingual display language switching"
```

## 2.1 使用するフォルダ・ファイル

```text
src/main/java/
└── io.github.mawsonlakes790913.chineseoutputforge/
    └── config/
        └── LocaleConfig.java          ← 新規作成

src/main/resources/
├── messages.properties               ← 新規作成（デフォルト・日本語）
├── messages_en.properties            ← 新規作成
├── messages_zh_CN.properties         ← 新規作成
└── messages_zh_TW.properties         ← 新規作成
```

002で作成したHTMLファイルには、「このアプリについて」などの固定文言が直接記述されている。

これらの文言をプロパティファイルから読み込むように変更する。

言語ごとのプロパティファイルを用意することで、HTML本体を変更することなく表示言語を切り替えられるようにする。

---

# 3. デフォルトメッセージの作成

## 3.1 messages.properties

デフォルトの表示言語を日本語とする。

```properties
# home.html
home.title=瞬間中国語作文
home.welcome=中国語トレーニングアプリへようこそ
home.about=このアプリについて

# about.html
about.title=このアプリについて
about.backToTop=Topへ戻る

# header.html
header.greeting.guest=こんにちは、ゲストさん
```

`中文造句工坊` はブランド名として全言語共通にする予定なので、propertiesには移さない。

## 3.2 home.html

```html
<h1 class="mb-3"
    th:text="#{home.title}">
    瞬間中国語作文
</h1>

<p class="text-muted"
   th:text="#{home.welcome}">
    中国語トレーニングアプリへようこそ
</p>

<div class="d-grid gap-3 col-md-3 mx-auto mt-5">

    <a th:href="@{/about}"
       class="btn btn-secondary"
       th:text="#{home.about}">
        このアプリについて
    </a>

</div>
```

このように、

```html
th:text="#{キー名}"
```

を使用することで、`messages.properties` 内の対応するメッセージを取得できる。

`about.html`、`header.html`についても同様に変更する。

## 3.3 実行

`http://localhost:8080/` にアクセスしたところ、`messages.properties` の内容が正常に表示された。

これにより、デフォルトメッセージの読み込みに成功したことを確認した。

---

# 4. 多言語メッセージの作成

## 4.1 messages_en.properties

```properties
# home.html
home.title=Instant Chinese Sentence Production
home.welcome=Welcome to the Chinese Training App
home.about=About This App

# about.html
about.title=About This App
about.backToTop=Back to Top

# header.html
header.greeting.guest=Hello, Guest
```

## 4.2 messages_zh_CN.properties

```properties
# home.html
home.title=中文快速造句
home.welcome=欢迎来到中文练习App
home.about=关于本App

# about.html
about.title=关于本App
about.backToTop=返回首页

# header.html
header.greeting.guest=你好，Guest
```

## 4.3 messages_zh_TW.properties

```properties
# home.html
home.title=中文快速造句
home.welcome=歡迎來到中文練習App
home.about=關於本App

# about.html
about.title=關於本App
about.backToTop=返回首頁

# header.html
header.greeting.guest=你好，Guest
```

---

# 5. Localeによる表示言語の切り替え

Localeを使用して、4つの `messages*.properties` を切り替える。

| 表示言語 | Locale | 読み込まれるファイル |
|---|---|---|
| 日本語 | `ja` | `messages.properties` |
| English | `en` | `messages_en.properties` |
| 简体中文 | `zh_CN` | `messages_zh_CN.properties` |
| 繁體中文 | `zh_TW` | `messages_zh_TW.properties` |

これに加えて、以下の仕組みを使用する。

- **LocaleResolver**
  - 現在どのLocaleを使用するかを管理・保持する。
- **LocaleChangeInterceptor**
  - `?lang=en` などのリクエストパラメータを検知してLocaleを変更する。

## 5.1 処理の流れ

```text
ユーザー
  ↓
「繁體中文」をクリック
  ↓
?lang=zh_TW
  ↓
LocaleChangeInterceptor
  ↓
Localeを zh_TW に変更
  ↓
SessionLocaleResolver
  ↓
LocaleをSessionに保持
  ↓
messages_zh_TW.properties
  ↓
Thymeleafの #{...} が繁體中文になる
```

---

# 6. LocaleConfigの実装

## 6.1 LocaleConfig.java

```java
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    LocaleResolver localeResolver() {

        SessionLocaleResolver resolver = new SessionLocaleResolver();

        // デフォルトは日本語
        resolver.setDefaultLocale(Locale.JAPANESE);

        return resolver;
    }

    @Bean
    LocaleChangeInterceptor localeChangeInterceptor() {

        LocaleChangeInterceptor interceptor =
                new LocaleChangeInterceptor();

        // ?lang=ja などの lang を監視
        interceptor.setParamName("lang");

        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
```

## 6.2 WebMvcConfigurerとは

SpringでWebアプリケーションのLocaleを切り替える仕組みは、Spring MVCの機能として提供されている。

そのSpring MVCの設定をJava Configでカスタマイズする際に、`WebMvcConfigurer` インタフェースを実装する。

今回の `LocaleConfig` では、主に以下の2つのBeanを登録している。

```text
LocaleConfig
├─ LocaleResolver
│   └─ 現在のLocaleを管理・保持
│
└─ LocaleChangeInterceptor
    └─ ?lang=○○ を検知してLocaleを変更
```

### localeResolver()

`SessionLocaleResolver` は、LocaleをHTTPセッションで管理する `LocaleResolver` の実装クラスである。

```java
resolver.setDefaultLocale(Locale.JAPANESE);
```

によって、デフォルトLocaleを日本語に設定する。

### localeChangeInterceptor()

```java
interceptor.setParamName("lang");
```

によって、`lang` というリクエストパラメータを監視する。

例えば、

```text
?lang=en
```

というリクエストが送信されると、Localeを英語へ変更する。

### addInterceptors()

`addInterceptors()` は `WebMvcConfigurer` に定義されているメソッドであり、使用したいInterceptorをSpring MVCへ登録するために使用する。

```java
registry.addInterceptor(localeChangeInterceptor());
```

によって、作成した `LocaleChangeInterceptor` を実際のHTTPリクエストで使用できるようにする。

---

# 7. Interceptorとは

Interceptorは、

**HTTPリクエストがControllerに届く前後に共通処理を挟む仕組み**

である。

今回使用する `LocaleChangeInterceptor` は、リクエストに

```text
?lang=○○
```

が存在するか確認し、存在すればLocaleを変更する。

## 7.1 InterceptorRegistryとは

```java
public void addInterceptors(InterceptorRegistry registry)
```

の `registry` は、Spring MVCから渡されるInterceptor登録用オブジェクトである。

```text
InterceptorRegistry → 型
registry            → 引数名
```

今回の、

```java
registry.addInterceptor(localeChangeInterceptor());
```

では、

1. `localeChangeInterceptor()` を呼び出す
2. `LocaleChangeInterceptor` のインスタンスを取得する
3. そのインスタンスをSpring MVCへ登録する

という処理を行っている。

---

# 8. 表示言語切り替えUIの実装

当初は `header.html` に表示言語切り替え用のドロップダウンを配置した。

```html
<div class="dropdown">

    <button class="btn btn-outline-light dropdown-toggle"
            type="button"
            data-bs-toggle="dropdown"
            aria-expanded="false">

        <span th:if="${#locale.language == 'ja'}">日本語</span>
        <span th:if="${#locale.language == 'en'}">English</span>
        <span th:if="${#locale.toString() == 'zh_CN'}">简体中文</span>
        <span th:if="${#locale.toString() == 'zh_TW'}">繁體中文</span>
    </button>

    <ul class="dropdown-menu dropdown-menu-end">

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=ja}">
                日本語
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=en}">
                English
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=zh_CN}">
                简体中文
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=zh_TW}">
                繁體中文
            </a>
        </li>

    </ul>
</div>
```

現在のLocaleによって、ドロップダウンの見出しも変更する。

日本語と英語では、

```text
#locale.language
```

を使用できる。

一方、中国語の場合、

```text
zh_CN
zh_TW
```

は `language` だけを見ると、どちらも `zh` になる。

そのため、簡体中文と繁體中文を区別する場合は、

```text
#locale.toString()
```

を使用して `zh_CN` / `zh_TW` を判定する。

---

# 9. 日本語表示で発生した問題

## 9.1 実行結果

以下については正常に切り替わった。

```text
http://localhost:8080/?lang=en
→ English

http://localhost:8080/?lang=zh_CN
→ 简体中文

http://localhost:8080/?lang=zh_TW
→ 繁體中文
```

![](../../images/0003-01.png)

![](../../images/0003-02.png)

![](../../images/0003-03.png)

しかし、

```text
http://localhost:8080/?lang=ja
```

へアクセスしても、日本語ではなく英語が表示された。

![](../../images/0003-04.png)

## 9.2 原因

Springのメッセージ解決時に、システム側のLocale（英語）へのフォールバックが発生していた。

## 9.3 修正

`application.yml` に以下を追加する。

```yaml
spring:
  messages:
    fallback-to-system-locale: false
```

これによって、

**システムのLocaleへフォールバックせず、指定したLocaleとデフォルトの `messages.properties` を基準にメッセージを解決する**

ようにする。

## 9.4 再実行

```text
http://localhost:8080/?lang=ja
```

へアクセスしたところ、正常に日本語が表示された。

![](../../images/0003-05.png)

---

# 10. 学習対象言語設定

```bash
git commit -m "feat: add study language switching"
```

まだ問題データ自体を実装していないため、今回はリクエストパラメータから学習対象言語を切り替え、その状態を保持する仕組みだけを実装する。

サイト表示言語とは処理方法が異なる。

### サイト表示言語

```text
?lang=ja
    ↓
LocaleChangeInterceptor
    ↓
SessionLocaleResolver
```

### 学習対象言語

```text
?languageVariant=MAINLAND
    ↓
自分たちで処理する
    ↓
SessionにMAINLANDを保存
```

`LocaleChangeInterceptor` はLocaleを変更するための仕組みなので、学習対象言語には使用しない。

---

# 11. 学習対象言語のファイル構成

```text
src/main/java/
└── io.github.mawsonlakes790913.chineseoutputforge/
    ├── controller/
    │   └── LanguageVariantController.java
    │
    └── constant/
        └── LanguageVariant.java

src/main/resources/
└── static/
    └── js/
        └── header.js
```

将来的には、

```text
?languageVariant=MAINLAND
        ↓
LanguageVariant.MAINLAND
        ↓
Sessionに保存
        ↓
Service
        ↓
Repository
        ↓
WHERE language_variant = 'MAINLAND'
```

という流れで、学習対象言語によって取得する問題を切り替える予定である。

---

# 12. LanguageVariantの実装

## 12.1 LanguageVariant.java

```java
public enum LanguageVariant {
    MAINLAND,
    TAIWAN
}
```

学習対象言語をEnumとして定義する。

```text
MAINLAND → 大陸普通話
TAIWAN   → 台湾華語（國語）
```

---

# 13. LanguageVariantControllerの実装

```java
@Controller
public class LanguageVariantController {

    @GetMapping("/language-variant")
    public String changeLanguageVariant(
            @RequestParam LanguageVariant languageVariant,
            HttpSession session) {

        LanguageVariant current =
                (LanguageVariant) session.getAttribute("languageVariant");

        // 同じ言語なら変更処理をしない
        if (languageVariant == current) {
            return "redirect:/";
        }

        // 学習対象言語をSessionに保存
        session.setAttribute("languageVariant", languageVariant);

        return "redirect:/";
    }
}
```

例えば、

```text
/language-variant?languageVariant=MAINLAND
```

へアクセスすると、

```java
@RequestParam LanguageVariant languageVariant
```

によってURLの `languageVariant` パラメータを受け取り、`LanguageVariant` 型へ変換する。

その値を、

```java
session.setAttribute("languageVariant", languageVariant);
```

によってSessionへ保存する。

---

# 14. 学習言語変更後にHomeへ戻す理由

例えばMAINLANDの問題を、

```text
問題1
 ↓
問題2
 ↓
問題3
 ↓
問題4
```

と学習している途中で、学習対象言語をTAIWANへ変更したとする。

そのまま問題4以降へ進んでしまうと、

```text
現在の設定
TAIWAN

すでに生成・取得済みの問題セット
MAINLAND
```

という不整合が発生する可能性がある。

そのため、学習対象言語を変更した場合はHome画面へ戻す。

また、将来的に問題セットをSessionへ保存するようになった場合には、

```java
session.removeAttribute("studyQuestions");
session.removeAttribute("studyCurrentPage");
```

などを追加し、現在の問題セットも破棄する予定である。

---

# 15. 学習対象言語の切り替えUI

`header.html` に学習対象言語のドロップダウンを追加する。

```html
<div class="d-flex align-items-center gap-2">

    <span th:text="#{header.studyLanguage}">
        学習言語
    </span>

    <div class="dropdown">

        <button class="btn btn-outline-light dropdown-toggle"
                type="button"
                data-bs-toggle="dropdown"
                aria-expanded="false">

            <span th:if="${session.languageVariant == null
                         || session.languageVariant.name() == 'MAINLAND'}">
                🇨🇳 普通话
            </span>

            <span th:if="${session.languageVariant != null
                         && session.languageVariant.name() == 'TAIWAN'}">
                🇹🇼 國語
            </span>

        </button>

        <ul class="dropdown-menu dropdown-menu-end">

            <!-- MAINLAND -->
            <li>
                <span th:if="${session.languageVariant == null
                             || session.languageVariant.name() == 'MAINLAND'}"
                      class="dropdown-item disabled">
                    ✓ 🇨🇳 普通话
                </span>

                <a th:if="${session.languageVariant != null
                          && session.languageVariant.name() != 'MAINLAND'}"
                   class="dropdown-item language-variant-link"
                   th:href="@{/language-variant(languageVariant='MAINLAND')}"
                   th:data-confirm-message="#{header.studyLanguage.confirmMainland}">
                    🇨🇳 普通话
                </a>
            </li>

            <!-- TAIWAN -->
            <li>
                <span th:if="${session.languageVariant != null
                             && session.languageVariant.name() == 'TAIWAN'}"
                      class="dropdown-item disabled">
                    ✓ 🇹🇼 國語
                </span>

                <a th:if="${session.languageVariant == null
                          || session.languageVariant.name() != 'TAIWAN'}"
                   class="dropdown-item language-variant-link"
                   th:href="@{/language-variant(languageVariant='TAIWAN')}"
                   th:data-confirm-message="#{header.studyLanguage.confirmTaiwan}">
                    🇹🇼 國語
                </a>
            </li>

        </ul>

    </div>
</div>
```

現在選択中の言語には `✓` を表示し、選択できない状態にする。

---

# 16. 学習言語変更時の確認ダイアログ

学習対象言語を変更すると、将来的には現在実行中のトレーニングも終了する。

そのため、誤操作を防ぐために確認ダイアログを表示する。

HTMLの `onclick` に直接メッセージを埋め込むのではなく、

```text
data-* 属性
+
header.js
```

で処理する。

## 16.1 layout.html

```html
<script th:src="@{/js/header.js}"></script>
```

を追加する。

## 16.2 header.js

```javascript
document.querySelectorAll('.language-variant-link').forEach(link => {
    link.addEventListener('click', function(event) {

        const message = this.dataset.confirmMessage;

        if (!confirm(message)) {
            event.preventDefault();
        }
    });
});
```

`.language-variant-link` を持つリンクがクリックされたとき、

```javascript
this.dataset.confirmMessage
```

から確認メッセージを取得する。

キャンセルされた場合は、

```javascript
event.preventDefault();
```

によってリンク遷移を中止する。

---

# 17. 学習言語変更メッセージの多言語化

## 17.1 messages.properties

```properties
header.studyLanguage=学習言語
header.studyLanguage.confirmMainland=学習言語を普通話に切り替えますか？\n切り替えると、現在のトレーニングは終了し、ホーム画面に戻ります。
header.studyLanguage.confirmTaiwan=学習言語を國語に切り替えますか？\n切り替えると、現在のトレーニングは終了し、ホーム画面に戻ります。
```

## 17.2 messages_en.properties

```properties
header.studyLanguage=Study Language
header.studyLanguage.confirmMainland=Switch the study language to Mandarin?\nYour current training session will end, and you will return to the Home page.
header.studyLanguage.confirmTaiwan=Switch the study language to Taiwanese Mandarin?\nYour current training session will end, and you will return to the Home page.
```

## 17.3 messages_zh_CN.properties

```properties
header.studyLanguage=学习语言
header.studyLanguage.confirmMainland=要将学习语言切换为普通话吗？\n切换后，当前训练将结束，并返回首页。
header.studyLanguage.confirmTaiwan=要将学习语言切换为国语吗？\n切换后，当前训练将结束，并返回首页。
```

## 17.4 messages_zh_TW.properties

```properties
header.studyLanguage=學習語言
header.studyLanguage.confirmMainland=要將學習語言切換為普通話嗎？\n切換後，目前的練習將結束，並返回首頁。
header.studyLanguage.confirmTaiwan=要將學習語言切換為國語嗎？\n切換後，目前的練習將結束，並返回首頁。
```

## 17.5 実行

`http://localhost:8080/` にアクセスし、学習対象言語を正常に切り替えられることを確認した。

### 普通話

![](../../images/0003-06.png)

### 國語

![](../../images/0003-07.png)

---

# 18. ヘッダーのUI修正

```bash
git commit -m "refactor: reorganize header UI"
```

学習対象言語と表示言語の切り替えを実装した結果、ヘッダー内の要素が増え、UIが窮屈になった。

主な問題は以下のとおり。

- 学習言語と表示言語のドロップダウンが横並びになっている
- 「こんにちは、ゲストさん」が横幅を使用する
- 今後ログイン・ログアウト機能を追加する予定
- 今後ハンバーガーメニューを追加する可能性がある

そこで、将来的には、

```text
中文造句工坊    学習言語 🇹🇼 國語 ▼    👤    ☰
```

のようなシンプルなヘッダーを目指す。

ユーザーアイコンは将来的に、

- カーソルを合わせるとユーザーIDを表示
- クリックするとユーザー関連メニューを表示
  - マイページ
  - アカウント設定
  - ログアウト

などへ拡張する。

ハンバーガーメニューには、アプリケーション全体に関するメニューを配置する。

現時点ではログイン機能やユーザーDBを実装していないため、今回は将来の機能を配置するための「器」だけを作成する。

---

# 19. Bootstrap Iconsの導入

## 19.1 pom.xml

```xml
<dependency>
    <groupId>org.webjars.npm</groupId>
    <artifactId>bootstrap-icons</artifactId>
    <version>1.11.3</version>
</dependency>
```

## 19.2 layout.html

Bootstrap IconsのCSSを読み込む。

```html
<!-- Bootstrap Icons -->
<link rel="stylesheet"
      th:href="@{/webjars/bootstrap-icons/font/bootstrap-icons.css}">
```

---

# 20. ユーザーアイコンの追加

これまで表示していた、

```text
こんにちは、ゲストさん
```

を削除し、ユーザーアイコンへ置き換える。

```html
<!-- ユーザーアイコン -->
<button type="button"
        class="btn p-0 text-white border-0"
        aria-label="ユーザー">
    <i class="bi bi-person-circle fs-4"></i>
</button>
```

学習言語や表示言語のドロップダウンは白枠で囲まれているが、ユーザーアイコンまで白枠で囲むとUIが重くなる。

そのため、ユーザーアイコンは枠のないシンプルなアイコンとして表示する。

現時点ではログイン機能がないため、ユーザーアイコン自体の機能はまだ実装しない。

---

# 21. 表示言語切り替えをハンバーガーメニューへ移動

表示言語の切り替えは常時ヘッダー上に表示する必要性が低いため、ハンバーガーメニュー内へ移動する。

一方、学習対象言語は現在どちらの中国語を学習しているのかを示す重要な設定なので、引き続きヘッダー上へ常時表示する。

```text
学習言語
    ↓
学習内容そのものに関係するため常時表示

表示言語
    ↓
変更頻度が低いためハンバーガーメニュー内へ移動
```

ハンバーガーメニューには現時点で、

- このアプリについて
- 表示言語切り替え

を配置する。

```html
<!-- ハンバーガーアイコン -->
<div class="dropdown">

    <button type="button"
            class="btn p-0 text-white border-0"
            data-bs-toggle="dropdown"
            aria-expanded="false"
            aria-label="メニュー">
        <i class="bi bi-list fs-3"></i>
    </button>

    <ul class="dropdown-menu dropdown-menu-end">

        <li>
            <a class="dropdown-item"
               th:href="@{/about}"
               th:text="#{home.about}">
                このアプリについて
            </a>
        </li>

        <li>
            <hr class="dropdown-divider">
        </li>

        <li>
            <span class="dropdown-header">
                表示言語
            </span>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=ja}">
                <span th:if="${#locale.language == 'ja'}">✓ </span>
                日本語
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=en}">
                <span th:if="${#locale.language == 'en'}">✓ </span>
                English
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=zh_CN}">
                <span th:if="${#locale.toString() == 'zh_CN'}">✓ </span>
                简体中文
            </a>
        </li>

        <li>
            <a class="dropdown-item"
               th:href="@{?lang=zh_TW}">
                <span th:if="${#locale.toString() == 'zh_TW'}">✓ </span>
                繁體中文
            </a>
        </li>

    </ul>
</div>
```

現在選択されている表示言語には `✓` を表示する。

これによってヘッダー上に常時表示する要素を減らし、今後ユーザー関連機能などを追加するためのスペースも確保する。

---

# 22. ヘッダーUI修正後の実行確認

`http://localhost:8080/` にアクセスしたところ、ヘッダーが以前より整理されていることを確認した。

![](../../images/0003-08.png)

現在のヘッダーは概ね、

```text
中文造句工坊       学習言語 🇹🇼 國語 ▼       👤    ☰
```

という構成になった。

それぞれの役割は以下のようになる。

```text
中文造句工坊
    ↓
Home

学習言語
    ↓
MAINLAND / TAIWAN の切り替え

👤
    ↓
将来のユーザー関連機能

☰
    ├─ このアプリについて
    └─ 表示言語
        ├─ 日本語
        ├─ English
        ├─ 简体中文
        └─ 繁體中文
```

これにより、学習対象言語のように学習中も意識する必要がある設定はヘッダーへ常時表示し、表示言語のように変更頻度の低い設定はハンバーガーメニューへまとめる構成となった。

---

# 23. 次にやること

**学習モードの実装**

今回作成した学習対象言語設定を利用し、今後は実際の問題データや学習処理を実装していく。