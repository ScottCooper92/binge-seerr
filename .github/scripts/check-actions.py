#!/usr/bin/env python3
"""Checks the composite actions under .github/actions, which actionlint does not read.

actionlint only knows the workflow file shape. Pointed at an action.yml it reports syntax
errors and stops, and its maintainer treats composite actions as out of scope (#562).

Two checks run here, and both are narrow:

1. shellcheck over every `run:` block. Each `${{ ... }}` becomes `$PLACEHOLDER`, so a bare word
   does not turn `[ "${{ inputs.x }}" = "true" ]` into a constant comparison (SC2050).
2. Every `${{ env.X }}` and `${{ inputs.X }}` an action reads must be provided. `inputs.X` has to
   be declared under the action's `inputs:`. `env.X` has to be set by the action itself (a step
   `env:` key or a `$GITHUB_ENV` write), or by every workflow step that calls the action: the
   step's own `env:`, the job's or workflow's `env:`, or a `$GITHUB_ENV` write in an earlier step
   of that job.

This is a name check. It proves X is assigned somewhere a caller can see it. It does not prove
the value is right, it does not follow an `env` key set through an expression or a matrix, and
it does not read `steps.*`, `github.*`, `runner.*` or `secrets.*`. shellcheck sees only the shell
text, so it cannot tell whether an expression inside `with:` resolves.
"""
import glob
import re
import subprocess
import sys

import yaml

EXPR = re.compile(r"\$\{\{(.*?)\}\}", re.S)
ENV_REF = re.compile(r"\benv\.([A-Za-z_][A-Za-z0-9_]*)")
INPUT_REF = re.compile(r"\binputs\.([A-Za-z_][A-Za-z0-9_-]*)")
GITHUB_ENV_WRITE = re.compile(
    r"^\s*(?:echo|printf)\s+(?:-\w+\s+)*[\"']?([A-Za-z_][A-Za-z0-9_]*)=[^\n]*>>\s*[\"']?\$\{?GITHUB_ENV",
    re.M,
)
LOCAL_USES = re.compile(r"^\./\.github/actions/([^/@\s]+)/?$")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def expressions(node):
    """Yields the text of every `${{ }}` expression in a parsed YAML value."""
    if isinstance(node, str):
        for match in EXPR.finditer(node):
            yield match.group(1)
    elif isinstance(node, dict):
        for value in node.values():
            yield from expressions(value)
    elif isinstance(node, list):
        for value in node:
            yield from expressions(value)


def env_written_by(step):
    keys = set(GITHUB_ENV_WRITE.findall(step.get("run") or ""))
    keys.update((step.get("env") or {}).keys())
    return keys


def shellcheck(path, steps):
    failures = 0
    checked = 0
    for step in steps:
        body = step.get("run")
        if not body:
            continue
        checked += 1
        shell = step.get("shell", "bash")
        dialect = "sh" if shell == "sh" else "bash"
        result = subprocess.run(
            ["shellcheck", "--severity=warning", "-s", dialect, "-"],
            input=EXPR.sub("$PLACEHOLDER", body),
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode:
            failures += 1
            print(f"FAIL shellcheck {path}\n{result.stdout}")
    return checked, failures


def callers():
    """Maps an action name to the (workflow, job, step index) places that call it."""
    found = {}
    for path in sorted(glob.glob(".github/workflows/*.y*ml")):
        workflow = load(path)
        for job_id, job in (workflow.get("jobs") or {}).items():
            for index, step in enumerate(job.get("steps") or []):
                match = LOCAL_USES.match(step.get("uses") or "")
                if match:
                    found.setdefault(match.group(1), []).append((path, workflow, job_id, job, index))
    return found


def provided_to_caller(workflow, job, index):
    provided = set((workflow.get("env") or {}).keys())
    provided.update((job.get("env") or {}).keys())
    steps = job.get("steps") or []
    provided.update((steps[index].get("env") or {}).keys())
    for earlier in steps[:index]:
        provided.update(env_written_by(earlier))
    return provided


def check_references(name, path, action, steps, calls):
    failures = 0
    declared_inputs = set((action.get("inputs") or {}).keys())
    own_env = set()
    for step in steps:
        own_env.update(env_written_by(step))
    env_refs, input_refs = set(), set()
    for expression in expressions(steps):
        env_refs.update(ENV_REF.findall(expression))
        input_refs.update(INPUT_REF.findall(expression))
    for ref in sorted(input_refs - declared_inputs):
        failures += 1
        print(f"FAIL {path}: reads inputs.{ref}, which the action does not declare")
    for ref in sorted(env_refs - own_env):
        if not calls:
            failures += 1
            print(f"FAIL {path}: reads env.{ref}, nothing in the action sets it and no workflow calls it")
        for caller_path, workflow, job_id, job, index in calls:
            if ref not in provided_to_caller(workflow, job, index):
                failures += 1
                print(
                    f"FAIL {path}: reads env.{ref}, but {caller_path} job '{job_id}' "
                    f"sets it in no env: key or $GITHUB_ENV write before the call"
                )
    return len(env_refs), failures


def main():
    actions = sorted(glob.glob(".github/actions/*/action.yml"))
    if not actions:
        print("FAIL no composite actions found; wrong working directory?")
        return 1
    calls_by_name = callers()
    blocks = refs = failures = 0
    for path in actions:
        name = path.split("/")[2]
        action = load(path)
        steps = (action.get("runs") or {}).get("steps") or []
        checked, bad = shellcheck(path, steps)
        blocks += checked
        failures += bad
        count, bad = check_references(name, path, action, steps, calls_by_name.get(name, []))
        refs += count
        failures += bad
    if failures:
        return 1
    print(f"ok   {len(actions)} action(s), {blocks} run block(s), {refs} env reference(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
