# gomacraft-utility エージェント向け作業規則

共通の規則(応答・Issue 駆動・エージェントの構成・作業の心得・PR とマージの流れ)は GitHome の `AGENTS.md` が正。ここにはこのリポジトリ固有のことだけを書く。

## このリポジトリの位置づけ

- gomacraft(ATM10)のための自作のユーティリティ Mod(id は `gomacraft_utility`)を作るところまでを持つ(build・試験・GitHub Release への jar の配置)。サーバとクライアントへ配るのは gomabase(台帳に release を宣言する側)。
- 計画・配り方・機能の順番の決定と経緯は goma-org/gomabase#2295 が正。設計と違うことをしたくなったら、実装せずにそこへ提案する。
- Minecraft・NeoForge・JDK の版は gomacraft に合わせる。版の正は gomabase の宣言(ここに数字を写さない)。

## 作りの原則(goma-org/gomabase#2295 の決定)

- jar は 1 つ。サーバとクライアントの両方に、同じ版の宣言から入れる。
- 新しい要素を足さない: ブロック・アイテム・レジストリ・ワールドのデータを足さない(いつでも外せる形を保つ)。
- 通信は optional(この Mod の無いクライアントも入れる)。
- 機能ごとに、対象の Mod の有無と版で有効を決め、設定で個別に切れる。mixin の当て先が無い・壊れたら、その機能だけが止まり、起動は止めない。起動時に機能ごとの状態を 1 行ログに出す(gomabase の起動の検証がこの行を読む)。
- 他の Mod のコードを写さない(mixin で当てるだけ)。ライセンスは MIT。
- 訳の中身は Mod の外(訳のパック)に置く。

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
- 検査の正は CI(流れの正は GitHome の `AGENTS.md`「仕事の進め方」)。手元では変更したところの速い検査だけ回す。
