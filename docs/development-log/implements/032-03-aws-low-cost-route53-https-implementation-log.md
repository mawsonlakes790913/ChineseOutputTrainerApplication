# 032-03 AWS低コスト構成③｜Route 53の切り替えとEC2側でのHTTPS構築（実装ログ）

本章では、032-02で動作確認したElastic BeanstalkのSingle Instance環境に独自ドメインを切り替え、ALBを使用しない構成でHTTPS通信を設定する。元の032実装ログの第4・第5節を分割したもので、実際の操作、設定値、確認結果、画像および参考資料を残している。

---

## 1. Route 53をSingle Instance環境へ切り替える

新しいSingle Instance環境でSpring BootアプリケーションおよびRDSへの接続が正常に動作することを確認できたため、独自ドメイン `chinese-output-forge.com` の接続先を新しい環境へ切り替える。

この時点では新しいSingle Instance環境のHTTPS設定はまだ行っていないため、まずHTTPで独自ドメインから新環境へアクセスできることを確認する。

![](../../images/0032-20.png)

なお、切り替え後に問題が発生した場合に旧環境へ戻せるよう、既存のLoad Balanced環境はまだ削除しない。


#### 現在のRoute 53の設定を確認する

Route 53 → ホストゾーン → `chinese-output-forge.com`

を開き、現在のAレコードを確認する。

![](../../images/0032-17.png)

現在のRoute 53のAレコードは、以下のように設定されている。

- レコード名：`chinese-output-forge.com`
- レコードタイプ：`A`
- ルーティングポリシー：シンプル
- エイリアス：有効
- ルーティング先：既存のElastic Beanstalk環境で使用しているApplication Load Balancer（ALB）

ルーティング先として設定されているALBを確認すると、既存のElastic BeanstalkのLoad Balanced環境で使用しているALBであることが分かる。

このALBはInternet-facingとして構成され、`ap-northeast-1a` と `ap-northeast-1c` の2つのAvailability Zoneを使用している。

![](../../images/0032-18.png)

したがって、現在の独自ドメインへの通信経路は以下となっている。

`chinese-output-forge.com`

↓ Route 53

既存ALB

↓

既存のElastic Beanstalk環境のEC2

これからRoute 53のAレコードを変更し、`chinese-output-forge.com` の接続先を新しいSingle Instance環境へ切り替える。

なお、切り替え後に問題が発生した場合に旧環境へ戻せるよう、この時点では既存のALBおよびLoad Balanced環境は削除しない。

また、NSレコード、SOAレコード、ACM証明書検証用のCNAMEレコードについては変更しない。

#### Route 53のAレコードを新しいSingle Instance環境へ切り替える

新しいSingle Instance環境には、Elastic BeanstalkによってElastic IP `13.193.140.206` が割り当てられている。

そのため、現在既存ALBを参照している `chinese-output-forge.com` のAレコードを、新しいSingle Instance環境のElastic IPへ変更する。

Route 53 → ホストゾーン → `chinese-output-forge.com`

から既存のAレコードを選択し、「レコードを編集」を開く。

現在はALBへのAliasレコードとなっているため、以下のように変更する。

- レコード名：`chinese-output-forge.com`
- レコードタイプ：`A`
- エイリアス：無効
- 値：`13.193.140.206`
- TTL：300秒
- ルーティングポリシー：シンプル

![](../../images/0032-19.png)

変更前：

`chinese-output-forge.com`

↓ Route 53（Alias）

既存ALB

↓

既存EC2


変更後：

`chinese-output-forge.com`

↓ Route 53（Aレコード）

Elastic IP `13.193.140.206`

↓

新しいSingle Instance環境のEC2

この時点ではHTTPSの設定はまだ行っていないため、Route 53の変更後はまず `http://chinese-output-forge.com` へアクセスし、新しいSingle Instance環境へ正常に接続できることを確認する。

なお、問題が発生した場合に元の構成へ戻せるよう、既存のLoad Balanced環境およびALBはまだ削除しない。


#### 独自ドメインから新しいSingle Instance環境へのアクセスを確認する

Route 53のAレコードを変更した後、以下のURLへアクセスする。

`http://chinese-output-forge.com`

新しいSingle Instance環境のSpring Bootアプリケーションが正常に表示されることを確認する。


これにより、Route 53のAレコードが新しいElastic IP `13.193.140.206` を参照し、独自ドメインから新しいSingle Instance環境へ通信できていることを確認できる。

![](../../images/0032-20.png)

現在の通信経路は以下となる。

`chinese-output-forge.com`

↓ Route 53

Elastic IP `13.193.140.206`

↓

新しいSingle Instance環境のEC2

なお、この時点ではHTTPSの設定はまだ行っていないため、HTTPで動作確認を行う。


---

## 2. Single Instance環境でHTTPS通信を設定

Route 53のAレコードを変更し、独自ドメイン `chinese-output-forge.com` から新しいSingle Instance環境へHTTPでアクセスできることを確認した。

しかし、現在はHTTP通信のみであり、ブラウザとEC2間の通信は暗号化されていない。

以前のLoad Balanced環境では、ALBにSSL/TLS証明書を設定し、ALBでHTTPS通信を終端していた。

一方、新しいSingle Instance環境ではALBを使用しないため、これまでとは異なる方法でHTTPS通信を確立する必要がある。

最終的な構図

![](../../images/0032-21.png)

今回はAWS公式ドキュメントの「インスタンスでのHTTPS終端の設定」に沿って、Single Instance環境でHTTPS通信を有効にする。

参考：

- [AWS公式：Elastic Beanstalk環境のHTTPSの設定](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/configuring-https.html)
- [AWS公式：インスタンスでのHTTPS終端の設定](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/https-singleinstance.html)

#### 実装の順番

AWS公式ドキュメント「インスタンスでのHTTPS終端の設定」に沿う。
実装にあたっては、AWS公式ドキュメントに記載されている設定と、今回の環境で追加対応が必要な作業を区別して進める。

##### 1. SSL/TLS証明書と秘密鍵を用意する【補足作業】

HTTPS通信に必要なSSL/TLS証明書と秘密鍵を用意する。

AWS公式ドキュメントのJava SE向け設定例では、証明書と秘密鍵を使用することが前提となっているが、その取得方法までは具体的に説明されていない。

今回は無料のLet's Encryptを利用して証明書を取得する。

##### 2. Elastic BeanstalkのHTTPS設定ファイルを作成する【AWS公式】

Elastic Beanstalkの設定ファイルを使用して、EC2インスタンスでHTTPS通信を終端するための設定を行う。

公式ドキュメントのJava SE向け手順を参考に、証明書と秘密鍵の配置、およびHTTPS通信を受け付けるための設定を追加する。

なお、現在の環境はAmazon Linux 2023を使用しているため、設定ファイルの配置方法などは現在のプラットフォームに対応させる。

##### 3. セキュリティグループで443番ポートを許可する【AWS公式】

HTTPS通信を受け付けるため、EC2インスタンスのセキュリティグループでTCP 443番ポートを開放する。

AWS公式ドキュメントでは、`.ebextensions` の設定ファイルを使用して、セキュリティグループにインバウンドルールを追加する方法が紹介されている。

##### 4. Elastic Beanstalkへ再デプロイする【実装作業】

HTTPS設定を追加したアプリケーションをElastic Beanstalkへ再デプロイする。

デプロイ後、設定が正常に反映され、アプリケーションが起動することを確認する。

##### 5. HTTPS通信を確認する【動作確認】

ブラウザから以下のURLへアクセスする。

`https://chinese-output-forge.com`

SSL/TLS証明書が正常に認識され、HTTPS通信でSpring Bootアプリケーションへアクセスできることを確認する。

##### 6. 証明書の安全な管理と自動更新を設定する【AWS公式＋補足作業】

AWS公式ドキュメントでは、秘密鍵をソースコード管理に含めず、Amazon S3などに安全に保存する方法が案内されている。

また、Let's Encryptの証明書には有効期限があるため、継続してHTTPS通信を利用できるよう、自動更新の仕組みを追加する。

証明書の自動更新については、AWS公式のHTTPS設定手順とは別に対応する。

参考：

- [AWS公式：インスタンスでのHTTPS終端の設定](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/https-singleinstance.html)
- [AWS公式：Java SEを実行するEC2インスタンスでHTTPSを終了する](https://docs.aws.amazon.com/ja_jp/elasticbeanstalk/latest/dg/https-singleinstance-java.html)

#### 1. SSL/TLS証明書と秘密鍵を用意する

##### どうやってSSL/TLS証明書を用意するか

#### SSL/TLS証明書の発行方法を選択する

Single Instance環境ではALBを使用しないため、EC2インスタンス上で利用できるSSL/TLS証明書を用意する必要がある。

今回は以下の2つの方法を比較する。

###### ACMでエクスポート可能なSSL/TLS証明書を発行

AWS Certificate Manager（ACM）を使用して、エクスポート可能なパブリック証明書を発行する方法。

2025年6月から、ACMで発行したパブリック証明書をエクスポートし、EC2上で利用できるようになった。

**特徴**

- AWSのサービスであるACMを利用して証明書を発行・管理できる。
- 証明書、証明書チェーン、秘密鍵をエクスポートしてEC2に配置できる。
- 証明書の発行時に「エクスポートを許可」を有効にする必要がある。
- 通常のドメイン証明書は発行時に15ドル、更新時にも15ドルの費用が発生する。
- ACM側で証明書が更新されても、EC2への再配置は自動では行われないため、別途仕組みを用意する必要がある。

参考文献：

[ACMの証明書をエクスポートしてEC2で使う](https://qiita.com/teshima_daichi/items/8a2bf99de53f238f48b6)

###### Let’s Encryptで発行

Let's Encryptは、無料でSSL/TLS証明書を発行できる認証局である。

Certbotというツールを使用することで、EC2上で証明書を取得できる。

**特徴**

- SSL/TLS証明書を無料で発行できる。
- ACMを使用せずに、EC2上で証明書を取得・管理できる。
- Certbotを使用して証明書の取得や更新を行える。
- 証明書の有効期間が短いため、定期的な更新が必要となる。
- cronなどを利用して証明書の更新を自動化できる。
- 証明書を使用するWebサーバー側の設定が必要となる。

参考文献：

[やさしいLet’s EncryptでSSL証明書を発行する方法](https://oji-cloud.net/2020/09/29/post-5570/)

###### 今回採用する方法

今回は、**Let's Encryptを使用してSSL/TLS証明書を発行する。**

今回の構成変更は、Elastic BeanstalkのLoad Balanced環境からSingle Instance環境へ移行し、AWSの運用コストを削減することが目的である。

ACMのエクスポート可能な証明書は有料である一方、Let's Encryptでは無料で証明書を発行できる。

そのため、今回はLet's EncryptとCertbotを使用し、証明書の取得・設定・更新を行う。

#### Let’s EncryptでSSL証明書を発行する

##### 証明書を発行する前に

以下の点を確認する。

###### SSL/TLS 証明書に使用する独自ドメインが取得済みである

chinese-output-forge.comを取得しているのでクリア

###### 証明書を発行するEC2 のインバウンドに80,443ポートが許可されている

現在、EC2のセキュリティグループではHTTP（80番ポート）を許可している。
HTTPS通信を利用できるようにするため、HTTPS（443番ポート）についてもインバウンドルールを追加する。

確認すると、セキュリティグループchinese-output-forge-public-ec2-sgのインバウンドルールにまだHTTPSが含まれていなかった。
![](../../images/0032-22.png)

HTTPSもインバウンドルールに追加した。

![](../../images/0032-23.png)

###### メールアドレスの準備

Let's Encryptで証明書を発行する際には、Certbotでメールアドレスを入力する。
証明書の有効期限などに関する通知に使用されるため、受信可能なメールアドレスを準備する。

##### Let’s Encrypt のCertbotクライアントをインストール

Let's EncryptからSSL/TLS証明書を取得するため、EC2にCertbotをインストールする。

###### Certbotとは

Certbotとは、**Let's EncryptのSSL/TLS証明書を取得・更新するためのクライアントツール**である。

Certbotを利用することで、ドメインの所有確認を行い、Let's EncryptからSSL/TLS証明書を取得できる。

また、Let's Encryptの証明書には有効期限があるため、Certbotは証明書の更新にも利用される。

今回の構成では、`chinese-output-forge.com` のSSL/TLS証明書を取得するためにCertbotをEC2へインストールする。

###### Certbotのインストール前準備

まず、Certbotのインストールに必要なパッケージをインストールする。
CertbotはPythonで作られたツールであるため、実行環境としてPython 3が必要となる。

ここでは以下のパッケージをインストールする。

- `python3`：Certbotを実行するためのPython 3
- `augeas-libs`：設定ファイルを解析・編集するためのライブラリ
- `pip`：Pythonのパッケージをインストール・管理するためのツール

```bash
sudo dnf install -y python3 augeas-libs pip
```

次に、Pythonの仮想環境を `/opt/certbot/` に作成する。

```bash
sudo python3 -m venv /opt/certbot/
```

作成した仮想環境内のpipを最新版へ更新する。

```bash
sudo /opt/certbot/bin/pip install --upgrade pip
```

続いて、仮想環境内にCertbotをインストールする。

```bash
sudo /opt/certbot/bin/pip install certbot
```

`/usr/bin/` にCertbotへのシンボリックリンクを作成し、`certbot` コマンドとして実行できるようにする。

```bash
sudo ln -s /opt/certbot/bin/certbot /usr/bin/certbot
```

> `/usr/bin/` への書き込みには管理者権限が必要なため、`sudo` を付けて実行する。

Certbotが正常にインストールされたことを確認する。

```bash
certbot --version
```

以下のようにバージョンが表示されたため、Certbotのインストールが完了したことを確認できた。

```text
certbot 4.2.0
```

###### Webサーバーを停止する

証明書を発行する前に、現在起動しているWebサーバーを停止する。

今回のElastic Beanstalk環境ではnginxを使用しているため、まずnginxが起動していることを確認する。

```bash
sudo systemctl status nginx
```

`active (running)` であることを確認したら、nginxを停止する。

```bash
sudo systemctl stop nginx
```

停止後、再度状態を確認する。

```bash
sudo systemctl status nginx
```

`inactive (dead)` となっていれば、nginxの停止は完了している。

nginxを停止すると、一時的にWebサイトへアクセスできない状態となる。


###### Certbotを実行してSSL/TLS証明書を取得する

Certbotのインストールが完了したら、スタンドアロンモードでCertbotを実行してSSL/TLS証明書を取得する。

スタンドアロンモードでは、Certbot自身が一時的にWebサーバーを起動してドメインの認証を行うため、事前にnginxを停止して80番ポートを空けておく。

```bash
sudo systemctl stop nginx
```

続いて、Certbotをスタンドアロンモードで実行する。

```bash
sudo certbot certonly --standalone
```

コマンドを実行すると、対話形式で必要な情報の入力を求められる。

1. **証明書に関する通知を受け取るメールアドレス**
   - 受信可能なメールアドレスを入力する。

2. **Let's Encryptの利用規約への同意**
   - `Y` を入力して同意する。

3. **メールアドレス情報を共有するか**
   - 今回は `N` を入力する。

4. **証明書を発行するドメイン名**
   - 今回は以下を入力する。

```text
chinese-output-forge.com
```

証明書の取得に成功すると、以下のように表示される。

```text
Successfully received certificate.
```

取得した証明書は、以下のディレクトリに保存される。

```text
/etc/letsencrypt/live/chinese-output-forge.com/
```

主に使用するファイルは以下となる。

- `fullchain.pem`：サーバー証明書と中間証明書を含む証明書チェーン
- `privkey.pem`：証明書に対応する秘密鍵

###### nginxにSSL/TLS証明書を設定する

取得した証明書を使用して、nginxが443番ポートでHTTPS通信を受け付けられるように設定する。

設定では主に以下を指定する。

```nginx
server {
    listen 443 ssl;
    server_name chinese-output-forge.com;

    ssl_certificate /etc/letsencrypt/live/chinese-output-forge.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/chinese-output-forge.com/privkey.pem;

    ...
}
```

ただし、今回のnginxはElastic Beanstalkによって管理されている。

そのため、既存のnginx設定ファイルを直接書き換えるのではなく、Elastic Beanstalkの仕組みに合わせてHTTPS設定を追加する。

###### nginxを起動する

HTTPSの設定が完了したら、nginxを起動する。

```bash
sudo systemctl start nginx
```

その後、以下へアクセスしてHTTPS通信が正常に行われることを確認する。

```text
https://chinese-output-forge.com
```

確認できた


###### nginxの設定ファイルを確認する

####### どこに設定ファイルがあるのか

nginxの設定ファイルは環境によって異なる。

今回のElastic Beanstalk環境では、メインの設定ファイルとして以下が使用されている。

```text
/etc/nginx/nginx.conf
```

また、このファイルから別の設定ファイルを読み込む構成になっている。

####### 引用サイトでは `/etc/nginx/conf.d/default.conf` が設定ファイルだったが？

引用サイトでは、Webサイトの設定が以下に記述されているため、このファイルにHTTPSの設定を追加している。

```text
/etc/nginx/conf.d/default.conf
```

一方、今回のElastic Beanstalk環境では `default.conf` は使用されておらず、nginxの設定構成が異なる。

そのため、引用サイトと同じファイルをそのまま編集するのではなく、実際に使用されているnginxの設定を確認する。

####### どうやって設定ファイルを探すのか

以下のコマンドを実行する。

```bash
sudo nginx -T
```

`nginx -T` を実行すると、現在nginxが読み込んでいる設定ファイルと、その設定内容を確認できる。

####### なにがあればそこが設定ファイルだとわかるのか

`nginx -T` を実行すると、nginxが現在使用している設定を確認できる。

今回の出力では、最初に以下が表示された。

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
# configuration file /etc/nginx/nginx.conf:
```

この出力から、nginxがメインの設定ファイルとして以下を使用していることが分かる。

```text
/etc/nginx/nginx.conf
```

さらに、この `nginx.conf` の中には以下の記述がある。

```nginx
include conf.d/*.conf;
```

`include` は、別の設定ファイルを読み込むための命令である。

つまり今回のnginxは、

1. `/etc/nginx/nginx.conf` を読み込む
2. そこから `/etc/nginx/conf.d/*.conf` も読み込む

という構成になっている。

そのため、`/etc/nginx/conf.d/` にHTTPS用の `.conf` ファイルを作成すれば、その内容もnginxの設定として読み込ませることができる。

今回は以下を新しく作成する。

```text
/etc/nginx/conf.d/https.conf
```

###### SSL/TLS証明書をnginxに設定する

####### /etc/nginx/conf.d/https.confの作成

Let's Encryptで取得したSSL/TLS証明書をnginxに設定し、443番ポートでHTTPS通信を受け付けられるようにする。

以下のコマンドで、HTTPS用のnginx設定ファイルを新規作成する。

```bash
sudo vi /etc/nginx/conf.d/https.conf
```

以下の内容を記述する。

```nginx
server {
    listen 443 ssl;
    server_name chinese-output-forge.com;

    ssl_certificate /etc/letsencrypt/live/chinese-output-forge.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/chinese-output-forge.com/privkey.pem;

    ssl_session_timeout 5m;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;

    location / {
        proxy_pass http://localhost:5000;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

![](../../images/0032-26.png)

主な設定内容は以下の通り。

- `listen 443 ssl`
  - 443番ポートでHTTPS通信を受け付ける
- `server_name chinese-output-forge.com`
  - HTTPS通信の対象となるドメインを指定
- `ssl_certificate`
  - Let's Encryptで取得したSSL/TLS証明書を指定
- `ssl_certificate_key`
  - 証明書に対応する秘密鍵を指定
- `proxy_pass http://localhost:5000`
  - nginxが受け取ったリクエストを、5000番ポートで動作しているSpring Bootへ転送する
- `X-Forwarded-Proto https`
  - 元のリクエストがHTTPSであったことをSpring Boot側へ伝える

記述後、以下の操作で保存して終了する。

```text
Esc → :wq → Enter
```

設定方法については、Let's EncryptおよびCertbotの導入手順として以下の記事を参考にした。

https://softwarenote.info/p3954/

nginxの設定内容については、Elastic BeanstalkのJava SE環境でHTTPSを終端するAWS公式ドキュメントを参考にし、今回のドメイン名、Let's Encryptの証明書パス、Spring Bootのポート番号など、自分の環境に合わせて変更した。

https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/https-singleinstance-java.html

###### 設定の確認

sudo systemctl reload nginx
で再読み込み

sudo systemctl status nginx
で active (running) になっていることを確認

![](../../images/0032-24.png)

nginxの再読み込みまで正常に完了した。

現時点では、
443 → nginx（Let's Encrypt証明書でTLS終端）→ localhost:5000 → Spring Boot
という設定がnginxに反映されている。

##### ブラウザで確認

ブラウザでhttps://chinese-output-forge.comにアクセス

![](../../images/0032-25.png)

HTTPSで正常にアプリケーションへアクセスできることを確認した。
これでいまの構成図はこうなる

![](../../images/0032-21.png)

---
