<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="180" alt="SearchUp アイコン" />

# SearchUp

### 上にスワイプしてすぐ検索し、Enterでアプリを開くLSPosedモジュール

[한국어](README.md) | [English](README.en.md) | **日本語**

</div>

SearchUpは、OnePlus OxygenOS 16のSystem Launcherにあるアプリドロワーを、すばやい検索画面として使えるようにするLSPosedモジュールです。

ホーム画面で上にスワイプすると、検索欄にすぐフォーカスが入り、キーボードが開きます。韓国語の初声検索とEnterでの起動も入れているので、アプリドロワーを開いてから検索欄をもう一度タップする手間を減らせます。

元のプロジェクトは[OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)です。このリポジトリは、OxygenOS 16対応とSearchUpの流れを追加したforkです。

## 機能

- 上スワイプでアプリドロワーに入ったとき、検索欄へ自動フォーカスしてキーボードを表示
- 韓国語/英語検索を改善し、ハングル初声検索に対応
- キーボードのEnterキーで最初の検索結果を起動
- 検索ボタン、下スワイプ検索、左スワイプのDiscover入口をアプリドロワー検索へリダイレクト
- 検索画面でBackを押したときに、キーボードと検索状態を整理
- 設定変更後、アプリ内からSystem Launcherを再起動
- 設定画面で韓国語/英語/中国語を簡単に切り替え

## 互換性

- 確認済み端末: OnePlus 13 / OxygenOS 16
- 確認済みランチャー: System Launcher `16.6.5`
- LSPosed scope: `com.android.launcher`
- アプリパッケージ: `com.wizpizz.onepluspluslauncher`
- モジュールバージョン: `1.0.0-oos16.0.7.201`
- 最小Android SDK: `27`
- 対象Android SDK: `35`
- 最小Xposedバージョン: `93`

**注意:** rootとLSPosedが必要です。ランチャー更新で内部クラスが変わると、一部または全部のhookが効かなくなることがあります。その場合、新しいAPKを出すまでは元のランチャー動作に戻るのが正常です。

## インストール

1. [Releases](https://github.com/devuterian/OnePlusPlusLauncher-OOS16/releases)から最新APKをダウンロードします。
2. APKをインストールします。
3. LSPosedでSearchUpモジュールを有効にします。
4. scopeにSystem Launcher、つまり`com.android.launcher`を追加します。
5. SearchUpアプリで`Apply changes: restart launcher`を押すか、端末を再起動します。

インストール後に動かない場合は、まずLSPosedのモジュール有効化とscopeを確認してください。この2つがないと、アプリを開いてもランチャー側では何も変わりません。

## 使い方

基本の流れはシンプルです。

1. ホーム画面で上にスワイプします。
2. キーボードが出たら、すぐアプリ名を入力します。
3. 目的のアプリが最初に出ていればEnterを押します。

設定画面の`SearchUp flow`は、基本的には有効のままで大丈夫です。`Keyboard behavior (advanced)`と`Redirects (advanced)`は、検索ボタン、下スワイプ、Discoverリダイレクトまで変えたいときだけ触ってください。

## ビルド

このプロジェクトはGradle Kotlin DSLを使っています。ローカルJDKは17基準です。

```sh
./gradlew test assembleRelease
```

生成物は通常のGradle出力パスに作られます。

- release APK: `app/build/outputs/apk/release/app-release.apk`
- debug APK: `app/build/outputs/apk/debug/app-debug.apk`

release署名は環境変数またはローカルの`.env`から読みます。このファイルはリポジトリに入れないでください。

```text
SIGNING_KEY_STORE_PATH=
SIGNING_KEY_ALIAS=
SIGNING_KEY_STORE_PASSWORD=
SIGNING_KEY_PASSWORD=
```

署名値がない場合、release signingはスキップされます。配布用APKを作るなら値を入れてください。

## トラブルシュート

- モジュール状態が`Not active yet`の場合は、LSPosedでSearchUpを有効にしてください。
- 設定がすぐ反映されない場合は、SearchUpアプリのランチャー再起動ボタンを押してください。
- ランチャー更新後に検索hookが壊れた場合は、確認済みSystem Launcherバージョンとrelease notesを先に比べてください。
- 動作がおかしくなった場合は、LSPosedでモジュールを無効化し、System Launcherを再起動すると元のランチャー動作に戻せます。

## ライセンス

このリポジトリは`AGPL-3.0`ライセンスです。詳しくは[LICENSE](LICENSE)を確認してください。

## クレジット

- 元のモジュール: [wizpizz/OnePlusPlusLauncher](https://github.com/wizpizz/OnePlusPlusLauncher)
- OxygenOS 16 adaptation: [zhangbaoshengrio/OnePlusPlusLauncher-OOS16](https://github.com/zhangbaoshengrio/OnePlusPlusLauncher-OOS16)
- SearchUp maintenance fork: [devuterian/OnePlusPlusLauncher-OOS16](https://github.com/devuterian/OnePlusPlusLauncher-OOS16)
- Hook framework: [YukiHookAPI](https://github.com/HighCapable/YuKiHookAPI)
