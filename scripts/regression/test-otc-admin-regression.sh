#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="${ROOT_DIR}/scripts/regression/run-otc-admin-regression.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'FAIL %s: expected content to contain %s\n' "${label}" "${needle}" >&2
    exit 1
  fi
}

if [[ ! -f "${SCRIPT}" ]]; then
  printf 'FAIL otc regression script is missing: %s\n' "${SCRIPT}" >&2
  exit 1
fi

bash -n "${SCRIPT}"

script_source="$(cat "${SCRIPT}")"
assert_contains "${script_source}" "Missing required LVT names" "summary refined lvt header"

help_output="$(bash "${SCRIPT}" --help)"
assert_contains "${help_output}" "OTC_ADMIN_JAR" "sample path env"
assert_contains "${help_output}" "/Users/jackma/ProjectCode/68集团/OTC/otc-admin.jar" "default OTC sample"
assert_contains "${help_output}" "OTC_ADMIN_REFERENCE_PROJECT" "reference project env"
assert_contains "${help_output}" "--restore-package-records" "package-record mode"
assert_contains "${help_output}" "--byte-exact-package" "byte-exact mode"
assert_contains "${help_output}" "otc-admin-summary.csv" "csv report"
assert_contains "${help_output}" "otc-admin-summary.md" "markdown report"
assert_contains "${help_output}" "otc-admin-source-diff.txt" "source diff report"
assert_contains "${help_output}" "reference-only Java files" "source diff purpose"
assert_contains "${help_output}" "original JAR class presence" "source diff class presence"
assert_contains "${help_output}" "class bytecode and ZIP metadata fidelity details" "artifact fidelity detail purpose"
assert_contains "${help_output}" "decompile parity risk summary" "parity risk detail purpose"
assert_contains "${help_output}" "source coverage gates" "parity source coverage gates"
