import importlib.util
import tempfile
import textwrap
import unittest
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[1] / "generate_api_conformance_report.py"
)
SPEC = importlib.util.spec_from_file_location(
    "generate_api_conformance_report", MODULE_PATH
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class GenerateApiConformanceReportTest(unittest.TestCase):
    def test_request_timeout_is_hard_and_renders_copyable_curl_block(self) -> None:
        xml = textwrap.dedent(
            """\
            <testsuites>
              <testsuite tests="1" failures="0" errors="1">
                <testcase classname="GET /api/v1/b3tr/actions/apps/{appId}/overview">
                  <error message="1. Test Case ID: Jzxiov - Response timeout The server failed to respond within the specified limit of 10000.00ms Reproduce with: curl -X GET https://example.com/api/v1/b3tr/actions/apps/app-1/overview" />
                </testcase>
              </testsuite>
            </testsuites>
            """
        )

        with tempfile.TemporaryDirectory() as tmp_dir:
            results_xml = Path(tmp_dir) / "results.xml"
            results_xml.write_text(xml)

            issues, summary = MODULE.parse_junit_xml(results_xml)
            self.assertEqual(summary["errors"], 1)
            self.assertEqual(len(issues), 1)

            issue = issues[0]
            self.assertEqual(issue["check"], "request_timeout")
            self.assertEqual(issue["severity"], "hard")
            self.assertEqual(issue["test_case_id"], "Jzxiov")
            self.assertEqual(
                issue["title"],
                "Response timeout The server failed to respond within the specified limit of 10000.00ms",
            )
            self.assertEqual(
                issue["reproduce_with"],
                "curl -X GET https://example.com/api/v1/b3tr/actions/apps/app-1/overview",
            )
            self.assertNotIn("Reproduce with:", issue["details"])
            self.assertNotIn("curl -X GET", issue["details"])

            report = {
                "metadata": {
                    "schemathesis_exit_code": 1,
                    "base_url": "https://example.com",
                },
                "summary": {
                    "status": "fail",
                    "hard_issues": 1,
                    "soft_issues": 0,
                    "warnings_total": 0,
                    "checks": {},
                },
                "issues": issues,
                "warnings": [],
            }

            markdown = MODULE.render_markdown(report)
            self.assertIn("### Hard issues", markdown)
            self.assertIn("- Reproduce with:", markdown)
            self.assertIn(
                "```sh\ncurl -X GET https://example.com/api/v1/b3tr/actions/apps/app-1/overview\n```",
                markdown,
            )
            self.assertEqual(markdown.count("curl -X GET"), 1)
            self.assertNotIn("Unclassified issue", markdown)

    def test_multiline_reproduction_block_is_extracted_from_details(self) -> None:
        raw_text = textwrap.dedent(
            """\
            Network Error

            Read timed out after 10.0 seconds

            Reproduce with:

                curl -X GET https://example.com/api/v1/b3tr/actions/apps/app-1/overview

            Need more help?
                Join our Discord server: https://discord.gg/R9ASRAmHnA
            """
        )

        self.assertEqual(
            MODULE.extract_reproduction_command(raw_text),
            "curl -X GET https://example.com/api/v1/b3tr/actions/apps/app-1/overview",
        )
        self.assertEqual(
            MODULE.strip_reproduction_section(raw_text),
            "Network Error\n\nRead timed out after 10.0 seconds",
        )


if __name__ == "__main__":
    unittest.main()
