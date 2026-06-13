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
OTC_ADMIN_TRACE_RUNTIME="${OTC_ADMIN_TRACE_RUNTIME:-0}"
OTC_ADMIN_TRACE_ARGS="${OTC_ADMIN_TRACE_ARGS:-}"
OTC_ADMIN_TRACE_TIMEOUT="${OTC_ADMIN_TRACE_TIMEOUT:-30}"

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
  OTC_ADMIN_TRACE_RUNTIME
      Set to 1 to enable runtime tracing for both restoration modes. Default: 0
  OTC_ADMIN_TRACE_ARGS
      Runtime trace arguments passed as one --trace-args string when tracing is enabled.
      Example: --spring.profiles.active=test --server.port=0
  OTC_ADMIN_TRACE_TIMEOUT
      Runtime trace timeout in seconds when tracing is enabled. Default: 30
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
  target/otc-admin-sample/report/otc-admin-source-content-diff.txt
  target/otc-admin-sample/report/otc-admin-source-rebuild-bytecode.md
  target/otc-admin-sample/report/package-record.cli.log
  target/otc-admin-sample/report/byte-exact.cli.log

The source diff report lists reference-only Java files, generated-only Java files,
shared Java content differences, format-only, import-only, decompiler-artifact,
byte-equivalent-text, and substantive content-diff classification, and original JAR class
presence when OTC_ADMIN_REFERENCE_PROJECT is present.
The summary also includes source-recompiled class bytecode fidelity from
target/classes, plus restored package artifact fidelity details from each
artifact-fidelity-summary.csv. The package-level class bytecode and ZIP metadata
numbers are not a source-recompiled class-byte equivalence signal.
The summary also includes the decompile parity risk summary,
HIGH/MEDIUM method index, risk reason breakdown from each decompile-parity-report.md,
the restoration score bucket breakdown from each restoration-score.md, and
remaining restoration gap / non-penalizing observation summaries plus gate status for build,
source coverage, byte package, and runtime observation.
When OTC_ADMIN_TRACE_RUNTIME=1, the summary includes runtime launch support,
run status, failure message and failure cause, and event count from each runtime-trace-report.md. The LocalVariableTable missing-name count is limited
to methods that need user parameter or local-variable names, excluding compiler-generated synthetic switch-map support
classes, bridge methods, enum support methods, lambda deserialization support methods, outer-this constructors,
and monitor temporaries. The source coverage gates
require zero class parse failures and zero missing-source methods in both modes.
Reflection risk is matched by java/lang/Class, java/lang/reflect, and known reflection utility owners rather than ordinary business method names.
Invokedynamic details include bootstrap methods and arguments for lambda implementation targets or string-concat recipes.
Risk method reasons distinguish lambda metafactory invokedynamic from generic invokedynamic.
Pure StringConcatFactory string-concat invokedynamic calls are still counted as invokedynamic facts, but are not MEDIUM risks.
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

parse_overall_score() {
  local report="$1"
  local value=""
  if [[ -f "${report}" ]]; then
    value="$(awk -F'[:/]' '/^- Overall:/ { gsub(/ /, "", $2); print $2; exit }' "${report}" 2>/dev/null || true)"
  fi
  printf '%s\n' "${value:-missing}"
}

parse_bucket_score() {
  local report="$1"
  local bucket="$2"
  local value=""
  if [[ -f "${report}" ]]; then
    value="$(
      awk -v bucket="${bucket}" -F'|' '
        $2 ~ "^[[:space:]]*" bucket "[[:space:]]*$" {
          value = $3
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
          print value
          exit
        }
      ' "${report}" 2>/dev/null || true
    )"
  fi
  printf '%s\n' "${value:-missing}"
}

parse_runtime_field() {
  local report="$1"
  local label="$2"
  local default_value="$3"
  if [[ ! -f "${report}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  local prefix="- ${label}: "
  local value
  value="$(
    awk -v prefix="${prefix}" '
      index($0, prefix) == 1 {
        value = $0
        sub(prefix, "", value)
        print value
        exit
      }
    ' "${report}" 2>/dev/null || true
  )"
  value="${value//\`/}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s\n' "${value:-${default_value}}"
}

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

classify_build_gate() {
  local verification_summary="$1"
  local failure_type="$2"
  if [[ "${verification_summary}" == "BUILD SUCCESS" && "${failure_type}" == "NONE" ]]; then
    printf '%s\n' "PASS"
    return
  fi
  printf '%s\n' "FAIL"
}

classify_source_coverage_gate() {
  local parse_failures="$1"
  local missing_source_methods="$2"
  if [[ "${parse_failures}" == "0" && "${missing_source_methods}" == "0" ]]; then
    printf '%s\n' "PASS"
    return
  fi
  printf '%s\n' "FAIL"
}

classify_byte_package_gate() {
  local exact="$1"
  local content_entries_match="$2"
  local archive_bytes_same="$3"
  local rebuilt_sha256="$4"
  local artifact_sha256="$5"
  if [[ "${exact}" == "true" \
      && "${content_entries_match}" == "true" \
      && "${archive_bytes_same}" == "true" \
      && "${rebuilt_sha256}" == "${ORIGINAL_SHA256}" \
      && "${artifact_sha256}" == "${ORIGINAL_SHA256}" ]]; then
    printf '%s\n' "PASS"
    return
  fi
  printf '%s\n' "FAIL"
}

classify_runtime_observation_gate() {
  local trace_runtime="$1"
  local runtime_report="$2"
  local launch_support="$3"
  local run_status="$4"
  local runtime_events="$5"
  local runtime_score="$6"
  if [[ "${trace_runtime}" != "1" ]]; then
    printf '%s\n' "NOT_RUN"
    return
  fi
  if [[ ! -f "${runtime_report}" ]]; then
    printf '%s\n' "FAIL_MISSING_REPORT"
    return
  fi
  if [[ "${launch_support}" != "SUPPORTED" ]]; then
    printf '%s\n' "SKIPPED_UNSUPPORTED"
    return
  fi
  if [[ "${run_status}" == "EXIT_ZERO" && "${runtime_score}" == "100" ]]; then
    printf '%s\n' "PASS_EXIT_ZERO"
    return
  fi
  if [[ "${run_status}" == "TRACE_COLLECTED_HEALTHY_TIMEOUT" && "${runtime_score}" == "100" ]] && is_positive_integer "${runtime_events}"; then
    printf '%s\n' "PASS_HEALTHY_TIMEOUT"
    return
  fi
  if [[ "${run_status}" == "TRACE_COLLECTED_TIMEOUT" ]] && is_positive_integer "${runtime_events}"; then
    printf '%s\n' "WARN_STARTED_TIMEOUT"
    return
  fi
  if [[ "${run_status}" == "STARTUP_FAILED_TIMEOUT" ]]; then
    printf '%s\n' "FAIL_STARTUP_FAILED_TIMEOUT"
    return
  fi
  if [[ "${run_status}" == "STARTUP_FAILED_EXIT" ]]; then
    printf '%s\n' "FAIL_STARTUP_FAILED_EXIT"
    return
  fi
  if [[ "${run_status}" == "EXIT_ZERO" ]]; then
    printf '%s\n' "WARN_EXIT_ZERO_SCORE_${runtime_score}"
    return
  fi
  printf '%s\n' "FAIL_${run_status:-missing}"
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

parity_risk_reason_counts() {
  local file="$1"
  local default_value="$2"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  local rows
  rows="$(
    awk -F '|' '
      /^## Risk method index$/ {
        in_index = 1
        seen_index = 1
        next
      }
      in_index && /^## / {
        in_index = 0
      }
      in_index && $0 ~ /^\|/ {
        reason = $5
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", reason)
        if (reason != "" && reason != "Reason" && reason != "---") {
          count[reason]++
        }
      }
      END {
        if (!seen_index) {
          exit 1
        }
        for (reason in count) {
          printf "%d\t%s\n", count[reason], reason
        }
      }
    ' "${file}" 2>/dev/null | sort -nr
  )" || {
    printf '%s\n' "${default_value}"
    return
  }
  if [[ -z "${rows}" ]]; then
    printf '%s\n' "none"
    return
  fi
  printf '%s\n' "${rows}" | awk -F '\t' '{
    printf "%s%s=%s", separator, $2, $1
    separator = "; "
  }
  END {
    printf "\n"
  }'
}

gap_category_summary() {
  local file="$1"
  local default_value="$2"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  local rows
  rows="$(
    awk -F '|' '
      {
        header = $2
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", header)
      }
      header == "Category" {
        in_gap_table = 1
        next
      }
      in_gap_table && header == "---" {
        next
      }
      in_gap_table && substr($0, 1, 1) == "|" {
        category = $2
        impact = $3
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", category)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", impact)
        if (category != "" && category != "Category" && category != "---" && category != "(none)" && impact + 0 > 0) {
          printf "%s\t%s\n", category, impact
        }
        next
      }
      in_gap_table && $0 !~ /^$/ {
        in_gap_table = 0
      }
    ' "${file}" 2>/dev/null
  )"
  if [[ -z "${rows}" ]]; then
    printf '%s\n' "none"
    return
  fi
  printf '%s\n' "${rows}" | awk -F '\t' '{
    printf "%s%s=%s", separator, $1, $2
    separator = "; "
  }
  END {
    printf "\n"
  }'
}

observation_category_summary() {
  local file="$1"
  local default_value="$2"
  if [[ ! -f "${file}" ]]; then
    printf '%s\n' "${default_value}"
    return
  fi
  local rows
  rows="$(
    awk -F '|' '
      {
        header = $2
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", header)
      }
      header == "Observation" {
        in_observation_table = 1
        next
      }
      in_observation_table && header == "---" {
        next
      }
      in_observation_table && substr($0, 1, 1) == "|" {
        category = $2
        impact = $3
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", category)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", impact)
        if (category != "" && category != "Observation" && category != "---" && category != "(none)") {
          printf "%s\t%s\n", category, impact
        }
        next
      }
      in_observation_table && $0 !~ /^$/ {
        in_observation_table = 0
      }
    ' "${file}" 2>/dev/null
  )"
  if [[ -z "${rows}" ]]; then
    printf '%s\n' "none"
    return
  fi
  printf '%s\n' "${rows}" | awk -F '\t' '{
    printf "%s%s=%s", separator, $1, $2
    separator = "; "
  }
  END {
    printf "\n"
  }'
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

write_original_app_class_hashes() {
  local original_jar="$1"
  local entries_file="$2"
  local output_file="$3"
  : > "${output_file}"
  if ! command -v unzip >/dev/null 2>&1 || [[ ! -f "${entries_file}" ]]; then
    return 1
  fi

  local class_prefix=""
  if grep -Eq '^BOOT-INF/classes/.*\.class$' "${entries_file}"; then
    class_prefix="BOOT-INF/classes/"
  elif grep -Eq '^WEB-INF/classes/.*\.class$' "${entries_file}"; then
    class_prefix="WEB-INF/classes/"
  fi

  local entry rel sha
  while IFS= read -r entry; do
    [[ "${entry}" == *.class ]] || continue
    if [[ -n "${class_prefix}" ]]; then
      [[ "${entry}" == "${class_prefix}"* ]] || continue
      rel="${entry#${class_prefix}}"
    else
      rel="${entry}"
    fi
    sha="$(unzip -p "${original_jar}" "${entry}" | shasum -a 256 | awk '{ print $1 }')"
    printf '%s\t%s\n' "${rel}" "${sha}" >> "${output_file}"
  done < "${entries_file}"
  sort -o "${output_file}" "${output_file}"
}

write_rebuilt_class_hashes() {
  local project_dir="$1"
  local output_file="$2"
  : > "${output_file}"
  local classes_dir="${project_dir}/target/classes"
  if [[ ! -d "${classes_dir}" ]]; then
    return 1
  fi
  find "${classes_dir}" -type f -name '*.class' | sort | while IFS= read -r class_file; do
    local rel sha
    rel="${class_file#${classes_dir}/}"
    sha="$(shasum -a 256 "${class_file}" | awk '{ print $1 }')"
    printf '%s\t%s\n' "${rel}" "${sha}"
  done > "${output_file}"
}

collect_source_rebuild_class_byte_metrics() {
  local prefix="$1"
  local project_dir="$2"
  local work_prefix="${REPORT_DIR}/.${prefix}-source-rebuild"
  local original_hashes="${work_prefix}.original.tsv"
  local rebuilt_hashes="${work_prefix}.rebuilt.tsv"
  local different_samples="${work_prefix}.different.tsv"
  local missing_samples="${work_prefix}.missing.tsv"
  local extra_samples="${work_prefix}.extra.tsv"

  : > "${different_samples}"
  : > "${missing_samples}"
  : > "${extra_samples}"
  write_original_app_class_hashes "${OTC_ADMIN_JAR}" "${ORIGINAL_JAR_ENTRIES}" "${original_hashes}" || true
  write_rebuilt_class_hashes "${project_dir}" "${rebuilt_hashes}" || true

  local summary
  summary="$(
    awk -F '\t' \
      -v different_samples="${different_samples}" \
      -v missing_samples="${missing_samples}" \
      -v extra_samples="${extra_samples}" '
      NR == FNR {
        original[$1] = $2
        original_total++
        next
      }
      {
        rebuilt[$1] = $2
        rebuilt_total++
      }
      END {
        for (class_path in original) {
          if (class_path in rebuilt) {
            common++
            if (original[class_path] == rebuilt[class_path]) {
              same++
            } else {
              different++
              if (different <= 20) {
                printf "%s\t%s\t%s\n", class_path, original[class_path], rebuilt[class_path] >> different_samples
              }
            }
          } else {
            missing++
            if (missing <= 20) {
              print class_path >> missing_samples
            }
          }
        }
        for (class_path in rebuilt) {
          if (!(class_path in original)) {
            extra++
            if (extra <= 20) {
              print class_path >> extra_samples
            }
          }
        }
        printf "%d,%d,%d,%d,%d,%d,%d\n", original_total, rebuilt_total, common, same, different, missing, extra
      }
    ' "${original_hashes}" "${rebuilt_hashes}"
  )"

  local original_total rebuilt_total common same different missing extra
  IFS=',' read -r original_total rebuilt_total common same different missing extra <<< "${summary}"
  set_var "${prefix}_source_rebuild_original_classes" "${original_total}"
  set_var "${prefix}_source_rebuild_classes" "${rebuilt_total}"
  set_var "${prefix}_source_rebuild_common_classes" "${common}"
  set_var "${prefix}_source_rebuild_same_class_bytes" "${same}"
  set_var "${prefix}_source_rebuild_different_class_bytes" "${different}"
  set_var "${prefix}_source_rebuild_missing_classes" "${missing}"
  set_var "${prefix}_source_rebuild_extra_classes" "${extra}"
  set_var "${prefix}_source_rebuild_original_hashes" "${original_hashes}"
  set_var "${prefix}_source_rebuild_rebuilt_hashes" "${rebuilt_hashes}"
  set_var "${prefix}_source_rebuild_different_samples" "${different_samples}"
  set_var "${prefix}_source_rebuild_missing_samples" "${missing_samples}"
  set_var "${prefix}_source_rebuild_extra_samples" "${extra_samples}"
}

append_source_rebuild_samples() {
  local title="$1"
  local file="$2"
  local mode="$3"
  printf '### %s (%s)\n\n' "${title}" "${mode}"
  if [[ ! -s "${file}" ]]; then
    printf -- '- None\n\n'
    return
  fi
  head -20 "${file}" | while IFS= read -r line; do
    printf -- '- `%s`\n' "${line}"
  done
  printf '\n'
}

write_source_rebuild_class_byte_report() {
  local report="$1"
  {
    printf '# OTC Admin Source Rebuild Class Bytecode Fidelity\n\n'
    printf 'This report compares application `.class` files compiled into `target/classes` from generated sources with application class entries from the original JAR. It is separate from restored package artifact fidelity.\n\n'
    printf '| Mode | Original app classes | Recompiled classes | Common | Same class bytes | Different class bytes | Missing recompiled classes | Extra recompiled classes |\n'
    printf '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n'
    printf '| package-record | %s | %s | %s | %s | %s | %s | %s |\n' \
      "${package_record_source_rebuild_original_classes}" \
      "${package_record_source_rebuild_classes}" \
      "${package_record_source_rebuild_common_classes}" \
      "${package_record_source_rebuild_same_class_bytes}" \
      "${package_record_source_rebuild_different_class_bytes}" \
      "${package_record_source_rebuild_missing_classes}" \
      "${package_record_source_rebuild_extra_classes}"
    printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s |\n\n' \
      "${byte_exact_source_rebuild_original_classes}" \
      "${byte_exact_source_rebuild_classes}" \
      "${byte_exact_source_rebuild_common_classes}" \
      "${byte_exact_source_rebuild_same_class_bytes}" \
      "${byte_exact_source_rebuild_different_class_bytes}" \
      "${byte_exact_source_rebuild_missing_classes}" \
      "${byte_exact_source_rebuild_extra_classes}"
    append_source_rebuild_samples "Different class byte examples" "${package_record_source_rebuild_different_samples}" "package-record"
    append_source_rebuild_samples "Missing recompiled class examples" "${package_record_source_rebuild_missing_samples}" "package-record"
    append_source_rebuild_samples "Extra recompiled class examples" "${package_record_source_rebuild_extra_samples}" "package-record"
    append_source_rebuild_samples "Different class byte examples" "${byte_exact_source_rebuild_different_samples}" "byte-exact"
    append_source_rebuild_samples "Missing recompiled class examples" "${byte_exact_source_rebuild_missing_samples}" "byte-exact"
    append_source_rebuild_samples "Extra recompiled class examples" "${byte_exact_source_rebuild_extra_samples}" "byte-exact"
  } > "${report}"
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

normalize_java_file_for_diff() {
  local strip_imports="$1"
  local source_file="$2"
  awk -v strip_imports="${strip_imports}" '
    {
      sub(/\r$/, "")
      if (strip_imports == "1" && $0 ~ /^[[:space:]]*import[[:space:]]/) {
        next
      }
      line = $0
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      gsub(/[[:space:]]+/, " ", line)
      if (line == "") {
        next
      }
      print line
    }
  ' "${source_file}"
}

java_files_equal_after_normalization() {
  local strip_imports="$1"
  local reference_file="$2"
  local generated_file="$3"
  cmp -s \
    <(normalize_java_file_for_diff "${strip_imports}" "${reference_file}") \
    <(normalize_java_file_for_diff "${strip_imports}" "${generated_file}")
}

normalize_java_file_for_decompiler_artifacts() {
  local source_file="$1"
  awk '
    {
      sub(/\r$/, "")
      line = $0
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      if (line == "" || line ~ /^import[[:space:]]/) {
        next
      }
      if (line == "/*" || line == "*/" || line ~ /^\*[[:space:]]+Exception performing whole class analysis ignored\.$/) {
        next
      }
      while (gsub(/<[^<>;{}()=]+>/, "", line)) {
      }
      gsub(/<>/, "", line)
      while (gsub(/\([[:space:]]*((byte|short|int|long|float|double|char|boolean)|([A-Z][A-Za-z0-9_$]*|([a-z_][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*)(\.[A-Za-z_$][A-Za-z0-9_$]*)*(\[\])?)[[:space:]]*\)/, "", line)) {
      }
      while (match(line, /= \([A-Za-z_$][A-Za-z0-9_$]*\)/)) {
        token = substr(line, RSTART + 3, RLENGTH - 4)
        line = substr(line, 1, RSTART + 1) " " token substr(line, RSTART + RLENGTH)
      }
      gsub(/[[:space:]]+/, " ", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      if (line != "") {
        print line
      }
    }
  ' "${source_file}"
}

java_files_equal_after_decompiler_artifact_normalization() {
  local reference_file="$1"
  local generated_file="$2"
  cmp -s \
    <(normalize_java_file_for_decompiler_artifacts "${reference_file}") \
    <(normalize_java_file_for_decompiler_artifacts "${generated_file}")
}

java_file_byte_equivalent_to_original() {
  local java_file="$1"
  local original_hashes="$2"
  local rebuilt_hashes="$3"
  if [[ ! -f "${original_hashes}" || ! -f "${rebuilt_hashes}" ]]; then
    return 1
  fi
  local class_base="${java_file%.java}"
  awk -F '\t' -v class_base="${class_base}" '
    function matches_class(path) {
      return path == class_base ".class" || index(path, class_base "$") == 1
    }
    NR == FNR {
      if (matches_class($1)) {
        original[$1] = $2
        original_count++
      }
      next
    }
    {
      if (matches_class($1)) {
        rebuilt[$1] = $2
        rebuilt_count++
      }
    }
    END {
      if (original_count == 0) {
        exit 1
      }
      for (path in original) {
        if (!(path in rebuilt) || rebuilt[path] != original[path]) {
          exit 1
        }
      }
      for (path in rebuilt) {
        if (!(path in original)) {
          exit 1
        }
      }
      exit 0
    }
  ' "${original_hashes}" "${rebuilt_hashes}"
}

classify_java_content_diff() {
  local reference_file="$1"
  local generated_file="$2"
  local java_file="$3"
  local original_hashes="$4"
  local rebuilt_hashes="$5"
  if java_files_equal_after_normalization "0" "${reference_file}" "${generated_file}"; then
    printf 'format_only\n'
  elif java_files_equal_after_normalization "1" "${reference_file}" "${generated_file}"; then
    printf 'import_only\n'
  elif java_files_equal_after_decompiler_artifact_normalization "${reference_file}" "${generated_file}"; then
    printf 'decompiler_artifact\n'
  elif java_file_byte_equivalent_to_original "${java_file}" "${original_hashes}" "${rebuilt_hashes}"; then
    printf 'byte_equivalent_text\n'
  else
    printf 'substantive\n'
  fi
}

write_content_diff_table() {
  local count="$1"
  local classification_file="$2"
  printf '## Shared Java files with content differences\n\n'
  if [[ "${count}" == "0" ]]; then
    printf 'None\n\n'
    return
  fi
  printf '| Java file | Classification |\n'
  printf '| --- | --- |\n'
  awk -F '\t' '{ printf "| `%s` | `%s` |\n", $1, $2 }' "${classification_file}"
  printf '\n'
}

write_source_diff_report() {
  local generated_dir="$1"
  local reference_dir="$2"
  local report="$3"
  local entries_file="$4"
  local content_diff_report="$5"
  local reference_list="${REPORT_DIR}/.reference-java-files"
  local generated_list="${REPORT_DIR}/.generated-java-files"
  local shared_list="${REPORT_DIR}/.shared-java-files"
  local reference_only="${REPORT_DIR}/.reference-only-java-files"
  local generated_only="${REPORT_DIR}/.generated-only-java-files"
  local content_diff_list="${REPORT_DIR}/.shared-java-content-diff-files"
  local content_diff_classification="${REPORT_DIR}/.shared-java-content-diff-classification"
  local content_diff_body="${REPORT_DIR}/.shared-java-content-diff-body"
  local format_only_diff_list="${REPORT_DIR}/.shared-java-format-only-diff-files"
  local import_only_diff_list="${REPORT_DIR}/.shared-java-import-only-diff-files"
  local decompiler_artifact_diff_list="${REPORT_DIR}/.shared-java-decompiler-artifact-diff-files"
  local byte_equivalent_text_diff_list="${REPORT_DIR}/.shared-java-byte-equivalent-text-diff-files"
  local substantive_diff_list="${REPORT_DIR}/.shared-java-substantive-diff-files"
  local reference_only_presence="${REPORT_DIR}/.reference-only-class-presence"
  local generated_only_presence="${REPORT_DIR}/.generated-only-class-presence"

  if [[ ! -d "${generated_dir}" || ! -d "${reference_dir}" ]]; then
    set_var "SHARED_JAVA_FILES" "missing"
    set_var "SHARED_JAVA_CONTENT_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_FORMAT_ONLY_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_IMPORT_ONLY_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES" "missing"
    set_var "SHARED_JAVA_SUBSTANTIVE_DIFF_FILES" "missing"
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
    {
      printf '# OTC Admin Shared Source Content Diff\n\n'
      printf 'Source content diff unavailable.\n\n'
      printf '%s\n' "- Generated source dir: \`${generated_dir}\`"
      printf '%s\n' "- Reference source dir: \`${reference_dir}\`"
    } > "${content_diff_report}"
    return
  fi

  (cd "${reference_dir}" && find . -type f -name '*.java' | sed 's#^\./##' | sort) > "${reference_list}"
  (cd "${generated_dir}" && find . -type f -name '*.java' | sed 's#^\./##' | sort) > "${generated_list}"
  comm -12 "${reference_list}" "${generated_list}" > "${shared_list}"
  comm -23 "${reference_list}" "${generated_list}" > "${reference_only}"
  comm -13 "${reference_list}" "${generated_list}" > "${generated_only}"

  : > "${content_diff_list}"
  : > "${content_diff_classification}"
  : > "${content_diff_body}"
  : > "${format_only_diff_list}"
  : > "${import_only_diff_list}"
  : > "${decompiler_artifact_diff_list}"
  : > "${byte_equivalent_text_diff_list}"
  : > "${substantive_diff_list}"
  while IFS= read -r java_file; do
    [[ -z "${java_file}" ]] && continue
    if cmp -s "${reference_dir}/${java_file}" "${generated_dir}/${java_file}"; then
      continue
    fi
    local classification
    classification="$(classify_java_content_diff "${reference_dir}/${java_file}" "${generated_dir}/${java_file}" \
      "${java_file}" "${package_record_source_rebuild_original_hashes:-}" "${package_record_source_rebuild_rebuilt_hashes:-}")"
    printf '%s\n' "${java_file}" >> "${content_diff_list}"
    printf '%s\t%s\n' "${java_file}" "${classification}" >> "${content_diff_classification}"
    case "${classification}" in
      format_only)
        printf '%s\n' "${java_file}" >> "${format_only_diff_list}"
        ;;
      import_only)
        printf '%s\n' "${java_file}" >> "${import_only_diff_list}"
        ;;
      decompiler_artifact)
        printf '%s\n' "${java_file}" >> "${decompiler_artifact_diff_list}"
        ;;
      byte_equivalent_text)
        printf '%s\n' "${java_file}" >> "${byte_equivalent_text_diff_list}"
        ;;
      *)
        printf '%s\n' "${java_file}" >> "${substantive_diff_list}"
        ;;
    esac
    {
      printf '\n## %s\n\n' "${java_file}"
      printf '%s\n\n' "- Classification: \`${classification}\`"
      diff -u "${reference_dir}/${java_file}" "${generated_dir}/${java_file}" || true
    } >> "${content_diff_body}"
  done < "${shared_list}"

  set_var "SHARED_JAVA_FILES" "$(count_file_lines "${shared_list}")"
  set_var "SHARED_JAVA_CONTENT_DIFF_FILES" "$(count_file_lines "${content_diff_list}")"
  set_var "SHARED_JAVA_FORMAT_ONLY_DIFF_FILES" "$(count_file_lines "${format_only_diff_list}")"
  set_var "SHARED_JAVA_IMPORT_ONLY_DIFF_FILES" "$(count_file_lines "${import_only_diff_list}")"
  set_var "SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES" "$(count_file_lines "${decompiler_artifact_diff_list}")"
  set_var "SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES" "$(count_file_lines "${byte_equivalent_text_diff_list}")"
  set_var "SHARED_JAVA_SUBSTANTIVE_DIFF_FILES" "$(count_file_lines "${substantive_diff_list}")"
  local resolved_content_diff_files=$((SHARED_JAVA_FORMAT_ONLY_DIFF_FILES
    + SHARED_JAVA_IMPORT_ONLY_DIFF_FILES
    + SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES
    + SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES))
  set_var "SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES" "${resolved_content_diff_files}"
  set_var "REFERENCE_ONLY_JAVA_FILES" "$(count_file_lines "${reference_only}")"
  set_var "GENERATED_ONLY_JAVA_FILES" "$(count_file_lines "${generated_only}")"
  {
    printf '# OTC Admin Shared Source Content Diff\n\n'
    printf '%s\n' "- Generated source dir: \`${generated_dir}\`"
    printf '%s\n' "- Reference source dir: \`${reference_dir}\`"
    printf '%s\n' "- Shared Java files: \`${SHARED_JAVA_FILES}\`"
    printf '%s\n' "- Shared Java files with content differences: \`${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
    printf '%s\n' "- Resolved shared Java content differences: \`${SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES}/${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
    printf '%s\n' "- Format-only shared Java content differences: \`${SHARED_JAVA_FORMAT_ONLY_DIFF_FILES}\`"
    printf '%s\n' "- Import-only shared Java content differences: \`${SHARED_JAVA_IMPORT_ONLY_DIFF_FILES}\`"
    printf '%s\n' "- Decompiler-artifact shared Java content differences: \`${SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES}\`"
    printf '%s\n' "- Byte-equivalent textual shared Java content differences: \`${SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES}\`"
    printf '%s\n\n' "- Substantive shared Java content differences: \`${SHARED_JAVA_SUBSTANTIVE_DIFF_FILES}\`"
    if [[ ! -s "${content_diff_list}" ]]; then
      printf 'No shared Java content differences.\n'
    else
      cat "${content_diff_body}"
    fi
  } > "${content_diff_report}"
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
    printf '%s\n' "- Shared Java files: \`${SHARED_JAVA_FILES}\`"
    printf '%s\n' "- Shared Java files with content differences: \`${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
    printf '%s\n' "- Resolved shared Java content differences: \`${SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES}/${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
    printf '%s\n' "- Format-only shared Java content differences: \`${SHARED_JAVA_FORMAT_ONLY_DIFF_FILES}\`"
    printf '%s\n' "- Import-only shared Java content differences: \`${SHARED_JAVA_IMPORT_ONLY_DIFF_FILES}\`"
    printf '%s\n' "- Decompiler-artifact shared Java content differences: \`${SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES}\`"
    printf '%s\n' "- Byte-equivalent textual shared Java content differences: \`${SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES}\`"
    printf '%s\n' "- Substantive shared Java content differences: \`${SHARED_JAVA_SUBSTANTIVE_DIFF_FILES}\`"
    printf '%s\n' "- Source content diff report: \`${content_diff_report}\`"
    printf '%s\n' "- Reference-only Java files: \`${REFERENCE_ONLY_JAVA_FILES}\`"
    printf '%s\n' "- Generated-only Java files: \`${GENERATED_ONLY_JAVA_FILES}\`"
    printf '%s\n' "- Reference-only Java files with original JAR class: \`${REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT}\`"
    printf '%s\n' "- Reference-only Java files absent from original JAR classes: \`${REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT}\`"
    printf '%s\n' "- Generated-only Java files with original JAR class: \`${GENERATED_ONLY_ORIGINAL_CLASS_PRESENT}\`"
    printf '%s\n\n' "- Generated-only Java files absent from original JAR classes: \`${GENERATED_ONLY_ORIGINAL_CLASS_ABSENT}\`"

    write_content_diff_table "${SHARED_JAVA_CONTENT_DIFF_FILES}" "${content_diff_classification}"
    write_presence_table "Reference-only Java files" "${REFERENCE_ONLY_JAVA_FILES}" "${reference_only_presence}"
    write_presence_table "Generated-only Java files" "${GENERATED_ONLY_JAVA_FILES}" "${generated_only_presence}"
  } > "${report}"

  rm -f "${reference_list}" "${generated_list}" "${shared_list}" "${reference_only}" "${generated_only}" \
    "${content_diff_list}" "${content_diff_classification}" "${content_diff_body}" \
    "${format_only_diff_list}" "${import_only_diff_list}" "${decompiler_artifact_diff_list}" \
    "${byte_equivalent_text_diff_list}" \
    "${substantive_diff_list}" \
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
  local cli_args=(--verbose "$@" --verify-build -f -o "${output_base}")
  if [[ "${OTC_ADMIN_TRACE_RUNTIME}" == "1" ]]; then
    cli_args+=(--trace-runtime --trace-timeout "${OTC_ADMIN_TRACE_TIMEOUT}")
    if [[ -n "${OTC_ADMIN_TRACE_ARGS}" ]]; then
      cli_args+=(--trace-args "${OTC_ADMIN_TRACE_ARGS}")
    fi
  fi
  set +e
  "${JAVA_BIN}" -jar "${JAR2MP_JAR}" "${cli_args[@]}" "${OTC_ADMIN_JAR}" \
    >"${log_file}" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find_project_dir "${output_base}" || true)"
  local verification_report="${project_dir}/verification-report.md"
  local score_report="${project_dir}/restoration-score.md"
  local gap_summary="${project_dir}/gap-summary.md"
  local runtime_report="${project_dir}/runtime-trace-report.md"
  local fidelity_summary="${project_dir}/target/${check_dir}/artifact-fidelity-summary.csv"
  local source_rebuild_summary="${project_dir}/source-rebuild-fidelity-summary.csv"
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
  set_var "${prefix}_overall_score" "$(parse_overall_score "${score_report}")"
  set_var "${prefix}_source_score" "$(parse_bucket_score "${score_report}" "source")"
  set_var "${prefix}_resource_score" "$(parse_bucket_score "${score_report}" "resource")"
  set_var "${prefix}_runtime_score" "$(parse_bucket_score "${score_report}" "runtime")"
  set_var "${prefix}_verification_score" "$(parse_bucket_score "${score_report}" "verification")"
  set_var "${prefix}_runtime_launch_support" "$(parse_runtime_field "${runtime_report}" "Launch support" "not-run")"
  set_var "${prefix}_runtime_run_status" "$(parse_runtime_field "${runtime_report}" "Run status" "not-run")"
  set_var "${prefix}_runtime_failure_message" "$(parse_runtime_field "${runtime_report}" "Failure message" "not-run")"
  set_var "${prefix}_runtime_failure_cause" "$(parse_runtime_field "${runtime_report}" "Failure cause" "not-run")"
  set_var "${prefix}_runtime_events" "$(parse_runtime_field "${runtime_report}" "Total events" "not-run")"
  set_var "${prefix}_gap_count" "$(markdown_field "${gap_summary}" "Gap count" "missing")"
  set_var "${prefix}_gap_categories" "$(gap_category_summary "${gap_summary}" "missing")"
  set_var "${prefix}_observation_count" "$(markdown_field "${gap_summary}" "Observation count" "missing")"
  set_var "${prefix}_observation_categories" "$(observation_category_summary "${gap_summary}" "missing")"
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
  set_var "${prefix}_source_rebuild_report_same" "$(csv_column_by_name "${source_rebuild_summary}" "source_recompiled_class_bytes_same" "missing")"
  set_var "${prefix}_source_rebuild_report_original_classes" "$(csv_column_by_name "${source_rebuild_summary}" "original_app_classes" "missing")"
  set_var "${prefix}_source_rebuild_report_classes" "$(csv_column_by_name "${source_rebuild_summary}" "recompiled_classes" "missing")"
  set_var "${prefix}_source_rebuild_report_common_classes" "$(csv_column_by_name "${source_rebuild_summary}" "common_classes" "missing")"
  set_var "${prefix}_source_rebuild_report_same_class_bytes" "$(csv_column_by_name "${source_rebuild_summary}" "same_class_bytes" "missing")"
  set_var "${prefix}_source_rebuild_report_different_class_bytes" "$(csv_column_by_name "${source_rebuild_summary}" "different_class_bytes" "missing")"
  set_var "${prefix}_source_rebuild_report_missing_classes" "$(csv_column_by_name "${source_rebuild_summary}" "missing_recompiled_classes" "missing")"
  set_var "${prefix}_source_rebuild_report_extra_classes" "$(csv_column_by_name "${source_rebuild_summary}" "extra_recompiled_classes" "missing")"
  set_var "${prefix}_source_rebuild_report_compile_fallback_classes" "$(csv_column_by_name "${source_rebuild_summary}" "compile_fallback_classes" "missing")"
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
  set_var "${prefix}_parity_risk_reasons" "$(parity_risk_reason_counts "${parity_report}" "missing")"

  local verification_summary_var="${prefix}_verification_summary"
  local verification_failure_type_var="${prefix}_verification_failure_type"
  local parse_failures_var="${prefix}_parity_parse_failures"
  local missing_source_methods_var="${prefix}_parity_missing_source_methods"
  local exact_var="${prefix}_exact"
  local content_entries_match_var="${prefix}_content_entries_match"
  local archive_bytes_same_var="${prefix}_archive_bytes_same"
  local rebuilt_sha256_var="${prefix}_rebuilt_sha256"
  local artifact_sha256_var="${prefix}_artifact_sha256"
  local runtime_launch_support_var="${prefix}_runtime_launch_support"
  local runtime_run_status_var="${prefix}_runtime_run_status"
  local runtime_events_var="${prefix}_runtime_events"
  local runtime_score_var="${prefix}_runtime_score"
  set_var "${prefix}_build_gate" "$(classify_build_gate "${!verification_summary_var}" "${!verification_failure_type_var}")"
  set_var "${prefix}_source_coverage_gate" "$(classify_source_coverage_gate "${!parse_failures_var}" "${!missing_source_methods_var}")"
  set_var "${prefix}_byte_package_gate" "$(classify_byte_package_gate "${!exact_var}" "${!content_entries_match_var}" "${!archive_bytes_same_var}" "${!rebuilt_sha256_var}" "${!artifact_sha256_var}")"
  set_var "${prefix}_runtime_observation_gate" "$(classify_runtime_observation_gate "${OTC_ADMIN_TRACE_RUNTIME}" "${runtime_report}" "${!runtime_launch_support_var}" "${!runtime_run_status_var}" "${!runtime_events_var}" "${!runtime_score_var}")"
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
SOURCE_CONTENT_DIFF_REPORT="${REPORT_DIR}/otc-admin-source-content-diff.txt"
SOURCE_REBUILD_BYTECODE_REPORT="${REPORT_DIR}/otc-admin-source-rebuild-bytecode.md"
collect_source_rebuild_class_byte_metrics "package_record" "${package_record_project_dir}"
collect_source_rebuild_class_byte_metrics "byte_exact" "${byte_exact_project_dir}"
write_source_diff_report \
  "${package_record_project_dir}/src/main/java" \
  "${OTC_ADMIN_REFERENCE_PROJECT}/src/main/java" \
  "${SOURCE_DIFF_REPORT}" \
  "${ORIGINAL_JAR_ENTRIES}" \
  "${SOURCE_CONTENT_DIFF_REPORT}"
write_source_rebuild_class_byte_report "${SOURCE_REBUILD_BYTECODE_REPORT}"

{
  printf 'sample,jar,jar_size_bytes,original_sha256,reference_project,reference_java_files,generated_java_files,shared_java_files,shared_java_content_diff_files,shared_java_resolved_content_diff_files,shared_java_format_only_diff_files,shared_java_import_only_diff_files,shared_java_decompiler_artifact_diff_files,shared_java_byte_equivalent_text_diff_files,shared_java_substantive_diff_files,reference_only_java_files,generated_only_java_files,reference_only_original_class_present,reference_only_original_class_absent,generated_only_original_class_present,generated_only_original_class_absent,source_diff_report,source_content_diff_report,package_record_exit_code,package_record_verification_summary,package_record_failure_type,package_record_error_count,package_record_compile_fallback_classes,package_record_decompile_failures,package_record_overall_score,package_record_source_score,package_record_resource_score,package_record_runtime_score,package_record_runtime_launch_support,package_record_runtime_run_status,package_record_runtime_failure_message,package_record_runtime_failure_cause,package_record_runtime_events,package_record_verification_score,package_record_gap_count,package_record_gap_categories,package_record_observation_count,package_record_observation_categories,package_record_build_gate,package_record_source_coverage_gate,package_record_byte_package_gate,package_record_runtime_observation_gate,package_record_exact,package_record_content_entries_match,package_record_same_class_bytes,package_record_different_class_bytes,package_record_same_nested_libs,package_record_different_nested_libs,package_record_archive_entry_order_same,package_record_archive_metadata_diff_entries,package_record_archive_bytes_same,package_record_original_sha256,package_record_rebuilt_sha256,package_record_artifact_sha256,package_record_parity_classes_scanned,package_record_parity_methods_scanned,package_record_parity_parse_failures,package_record_parity_missing_source_methods,package_record_parity_reflection_methods,package_record_parity_invokedynamic_methods,package_record_parity_missing_lvt_methods,package_record_parity_high_methods,package_record_parity_medium_methods,package_record_parity_low_methods,package_record_parity_risk_reasons,package_record_project,byte_exact_exit_code,byte_exact_verification_summary,byte_exact_failure_type,byte_exact_error_count,byte_exact_compile_fallback_classes,byte_exact_decompile_failures,byte_exact_overall_score,byte_exact_source_score,byte_exact_resource_score,byte_exact_runtime_score,byte_exact_runtime_launch_support,byte_exact_runtime_run_status,byte_exact_runtime_failure_message,byte_exact_runtime_failure_cause,byte_exact_runtime_events,byte_exact_verification_score,byte_exact_gap_count,byte_exact_gap_categories,byte_exact_observation_count,byte_exact_observation_categories,byte_exact_build_gate,byte_exact_source_coverage_gate,byte_exact_byte_package_gate,byte_exact_runtime_observation_gate,byte_exact_exact,byte_exact_content_entries_match,byte_exact_same_class_bytes,byte_exact_different_class_bytes,byte_exact_same_nested_libs,byte_exact_different_nested_libs,byte_exact_archive_entry_order_same,byte_exact_archive_metadata_diff_entries,byte_exact_archive_bytes_same,byte_exact_original_sha256,byte_exact_rebuilt_sha256,byte_exact_artifact_sha256,byte_exact_parity_classes_scanned,byte_exact_parity_methods_scanned,byte_exact_parity_parse_failures,byte_exact_parity_missing_source_methods,byte_exact_parity_reflection_methods,byte_exact_parity_invokedynamic_methods,byte_exact_parity_missing_lvt_methods,byte_exact_parity_high_methods,byte_exact_parity_medium_methods,byte_exact_parity_low_methods,byte_exact_parity_risk_reasons,byte_exact_project\n'
  csv_field "otc-admin"; printf ','
  csv_field "${OTC_ADMIN_JAR}"; printf ','
  csv_field "${JAR_SIZE_BYTES}"; printf ','
  csv_field "${ORIGINAL_SHA256}"; printf ','
  csv_field "${OTC_ADMIN_REFERENCE_PROJECT}"; printf ','
  csv_field "${REFERENCE_JAVA_FILES}"; printf ','
  csv_field "${GENERATED_JAVA_FILES}"; printf ','
  csv_field "${SHARED_JAVA_FILES}"; printf ','
  csv_field "${SHARED_JAVA_CONTENT_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_FORMAT_ONLY_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_IMPORT_ONLY_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES}"; printf ','
  csv_field "${SHARED_JAVA_SUBSTANTIVE_DIFF_FILES}"; printf ','
  csv_field "${REFERENCE_ONLY_JAVA_FILES}"; printf ','
  csv_field "${GENERATED_ONLY_JAVA_FILES}"; printf ','
  csv_field "${REFERENCE_ONLY_ORIGINAL_CLASS_PRESENT}"; printf ','
  csv_field "${REFERENCE_ONLY_ORIGINAL_CLASS_ABSENT}"; printf ','
  csv_field "${GENERATED_ONLY_ORIGINAL_CLASS_PRESENT}"; printf ','
  csv_field "${GENERATED_ONLY_ORIGINAL_CLASS_ABSENT}"; printf ','
  csv_field "${SOURCE_DIFF_REPORT}"; printf ','
  csv_field "${SOURCE_CONTENT_DIFF_REPORT}"; printf ','
  csv_field "${package_record_exit_code}"; printf ','
  csv_field "${package_record_verification_summary}"; printf ','
  csv_field "${package_record_verification_failure_type}"; printf ','
  csv_field "${package_record_verification_error_count}"; printf ','
  csv_field "${package_record_compile_fallback_classes}"; printf ','
  csv_field "${package_record_decompile_failures}"; printf ','
  csv_field "${package_record_overall_score}"; printf ','
  csv_field "${package_record_source_score}"; printf ','
  csv_field "${package_record_resource_score}"; printf ','
  csv_field "${package_record_runtime_score}"; printf ','
  csv_field "${package_record_runtime_launch_support}"; printf ','
  csv_field "${package_record_runtime_run_status}"; printf ','
  csv_field "${package_record_runtime_failure_message}"; printf ','
  csv_field "${package_record_runtime_failure_cause}"; printf ','
  csv_field "${package_record_runtime_events}"; printf ','
  csv_field "${package_record_verification_score}"; printf ','
  csv_field "${package_record_gap_count}"; printf ','
  csv_field "${package_record_gap_categories}"; printf ','
  csv_field "${package_record_observation_count}"; printf ','
  csv_field "${package_record_observation_categories}"; printf ','
  csv_field "${package_record_build_gate}"; printf ','
  csv_field "${package_record_source_coverage_gate}"; printf ','
  csv_field "${package_record_byte_package_gate}"; printf ','
  csv_field "${package_record_runtime_observation_gate}"; printf ','
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
  csv_field "${package_record_parity_risk_reasons}"; printf ','
  csv_field "${package_record_project_dir}"; printf ','
  csv_field "${byte_exact_exit_code}"; printf ','
  csv_field "${byte_exact_verification_summary}"; printf ','
  csv_field "${byte_exact_verification_failure_type}"; printf ','
  csv_field "${byte_exact_verification_error_count}"; printf ','
  csv_field "${byte_exact_compile_fallback_classes}"; printf ','
  csv_field "${byte_exact_decompile_failures}"; printf ','
  csv_field "${byte_exact_overall_score}"; printf ','
  csv_field "${byte_exact_source_score}"; printf ','
  csv_field "${byte_exact_resource_score}"; printf ','
  csv_field "${byte_exact_runtime_score}"; printf ','
  csv_field "${byte_exact_runtime_launch_support}"; printf ','
  csv_field "${byte_exact_runtime_run_status}"; printf ','
  csv_field "${byte_exact_runtime_failure_message}"; printf ','
  csv_field "${byte_exact_runtime_failure_cause}"; printf ','
  csv_field "${byte_exact_runtime_events}"; printf ','
  csv_field "${byte_exact_verification_score}"; printf ','
  csv_field "${byte_exact_gap_count}"; printf ','
  csv_field "${byte_exact_gap_categories}"; printf ','
  csv_field "${byte_exact_observation_count}"; printf ','
  csv_field "${byte_exact_observation_categories}"; printf ','
  csv_field "${byte_exact_build_gate}"; printf ','
  csv_field "${byte_exact_source_coverage_gate}"; printf ','
  csv_field "${byte_exact_byte_package_gate}"; printf ','
  csv_field "${byte_exact_runtime_observation_gate}"; printf ','
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
  csv_field "${byte_exact_parity_risk_reasons}"; printf ','
  csv_field "${byte_exact_project_dir}"; printf '\n'
} > "${CSV_REPORT}"

{
  printf '# OTC Admin Sample Regression\n\n'
  printf '%s\n' "- Sample: \`${OTC_ADMIN_JAR}\`"
  printf '%s\n' "- Original SHA-256: \`${ORIGINAL_SHA256}\`"
  printf '%s\n' "- Reference project: \`${OTC_ADMIN_REFERENCE_PROJECT}\`"
  printf '%s\n' "- Reference Java files: \`${REFERENCE_JAVA_FILES}\`"
  printf '%s\n\n' "- Generated Java files: \`${GENERATED_JAVA_FILES}\`"
  printf '%s\n' "- Shared Java files: \`${SHARED_JAVA_FILES}\`"
  printf '%s\n' "- Shared Java files with content differences: \`${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
  printf '%s\n' "- Resolved shared Java content differences: \`${SHARED_JAVA_RESOLVED_CONTENT_DIFF_FILES}/${SHARED_JAVA_CONTENT_DIFF_FILES}\`"
  printf '%s\n' "- Format-only shared Java content differences: \`${SHARED_JAVA_FORMAT_ONLY_DIFF_FILES}\`"
  printf '%s\n' "- Import-only shared Java content differences: \`${SHARED_JAVA_IMPORT_ONLY_DIFF_FILES}\`"
  printf '%s\n' "- Decompiler-artifact shared Java content differences: \`${SHARED_JAVA_DECOMPILER_ARTIFACT_DIFF_FILES}\`"
  printf '%s\n' "- Byte-equivalent textual shared Java content differences: \`${SHARED_JAVA_BYTE_EQUIVALENT_TEXT_DIFF_FILES}\`"
  printf '%s\n' "- Substantive shared Java content differences: \`${SHARED_JAVA_SUBSTANTIVE_DIFF_FILES}\`"
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
  printf '## Restoration score breakdown\n\n'
  printf '| Mode | Overall | Source | Resource | Runtime | Verification |\n'
  printf '| --- | ---: | ---: | ---: | ---: | ---: |\n'
  printf '| package-record | %s | %s | %s | %s | %s |\n' \
    "${package_record_overall_score}" "${package_record_source_score}" \
    "${package_record_resource_score}" "${package_record_runtime_score}" \
    "${package_record_verification_score}"
  printf '| byte-exact | %s | %s | %s | %s | %s |\n\n' \
    "${byte_exact_overall_score}" "${byte_exact_source_score}" \
    "${byte_exact_resource_score}" "${byte_exact_runtime_score}" \
    "${byte_exact_verification_score}"
  printf '## Source rebuild class bytecode fidelity\n\n'
  printf 'These numbers compare `target/classes` compiled from generated sources with application class entries from the original JAR. They are stricter than restored package artifact fidelity.\n\n'
  printf '| Mode | Source-recompiled class bytes same | Original app classes | Recompiled classes | Common | Same class bytes | Different class bytes | Missing recompiled classes | Extra recompiled classes | Compile fallback classes |\n'
  printf '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n'
  printf '| package-record | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "${package_record_source_rebuild_report_same}" \
    "${package_record_source_rebuild_report_original_classes}" \
    "${package_record_source_rebuild_report_classes}" \
    "${package_record_source_rebuild_report_common_classes}" \
    "${package_record_source_rebuild_report_same_class_bytes}" \
    "${package_record_source_rebuild_report_different_class_bytes}" \
    "${package_record_source_rebuild_report_missing_classes}" \
    "${package_record_source_rebuild_report_extra_classes}" \
    "${package_record_source_rebuild_report_compile_fallback_classes}"
  printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n\n' \
    "${byte_exact_source_rebuild_report_same}" \
    "${byte_exact_source_rebuild_report_original_classes}" \
    "${byte_exact_source_rebuild_report_classes}" \
    "${byte_exact_source_rebuild_report_common_classes}" \
    "${byte_exact_source_rebuild_report_same_class_bytes}" \
    "${byte_exact_source_rebuild_report_different_class_bytes}" \
    "${byte_exact_source_rebuild_report_missing_classes}" \
    "${byte_exact_source_rebuild_report_extra_classes}" \
    "${byte_exact_source_rebuild_report_compile_fallback_classes}"
  printf '## Remaining restoration gaps\n\n'
  printf '| Mode | Gap count | Categories |\n'
  printf '| --- | ---: | --- |\n'
  printf '| package-record | %s | %s |\n' \
    "${package_record_gap_count}" "${package_record_gap_categories}"
  printf '| byte-exact | %s | %s |\n\n' \
    "${byte_exact_gap_count}" "${byte_exact_gap_categories}"
  printf '## Non-penalizing observations\n\n'
  printf '| Mode | Observation count | Categories |\n'
  printf '| --- | ---: | --- |\n'
  printf '| package-record | %s | %s |\n' \
    "${package_record_observation_count}" "${package_record_observation_categories}"
  printf '| byte-exact | %s | %s |\n\n' \
    "${byte_exact_observation_count}" "${byte_exact_observation_categories}"
  printf '## Gate status\n\n'
  printf '| Mode | Build verification | Source coverage | Byte package | Runtime observation | Runtime support | Runtime status | Runtime events |\n'
  printf '| --- | --- | --- | --- | --- | --- | --- | ---: |\n'
  printf '| package-record | %s | %s | %s | %s | %s | %s | %s |\n' \
    "${package_record_build_gate}" "${package_record_source_coverage_gate}" \
    "${package_record_byte_package_gate}" "${package_record_runtime_observation_gate}" \
    "${package_record_runtime_launch_support}" "${package_record_runtime_run_status}" \
    "${package_record_runtime_events}"
  printf '| byte-exact | %s | %s | %s | %s | %s | %s | %s |\n\n' \
    "${byte_exact_build_gate}" "${byte_exact_source_coverage_gate}" \
    "${byte_exact_byte_package_gate}" "${byte_exact_runtime_observation_gate}" \
    "${byte_exact_runtime_launch_support}" "${byte_exact_runtime_run_status}" \
    "${byte_exact_runtime_events}"
  printf '## Runtime failure summary\n\n'
  printf '| Mode | Failure message | Failure cause |\n'
  printf '| --- | --- | --- |\n'
  printf '| package-record | %s | %s |\n' \
    "${package_record_runtime_failure_message}" "${package_record_runtime_failure_cause}"
  printf '| byte-exact | %s | %s |\n\n' \
    "${byte_exact_runtime_failure_message}" "${byte_exact_runtime_failure_cause}"
  printf '## Restored package artifact fidelity details\n\n'
  printf 'These numbers compare the restored Maven package artifact with the original archive. They do not prove source-recompiled class byte equivalence.\n\n'
  printf '| Mode | Content entries match | Same packaged class bytes | Different packaged class bytes | Same nested libs | Different nested libs | Entry order same | ZIP metadata diff entries | Archive bytes same |\n'
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
  printf 'Advisory source-review signals only; when build, source coverage, byte package, and source-recompiled class-byte gates pass, these counts are not remaining restoration gaps.\n\n'
  printf '| Mode | Classes | Methods | Parse failures | Missing source methods | Reflection methods | Invokedynamic methods | Missing required LVT names | HIGH | MEDIUM | LOW |\n'
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
  printf '## Decompile parity risk reasons\n\n'
  printf '| Mode | Reason counts |\n'
  printf '| --- | --- |\n'
  printf '| package-record | %s |\n' "${package_record_parity_risk_reasons}"
  printf '| byte-exact | %s |\n\n' "${byte_exact_parity_risk_reasons}"
  printf '## Artifacts\n\n'
  printf '%s\n' "- package-record project: \`${package_record_project_dir}\`"
  printf '%s\n' "- package-record artifact: \`${package_record_artifact_path}\`"
  printf '%s\n' "- byte-exact project: \`${byte_exact_project_dir}\`"
  printf '%s\n' "- byte-exact artifact: \`${byte_exact_artifact_path}\`"
  printf '%s\n' "- Source diff: \`${SOURCE_DIFF_REPORT}\`"
  printf '%s\n' "- Source content diff: \`${SOURCE_CONTENT_DIFF_REPORT}\`"
  printf '%s\n' "- Source rebuild bytecode fidelity: \`${SOURCE_REBUILD_BYTECODE_REPORT}\`"
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
check_gate "package-record source rebuild report exact" "${package_record_source_rebuild_report_same}" "true"
check_gate "package-record source rebuild report fallback classes" "${package_record_source_rebuild_report_compile_fallback_classes}" "0"
check_gate "package-record source rebuild report class byte diffs" "${package_record_source_rebuild_report_different_class_bytes}" "0"
check_gate "package-record source rebuild report missing classes" "${package_record_source_rebuild_report_missing_classes}" "0"
check_gate "package-record source rebuild report extra classes" "${package_record_source_rebuild_report_extra_classes}" "0"

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
check_gate "byte-exact source rebuild report exact" "${byte_exact_source_rebuild_report_same}" "true"
check_gate "byte-exact source rebuild report fallback classes" "${byte_exact_source_rebuild_report_compile_fallback_classes}" "0"
check_gate "byte-exact source rebuild report class byte diffs" "${byte_exact_source_rebuild_report_different_class_bytes}" "0"
check_gate "byte-exact source rebuild report missing classes" "${byte_exact_source_rebuild_report_missing_classes}" "0"
check_gate "byte-exact source rebuild report extra classes" "${byte_exact_source_rebuild_report_extra_classes}" "0"

log "Summary written to ${MD_REPORT}"
log "CSV written to ${CSV_REPORT}"
exit "${gate_status}"
