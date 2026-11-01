#!/bin/sh
# Local verification for this box.
#
#   tools/localbuild.sh <worktree-dir> <gradle task...>
#
# ## Why this exists
#
# This development machine is aarch64 with no usable aapt2, so `:app` unit tests and
# `assembleDebug` cannot run here (`docs/BUILD-AND-CI.md` §0). Kotlin compilation can,
# and so can `:design`'s own test task — but only under two conditions that
# `build.sh` does not provide:
#
#  1. **JDK 21, not 17.** miuix 0.9.4 ships Java 21 bytecode (class file 65), and since
#     2.4 the palette adapter puts miuix on `:design`'s test classpath. `build.sh`
#     hard-codes `JAVA_HOME=.toolchain/jdk-17.0.20.1+1` and dies with
#     `UnsupportedClassVersionError`.
#  2. **build-tools 36.0.0.** AGP asks for 37.0.0; this box has 35 and 36. Five
#     `build.gradle.kts` files name it, and the override must NOT be committed — a
#     `git diff` that shows `buildToolsVersion` is a forgotten restore.
#
# Both are applied here and the second is undone by the `trap`, so the worktree is left
# exactly as it was found. The build lock is shared with `build.sh` because the box has
# 3 GB of RAM and one Gradle at a time.
#
# ## Not a CI substitute
#
# It runs what CI runs, in a narrower form. `:app`'s test sources still cannot compile
# here (aapt2), so a green run here does not mean the `:app` tests pass — see
# `docs/BUILD-AND-CI.md` §0.0c.
set -e

WT="${1:?usage: tools/localbuild.sh <worktree-dir> <gradle task...>}"
shift
[ -d "$WT" ] || { echo "localbuild: no such directory: $WT" >&2; exit 2; }
WT=$(cd "$WT" && pwd)
# The *main* checkout, not this script's own directory. `.toolchain/` (gradle, the
# android-home and xdg-data caches) and `.build.lock` are gitignored, so they exist
# only in the primary working tree — a git worktree has neither, and building in one
# has to borrow them or every agent would download a second Gradle. `--git-common-dir`
# answers "where is the repository" from either place: in the main checkout it is
# `.git` and here it is `<main>/.git`, and its parent is the main checkout in both.
ROOT=$(cd "$(git -C "$WT" rev-parse --git-common-dir)/.." && pwd)

JDK_HOME=${DSH_LOCAL_JDK:-/usr/lib/jvm/java-21-openjdk-arm64}
[ -x "$JDK_HOME/bin/java" ] || { echo "localbuild: no JDK at $JDK_HOME (override with DSH_LOCAL_JDK)" >&2; exit 2; }

export JAVA_HOME="$JDK_HOME"
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$ROOT/.toolchain/android-home"
export XDG_DATA_HOME="$ROOT/.toolchain/xdg-data"
export GRADLE_USER_HOME="$ROOT/.toolchain/gradle-home"
mkdir -p "$ANDROID_USER_HOME" "$XDG_DATA_HOME"
echo "sdk.dir=$ANDROID_HOME" > "$WT/local.properties"

# One build at a time across every worktree and every agent: 3 GB of RAM.
exec 9>"$ROOT/.build.lock"
if ! flock -n 9; then echo "localbuild: waiting for the shared build lock..." >&2; flock 9; fi

GRADLE="$ROOT/.toolchain/gradle-9.5.0/bin/gradle"
[ -x "$GRADLE" ] || { echo "localbuild: no gradle at $GRADLE (run build.sh once to lay it out)" >&2; exit 2; }

FILES="app/build.gradle.kts core/model/build.gradle.kts core/config/build.gradle.kts core/protocol/build.gradle.kts design/build.gradle.kts"
restore() {
    for f in $FILES; do
        [ -f "$WT/$f.btbak" ] && mv -f "$WT/$f.btbak" "$WT/$f"
    done
}
trap restore EXIT INT TERM
for f in $FILES; do
    [ -f "$WT/$f" ] || continue
    if grep -q 'buildToolsVersion = "37.0.0"' "$WT/$f"; then
        cp "$WT/$f" "$WT/$f.btbak"
        sed -i 's/buildToolsVersion = "37.0.0"/buildToolsVersion = "36.0.0"/' "$WT/$f"
    fi
done

cd "$WT"
exec 9>&-
"$GRADLE" -g "$GRADLE_USER_HOME" --no-daemon "$@"
