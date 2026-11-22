#!/bin/sh
# SessionStart: クラウドでこのリポジトリだけを開いたとき、GitHome が付いていないことを文脈へ出す。
# 手元では何もしない(CLAUDE_CODE_REMOTE が無ければ即 0 で抜ける)。
#
# なぜ要るか: クラウドのセッションは付けたリポジトリしか clone しない。GitHome(全リポジトリ共通の
# 規則・入口スクリプト・スキル・フック)が無いまま、PR やマージへ進まないようにする。
# 複数リポジトリのセッションは settings.json の hooks を読まない(docs)ので、ここが動くのは
# このリポジトリ単体で開いたときだけ。規則の正は GitHome の AGENTS.md(このファイルは各作業リポジトリに
# 同じ中身で置く)。
set -u
[ -n "${CLAUDE_CODE_REMOTE:-}" ] || exit 0

here=${CLAUDE_PROJECT_DIR:-$(pwd)}
# クラウドの複数リポジトリのセッションはクローンを並べて置く。隣に GitHome が在れば、そう言って終わる。
for dir in "$(dirname "$here")"/*/; do
  if [ -f "${dir}.agents/bin/pr-and-watch.sh" ] && [ -f "${dir}AGENTS.md" ]; then
    echo "GitHome: ${dir%/}(全リポジトリ共通の規則・入口・スキルはそこに在る)"
    exit 0
  fi
done

cat <<'EOF'
## GitHome が付いていない(クラウド・SessionStart)
このクラウドセッションには GitHome(全リポジトリ共通の規則・入口スクリプト・スキル・フック)が付いていない。
- 読む・調べる・設計する・実装してこのリポジトリのゲートを回すまでは、AGENTS.md に従って進めてよい。
- PR を出す・マージする・deploy する作業は、GitHome を一緒に付けて開き直すようユーザに頼んで止まる
  (入口が CI の待ち・deploy 中のマージ止めなどを 1 手に畳んでいる。素のコマンドで代わりにやらない)。
- クラウドは PR まで(マージと deploy は手元)。
EOF
exit 0
