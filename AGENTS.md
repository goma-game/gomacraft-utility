# gomacraft-utility エージェント向け作業規則

共通の規則(応答・Issue 駆動・作業の心得・PR とマージの流れ)は GitHome の `AGENTS.md` と続きの `.agents/AGENTS.md`、Claude Code のエージェントの構成は GitHome の `.claude/claude-only.md` が正。ここにはこのリポジトリ固有のことだけを書く。

全体の規則は、持ち主の非公開の作業場のリポジトリ(手元ではこのリポジトリの親)が持つ。このリポジトリだけを開いたセッション(作業場を一緒に開いていない clone や、規則をこのリポジトリの根までしか読まないエージェント)には、その規則・スキル・入口が届かないので、作業の前にそちらの規則を読む。

## このリポジトリの位置づけ

- gomacraft(ATM10)のための自作のユーティリティ Mod(id は `gomacraft_utility`)を作るところまでを持つ(build・試験・GitHub Release への jar の配置)。サーバとクライアントへ配るのは gomabase(台帳に release を宣言する側)。
- 計画・配り方・機能の順番の決定と経緯は goma-org/gomabase#2295 が正。設計と違うことをしたくなったら、実装せずにそこへ提案する。
- Minecraft・NeoForge・JDK の版は gomacraft に合わせる。Minecraft の版の正は gomabase の宣言、NeoForge の版はパック(ATM10)が決める(gomabase は宣言しない)。組み立てに使う版は `gradle.properties` の 1 か所に置き、上げるときはサーバが実際に積む版を見てから上げる(ここに数字を写さない)。

## 作りの原則(goma-org/gomabase#2295 の決定)

- jar は 1 つ。サーバとクライアントの両方に、同じ版の宣言から入れる。
- 新しい要素を足さない: ブロック・アイテム・レジストリ・ワールドのデータを足さない(いつでも外せる形を保つ)。
- 通信は optional(この Mod の無いクライアントも入れる)。
- 機能ごとに、対象の Mod の有無と版で有効を決め、設定で個別に切れる。mixin の当て先が無い・壊れたら、その機能だけが止まり、起動は止めない。起動時に機能ごとの状態を 1 行ログに出す(gomabase の起動の検証がこの行を読む)。
- 他の Mod のコードは、ライセンスが許す範囲で写してよい。写すときは出典・元の版・ライセンスをコメントに書く(ユーザ決定 2026-11-28、goma-org/gomabase#2295)。この Mod のライセンスは MIT。
- 訳の中身は Mod の外(訳のパック)に置く。これは他の Mod のハードコードの英語を訳す辞書の話で、この Mod 自身の文(操作の名前・知らせの文)の訳は jar の `assets/gomacraft_utility/lang` に置く。

## 公開のリポジトリであること

- このリポジトリは public。秘密(token・鍵・パスワード)を jar にもリポジトリにも入れない。
- 内部のホスト名・IP・手元のパス・Discord の ID など、自宅の環境の内部の値を書かない(必要なら gomabase の Issue の番号で指す)。

## 記録の置き場

- この Mod の中の仕事・決定は、このリポジトリの Issue と PR に書く。gomabase へ書くのは、gomabase のファイルを触る境界の出来事(配布の宣言・起動の検証)と全体の計画(goma-org/gomabase#2295)だけ。
- 決定はラベル `decision` の Issue に残す。

## 作業規則

- main へ直接コミットしない。`.githooks/pre-commit` が拒否する。クラウドのセッションでは `.githooks/pre-push` が既定の枝と production への push・強制 push・ref の削除などを断る(断る範囲の正は `.githooks/pre-push` の冒頭。中身は GitHome の `.githooks/pre-push` と同じで、直すときは GitHome から全リポジトリへ)。
  - 新しい clone では `git config core.hooksPath .githooks` が要る。
- `.claude/settings.json` と `.claude/hooks/cloud-session-start.sh` は、ほかの作業リポジトリと同じ中身で置く(揃いは GitHome の検査が見る。ここだけ直さない)。
- 検査の正は CI(流れの正は GitHome の `.agents/AGENTS.md`「仕事の進め方」)。手元では変更したところの速い検査だけ回す。
  - CI(`.github/workflows/build.yml`)は、組み立てと JUnit・配る jar の中身・起動の自己検査(gametest のサーバ)を回す。何を確かめるかの正は `.github/scripts/check-build.sh`。
  - 起動の自己検査は、わざと壊した機能(`Features.SELFTEST_FEATURES`。自己検査のときだけ一覧に入る)の mixin が当て損ねても、それぞれの機能だけが FAILED になって起動が続き、最初の 1 行に間に合うことを見る。壊し方は 3 つ(当て先のメソッドが無い・`@At` が合わない・当て先のクラスが無い)。機能の枠(`gomacraft.utility.feature`・`mixinsupport`)を変えたら、ここが落ちないことを確かめる。
  - 手元に JDK 21 が無ければ、Docker の `gradle:<版>-jdk21` の箱で `./gradlew build runGameTestServer` を回す(手元の JDK は 21 と限らない)。
- 機能の足し方:
  - `Feature` を実装して `Features.catalog` に足す。mixin は `gomacraft.utility.mixin.<機能の名前>` の包みに置いて `gomacraft_utility.mixins.json` に並べる。
    - クライアントの画面などに当てる機能は、`Feature#clientOnly` を true にし(専用サーバでは `off(client only)` になる)、mixin は `client` の欄に置く(サーバには当て先のクラスが無い)。機能の定義(名前・対象の Mod)のクラスはサーバでも読まれるので、クライアントのクラスを名前で持たせず、中身は `init` から呼ぶ別のクラスに置く(例: `search/SearchFeatures` と `search/client/`)。
    - 当てる先の Mod は `build.gradle` の `compileOnly`(と当て先の試験のための `testRuntimeOnly`)に足し、版は `gradle.properties` の 1 か所に置く(パックが積む版。上げるときはパックの jar を見てから)。
    - L1 の `MixinTargetsTest` が、当て先のクラス・メソッド・`@At` の呼び出しと field・`@Shadow`・`@Accessor` が組み立ての版の jar に在るかを見る。読める `@At` は HEAD・RETURN・TAIL・INVOKE・FIELD だけで、ほかは落とす(見方を足すまで通さない)。クライアントの機能が実際に当たったかは、gametest のサーバでは見られず、gomabase のクライアントの起動の検証が起動時の 1 行で見る。
  - injector(`@Inject`・`@Redirect`・`@ModifyArg`、MixinExtras の `@WrapOperation` など)は全部 `require = 0` と `@Group(name = "gomacraft_utility$<機能>$<injector の名前>", min = 1)` で書く。group の名前は injector ごとに分ける。
    - `require` の不足は Mixin が Error を投げて起動ごと止まり、この Mod では拾えない。group の不足は、その機能だけの FAILED に落とせる。
    - group は中の成功を合計で数えるので、2 つの injector で共有すると、片方が当て損ねても `on` のままになる。
    - L1 の `MixinContractTest` が、書き忘れと group の名前の重なりを落とす。mixin と MixinExtras の注釈は、`@At`・`@Slice`・`@Group` などの決まった部品を除いて、全部 injector として見る(知らない注釈を素通りさせない)。
  - mixin の中身と event の listener は `Features.guard("<機能>", () -> …)` で包む。有効でない機能は走らず、例外を投げた機能は FAILED になって、以後はその機能のどの部分も走らない(FAILED の機能の、当たってしまった残りの mixin を止める)。
    - 起動のときに有効になった機能は、設定の画面から遊んでいる間に止める・動かし直せる(`FeatureRegistry#setActive`。起動の 1 行は変えず、FAILED にもしない)。だから `isOn` の答えを覚えて使い回さず、毎回問う。止めている間に溜まる古い作り置き・途中の操作の待ちは `onActiveChange` で捨てる。後から何度も呼ばれる登録の口(EMI の plugin・クライアントのコマンド)は、止めていても登録する `guardLoaded` で包み、中身を `guard` で分ける(止めている間に登録を飛ばすと、動かし直しても効かない)。別の機能の mixin(accessor など)が当たっているかを問うときは `isLoaded` で問う(`isOn` だと、その機能を止めただけで、こちらの機能まで黙って止まる)。起動のときに切ってあった機能は mixin が当たっていないので、入にしたら起動し直す(画面が「完了」のときに知らせる)。
    - 設定の画面の並び(タブと木)は `settings/SettingsLayout` に置く。機能・項目を足したら、そこへ置く(置き忘れは「その他」のタブに並ぶ)。
    - ⚠ 元の処理を包む mixin(`@WrapMethod`・`@WrapOperation` の `original.call`)は、`original.call` を guard の中に入れない(guard が例外を飲み、元の処理の例外まで隠す)。`Features.registry().isOn("<機能>")` が偽なら足した処理をせずに `original.call` だけを呼ぶ形で分ける(例: `ttf_glyph_lock`)。
  - 当て先のクラスは、起動時の 1 行の前に読み込ませる(`GomacraftMixinPlugin.preloadTargets`。初期化はしない)。クラスを読み込んだだけで副作用のあるものに当てるなら、先にここで困らないかを確かめる。
  - 自己検査(gametest のサーバ)で当てる先の Mod を動かすときは `localRuntime` に足す(公式の maven が無い Mod は cursemaven —— gomabase の配布と同じ取得元)。
  - 専用サーバだけで要る直し(クライアントの部品をサーバで呼ぶ不具合など)は、mixin の設定の `server` の一覧に置く(シングルプレイの内蔵サーバには当てない)。
  - 通信を足すときは payload を全部 `optional()` にし、送る前に相手がその channel を持つかを確かめる(`hasChannel`。この Mod の無い相手もいる)。
- 起動時の状態の 1 行の形(`StatusLine`)は gomabase の起動の検証との約束。変えるときは gomabase の読み手(`tools/mc-client-boot.py`・`tools/scan-server-logs.py`)と同時に変える(見本の文字列を両方の試験に置いてある)。jar の名前(`gomacraft_utility-<mod_version>.jar`)も約束(`check-build.sh jar` が固定する)。
- release: `gradle.properties` の `mod_version` を上げた PR をマージしてから、main の commit に `v<mod_version>` の tag を付けて `git push origin refs/tags/v<mod_version>` で押す(`git push origin v<mod_version>` の形は、GitHome のフックが恒久の枝への push として止める)。release の workflow が検査を回し、jar と sha256 を GitHub Release に置く(同じ tag の作り直しはしない —— gomabase が sha256 をピンする)。
