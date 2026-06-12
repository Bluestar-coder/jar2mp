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
  JAR_CMD
      jar executable used to inspect original JAR entries. Defaults to JAVA_CMD's sibling jar, then PATH jar.

The script runs both byte-level package restoration paths:
  java -jar jar2mp.jar --restore-package-records --verify-build ...
  java -jar jar2mp.jar --byte-exact-package --verify-build ...

Reports:
  target/otc-admin-sample/report/otc-admin-summary.csv
  target/otc-admin-sample/report/otc-admin-summary.md
  target/otc-admin-sample/report/otc-admin-source-diff.txt
  target/otc-admin-sample/report/package-record.cli.log
  target/otc-admin-sample/report/byte-exact.cli.log

The source diff report lists reference-only Java files, generated-only Java files,
and original JAR class presence when OTC_ADMIN_REFERENCE_PROJECT is present.
The summary also includes class bytecode and ZIP metadata fidelity details from
each artifact-fidelity-summary.csv, plus the decompile parity risk summary from
each decompile-parity-report.md. The LocalVariableTable missing-name count is limited
to methods that need user parameter or local-variable names. The source coverage gates
require zero class parse failures and zero missing-source methods in both modes.
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

resolve_jar_tool() {
  if [[ -n "${JAR_CMD:-}" ]]; then
    printf '%s\n' "${JAR_CMD}"
    return
  fi
  local java_dir
  java_dir="$(dirname "${JAVA_BIN}")"
  if [[ -x "${java_dir}/jar" ]]; then
    printf '%s\n' "${java_dir}/jar"
    return
  fi
  if command -v jar >/dev/null 2>&1; then
    command -v jar
    return
  fi
  printf '%s\n' ""
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

parity_summary_field() {
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

parity_risk_count() {
  local file="$1"
  local risk="$2"
  local default_value="$3"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  awk -F '|' -v risk="${risk}" '
    $2 ~ "^[[:space:]]*" risk "[[:space:]]*$" {
      value = $3
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
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

count_file_lines() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  wc -l < "${file}" | tr -d '[:space:]'
}

count_present_classes() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  awk -F '\t' '$2 != "absent" { count++ } END { print count + 0 }' "${file}"
}

count_absent_classes() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "missing"
    return
  fi
  awk -F '\t' '$2 == "absent" { count++ } END { print count + 0 }' "${file}"
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

write_original_jar_entries() {
  local original_jar="$1"
  local entries_file="$2"
  local jar_tool
  jar_tool="$(resolve_jar_tool)"

  if [[ -n "${jar_tool}" ]]; then
    "${jar_tool}" tf "${original_jar}" > "${entries_file}"
    return
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "${original_jar}" > "${entries_file}"
    return
  fi
  : > "${entries_file}"
  return 1
}

write_class_presence_list() {
  local java_list="$1"
  local entries_file="$2"
  local presence_file="$3"
  : > "${presence_file}"
  if [[ ! -f "${java_list}" ]]; then
    return
  fi
  while IFS= read -r java_file; do
    [[ -z "${java_file}" ]] && continue
    local class_path="${java_file%.java}.class"
    local found="absent"
    local candidate
    for candidate in "${class_path}" "BOOT-INF/classes/${class_path}" "WEB-INF/classes/${class_path}"; do
      if [[ -f "${entries_file}" ]] && grep -Fxq "${candidate}" "${entries_file}"; then
        found="${candidate}"
        break
      fi
    done
    printf '%s\t%s\n' "${java_file}" "${found}" >> "${presence_file}"
  done < "${java_list}"
}

write_presence_table() {
  local title="$1"
  local count="$2"
  local presence_file="$3"
  printf '## %s\n\n' "${title}"
  if [[ "${count}" == "0" ]]; then
    printf 'None\n\n'
    return
  fi
  printf '| Java file | Original JAR class presence |\n'
  printf '| --- | --- |\n'
  awk -F '\t' '{ printf "| `%s` | `%s` |\n", $1, $2 }' "${presence_file}"
  printf '\n'
}

write_source_diff_report() {
  local generated_dir="$1"
  local reference_dir="$2"
  local report="$3"
  local entries_file="$4"
  local reference_list="${REPORT_DIR}/.reference-java-files"
  local generated_list="${REPORT_DIR}/.generated-java-files"
  local reference_only="${REPORT_DIR}/.reference-only-java-files"
  local generated_only="${REPORT_DIR}/.generated-only-java-files"
  local reference_only_presence="${REPORT_DIR}/.reference-only-class-presence"
  local generated_only_presence="${REPORT_DIR}/.generated-only-class-presence"

  if [[ ! -d "${generated_dir}" || ! -d "${reference_dir}" ]]; then
    set_var "REFERENCE_ONLY_JAVA_FILES" "missing"
    set_var "GENERATED_ONLY_JAVA_FILES" "missing"
    set_var "REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT" "missing"
    set_var "REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT" "missing"
    set_var "GENERATED_ONLY_ORIGINAL_CLASS_PRESENT" "missing"
    set_var "GENERATED_ONLY_ORIGINAL_CLASS_ABSENT" "missing"
    {
      printf '# OTC Admin Source File Diff\n\n'
      printf 'Source diff unavailable.\n\n'
      printf '%s\n' "- Generated source dir: \`${generated_dir}\`"
      printf '%s\n' "- Reference source dir: \`${reference_dir}\`"
    } > "${report}"
    return
  fi

  (cd "${reference_dir}" && find . -type f -name '*.java' | sed 's#^\./##' | sort) > "${reference_list}"
  (cd "${generated_dir}" && find . -type f -name '*.java' | sed 's#^\./##' | sort) > "${generated_list}"
  comm -23 "${reference_list}" "${generated_list}" > "${reference_only}"
  comm -13 "${reference_list}" "${generated_list}" > "${generated_only}"

  set_var "REFERENCE_ONLY_JAVA_FILES" "$(count_file_lines "${reference_only}")"
  set_var "GENERATED_ONLY_JAVA_FILES" "$(count_file_lines "${generated_only}")"
  write_class_presence_list "${reference_only}" "${entries_file}" "${reference_only_presence}"
  write_class_presence_list "${generated_only}" "${entries_file}" "${generated_only_presence}"
  set_var "REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT" "$(count_present_classes "${reference_only_presence}")"
  set_var "REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT" "$(count_absent_classes "${reference_only_presence}")"
  set_var "GENERATED_ONLY_ORIGINAL_CLASS_PRESENT" "$(count_present_classes "${generated_only_presence}")"
  set_var "GENERATED_ONLY_ORIGINAL_CLASS_ABSENT" "$(count_absent_classes "${generated_only_presence}")"

  {
    printf '# OTC Admin Source File Diff\n\n'
    printf '%s\n' "- Generated source dir: \`${generated_dir}\`"
    printf '%s\n' "- Reference source dir: \`${reference_dir}\`"
    printf '%s\n' "- Reference-only Java files: \`${REFERENCE_ONLY_JAVA_FILES}\`"
    printf '%s\n' "- Generated-only Java files: \`${GENERATED_ONLY_JAVA_FILES}\`"
    printf '%s\n' "- Reference-only Java files with original JAR class: \`${REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT}\`"
    printf '%s\n' "- Reference-only Java files absent from original JAR classes: \`${REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT}\`"
    printf '%s\n' "- Generated-only Java files with original JAR class: \`${GENERATED_ONLY_ORIGINAL_CLASS_PRESENT}\`"
    printf '%s\n\n' "- Generated-only Java files absent from original JAR classes: \`${GENERATED_ONLY_ORIGINAL_CLASS_ABSENT}\`"

    write_presence_table "Reference-only Java files" "${REFERENCE_ONLY_JAVA_FILES}" "${reference_only_presence}"
    write_presence_table "Generated-only Java files" "${GENERATED_ONLY_JAVA_FILES}" "${generated_only_presence}"
  } > "${report}"

  rm -f "${reference_list}" "${generated_list}" "${reference_only}" "${generated_only}" \
    "${reference_only_presence}" "${generated_only_presence}"
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
  local parity_report="${project_dir}/decompile-parity-report.md"
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
  set_var "${prefix}_content_entries_match" "$(csv_column_by_name "${fidelity_summary}" "content_entries_match" "missing")"
  set_var "${prefix}_same_class_bytes" "$(csv_column_by_name "${fidelity_summary}" "same_class_bytes" "missing")"
  set_var "${prefix}_different_class_bytes" "$(csv_column_by_name "${fidelity_summary}" "different_class_bytes" "missing")"
  set_var "${prefix}_same_nested_libs" "$(csv_column_by_name "${fidelity_summary}" "same_nested_libs" "missing")"
  set_var "${prefix}_different_nested_libs" "$(csv_column_by_name "${fidelity_summary}" "different_nested_libs" "missing")"
  set_var "${prefix}_archive_entry_order_same" "$(csv_column_by_name "${fidelity_summary}" "archive_entry_order_same" "missing")"
  set_var "${prefix}_archive_metadata_diff_entries" "$(csv_column_by_name "${fidelity_summary}" "archive_metadata_diff_entries" "missing")"
  set_var "${prefix}_archive_bytes_same" "$(csv_column_by_name "${fidelity_summary}" "archive_bytes_same" "missing")"
  set_var "${prefix}_original_sha256" "$(csv_column_by_name "${fidelity_summary}" "original_archive_sha256" "missing")"
  set_var "${prefix}_rebuilt_sha256" "$(csv_column_by_name "${fidelity_summary}" "rebuilt_archive_sha256" "missing")"
  set_var "${prefix}_parity_classes_scanned" "$(parity_summary_field "${parity_report}" "Classes scanned" "missing")"
  set_var "${prefix}_parity_methods_scanned" "$(parity_summary_field "${parity_report}" "Methods scanned" "missing")"
  set_var "${prefix}_parity_parse_failures" "$(parity_summary_field "${parity_report}" "Class parse failures" "missing")"
  set_var "${prefix}_parity_missing_source_methods" "$(parity_summary_field "${parity_report}" "Methods with missing source" "missing")"
  set_var "${prefix}_parity_reflection_methods" "$(parity_summary_field "${parity_report}" "Methods with reflection calls" "missing")"
  set_var "${prefix}_parity_invokedynamic_methods" "$(parity_summary_field "${parity_report}" "Methods with invokedynamic" "missing")"
  set_var "${prefix}_parity_missing_lvt_methods" "$(parity_summary_field "${parity_report}" "Methods without LocalVariableTable names" "missing")"
  set_var "${prefix}_parity_high_methods" "$(parity_risk_count "${parity_report}" "HIGH" "missing")"
  set_var "${prefix}_parity_medium_methods" "$(parity_risk_count "${parity_report}" "MEDIUM" "missing")"
  set_var "${prefix}_parity_low_methods" "$(parity_risk_count "${parity_report}" "LOW" "missing")"
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
ORIGINAL_JAR_ENTRIES="${REPORT_DIR}/.original-jar-entries"
ORIGINAL_SHA256="$(shasum256 "${OTC_ADMIN_JAR}")"

require_file "${OTC_ADMIN_JAR}" "OTC admin sample JAR"

rm -rf "${RESTORE_DIR}" "${REPORT_DIR}"
mkdir -p "${RESTORE_DIR}" "${REPORT_DIR}"
write_original_jar_entries "${OTC_ADMIN_JAR}" "${ORIGINAL_JAR_ENTRIES}"

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
SOURCE_DIFF_REPORT="${REPORT_DIR}/otc-admin-source-diff.txt"
write_source_diff_report \
  "${package_record_project_dir}/src/main/java" \
  "${OTC_ADMIN_REFERENCE_PROJECT}/src/main/java" \
  "${SOURCE_DIFF_REPORT}" \
  "${ORIGINAL_JAR_ENTRIES}"

{
  printf 'sample,jar,jar_size_bytes,original_sha256,reference_project,reference_java_files,generated_java_files,reference_only_java_files,generated_only_java_files,reference_only_original_class_present,reference_only_original_class_absent,generated_only_original_class_present,generated_only_original_class_absent,source_diff_report,package_record_exit_code,package_record_verification_summary,package_record_failure_type,package_record_error_count,package_record_compile_fallback_classes,package_record_decompile_failures,package_record_exact,package_record_content_entries_match,package_record_same_class_bytes,package_record_different_class_bytes,package_record_same_nested_libs,package_record_different_nested_libs,package_record_archive_entry_order_same,package_record_archive_metadata_diff_entries,package_record_archive_bytes_same,package_record_original_sha256,package_record_rebuilt_sha256,package_record_artifact_sha256,package_record_parity_classes_scanned,package_record_parity_methods_scanned,package_record_parity_parse_failures,package_record_parity_missing_source_methods,package_record_parity_reflection_methods,package_record_parity_invokedynamic_methods,package_record_parity_missing_lvt_methods,package_record_parity_high_methods,package_record_parity_medium_methods,package_record_parity_low_methods,package_record_project,byte_exact_exit_code,byte_exact_verification_summary,byte_exact_failure_type,byte_exact_error_count,byte_exact_compile_fallback_classes,byte_exact_decompile_failures,byte_exact_exact,byte_exact_content_entries_match,byte_exact_same_class_bytes,byte_exact_different_class_bytes,byte_exact_same_nested_libs,byte_exact_different_nested_libs,byte_exact_archive_entry_order_same,byte_exact_archive_metadata_diff_entries,byte_exact_archive_bytes_same,byte_exact_original_sha256,byte_exact_rebuilt_sha256,byte_exact_artifact_sha256,byte_exact_parity_classes_scanned,byte_exact_parity_methods_scanned,byte_exact_parity_parse_failures,byte_exact_parity_missing_source_methods,byte_exact_parity_reflection_methods,byte_exact_parity_invokedynamic_methods,byte_exact_parity_missing_lvt_methods,byte_exact_parity_high_methods,byte_exact_parity_medium_methods,byte_exact_parity_low_methods,byte_exact_project\n'
  csv_field "otc-admin"; printf ','
  csv_field "${OTC_ADMIN_JAR}"; printf ','
  csv_field "${JAR_SIZE_BYTES}"; printf ','
  csv_field "${ORIGINAL_SHA256}"; printf ','
  csv_field "${OTC_ADMIN_REFERENCE_PROJECT}"; printf ','
  csv_field "${REFERENCE_JAVA_FILES}"; printf ','
  csv_field "${GENERATED_JAVA_FILES}"; printf ','
  csv_field "${REFERENCE_ONLY_JAVA_FILES}"; printf ','
  csv_field "${GENERATED_ONLY_JAVA_FILES}"; printf ','
  csv_field "${REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT}"; printf ','
  csv_field "${REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT}"; printf ','
  csv_field "${GENERATED_ONLY_ORIGINAL_CLASS_PRESENT}"; printf ','
  csv_field "${GENERATED_ONLY_ORIGINAL_CLASS_ABSENT}"; printf ','
  csv_field "${SOURCE_DIFF_REPORT}"; printf ','
  csv_field "${package_record_exit_code}"; printf ','
  csv_field "${package_record_verification_summary}"; printf ','
  csv_field "${package_record_verification_failure_type}"; printf ','
  csv_field "${package_record_verification_error_count}"; printf ','
  csv_field "${package_record_compile_fallback_classes}"; printf ','
  csv_field "${package_record_decompile_failures}"; printf ','
  csv_field "${package_record_exact}"; printf ','
  csv_field "${package_record_content_entries_match}"; printf ','
  csv_field "${package_record_same_class_bytes}"; printf ','
  csv_field "${package_record_different_class_bytes}"; printf ','
  csv_field "${package_record_same_nested_libs}"; printf ','
  csv_field "${package_record_different_nested_libs}"; printf ','
  csv_field "${package_record_archive_entry_order_same}"; printf ','
  csv_field "${package_record_archive_metadata_diff_entries}"; printf ','
  csv_field "${package_record_archive_bytes_same}"; printf ','
  csv_field "${package_record_original_sha256}"; printf ','
  csv_field "${package_record_rebuilt_sha256}"; printf ','
  csv_field "${package_record_artifact_sha256}"; printf ','
  csv_field "${package_record_parity_classes_scanned}"; printf ','
  csv_field "${package_record_parity_methods_scanned}"; printf ','
  csv_field "${package_record_parity_parse_failures}"; printf ','
  csv_field "${package_record_parity_missing_source_methods}"; printf ','
  csv_field "${package_record_parity_reflection_methods}"; printf ','
  csv_field "${package_record_parity_invokedynamic_methods}"; printf ','
  csv_field "${package_record_parity_missing_lvt_methods}"; printf ','
  csv_field "${package_record_parity_high_methods}"; printf ','
  csv_field "${package_record_parity_medium_methods}"; printf ','
  csv_field "${package_record_parity_low_methods}"; printf ','
  csv_field "${package_record_project_dir}"; printf ','
  csv_field "${byte_exact_exit_code}"; printf ','
  csv_field "${byte_exact_verification_summary}"; printf ','
  csv_field "${byte_exact_verification_failure_type}"; printf ','
  csv_field "${byte_exact_verification_error_count}"; printf ','
  csv_field "${byte_exact_compile_fallback_classes}"; printf ','
  csv_field "${byte_exact_decompile_failures}"; printf ','
  csv_field "${byte_exact_exact}"; printf ','
  csv_field "${byte_exact_content_entries_match}"; printf ','
  csv_field "${byte_exact_same_class_bytes}"; printf ','
  csv_field "${byte_exact_different_class_bytes}"; printf ','
  csv_field "${byte_exact_same_nested_libs}"; printf ','
  csv_field "${byte_exact_different_nested_libs}"; printf ','
  csv_field "${byte_exact_archive_entry_order_same}"; printf ','
  csv_field "${byte_exact_archive_metadata_diff_entries}"; printf ','
  csv_field "${byte_exact_archive_bytes_same}"; printf ','
  csv_field "${byte_exact_original_sha256}"; printf ','
  csv_field "${byte_exact_rebuilt_sha256}"; printf ','
  csv_field "${byte_exact_artifact_sha256}"; printf ','
  csv_field "${byte_exact_parity_classes_scanned}"; printf ','
  csv_field "${byte_exact_parity_methods_scanned}"; printf ','
  csv_field "${byte_exact_parity_parse_failures}"; printf ','
  csv_field "${byte_exact_parity_missing_source_methods}"; printf ','
  csv_field "${byte_exact_parity_reflection_methods}"; printf ','
  csv_field "${byte_exact_parity_invokedynamic_methods}"; printf ','
  csv_field "${byte_exact_parity_missing_lvt_methods}"; printf ','
  csv_field "${byte_exact_parity_high_methods}"; printf ','
  csv_field "${byte_exact_parity_medium_methods}"; printf ','
  csv_field "${byte_exact_parity_low_methods}"; printf ','
  csv_field "${byte_exact_project_dir}"; printf '\n'
} > "${CSV_REPORT}"

{
  printf '# OTC Admin Sample Regression\n\n'
  printf '%s\n' "- Sample: \`${OTC_ADMIN_JAR}\`"
  printf '%s\n' "- Original SHA-256: \`${ORIGINAL_SHA256}\`"
  printf '%s\n' "- Reference project: \`${OTC_ADMIN_REFERENCE_PROJECT}\`"
  printf '%s\n' "- Reference Java files: \`${REFERENCE_JAVA_FILES}\`"
  printf '%s\n\n' "- Generated Java files: \`${GENERATED_JAVA_FILES}\`"
  printf '%s\n' "- Reference-only Java files: \`${REFERENCE_ONLY_JAVA_FILES}\`"
  printf '%s\n' "- Generated-only Java files: \`${GENERATED_ONLY_JAVA_FILES}\`"
  printf '%s\n' "- Reference-only Java files with original JAR class: \`${REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT}\`"
  printf '%s\n' "- Reference-only Java files absent from original JAR classes: \`${REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT}\`"
  printf '%s\n' "- Generated-only Java files with original JAR class: \`${GENERATED_ONLY_ORIGINAL_CLASS_PRESENT}\`"
  printf '%s\n\n' "- Generated-only Java files absent from original JAR classes: \`${GENERATED_ONLY_ORIGINAL_CLASS_ABSENT}\`"
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
  printf '## Artifact fidelity details\n\n'
  printf '| Mode | Content entries match | Same class bytes | Different class bytes | Same nested libs | Different nested libs | Entry order same | ZIP metadata diff entries | Archive bytes same |\n'
  printf '| --- | --- | ---: | ---: | ---: | ---: | --- | ---: | --- |\n'
  printf '| package-record | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "${package_record_content_entries_match}" "${package_record_same_class_bytes}" \
    "${package_record_different_class_bytes}" "${package_record_same_nested_libs}" \
    "${package_record_different_nested_libs}" "${package_record_archive_entry_order_same}" \
    "${package_record_archive_metadata_diff_entries}" "${package_record_archive_bytes_same}"
  printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s | %s |\n\n' \
    "${byte_exact_content_entries_match}" "${byte_exact_same_class_bytes}" \
    "${byte_exact_different_class_bytes}" "${byte_exact_same_nested_libs}" \
    "${byte_exact_different_nested_libs}" "${byte_exact_archive_entry_order_same}" \
    "${byte_exact_archive_metadata_diff_entries}" "${byte_exact_archive_bytes_same}"
  printf '## Decompile parity risk\n\n'
  printf '| Mode | Classes | Methods | Parse failures | Missing source methods | Reflection methods | Invokedynamic methods | Missing LVT methods | HIGH | MEDIUM | LOW |\n'
  printf '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n'
  printf '| package-record | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "${package_record_parity_classes_scanned}" "${package_record_parity_methods_scanned}" \
    "${package_record_parity_parse_failures}" "${package_record_parity_missing_source_methods}" \
    "${package_record_parity_reflection_methods}" "${package_record_parity_invokedynamic_methods}" \
    "${package_record_parity_missing_lvt_methods}" "${package_record_parity_high_methods}" \
    "${package_record_parity_medium_methods}" "${package_record_parity_low_methods}"
  printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n\n' \
    "${byte_exact_parity_classes_scanned}" "${byte_exact_parity_methods_scanned}" \
    "${byte_exact_parity_parse_failures}" "${byte_exact_parity_missing_source_methods}" \
    "${byte_exact_parity_reflection_methods}" "${byte_exact_parity_invokedynamic_methods}" \
    "${byte_exact_parity_missing_lvt_methods}" "${byte_exact_parity_high_methods}" \
    "${byte_exact_parity_medium_methods}" "${byte_exact_parity_low_methods}"
  printf '## Artifacts\n\n'
  printf '%s\n' "- package-record project: \`${package_record_project_dir}\`"
  printf '%s\n' "- package-record artifact: \`${package_record_artifact_path}\`"
  printf '%s\n' "- byte-exact project: \`${byte_exact_project_dir}\`"
  printf '%s\n' "- byte-exact artifact: \`${byte_exact_artifact_path}\`"
  printf '%s\n' "- Source diff: \`${SOURCE_DIFF_REPORT}\`"
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
check_gate "package-record content entries match" "${package_record_content_entries_match}" "true"
check_gate "package-record class byte diffs" "${package_record_different_class_bytes}" "0"
check_gate "package-record nested lib diffs" "${package_record_different_nested_libs}" "0"
check_gate "package-record entry order" "${package_record_archive_entry_order_same}" "true"
check_gate "package-record metadata diffs" "${package_record_archive_metadata_diff_entries}" "0"
check_gate "package-record archive bytes" "${package_record_archive_bytes_same}" "true"
check_gate "package-record original SHA" "${package_record_original_sha256}" "${ORIGINAL_SHA256}"
check_gate "package-record rebuilt SHA" "${package_record_rebuilt_sha256}" "${ORIGINAL_SHA256}"
check_gate "package-record artifact SHA" "${package_record_artifact_sha256}" "${ORIGINAL_SHA256}"
check_gate "package-record parity parse failures" "${package_record_parity_parse_failures}" "0"
check_gate "package-record parity missing-source methods" "${package_record_parity_missing_source_methods}" "0"

check_gate "byte-exact jar2mp exit" "${byte_exact_exit_code}" "0"
check_gate "byte-exact verification summary" "${byte_exact_verification_summary}" "BUILD SUCCESS"
check_gate "byte-exact failure type" "${byte_exact_verification_failure_type}" "NONE"
check_gate "byte-exact exact match" "${byte_exact_exact}" "true"
check_gate "byte-exact content entries match" "${byte_exact_content_entries_match}" "true"
check_gate "byte-exact class byte diffs" "${byte_exact_different_class_bytes}" "0"
check_gate "byte-exact nested lib diffs" "${byte_exact_different_nested_libs}" "0"
check_gate "byte-exact entry order" "${byte_exact_archive_entry_order_same}" "true"
check_gate "byte-exact metadata diffs" "${byte_exact_archive_metadata_diff_entries}" "0"
check_gate "byte-exact archive bytes" "${byte_exact_archive_bytes_same}" "true"
check_gate "byte-exact original SHA" "${byte_exact_original_sha256}" "${ORIGINAL_SHA256}"
check_gate "byte-exact rebuilt SHA" "${byte_exact_rebuilt_sha256}" "${ORIGINAL_SHA256}"
check_gate "byte-exact artifact SHA" "${byte_exact_artifact_sha256}" "${ORIGINAL_SHA256}"
check_gate "byte-exact parity parse failures" "${byte_exact_parity_parse_failures}" "0"
check_gate "byte-exact parity missing-source methods" "${byte_exact_parity_missing_source_methods}" "0"

log "Summary written to ${MD_REPORT}"
log "CSV written to ${CSV_REPORT}"
exit "${gate_status}"
