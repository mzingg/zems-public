#!/usr/bin/env bash
# Auto-format staged files with treefmt (the single formatter — Java included),
# then run the commitGate profile (checkstyle, PMD, tests) when Java/frontend
# files are staged. Installed by flake.nix's shellHook as a symlink at
# .git/hooks/pre-commit. Edits here apply on the next `nix develop` entry.
#
# Default output is a compact checklist; tool output is captured and only
# replayed when a step fails. Set PRE_COMMIT_VERBOSE=1 to stream tool output
# live instead.
#
# Bypass with `git commit --no-verify` when the failure is genuinely
# unrelated to the staged change.

set -e

VERBOSE=${PRE_COMMIT_VERBOSE:-0}

run_step() {
  local label="$1"
  shift
  if [ "$VERBOSE" = "1" ]; then
    if "$@"; then
      printf '  [ OK ] %s\n' "$label"
    else
      local rc=$?
      printf '  [FAIL] %s\n' "$label"
      return $rc
    fi
  else
    local log rc
    log=$(mktemp)
    if "$@" >"$log" 2>&1; then
      printf '  [ OK ] %s\n' "$label"
      rm -f "$log"
    else
      rc=$?
      printf '  [FAIL] %s\n' "$label"
      echo
      echo "--- $label output ---"
      cat "$log"
      echo "--- end $label output ---"
      rm -f "$log"
      return $rc
    fi
  fi
}

# 1. Collect currently staged files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)
if [ -z "$STAGED_FILES" ]; then exit 0; fi

REPO_ROOT=$(git rev-parse --show-toplevel)

echo "Pre-commit checks:"

# 2. Format ALL staged files with treefmt — the single formatter, Java included.
#    The pinned formatter version is printed on every run (Playwright-style).
echo "  formatter: $(prettier-banner)"
step_treefmt() { echo "$STAGED_FILES" | xargs treefmt --; }
run_step "treefmt" step_treefmt

# 3. Run the commitGate profile from the root when Java or frontend files
#    are staged. The profile bundles checkstyle, PMD, and npm run lint, then
#    tests via the verify lifecycle (formatting already ran in step 2).
#    `-fae` (--fail-at-end) keeps the reactor going through independent
#    modules after a failure so a single commit attempt surfaces every
#    gate violation at once, instead of forcing fix-retry-fix iterations.
#    `-T 1C` runs reactor modules in parallel (~1 thread per CPU core)
#    on top of surefire's in-module test parallelism.
STAGED_JAVA=$(echo "$STAGED_FILES" | grep '\.java$' || true)
STAGED_FRONTEND=$(echo "$STAGED_FILES" | grep -E '\.(ts|tsx|js|jsx|cjs|mjs)$|(^|/)package(-lock)?\.json$' || true)
if [ -n "$STAGED_JAVA" ] || [ -n "$STAGED_FRONTEND" ]; then
  step_commitgate() {
    cd "$REPO_ROOT" && mvn -B -T 1C -fae -PcommitGate verify
  }
  if ! run_step "commitGate verify" step_commitgate; then
    echo
    echo "Pre-commit verification failed."
    echo "Fix the violations above, or rerun with 'git commit --no-verify' to bypass."
    echo "Re-run the commit with PRE_COMMIT_VERBOSE=1 to stream tool output live."
    exit 1
  fi
fi

# 4. Re-stage originally-staged files that were rewritten by treefmt, so the
#    commit picks up the formatted versions. Files rewritten outside the staged
#    set are left as unstaged modifications.
echo "$STAGED_FILES" | while IFS= read -r file; do
  if [ -f "$file" ]; then git add "$file"; fi
done
