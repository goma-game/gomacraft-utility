#!/bin/sh
# fabric 版(fabric/)の build の後に、配る jar と起動の自己検査のログを確かめる(build-fabric と release-fabric が呼ぶ)。
# forge 版の check-build.sh の対。cwd は fabric/ にして呼ぶ。
#
# 1. jar: build/libs に jar がちょうど 1 つ在り、名前が gomacraft_utility-fabric-<mod_version>.jar で、Mod の宣言
#    (fabric.mod.json)と mixin の設定を持ち、ワールドのデータを含まない。
#    ⚠ 名前は gomabase との約束(fabric のサーバとクライアントの配布の宣言がこの名前で読む)。forge 版の
#    gomacraft_utility-<版>.jar と取り違えないよう、-fabric- を挟む。
# 2. 自己検査のログ(gradlew runSelftestServer の後): 機能ごとの状態の行が在り、全部の行が約束の文法に合い、
#    わざと壊した機能(当て先のメソッドが無い・@At が合わない・当て先のクラスが無い)が、⚠ 最初の 1 行で
#    全部 FAILED になっている(= 壊れた mixin がその機能だけを止め、起動は続いた)。
#    文法の正は fabric/src/main/java/gomacraft/utility/feature/StatusLine.java(forge 版と同じ形。gomabase の起動の
#    検証も同じ形を読む)。
# 3. 写し: forge 版から直さずに写したファイル(下の COPIES)が、forge 版と同じ中身のままか。forge 版だけを直して、
#    fabric 版が黙って古いまま残るのを止める(どちらかを直したら、もう片方も揃える。fabric 版だけ違う形にするなら、
#    一覧から外し、冒頭に「fabric 版:」で違いを書く)。build-fabric.yml は forge 版の src/** の変更でも回る。
#
# 使い方(fabric/ で): sh ../.github/scripts/check-fabric-build.sh jar
#                     sh ../.github/scripts/check-fabric-build.sh selftest <latest.log>
#                     sh ../.github/scripts/check-fabric-build.sh copies
set -eu

# forge 版(../src)と同じ中身のはずのファイル(src/ からの相対パス)
COPIES='
main/java/gomacraft/utility/feature/Feature.java
main/java/gomacraft/utility/feature/FeatureConfig.java
main/java/gomacraft/utility/feature/FeatureState.java
main/java/gomacraft/utility/feature/FeatureStatus.java
main/java/gomacraft/utility/feature/StatusLine.java
main/java/gomacraft/utility/feature/TomlLines.java
main/java/gomacraft/utility/mixin/selftest_at/SelftestAtMixin.java
main/java/gomacraft/utility/mixin/selftest_broken/SelftestBrokenMixin.java
main/java/gomacraft/utility/mixin/selftest_missing_class/SelftestMissingClassMixin.java
main/java/gomacraft/utility/mixinsupport/GomacraftMixinPlugin.java
main/java/gomacraft/utility/mixinsupport/MixinFailureHandler.java
main/java/gomacraft/utility/screenshot/ScreenshotRules.java
main/java/gomacraft/utility/screenshot/server/InboxState.java
main/java/gomacraft/utility/screenshot/server/Spool.java
main/java/gomacraft/utility/settings/ClientSettings.java
main/java/gomacraft/utility/settings/Settings.java
test/java/gomacraft/utility/MixinContractTest.java
test/java/gomacraft/utility/StatusLineContractTest.java
test/java/gomacraft/utility/feature/FeatureConfigTest.java
test/java/gomacraft/utility/feature/FeatureConfigUpdateTest.java
test/java/gomacraft/utility/screenshot/ScreenshotRulesTest.java
test/java/gomacraft/utility/screenshot/server/InboxStateTest.java
test/java/gomacraft/utility/screenshot/server/SpoolTest.java
test/java/gomacraft/utility/settings/ClientSettingsTest.java
'

case "${1:-}" in
  copies)
    bad=0
    count=0
    for path in $COPIES; do
      count=$((count + 1))
      if [ ! -f "../src/$path" ] || [ ! -f "src/$path" ]; then
        echo "NG: 写しの片方が無い: $path(forge 版 ../src・fabric 版 src)" >&2
        bad=1
      elif ! cmp -s "../src/$path" "src/$path"; then
        echo "NG: forge 版と中身が違う: $path(揃えるか、一覧から外して冒頭に「fabric 版:」を書く)" >&2
        bad=1
      fi
    done
    [ "$count" -gt 0 ] || { echo "NG: 写しの一覧が空" >&2; exit 1; }
    [ "$bad" -eq 0 ] || exit 1
    echo "OK: 写し $count 件が forge 版と同じ"
    ;;
  jar)
    version=$(sed -n 's/^mod_version=//p' gradle.properties)
    [ -n "$version" ] || { echo "NG: gradle.properties に mod_version が無い" >&2; exit 1; }
    set -- build/libs/*.jar
    [ "$#" -eq 1 ] && [ -f "$1" ] || { echo "NG: build/libs の jar が 1 つでない: $*" >&2; exit 1; }
    jar=$1
    [ "$jar" = "build/libs/gomacraft_utility-fabric-$version.jar" ] \
      || { echo "NG: jar の名前が gomacraft_utility-fabric-$version.jar でない: $jar" >&2; exit 1; }
    listing=$(unzip -Z1 "$jar")
    printf '%s\n' "$listing" | grep -qx 'fabric.mod.json' || { echo "NG: $jar に fabric.mod.json が無い" >&2; exit 1; }
    printf '%s\n' "$listing" | grep -qx 'gomacraft_utility.mixins.json' || { echo "NG: $jar に mixin の設定が無い" >&2; exit 1; }
    if printf '%s\n' "$listing" | grep -E '^data/'; then
      echo "NG: $jar にワールドのデータが入っている(上の行)" >&2
      exit 1
    fi
    # 版の置き換えが効いているか(${mod_version} のまま配らない)
    if ! unzip -p "$jar" fabric.mod.json | grep -q "\"version\": \"$version\""; then
      echo "NG: $jar の fabric.mod.json の version が $version でない" >&2
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
    first=$(printf '%s\n' "$lines" | head -n 1)
    for feature in selftest_broken selftest_at selftest_missing_class; do
      case " ${first#gomacraft_utility: features } " in
        *" $feature=FAILED("*) ;;
        *) echo "NG: わざと壊した機能 $feature が、最初の 1 行で FAILED になっていない" >&2; exit 1 ;;
      esac
    done
    echo "OK: 壊れた機能だけが止まり、起動は続いた"
    ;;
  *)
    echo "使い方: $0 jar | selftest <latest.log> | copies" >&2
    exit 2
    ;;
esac
