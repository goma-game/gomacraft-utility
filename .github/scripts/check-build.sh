#!/bin/sh
# build の後に、配る jar と起動の自己検査のログを確かめる(CI と release の両方が呼ぶ)。
#
# 1. jar: build/libs に jar がちょうど 1 つ在り、Mod の宣言を持ち、自己検査のコード(selftest の source set)を含まない。
# 2. 自己検査のログ(gradlew runGameTestServer の後): 機能ごとの状態の行が在り、全部の行が約束の文法に合い、
#    わざと壊した機能 selftest_broken が FAILED になっている(= 壊れた mixin がその機能だけを止め、起動は続いた)。
#    文法の正は src/main/java/gomacraft/utility/feature/StatusLine.java(gomabase の起動の検証も同じ形を読む)。
#
# 使い方: sh .github/scripts/check-build.sh jar
#         sh .github/scripts/check-build.sh selftest <latest.log>
set -eu

case "${1:-}" in
  jar)
    set -- build/libs/*.jar
    [ "$#" -eq 1 ] && [ -f "$1" ] || { echo "NG: build/libs の jar が 1 つでない: $*" >&2; exit 1; }
    jar=$1
    listing=$(unzip -Z1 "$jar")
    printf '%s\n' "$listing" | grep -qx 'META-INF/neoforge.mods.toml' || { echo "NG: $jar に neoforge.mods.toml が無い" >&2; exit 1; }
    printf '%s\n' "$listing" | grep -qx 'gomacraft_utility.mixins.json' || { echo "NG: $jar に mixin の設定が無い" >&2; exit 1; }
    if printf '%s\n' "$listing" | grep -E '^(gomacraft/utility/selftest/|data/)'; then
      echo "NG: $jar に自己検査のコードかワールドのデータが入っている(上の行)" >&2
      exit 1
    fi
    echo "OK: $jar"
    ;;
  selftest)
    log=${2:?自己検査のログのパスが要る}
    [ -f "$log" ] || { echo "NG: ログが無い: $log" >&2; exit 1; }
    if ! lines=$(grep -o 'gomacraft_utility: features .*$' "$log"); then
      echo "NG: 機能ごとの状態の行が無い($log)" >&2
      exit 1
    fi
    printf '%s\n' "$lines"
    token='[a-z0-9_]+=(on|off|FAILED)(\([^()]*\))?'
    # grep の rc: 0 = 合わない行が在る / 1 = 全部合う / それ以外 = 読めない(どれも黙って通さない)
    set +e
    bad=$(printf '%s\n' "$lines" | sed 's/^gomacraft_utility: features //' | grep -Evx "none|$token( $token)*")
    rc=$?
    set -e
    case $rc in
      1) ;;
      0) echo "NG: 文法に合わない行: $bad" >&2; exit 1 ;;
      *) echo "NG: 行を読めない(grep rc=$rc)" >&2; exit 1 ;;
    esac
    printf '%s\n' "$lines" | grep -q 'selftest_broken=FAILED(' \
      || { echo "NG: わざと壊した機能が FAILED になっていない(壊れた mixin を拾えていない)" >&2; exit 1; }
    echo "OK: 壊れた機能だけが止まり、起動は続いた"
    ;;
  *)
    echo "使い方: $0 jar | selftest <latest.log>" >&2
    exit 2
    ;;
esac
