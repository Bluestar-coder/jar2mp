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
assert_contains "${script_source}" "package_record_parity_risk_reasons" "csv parity reason column"
assert_contains "${script_source}" "package_record_runtime_score" "csv restoration score runtime column"
assert_contains "${script_source}" "OTC_ADMIN_TRACE_RUNTIME" "runtime trace env"
assert_contains "${script_source}" "OTC_ADMIN_TRACE_ARGS" "runtime trace args env"
assert_contains "${script_source}" "OTC_ADMIN_TRACE_TIMEOUT" "runtime trace timeout env"
assert_contains "${script_source}" "package_record_runtime_launch_support" "csv runtime launch support column"
assert_contains "${script_source}" "package_record_runtime_run_status" "csv runtime run status column"
assert_contains "${script_source}" "package_record_runtime_events" "csv runtime events column"
assert_contains "${script_source}" "package_record_gap_count" "csv package-record gap count column"
assert_contains "${script_source}" "package_record_gap_categories" "csv package-record gap categories column"
assert_contains "${script_source}" "in_gap_table" "gap parser table guard"
assert_contains "${script_source}" "package_record_byte_package_gate" "csv byte package gate column"
assert_contains "${script_source}" "package_record_runtime_observation_gate" "csv runtime observation gate column"
assert_contains "${script_source}" "byte_exact_runtime_launch_support" "csv byte-exact runtime launch support column"
assert_contains "${script_source}" "byte_exact_runtime_run_status" "csv byte-exact runtime run status column"
assert_contains "${script_source}" "byte_exact_runtime_events" "csv byte-exact runtime events column"
assert_contains "${script_source}" "byte_exact_gap_count" "csv byte-exact gap count column"
assert_contains "${script_source}" "byte_exact_gap_categories" "csv byte-exact gap categories column"
assert_contains "${script_source}" "byte_exact_byte_package_gate" "csv byte-exact package gate column"
assert_contains "${script_source}" "byte_exact_runtime_observation_gate" "csv byte-exact runtime observation gate column"
assert_contains "${script_source}" "PASS_HEALTHY_TIMEOUT" "runtime healthy timeout observation status"
assert_contains "${script_source}" "STARTUP_FAILED_TIMEOUT" "runtime startup failure observation status"
assert_contains "${script_source}" "STARTUP_FAILED_EXIT" "runtime startup failure exit observation status"
assert_contains "${script_source}" "WARN_STARTED_TIMEOUT" "runtime timeout observation status"
assert_contains "${script_source}" "Gate status" "markdown gate status section"
assert_contains "${script_source}" "Runtime observation" "markdown runtime observation gate header"
assert_contains "${script_source}" "Remaining gaps" "markdown remaining gaps section"
assert_contains "${script_source}" "Restoration score breakdown" "markdown restoration score section"
assert_contains "${script_source}" "Decompile parity risk reasons" "markdown parity reason section"

help_output="$(bash "${SCRIPT}" --help)"
assert_contains "${help_output}" "OTC_ADMIN_JAR" "sample path env"
assert_contains "${help_output}" "/Users/jackma/ProjectCode/68集团/OTC/otc-admin.jar" "default OTC sample"
assert_contains "${help_output}" "OTC_ADMIN_REFERENCE_PROJECT" "reference project env"
assert_contains "${help_output}" "OTC_ADMIN_TRACE_RUNTIME" "runtime trace env help"
assert_contains "${help_output}" "OTC_ADMIN_TRACE_ARGS" "runtime trace args env help"
assert_contains "${help_output}" "OTC_ADMIN_TRACE_TIMEOUT" "runtime trace timeout env help"
assert_contains "${help_output}" "Runtime trace timeout in seconds when tracing is enabled. Default: 30" "runtime trace timeout default"
assert_contains "${help_output}" "--restore-package-records" "package-record mode"
assert_contains "${help_output}" "--byte-exact-package" "byte-exact mode"
assert_contains "${help_output}" "otc-admin-summary.csv" "csv report"
assert_contains "${help_output}" "otc-admin-summary.md" "markdown report"
assert_contains "${help_output}" "otc-admin-source-diff.txt" "source diff report"
assert_contains "${help_output}" "reference-only Java files" "source diff purpose"
assert_contains "${help_output}" "original JAR class presence" "source diff class presence"
assert_contains "${help_output}" "class bytecode and ZIP metadata fidelity details" "artifact fidelity detail purpose"
assert_contains "${help_output}" "decompile parity risk summary" "parity risk detail purpose"
assert_contains "${help_output}" "HIGH/MEDIUM method index" "parity risk method index purpose"
assert_contains "${help_output}" "risk reason breakdown" "parity risk reason breakdown purpose"
assert_contains "${help_output}" "restoration score bucket breakdown" "restoration score bucket purpose"
assert_contains "${help_output}" "remaining gap summary" "remaining gap summary purpose"
assert_contains "${help_output}" "gate status for build, source coverage, byte package, and runtime observation" "gate status purpose"
assert_contains "${help_output}" "source coverage gates" "parity source coverage gates"
