#! /usr/bin/env bash
# Commits what is staged on the current branch, pushes the branch and opens
# a pull request for it. For changes reviewed on GitHub rather than
# committed straight to main.
#
#   make-pr.sh <message file>
#
# Run it from this repository's root. The
# commit opens in the editor (-e), and is signed if git is set up to sign
# commits. The pull request takes its title and description from the
# commit. If the branch already has an open pull request, the commit is
# just pushed to it, which is how review comments get addressed.
set -euo pipefail

msg=${1:?usage: make-pr.sh <message file>}
[[ -f $msg ]] || { echo "no such message file: $msg" >&2; exit 1; }

branch=$(git symbolic-ref --short HEAD)
base=$(git symbolic-ref --short refs/remotes/origin/HEAD 2> /dev/null || echo origin/main)
base=${base#origin/}
if [[ $branch == "$base" ]]; then
    echo "on $base: switch to a branch first (git switch -c <name>)" >&2
    exit 1
fi
if git diff --cached --quiet; then
    echo "nothing is staged" >&2
    exit 1
fi

git commit -e -F "$msg"
git push -u origin "$branch"

if gh pr view "$branch" --json state --jq .state 2> /dev/null | grep -qx OPEN; then
    echo "Pushed to the open pull request:"
    gh pr view "$branch" --json url --jq .url
else
    gh pr create --base "$base" --head "$branch" --fill
fi
