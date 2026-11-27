#!/usr/bin/env python3
"""Static property checks for the delivery workflows.

Asserts the controls required by the approved trunk-based delivery model
(shared PROJECT_EXECUTION.md, "Repository and delivery strategy"):

  * ci.yml runs on pull requests to main and on pushes to main;
  * pre-merge gates exist and are bound to one explicit candidate revision;
  * the secret scan runs the open-source gitleaks CLI at a pinned,
    checksum-verified version (no licence-gated action, no licence secret);
  * after merge, one build produces a stored artifact with an immutable
    SHA-256 digest and provenance, and is not rebuilt later;
  * no workflow performs a deployment;
  * promotion is a separate, manually gated, digest-verified path;
  * no gate can be silently skipped.

These are static assertions over the YAML, not a substitute for a real
pipeline run, provider-side branch/environment protection, QA or Security
review.

Usage:
    python3 scripts/check_delivery_workflows.py [ci.yml] [release-promotion.yml]

Exit codes: 0 all properties hold, 1 a property failed, 2 the checker could
not run (missing file, unparsable YAML, missing PyYAML).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml
except ModuleNotFoundError:  # pragma: no cover - environment guard
    print("::error::PyYAML is required to check the workflow properties")
    sys.exit(2)

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CI = REPO_ROOT / ".github" / "workflows" / "ci.yml"
DEFAULT_RELEASE = REPO_ROOT / ".github" / "workflows" / "release-promotion.yml"

# Commands that would deploy, release or mutate infrastructure. None may appear
# in a delivery workflow of this repository.
FORBIDDEN_COMMANDS = [
    r"\bterraform\s+apply\b",
    r"\bterraform\s+destroy\b",
    r"\bterraform\s+import\b",
    r"\baws\s+deploy\b",
    r"\baws\s+cloudformation\s+(deploy|create-stack|update-stack)\b",
    r"\baws\s+lambda\s+update-function-code\b",
    r"\baws\s+s3\s+(cp|sync)\b",
    r"\bkubectl\s+apply\b",
    r"\bhelm\s+(install|upgrade)\b",
    r"\bdocker\s+push\b",
    r"\bgh\s+release\s+(create|upload)\b",
    r"\bserverless\s+deploy\b",
    r"\bcdk\s+deploy\b",
    r"\bgh\s+pr\s+merge\b",
]

GATE_JOBS = ("build-test", "secret-scan", "iac-and-dependency-scan", "codeql", "workflow-policy")
RELEASE_JOB = "release-artifact"
SUMMARY_JOB = "gate-summary"

SHA40 = re.compile(r"^[a-f0-9]{40}$")


class Report:
    def __init__(self, title: str) -> None:
        self.title = title
        self.rows: list[tuple[str, bool, str]] = []

    def check(self, name: str, ok: bool, detail: str = "") -> None:
        self.rows.append((name, bool(ok), detail))

    def emit(self) -> bool:
        print(f"\n== {self.title} ==")
        for name, ok, detail in self.rows:
            status = "PASS" if ok else "FAIL"
            line = f"  [{status}] {name}"
            if detail:
                line += f" — {detail}"
            print(line)
        failed = [name for name, ok, _ in self.rows if not ok]
        print(f"  {len(self.rows) - len(failed)}/{len(self.rows)} properties hold")
        return not failed


def load(path: Path):
    if not path.is_file():
        print(f"::error::Workflow file not found: {path}")
        sys.exit(2)
    text = path.read_text(encoding="utf-8")
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        print(f"::error::YAML does not parse in {path}: {exc}")
        sys.exit(2)
    if not isinstance(doc, dict):
        print(f"::error::Workflow root is not a mapping in {path}")
        sys.exit(2)
    return doc, text


def on_block(doc: dict):
    # YAML 1.1 parses the bare key `on` as boolean True.
    for key in ("on", True):
        if key in doc:
            return doc[key]
    return None


def jobs(doc: dict) -> dict:
    value = doc.get("jobs")
    return value if isinstance(value, dict) else {}


def steps_of(job: dict) -> list:
    value = job.get("steps")
    return value if isinstance(value, list) else []


def run_text(job: dict) -> str:
    return "\n".join(str(step.get("run", "")) for step in steps_of(job) if isinstance(step, dict))


def all_run_text(doc: dict) -> str:
    return "\n".join(run_text(job) for job in jobs(doc).values() if isinstance(job, dict))


def all_uses(doc: dict) -> list:
    return [
        str(step.get("uses", ""))
        for job in jobs(doc).values()
        if isinstance(job, dict)
        for step in steps_of(job)
        if isinstance(step, dict) and step.get("uses")
    ]


def steps_using(job: dict, prefix: str) -> list:
    return [
        step
        for step in steps_of(job)
        if isinstance(step, dict) and str(step.get("uses", "")).startswith(prefix)
    ]


def needs_of(job: dict) -> list:
    value = job.get("needs")
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    return list(value)


def secret_scan_pin(job: dict) -> tuple[str | None, str | None]:
    """Return (GITLEAKS_VERSION, GITLEAKS_SHA256) declared on the job's steps."""
    version = None
    digest = None
    for step in steps_of(job):
        if not isinstance(step, dict) or not isinstance(step.get("env"), dict):
            continue
        for key, value in step["env"].items():
            if key == "GITLEAKS_VERSION":
                version = str(value)
            elif key == "GITLEAKS_SHA256":
                digest = str(value)
    return version, digest


def dispatch_inputs(doc: dict) -> dict:
    block = on_block(doc)
    if not isinstance(block, dict):
        return {}
    dispatch = block.get("workflow_dispatch")
    if not isinstance(dispatch, dict):
        return {}
    inputs = dispatch.get("inputs")
    return inputs if isinstance(inputs, dict) else {}


def check_ci(doc: dict, text: str, report: Report) -> None:
    trigger = on_block(doc)
    trigger = trigger if isinstance(trigger, dict) else {}
    pull_request = trigger.get("pull_request")
    push = trigger.get("push")

    pr_branches = pull_request.get("branches") if isinstance(pull_request, dict) else None
    report.check(
        "triggers on pull requests targeting main",
        isinstance(pr_branches, list) and "main" in pr_branches,
        f"pull_request.branches={pr_branches}",
    )

    push_branches = push.get("branches") if isinstance(push, dict) else None
    report.check(
        "triggers on pushes to main",
        isinstance(push_branches, list) and "main" in push_branches,
        f"push.branches={push_branches}",
    )

    ignored = push.get("branches-ignore") if isinstance(push, dict) else None
    report.check(
        "main is not excluded from push triggers",
        not ignored,
        f"push.branches-ignore={ignored}",
    )

    job_map = jobs(doc)

    candidate = job_map.get("candidate")
    report.check(
        "a single candidate revision is resolved for the run",
        isinstance(candidate, dict)
        and isinstance(candidate.get("outputs"), dict)
        and "sha" in (candidate.get("outputs") or {}),
        "jobs.candidate.outputs.sha",
    )

    missing_gates = [name for name in GATE_JOBS if name not in job_map]
    report.check(
        "required gates exist (tests, secret scan, IaC/dependency scan, SAST, workflow policy)",
        not missing_gates,
        f"missing={missing_gates}" if missing_gates else "all present",
    )

    unbound = []
    for name in GATE_JOBS:
        job = job_map.get(name)
        if not isinstance(job, dict):
            unbound.append(name)
            continue
        checkouts = steps_using(job, "actions/checkout")
        ref_ok = any(
            str((step.get("with") or {}).get("ref", "")).find("needs.candidate.outputs.sha") >= 0
            for step in checkouts
        )
        verify_ok = "rev-parse HEAD" in run_text(job) and "::error::" in run_text(job)
        if not (ref_ok and verify_ok and "candidate" in needs_of(job)):
            unbound.append(name)
    report.check(
        "every gate job checks out and re-verifies the candidate revision",
        not unbound,
        f"unbound={unbound}" if unbound else "all gates bound to needs.candidate.outputs.sha",
    )

    report.check(
        "unit/contract tests are a required gate",
        "mvn -B -ntp verify" in run_text(job_map.get("build-test", {})),
        "build-test runs mvn verify",
    )

    skipped = []
    if any(job.get("if") in (False, "false", "${{ false }}") for job in job_map.values()):
        skipped.append("if:false job")
    for job_name, job in job_map.items():
        if not isinstance(job, dict):
            continue
        if job.get("continue-on-error"):
            skipped.append(f"{job_name}: job continue-on-error")
        for index, step in enumerate(steps_of(job)):
            if isinstance(step, dict) and step.get("continue-on-error"):
                skipped.append(f"{job_name} step {index}: continue-on-error")
    report.check(
        "gates cannot be silently skipped or made non-blocking",
        not skipped,
        f"offenders={skipped}" if skipped else "no if:false and no continue-on-error",
    )

    gate_ifs = [
        f"{name}: {job.get('if')}"
        for name, job in job_map.items()
        if name in GATE_JOBS and job.get("if")
    ]
    report.check(
        "gate jobs are not restricted to only one event",
        not gate_ifs,
        f"conditional gates={gate_ifs}" if gate_ifs else "gates run on every trigger",
    )

    secret_job = job_map.get("secret-scan")
    secret_job = secret_job if isinstance(secret_job, dict) else {}
    secret_run = run_text(secret_job)
    secret_version, secret_digest = secret_scan_pin(secret_job)

    report.check(
        "the secret scan runs the open-source gitleaks CLI instead of the licence-gated action",
        "secret-scan" in job_map
        and not any(use.startswith("gitleaks/gitleaks-action") for use in all_uses(doc))
        and re.search(r"\bgitleaks\s+git\b", secret_run) is not None,
        "no gitleaks/gitleaks-action step; `gitleaks git` CLI invocation",
    )

    report.check(
        "no workflow references the gitleaks licence credential",
        "GITLEAKS_LICENSE" not in text,
        "GITLEAKS_LICENSE absent from ci.yml",
    )

    report.check(
        "the secret scan fails the build when findings exist",
        "--exit-code 1" in secret_run
        and "set -euo pipefail" in secret_run
        and "::error::" in secret_run
        and re.search(r"exit\s+1", secret_run) is not None,
        "gitleaks --exit-code 1 plus an explicit ::error:: failure",
    )

    report.check(
        "the gitleaks binary version is pinned",
        secret_version is not None
        and re.match(r"^\d+\.\d+\.\d+$", secret_version) is not None,
        f"GITLEAKS_VERSION={secret_version}",
    )

    report.check(
        "the gitleaks download is checksum-verified before execution",
        secret_digest is not None
        and re.match(r"^[a-f0-9]{64}$", secret_digest) is not None
        and re.search(r"sha256sum\s+--strict\s+-c", secret_run) is not None,
        f"GITLEAKS_SHA256={secret_digest}",
    )

    report.check(
        "no workflow pipes a downloaded installer into a shell",
        re.search(r"(curl|wget)[^\n|]*\|\s*(sudo\s+)?(ba|z)?sh\b", text) is None,
        "no unverified installer pipeline",
    )

    report.check(
        "the secret scan fails closed when the scanner cannot be installed or run",
        secret_run.count("::error::") >= 3
        and secret_run.count("fails closed") >= 3
        and "command -v gitleaks" in secret_run
        and re.search(r"exit\s+1", secret_run) is not None,
        f"explicit fail-closed messages: {secret_run.count('fails closed')}",
    )

    release = job_map.get(RELEASE_JOB)
    release_if = str(release.get("if", "")) if isinstance(release, dict) else ""
    report.check(
        "release artifact job only runs for a push to main",
        "github.event_name == 'push'" in release_if and "refs/heads/main" in release_if,
        f"if: {release_if or 'MISSING'}",
    )

    release_needs = set(needs_of(release)) if isinstance(release, dict) else set()
    report.check(
        "release artifact is built only after every gate passed",
        set(GATE_JOBS).issubset(release_needs) and "candidate" in release_needs,
        f"needs={sorted(release_needs)}",
    )

    package_steps = [
        name
        for name, job in job_map.items()
        if isinstance(job, dict)
        and re.search(r"\bmvn\b[^\n]*\bpackage\b", run_text(job))
    ]
    report.check(
        "the application artifact is packaged exactly once",
        package_steps == [RELEASE_JOB],
        f"packaging jobs={package_steps}",
    )

    uploads = steps_using(release, "actions/upload-artifact") if isinstance(release, dict) else []
    artifact_ok = False
    retention = None
    if uploads:
        step = uploads[0]
        with_block = step.get("with") or {}
        paths = str(with_block.get("path", ""))
        name_expr = str(with_block.get("name", ""))
        try:
            retention = int(with_block.get("retention-days"))
        except (TypeError, ValueError):
            retention = None
        artifact_ok = (
            ".jar" in paths
            and "artifact-manifest.json" in paths
            and "needs.candidate.outputs.sha" in name_expr
            and retention is not None
            and retention >= 30
            and str(with_block.get("if-no-files-found", "")) == "error"
        )
    report.check(
        "the deployable artifact is stored with a retention policy",
        artifact_ok,
        f"retention-days={retention}",
    )

    release_run = run_text(release) if isinstance(release, dict) else ""
    provenance_ok = (
        "sha256sum" in release_run
        and "artifact-manifest.json" in release_run
        and "source_commit" in release_run
        and "workflow_run_id" in release_run
        and "sha256" in release_run
    )
    report.check(
        "an immutable digest and provenance are recorded for the artifact",
        provenance_ok,
        "sha256 + commit + workflow run id in artifact-manifest.json",
    )

    offenders = []
    haystack = all_run_text(doc)
    for pattern in FORBIDDEN_COMMANDS:
        match = re.search(pattern, haystack)
        if match:
            offenders.append(match.group(0))
    report.check(
        "no workflow step deploys, releases or mutates infrastructure",
        not offenders,
        f"offenders={offenders}" if offenders else "no deploy/release commands",
    )

    report.check(
        "ci.yml declares no production deployment environment",
        not any(
            isinstance(job, dict) and job.get("environment")
            for job in job_map.values()
        ),
        "no job-level environment",
    )

    summary = job_map.get(SUMMARY_JOB)
    summary_needs = set(needs_of(summary)) if isinstance(summary, dict) else set()
    summary_run = run_text(summary) if isinstance(summary, dict) else ""
    report.check(
        "a required-check summary fails the workflow when any gate did not succeed",
        set(GATE_JOBS).issubset(summary_needs)
        and RELEASE_JOB in summary_needs
        and str(summary.get("if", "")).startswith("always()")
        and "::error::" in summary_run
        and "success" in summary_run,
        f"needs={sorted(summary_needs)}",
    )

    unpinned = [use for use in all_uses(doc) if not SHA40.match(use.split("@", 1)[-1])]
    report.check(
        "every third-party action is pinned to a reviewed commit SHA",
        not unpinned,
        f"unpinned={unpinned}" if unpinned else "all pinned",
    )


def check_release(doc: dict, report: Report) -> None:
    trigger = on_block(doc)
    trigger = trigger if isinstance(trigger, dict) else {}
    report.check(
        "promotion is manual only (no push or pull_request trigger)",
        set(trigger) == {"workflow_dispatch"},
        f"triggers={sorted(map(str, trigger))}",
    )

    inputs = dispatch_inputs(doc)
    required = {
        "source_commit",
        "artifact_digest",
        "ci_run_id",
        "qa_report_path",
        "approval_reference",
    }
    optional = [name for name, spec in inputs.items() if not (spec or {}).get("required")]
    report.check(
        "every promotion input is declared and required",
        required.issubset(inputs) and not optional,
        f"missing={sorted(required - set(inputs))} optional={optional}",
    )

    job_map = jobs(doc)
    promote = job_map.get("promote")
    if not isinstance(promote, dict):
        promote = next((job for job in job_map.values() if isinstance(job, dict)), {})
    environment = promote.get("environment")
    env_name = environment.get("name") if isinstance(environment, dict) else environment
    report.check(
        "promotion enters the protected production environment",
        env_name == "production",
        f"environment={env_name}",
    )

    promote_run = run_text(promote)
    build_commands = re.findall(
        r"\b(mvn|mvnw|gradle|gradlew|npm\s+run\s+build|docker\s+build|javac)\b", promote_run
    )
    report.check(
        "promotion never rebuilds the artifact",
        not build_commands,
        f"build commands={sorted(set(build_commands))}" if build_commands else "no build step",
    )

    report.check(
        "promotion downloads the stored candidate artifact",
        "gh run download" in promote_run and "acquiring-simulator-" in promote_run,
        "gh run download of the candidate artifact name",
    )

    report.check(
        "promotion verifies the requested digest against the downloaded artifact",
        "sha256sum" in promote_run
        and "EXPECTED_DIGEST" in promote_run
        and "Digest mismatch" in promote_run,
        "sha256 compare against inputs.artifact_digest",
    )

    report.check(
        "promotion verifies provenance against the candidate commit",
        "artifact-manifest.json" in promote_run
        and "source_commit" in promote_run
        and "Provenance does not match" in promote_run,
        "manifest source_commit == inputs.source_commit",
    )

    report.check(
        "promotion requires QA evidence at the candidate revision",
        "QA_REPORT_PATH" in promote_run and "[ ! -f " in promote_run,
        "qa_report_path must exist at inputs.source_commit",
    )

    report.check(
        "missing promotion input fails closed with an explicit message",
        "Missing required promotion input" in promote_run and "exit 1" in promote_run,
        "empty input aborts before any provider action",
    )

    report.check(
        "the unconfigured deployment target fails closed instead of reporting success",
        "Deployment handoff" in "\n".join(
            str(step.get("name", "")) for step in steps_of(promote) if isinstance(step, dict)
        )
        and "not configured" in promote_run,
        "no invented account, role or deploy API",
    )

    offenders = []
    for pattern in FORBIDDEN_COMMANDS:
        match = re.search(pattern, promote_run)
        if match:
            offenders.append(match.group(0))
    report.check(
        "promotion runs no infrastructure or release mutation command",
        not offenders,
        f"offenders={offenders}" if offenders else "no deploy/release commands",
    )

    concurrency = doc.get("concurrency")
    group = str((concurrency or {}).get("group", "")) if isinstance(concurrency, dict) else ""
    cancel = (concurrency or {}).get("cancel-in-progress") if isinstance(concurrency, dict) else None
    report.check(
        "promotions of the same candidate are serialized",
        "source_commit" in group and cancel is False,
        f"group={group} cancel-in-progress={cancel}",
    )


def main(argv: list[str]) -> int:
    ci_path = Path(argv[1]) if len(argv) > 1 else DEFAULT_CI
    release_path = Path(argv[2]) if len(argv) > 2 else DEFAULT_RELEASE

    ci_doc, ci_text = load(ci_path)
    release_doc, _ = load(release_path)

    ok = True
    ci_report = Report(f"ci.yml — {ci_path}")
    check_ci(ci_doc, ci_text, ci_report)
    ok = ci_report.emit() and ok

    release_report = Report(f"release-promotion.yml — {release_path}")
    check_release(release_doc, release_report)
    ok = release_report.emit() and ok

    print("\nRESULT:", "all delivery workflow properties hold" if ok else "property violations found")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
