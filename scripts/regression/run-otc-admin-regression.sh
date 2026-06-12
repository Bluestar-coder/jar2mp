#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEFAULT_OTC_ADMIN_JAR="/Users/jackma/ProjectCode/68集团/OTC/otc-admin.jar"
DEFAULT_OTC_ADMIN_REFERENCE_PROJECT="/Users/jackma/ProjectCode/68集团/OTC/OTC-Admin"

OTC_ADMIN_JAR="${OTC_ADMIN_JAR:-${DEFAULT_OTC_ADMIN_JAR}}"
OTC_ADMIN_REFERENCE_PROJECT="${OTC_ADMIN_REFERENCE_PROJECT:-${DEFAULT_OTC_ADMIN_REFERENCE_PROJECT}}"
WORK_DIR="${OTC_ADMIN_WORK_DIR:-${ROOT_DIR}/target/otc-admin-sample}"
RESTORE_DIR="${WORK_DIR}/restored"
REPORT_DIR="${WORK_DIR}/report"
JAR2MP_JAR="${JAR2MP_JAR:-${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar}"
BUILD_JAR2MP="${BUILD_JAR2MP:-1}"

usage() {
  cat <<EOF
Usage: scripts/regression/run-otc-admin-regression.sh

Runs the local OTC admin sample as a focused byte-level restoration regression.

Environment:
  OTC_ADMIN_JAR
      Sample JAR path. Default: ${DEFAULT_OTC_ADMIN_JAR}
  OTC_ADMIN_REFERENCE_PROJECT
      Previously repaired reference project used for source-count comparison.
      Default: ${DEFAULT_OTC_ADMIN_REFERENCE_PROJECT}
  OTC_ADMIN_WORK_DIR
      Output root. Default: target/otc-admin-sample
  JAR2MP_JAR
      jar2mp executable JAR. Default: target/jar2mp-1.0-jar-with-dependencies.jar
  BUILD_JAR2MP
      Set to 0 to skip rebuilding jar2mp before running. Default: 1
  MVN
      Maven executable. Defaults to mvn, or IntelliJ IDEA's bundled Maven when mvn is not on PATH.
  JAVA_CMD
      Java executable. Defaults to JAVA_HOME/bin/java when JAVA_HOME is set, otherwise java.

The script runs both byte-level package restoration paths:
  java -jar jar2mp.jar --restore-package-records --verify-build ...
  java -jar jar2mp.jar --byte-exact-package --verify-build ...

Reports:
  target/otc-admin-sample/report/otc-admin-summary.csv
  target/otc-admin-sample/report/otc-admin-summary.md
  target/otc-admin-sample/report/package-record.cli.log
  target/otc-admin-sample/report/byte-exact.cli.log
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

log() {
  printf '[otc-regression] %s\n' "$*"
}

csv_field() {
  local value="$1"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
}

resolve_maven() {
  if [[ -n "${MVN:-}" ]]; then
    printf '%s\n' "${MVN}"
    return
  fi
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return
  fi
  local idea_maven="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
  if [[ -x "${idea_maven}" ]]; then
    printf '%s\n' "${idea_maven}"
    return
  fi
  printf '%s\n' "mvn"
}

resolve_java() {
  if [[ -n "${JAVA_CMD:-}" ]]; then
    printf '%s\n' "${JAVA_CMD}"
    return
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "${JAVA_HOME}/bin/java"
    return
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return
  fi
  printf '%s\n' "java"
}

markdown_field() {
  local file="$1"
  local field="$2"
  local default_value="$3"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  awk -v key="- ${field}: " '
    index($0, key) == 1 {
      sub(key, "", $0)
      print
      found = 1
      exit
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "${file}" 2>/dev/null || printf '%s\n' "${default_value}"
}

csv_column_by_name() {
  local file="$1"
  local column_name="$2"
  local default_value="$3"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  awk -F',' -v column_name="${column_name}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == column_name) {
          column = i
          break
        }
      }
      next
    }
    NR == 2 && column {
      print $column
      found = 1
      exit
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "${file}" 2>/dev/null || printf '%s\n' "${default_value}"
}

count_java_files() {
  local dir="$1"
  if [[ ! -d "${dir}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  find "${dir}" -type f -name '*.java' | wc -l | tr -d '[:space:]'
}

count_decompile_failures() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  grep -c '^- Failed to decompile ' "${file}" || true
}

shasum256() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  shasum -a 256 "${file}" | awk '{ print $1 }'
}

find_project_dir() {
  local output_base="$1"
  find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1
}

restored_artifact_path() {
  local project_dir="$1"
  local mode="$2"
  local artifact_name
  artifact_name="$(basename "${OTC_ADMIN_JAR}")"

  local candidate
  if [[ "${mode}" == "byte_exact" ]]; then
    candidate="${project_dir}/target/byte-exact-package-restored/${artifact_name}"
  else
    candidate="${project_dir}/target/${artifact_name}"
  fi

  if [[ -f "${candidate}" ]]; then
    printf '%s\n' "${candidate}"
    return
  fi

  if [[ "${mode}" == "package_record" && -d "${project_dir}/target/package-record-restored" ]]; then
    candidate="$(find "${project_dir}/target/package-record-restored" -maxdepth 1 -type f \
      \( -name '*.jar' -o -name '*.war' \) | sort | head -n 1)"
    if [[ -n "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  fi

  find "${project_dir}/target" -maxdepth 3 -type f \( -name '*.jar' -o -name '*.war' \) \
    ! -name '*.original' ! -name 'compiler-fallback-classes.jar' \
    ! -path '*/raw-artifact/*' ! -path '*/original-libs/*' | sort | head -n 1
}

set_var() {
  printf -v "$1" '%s' "$2"
}

run_restore_mode() {
  local prefix="$1"
  local label="$2"
  local output_base="$3"
  local check_dir="$4"
  shift 4

  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  local log_file="${REPORT_DIR}/${label}.cli.log"
  log "Running ${label}: ${output_base}"
  set +e
  "${JAVA_BIN}" -jar "${JAR2MP_JAR}" \
    --verbose "$@" --verify-build -f -o "${output_base}" "${OTC_ADMIN_JAR}" \
    >"${log_file}" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find_project_dir "${output_base}" || true)"
  local verification_report="${project_dir}/verification-report.md"
  local fidelity_summary="${project_dir}/target/${check_dir}/artifact-fidelity-summary.csv"
  local decompile_failures="${project_dir}/decompile-failures.md"
  local artifact_path
  artifact_path="$(restored_artifact_path "${project_dir}" "${prefix}" || true)"

  set_var "${prefix}_exit_code" "${exit_code}"
  set_var "${prefix}_project_dir" "${project_dir:-missing}"
  set_var "${prefix}_artifact_path" "${artifact_path:-missing}"
  set_var "${prefix}_artifact_sha256" "$(shasum256 "${artifact_path:-}")"
  set_var "${prefix}_verification_summary" "$(markdown_field "${verification_report}" "Summary" "missing")"
  set_var "${prefix}_verification_failure_type" "$(markdown_field "${verification_report}" "Failure type" "missing")"
  set_var "${prefix}_verification_error_count" "$(markdown_field "${verification_report}" "Error count" "missing")"
  set_var "${prefix}_compile_fallback_classes" "$(markdown_field "${verification_report}" "Compile fallback classes" "missing")"
  set_var "${prefix}_decompile_failures" "$(count_decompile_failures "${decompile_failures}")"
  set_var "${prefix}_exact" "$(csv_column_by_name "${fidelity_summary}" "exact_match" "missing")"
  set_var "${prefix}_original_sha256" "$(csv_column_by_name "${fidelity_summary}" "original_archive_sha256" "missing")"
  set_var "${prefix}_rebuilt_sha256" "$(csv_column_by_name "${fidelity_summary}" "rebuilt_archive_sha256" "missing")"
}

require_file() {
  local file="$1"
  local label="$2"
  if [[ ! -f "${file}" ]]; then
    printf 'Missing %s: %s\n' "${label}" "${file}" >&2
    exit 2
  fi
}

MVN_CMD="$(resolve_maven)"
JAVA_BIN="$(resolve_java)"
ORIGINAL_SHA256="$(shasum256 "${OTC_ADMIN_JAR}")"

require_file "${OTC_ADMIN_JAR}" "OTC admin sample JAR"

rm -rf "${RESTORE_DIR}" "${REPORT_DIR}"
mkdir -p "${RESTORE_DIR}" "${REPORT_DIR}"

if [[ "${BUILD_JAR2MP}" != "0" ]]; then
  log "Building jar2mp with ${MVN_CMD}"
  (cd "${ROOT_DIR}" && "${MVN_CMD}" -q -DskipTests package)
fi
require_file "${JAR2MP_JAR}" "jar2mp executable JAR"

PACKAGE_RECORD_OUTPUT="${RESTORE_DIR}/package-record"
BYTE_EXACT_OUTPUT="${RESTORE_DIR}/byte-exact"

run_restore_mode "package_record" "package-record" "${PACKAGE_RECORD_OUTPUT}" \
  "package-record-restore-check" --restore-package-records
run_restore_mode "byte_exact" "byte-exact" "${BYTE_EXACT_OUTPUT}" \
  "byte-exact-package-check" --byte-exact-package

REFERENCE_JAVA_FILES="$(count_java_files "${OTC_ADMIN_REFERENCE_PROJECT}/src/main/java")"
GENERATED_JAVA_FILES="$(count_java_files "${package_record_project_dir}/src/main/java")"
JAR_SIZE_BYTES="$(wc -c < "${OTC_ADMIN_JAR}" | tr -d '[:space:]')"

CSV_REPORT="${REPORT_DIR}/otc-admin-summary.csv"
MD_REPORT="${REPORT_DIR}/otc-admin-summary.md"

{
  printf 'sample,jar,jar_size_bytes,original_sha256,reference_project,reference_java_files,generated_java_files,package_record_exit_code,package_record_verification_summary,package_record_failure_type,package_record_error_count,package_record_compile_fallback_classes,package_record_decompile_failures,package_record_exact,package_record_original_sha256,package_record_rebuilt_sha256,package_record_artifact_sha256,package_record_project,byte_exact_exit_code,byte_exact_verification_summary,byte_exact_failure_type,byte_exact_error_count,byte_exact_compile_fallback_classes,byte_exact_decompile_failures,byte_exact_exact,byte_exact_original_sha256,byte_exact_rebuilt_sha256,byte_exact_artifact_sha256,byte_exact_project\n'
  csv_field "otc-admin"; printf ','
  csv_field "${OTC_ADMIN_JAR}"; printf ','
  csv_field "${JAR_SIZE_BYTES}"; printf ','
  csv_field "${ORIGINAL_SHA256}"; printf ','
  csv_field "${OTC_ADMIN_REFERENCE_PROJECT}"; printf ','
  csv_field "${REFERENCE_JAVA_FILES}"; printf ','
  csv_field "${GENERATED_JAVA_FILES}"; printf ','
  csv_field "${package_record_exit_code}"; printf ','
  csv_field "${package_record_verification_summary}"; printf ','
  csv_field "${package_record_verification_failure_type}"; printf ','
  csv_field "${package_record_verification_error_count}"; printf ','
  csv_field "${package_record_compile_fallback_classes}"; printf ','
  csv_field "${package_record_decompile_failures}"; printf ','
  csv_field "${package_record_exact}"; printf ','
  csv_field "${package_record_original_sha256}"; printf ','
  csv_field "${package_record_rebuilt_sha256}"; printf ','
  csv_field "${package_record_artifact_sha256}"; printf ','
  csv_field "${package_record_project_dir}"; printf ','
  csv_field "${byte_exact_exit_code}"; printf ','
  csv_field "${byte_exact_verification_summary}"; printf ','
  csv_field "${byte_exact_verification_failure_type}"; printf ','
  csv_field "${byte_exact_verification_error_count}"; printf ','
  csv_field "${byte_exact_compile_fallback_classes}"; printf ','
  csv_field "${byte_exact_decompile_failures}"; printf ','
  csv_field "${byte_exact_exact}"; printf ','
  csv_field "${byte_exact_original_sha256}"; printf ','
  csv_field "${byte_exact_rebuilt_sha256}"; printf ','
  csv_field "${byte_exact_artifact_sha256}"; printf ','
  csv_field "${byte_exact_project_dir}"; printf '\n'
} > "${CSV_REPORT}"

{
  printf '# OTC Admin Sample Regression\n\n'
  printf '%s\n' "- Sample: \`${OTC_ADMIN_JAR}\`"
  printf '%s\n' "- Original SHA-256: \`${ORIGINAL_SHA256}\`"
  printf '%s\n' "- Reference project: \`${OTC_ADMIN_REFERENCE_PROJECT}\`"
  printf '%s\n' "- Reference Java files: \`${REFERENCE_JAVA_FILES}\`"
  printf '%s\n\n' "- Generated Java files: \`${GENERATED_JAVA_FILES}\`"
  printf '| Mode | jar2mp exit | Verification | Failure type | Errors | Compile fallback classes | Decompile failures | Exact package | Rebuilt SHA-256 |\n'
  printf '| --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- |\n'
  printf '| package-record | %s | %s | %s | %s | %s | %s | %s | `%s` |\n' \
    "${package_record_exit_code}" "${package_record_verification_summary}" \
    "${package_record_verification_failure_type}" "${package_record_verification_error_count}" \
    "${package_record_compile_fallback_classes}" "${package_record_decompile_failures}" \
    "${package_record_exact}" "${package_record_rebuilt_sha256}"
  printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s | `%s` |\n\n' \
    "${byte_exact_exit_code}" "${byte_exact_verification_summary}" \
    "${byte_exact_verification_failure_type}" "${byte_exact_verification_error_count}" \
    "${byte_exact_compile_fallback_classes}" "${byte_exact_decompile_failures}" \
    "${byte_exact_exact}" "${byte_exact_rebuilt_sha256}"
  printf '## Artifacts\n\n'
  printf '%s\n' "- package-record project: \`${package_record_project_dir}\`"
  printf '%s\n' "- package-record artifact: \`${package_record_artifact_path}\`"
  printf '%s\n' "- byte-exact project: \`${byte_exact_project_dir}\`"
  printf '%s\n' "- byte-exact artifact: \`${byte_exact_artifact_path}\`"
  printf '%s\n' "- CSV: \`${CSV_REPORT}\`"
} > "${MD_REPORT}"

gate_status=0
check_gate() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL %s: expected %s, got %s\n' "${label}" "${expected}" "${actual}" >&2
    gate_status=1
  fi
}

check_gate "package-record jar2mp exit" "${package_record_exit_code}" "0"
check_gate "package-record verification summary" "${package_record_verification_summary}" "BUILD SUCCESS"
check_gate "package-record failure type" "${package_record_verification_failure_type}" "NONE"
check_gate "package-record exact match" "${package_record_exact}" "true"
check_gate "package-record original SHA" "${package_record_original_sha256}" "${ORIGINAL_SHA256}"
check_gate "package-record rebuilt SHA" "${package_record_rebuilt_sha256}" "${ORIGINAL_SHA256}"
check_gate "package-record artifact SHA" "${package_record_artifact_sha256}" "${ORIGINAL_SHA256}"

check_gate "byte-exact jar2mp exit" "${byte_exact_exit_code}" "0"
check_gate "byte-exact verification summary" "${byte_exact_verification_summary}" "BUILD SUCCESS"
check_gate "byte-exact failure type" "${byte_exact_verification_failure_type}" "NONE"
check_gate "byte-exact exact match" "${byte_exact_exact}" "true"
check_gate "byte-exact original SHA" "${byte_exact_original_sha256}" "${ORIGINAL_SHA256}"
check_gate "byte-exact rebuilt SHA" "${byte_exact_rebuilt_sha256}" "${ORIGINAL_SHA256}"
check_gate "byte-exact artifact SHA" "${byte_exact_artifact_sha256}" "${ORIGINAL_SHA256}"

log "Summary written to ${MD_REPORT}"
log "CSV written to ${CSV_REPORT}"
exit "${gate_status}"
