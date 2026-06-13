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
assert_contains "${script_source}" "local prefix=\"- \${label}: \"" "runtime field parser prefix"
assert_contains "${script_source}" "sub(prefix, \"\", value)" "runtime field parser preserves colon content"
assert_contains "${script_source}" "package_record_runtime_launch_support" "csv runtime launch support column"
assert_contains "${script_source}" "package_record_runtime_run_status" "csv runtime run status column"
assert_contains "${script_source}" "package_record_runtime_failure_message" "csv runtime failure message column"
assert_contains "${script_source}" "package_record_runtime_failure_cause" "csv runtime failure cause column"
assert_contains "${script_source}" "package_record_runtime_events" "csv runtime events column"
assert_contains "${script_source}" "shared_java_files" "csv shared java files column"
assert_contains "${script_source}" "shared_java_content_diff_files" "csv shared java content diff column"
assert_contains "${script_source}" "shared_java_format_only_diff_files" "csv shared java format-only diff column"
assert_contains "${script_source}" "shared_java_import_only_diff_files" "csv shared java import-only diff column"
assert_contains "${script_source}" "shared_java_decompiler_artifact_diff_files" "csv shared java decompiler artifact diff column"
assert_contains "${script_source}" "shared_java_substantive_diff_files" "csv shared java substantive diff column"
assert_contains "${script_source}" "source_content_diff_report" "csv source content diff report column"
assert_contains "${script_source}" "Shared Java files with content differences" "markdown shared content diff section"
assert_contains "${script_source}" "Decompiler-artifact shared Java content differences" "markdown decompiler artifact content diff count"
assert_contains "${script_source}" "Substantive shared Java content differences" "markdown substantive content diff count"
assert_contains "${script_source}" "package_record_gap_count" "csv package-record gap count column"
assert_contains "${script_source}" "package_record_gap_categories" "csv package-record gap categories column"
assert_contains "${script_source}" "in_gap_table" "gap parser table guard"
assert_contains "${script_source}" "package_record_byte_package_gate" "csv byte package gate column"
assert_contains "${script_source}" "package_record_runtime_observation_gate" "csv runtime observation gate column"
assert_contains "${script_source}" "byte_exact_runtime_launch_support" "csv byte-exact runtime launch support column"
assert_contains "${script_source}" "byte_exact_runtime_run_status" "csv byte-exact runtime run status column"
assert_contains "${script_source}" "byte_exact_runtime_failure_message" "csv byte-exact runtime failure message column"
assert_contains "${script_source}" "byte_exact_runtime_failure_cause" "csv byte-exact runtime failure cause column"
assert_contains "${script_source}" "byte_exact_runtime_events" "csv byte-exact runtime events column"
assert_contains "${script_source}" "byte_exact_gap_count" "csv byte-exact gap count column"
assert_contains "${script_source}" "byte_exact_gap_categories" "csv byte-exact gap categories column"
assert_contains "${script_source}" "package_record_observation_count" "csv package-record observation count column"
assert_contains "${script_source}" "package_record_observation_categories" "csv package-record observation categories column"
assert_contains "${script_source}" "byte_exact_observation_count" "csv byte-exact observation count column"
assert_contains "${script_source}" "byte_exact_observation_categories" "csv byte-exact observation categories column"
assert_contains "${script_source}" "byte_exact_byte_package_gate" "csv byte-exact package gate column"
assert_contains "${script_source}" "byte_exact_runtime_observation_gate" "csv byte-exact runtime observation gate column"
assert_contains "${script_source}" "PASS_HEALTHY_TIMEOUT" "runtime healthy timeout observation status"
assert_contains "${script_source}" "STARTUP_FAILED_TIMEOUT" "runtime startup failure observation status"
assert_contains "${script_source}" "STARTUP_FAILED_EXIT" "runtime startup failure exit observation status"
assert_contains "${script_source}" "WARN_STARTED_TIMEOUT" "runtime timeout observation status"
assert_contains "${script_source}" "Gate status" "markdown gate status section"
assert_contains "${script_source}" "Runtime observation" "markdown runtime observation gate header"
assert_contains "${script_source}" "Runtime failure summary" "markdown runtime failure summary section"
assert_contains "${script_source}" "Remaining restoration gaps" "markdown remaining gaps section"
assert_contains "${script_source}" "Non-penalizing observations" "markdown observations section"
assert_contains "${script_source}" "Restoration score breakdown" "markdown restoration score section"
assert_contains "${script_source}" "Decompile parity risk reasons" "markdown parity reason section"
assert_contains "${script_source}" "Restored package artifact fidelity details" "markdown restored package artifact section"
assert_contains "${script_source}" "source-recompiled class byte equivalence" "artifact fidelity scope note"
assert_contains "${script_source}" "Source rebuild class bytecode fidelity" "markdown source rebuild fidelity section"
assert_contains "${script_source}" "otc-admin-source-rebuild-bytecode.md" "source rebuild fidelity report"
assert_contains "${script_source}" "package_record_source_rebuild_same_class_bytes" "package-record source rebuild metric"
assert_contains "${script_source}" "byte_exact_source_rebuild_same_class_bytes" "byte-exact source rebuild metric"
assert_contains "${script_source}" "source-rebuild-fidelity-summary.csv" "generic source rebuild fidelity summary"
assert_contains "${script_source}" "package_record_source_rebuild_report_same" "package-record generic source rebuild exact gate"
assert_contains "${script_source}" "byte_exact_source_rebuild_report_same" "byte-exact generic source rebuild exact gate"
assert_contains "${script_source}" "source rebuild report fallback classes" "generic source rebuild fallback gate"

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
assert_contains "${help_output}" "otc-admin-source-content-diff.txt" "source content diff report"
assert_contains "${help_output}" "otc-admin-source-rebuild-bytecode.md" "source rebuild bytecode report"
assert_contains "${help_output}" "reference-only Java files" "source diff purpose"
assert_contains "${help_output}" "shared Java content differences" "source content diff purpose"
assert_contains "${help_output}" "format-only, import-only, decompiler-artifact, and substantive" "source content diff classification purpose"
assert_contains "${help_output}" "original JAR class presence" "source diff class presence"
assert_contains "${help_output}" "restored package artifact fidelity details" "artifact fidelity detail purpose"
assert_contains "${help_output}" "source-recompiled class bytecode fidelity" "source rebuild fidelity detail purpose"
assert_contains "${help_output}" "not a source-recompiled class-byte equivalence signal" "artifact fidelity scope help"
assert_contains "${help_output}" "decompile parity risk summary" "parity risk detail purpose"
assert_contains "${help_output}" "HIGH/MEDIUM method index" "parity risk method index purpose"
assert_contains "${help_output}" "risk reason breakdown" "parity risk reason breakdown purpose"
assert_contains "${help_output}" "restoration score bucket breakdown" "restoration score bucket purpose"
assert_contains "${help_output}" "remaining gap summary" "remaining gap summary purpose"
assert_contains "${help_output}" "gate status for build, source coverage, byte package, and runtime observation" "gate status purpose"
assert_contains "${help_output}" "failure message and failure cause" "runtime failure summary purpose"
assert_contains "${help_output}" "source coverage gates" "parity source coverage gates"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

extract_function() {
  local function_name="$1"
  awk "/^${function_name}\\(\\) \\{/,/^}/ { print }" "${SCRIPT}"
}

eval "$(extract_function normalize_java_file_for_decompiler_artifacts)"
eval "$(extract_function java_files_equal_after_decompiler_artifact_normalization)"

cat > "${tmp_dir}/reference.java" <<'JAVA'
class Sample {
    boolean same(Object o) {
        Sample other = (Sample)((Object)o);
        return !((Object)this$id).equals(other$id);
    }

    Object page(Request req) {
        return new PageData<>(Collections.emptyList(), 0L, req.getPageNum().intValue(), req.getPageSize().intValue());
    }
}
JAVA

cat > "${tmp_dir}/generated.java" <<'JAVA'
class Sample {
    boolean same(Object o) {
        Sample other = (Sample)o;
        return !(this$id).equals(other$id);
    }

    Object page(Request req) {
        return new PageData(Collections.emptyList(), 0L, (long)req.getPageNum().intValue(), (long)req.getPageSize().intValue());
    }
}
JAVA

if ! java_files_equal_after_decompiler_artifact_normalization \
    "${tmp_dir}/reference.java" "${tmp_dir}/generated.java"; then
  printf 'FAIL decompiler artifact normalization should preserve parenthesized variables while removing casts\n' >&2
  exit 1
fi
