"""Offline regression checks: python3 .github/test_publish_release.py."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("publish_release.sh").resolve()
GH_STUB = r'''
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
with Path("gh-calls.jsonl").open("a", encoding="utf-8") as log:
    log.write(json.dumps(args) + "\n")
command = args[1]
if command == os.environ.get("FAIL_COMMAND"):
    sys.exit(1)
if command == "view":
    draft = os.environ.get("EXISTING_DRAFT", "")
    if not draft:
        sys.exit(1)
    print(draft)
elif command == "list":
    print(os.environ.get("RELEASE_TAGS", "v1.0.0"))
elif command in ("create", "edit"):
    notes = Path(args[args.index("--notes-file") + 1])
    Path("captured-notes.md").write_text(notes.read_text(encoding="utf-8"), encoding="utf-8")
elif command != "upload":
    sys.exit("Unexpected gh command: " + repr(args))
'''


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("commit", "--quiet", "--allow-empty", "-m", "feat: old release")
        self.git("tag", "v1.0.0")
        self.git("commit", "--quiet", "--allow-empty", "-m", "fix: 中文更新 $(touch INJECTED)")
        self.sha = self.git("rev-parse", "HEAD")
        self.version = "4.0.0-beta.1+" + self.sha[:7]
        self.assets = ["dist/release-" + self.version + "(390100).apk", "dist/mapping.txt"]
        for name in self.assets:
            asset = self.root / name
            asset.parent.mkdir(parents=True, exist_ok=True)
            asset.write_bytes(b"test build artifact")
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        gh = bin_dir / "gh"
        gh.write_text("#!" + sys.executable + "\n" + GH_STUB, encoding="utf-8")
        gh.chmod(0o755)
        self.env = {
            **os.environ,
            "PATH": str(bin_dir) + os.pathsep + os.environ["PATH"],
            "GH_REPO": "example/TiebaLite",
            "GITHUB_REF": "refs/heads/4.0-dev",
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_SHA": self.sha,
            "GITHUB_RUN_ID": "12345",
            "GITHUB_SERVER_URL": "https://github.com",
            "GITHUB_STEP_SUMMARY": str(self.root / "summary.md"),
            "VERSION_NAME": self.version,
            "VERSION_CODE": "390100",
            "SIGNING_CONFIGURED": "true",
            "EXISTING_DRAFT": "",
            "RELEASE_TAGS": "v1.0.0",
            "FAIL_COMMAND": "",
        }

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def publish(self, assets=None, **env):
        return subprocess.run(
            ["bash", str(SCRIPT), *(self.assets if assets is None else assets)],
            cwd=self.root,
            env={**self.env, **env},
            text=True,
            capture_output=True,
        )

    def calls(self):
        log = self.root / "gh-calls.jsonl"
        return [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []

    def notes(self):
        return (self.root / "captured-notes.md").read_text(encoding="utf-8")

    def test_dev_push_publishes_exact_artifacts_and_incremental_notes(self):
        result = self.publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        create = self.calls()[-1]
        self.assertEqual(create[:3], ["release", "create", "v" + self.version])
        self.assertEqual(create[3:5], self.assets)
        self.assertEqual(create[create.index("--target") + 1], self.sha)
        self.assertIn("--prerelease=true", create)
        self.assertIn("fix: 中文更新 $(touch INJECTED)", self.notes())
        self.assertNotIn("feat: old release", self.notes())
        self.assertIn("/compare/v1.0.0..." + self.sha, self.notes())
        self.assertIn("/actions/runs/12345", self.notes())
        self.assertNotIn("签名提醒", self.notes())
        self.assertFalse((self.root / "INJECTED").exists())
        self.assertIn("Release 已发布", (self.root / "summary.md").read_text())

    def test_other_branches_tags_and_pull_requests_never_publish(self):
        for ref, event in [
            ("refs/heads/master", "push"),
            ("refs/heads/feature/test", "push"),
            ("refs/tags/v4.0.0", "push"),
            ("refs/pull/1/merge", "pull_request"),
            ("refs/heads/4.0-dev", "pull_request"),
        ]:
            with self.subTest(ref=ref, event=event):
                result = self.publish(GITHUB_REF=ref, GITHUB_EVENT_NAME=event)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.calls(), [])

    def test_manual_dev_build_can_publish_stable_version(self):
        result = self.publish(GITHUB_EVENT_NAME="workflow_dispatch", VERSION_NAME="4.0.0+" + self.sha[:7])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--prerelease=false", self.calls()[-1])

    def test_missing_signing_is_visible_in_release_notes(self):
        result = self.publish(SIGNING_CONFIGURED="false")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("签名提醒", self.notes())
        self.assertIn("debug", self.notes())

    def test_rerun_does_not_modify_published_release(self):
        result = self.publish(EXISTING_DRAFT="false")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([call[1] for call in self.calls()], ["view"])
        self.assertIn("Release 已存在", (self.root / "summary.md").read_text())

    def test_rerun_recovers_interrupted_draft(self):
        result = self.publish(EXISTING_DRAFT="true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([call[1] for call in self.calls()], ["view", "list", "upload", "edit"])
        self.assertIn("--clobber", self.calls()[-2])
        self.assertIn("--draft=false", self.calls()[-1])

    def test_first_release_falls_back_to_ancestor_tag_then_history(self):
        result = self.publish(RELEASE_TAGS="")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("feat: old release", self.notes())
        self.git("tag", "-d", "v1.0.0")
        result = self.publish(RELEASE_TAGS="")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("feat: old release", self.notes())
        self.assertIn("/commits/" + self.sha, self.notes())

    def test_unrelated_release_and_unpublished_tag_do_not_hide_changes(self):
        self.git("tag", "unpublished", self.sha)
        branch = self.git("branch", "--show-current")
        self.git("checkout", "--quiet", "--orphan", "unrelated")
        self.git("commit", "--quiet", "--allow-empty", "-m", "feat: unrelated")
        self.git("tag", "v9.0.0")
        self.git("checkout", "--quiet", branch)
        result = self.publish(RELEASE_TAGS="v9.0.0\nv1.0.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("/compare/v1.0.0..." + self.sha, self.notes())
        self.assertIn("fix: 中文更新", self.notes())
        self.assertNotIn("unrelated", self.notes())

    def test_missing_empty_or_absent_assets_fail_before_publication(self):
        (self.root / "empty.apk").touch()
        for assets in [[], ["missing.apk"], ["empty.apk"]]:
            with self.subTest(assets=assets):
                result = self.publish(assets=assets)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.calls(), [])

    def test_github_failure_never_reports_success(self):
        for command in ["list", "create"]:
            with self.subTest(command=command):
                result = self.publish(FAIL_COMMAND=command)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.root / "summary.md").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
