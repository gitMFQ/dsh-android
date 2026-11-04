#!/bin/sh
# Run the pure-JVM tests on this box, without aapt2.
#
# ## Why this exists
#
# `docs/BUILD-AND-CI.md` §0.0 says `:core:model`'s tests cannot run here because
# `processDebugUnitTestResources` needs aapt2, which this aarch64 box does not have.
# That is true of the *Gradle* path — and it made every round of UI work unverifiable
# locally, which is how a stray brace survived in `ScreenBaselineTest` until CI.
#
# The resource APK is only needed to *package* the test for Robolectric. The model
# module's tests are pure JVM: they touch no Context, no resource and no manifest. So
# this script does what Gradle would do, minus the parts that need aapt2:
#
#   1. compile the module's main and test Kotlin (Gradle can do this part:
#      `:core:model:compileDebugUnitTestKotlin` works locally),
#   2. put the module's own `R.jar` where the test task expects it —
#      `generateDebugRFile` produces a real one even without aapt2,
#   3. run JUnit directly over the compiled classes, with the same classpath Gradle
#      would assemble.
#
# ## What it does and does not prove
#
# It proves the module's tests pass. It does **not** replace CI: `:app`'s tests are
# Robolectric and genuinely need the resource APK, so `:app` stays CI-only — but
# `tools/localbuild.sh . :app:compileDebugUnitTestKotlin` (with the init script below)
# *does* compile `:app`'s test sources here, which catches the syntax and reference
# errors that are the usual cause of a red CI run.
#
# Usage: tools/local-jvm-tests.sh [module]    (default: core/model)
set -e

WT=$(cd "${1:-.}" && pwd)
MODULE=${2:-core/model}
ROOT=$(cd "$(git -C "$WT" rev-parse --git-common-dir)/.." && pwd)
CACHE="$ROOT/.toolchain/gradle-home/caches"

echo "building $MODULE test classes..."
"$ROOT/tools/localbuild.sh" "$WT" -I "$ROOT/tools/skip-res.gradle" ":$MODULE:compileDebugUnitTestKotlin" >/dev/null 2>&1 || true

# `generateDebugRFile` writes a real R.jar without aapt2; the test task just looks
# for it in the packaging directory.
GEN=$(find "$WT/$MODULE/build" -path "*compile_r_class_jar*" -name R.jar 2>/dev/null | head -1)
DEST="$WT/$MODULE/build/intermediates/compile_and_runtime_r_class_jar/debugUnitTest/processDebugUnitTestResources"
if [ -n "$GEN" ]; then mkdir -p "$DEST" && cp "$GEN" "$DEST/R.jar"; fi

TESTC=$(find "$WT/$MODULE/build" -type d -path "*built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes" | head -1)
MAIN=$(find "$WT/$MODULE/build" -type d -path "*built_in_kotlinc/debug/compileDebugKotlin/classes" | head -1)
[ -d "$TESTC" ] || { echo "no test classes at $TESTC" >&2; exit 2; }

# The classpath, by name.
#
# Deliberately not "every jar in the cache": that shadows classes with wrong
# versions, and not "ask Gradle for the configuration" either: resolving
# `debugUnitTestCompileClasspath` outside the task graph fails with a project
# variant ambiguity. The list below is what the modules this script is used for
# actually need, and it is short enough to keep honest.
CP="$TESTC:$MAIN"
for pat in "junit-4.13.2.jar" "hamcrest-core-1.3.jar" "kotlin-stdlib-2*.jar" "json-2024*.jar" \
           "kotlinx-coroutines-core-jvm-*.jar" "kotlinx-coroutines-test-jvm-*.jar" \
           "kotlinx-serialization-core-jvm-*.jar" "kotlinx-serialization-json-jvm-*.jar" \
           "okhttp-*.jar" "okio-jvm-*.jar"; do
    J=$(find "$CACHE/modules-2" -name "$pat" 2>/dev/null | grep -v sources | head -1)
    [ -n "$J" ] && CP="$CP:$J"
done

# Test resources: some suites read captured wire bytes from `src/test/resources`
# (`MultipartTest` does), and without them they fail as "fixture missing" - a
# classpath problem that looks exactly like a broken test.
for d in "$WT/$MODULE/src/test/resources" "$WT/$MODULE/src/main/resources"; do
    [ -d "$d" ] && CP="$CP:$d"
done
CLASSES=$(cd "$TESTC" && find . -name "*Test.class" | sed "s|^\./||;s|\.class$||;s|/|.|g" | tr "\n" " ")
[ -n "$CLASSES" ] || { echo "no test classes found" >&2; exit 2; }

JAVA=${DSH_LOCAL_JDK:-/usr/lib/jvm/java-21-openjdk-arm64}/bin/java
echo "running $(echo $CLASSES | wc -w) test classes from $MODULE"
"$JAVA" -cp "$CP" org.junit.runner.JUnitCore $CLASSES