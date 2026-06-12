# Sample Regression Set

`scripts/regression/run-sample-regression.sh` builds a local artifact matrix, runs jar2mp against each artifact, and writes per-sample restoration scores. The generated projects, restored outputs, and reports live under `target/regression-samples/`, so they stay out of git.

Run:

```bash
./scripts/regression/run-sample-regression.sh
```

Optional environment:

```bash
MVN=/path/to/mvn JAVA_TRACE_TIMEOUT=45 ./scripts/regression/run-sample-regression.sh
```

## Samples

| Sample | Artifact type | Trace mode | Threshold | Purpose |
| --- | --- | --- | ---: | --- |
| `plain-maven-jar` | executable Maven JAR | trace | 95 | Baseline JAR with reflection, resource loading, and file I/O. |
| `spring-boot-jar` | Spring Boot executable JAR | trace | 95 | Nested `BOOT-INF/classes` and `BOOT-INF/lib` restoration. |
| `servlet-war` | WAR | verify-only | 75 | Servlet layout, `WEB-INF/classes`, resources, and compile verification. |
| `mybatis-jar` | thin Maven JAR | trace | 90 | Manifest `Class-Path`, MyBatis dependency, and XML mapper resources. |
| `shiro-jar` | thin Maven JAR | trace | 90 | Apache Shiro dependency and auth-related package detection. |
| `spring-security-jar` | thin Maven JAR | trace | 90 | Spring Security dependency and runtime context usage. |
| `obfuscated-jar` | ProGuard-obfuscated JAR | trace | 85 | Renamed implementation classes and reduced source readability. |
| `no-debug-jar` | executable Maven JAR | trace | 85 | Class files compiled with `debug=false` and no local variable table. |

## Pass Criteria

Each sample is marked `PASS` only when:

- jar2mp exits successfully.
- `restoration-score.md` exists and the overall source/resource/runtime-observation/build-verification score meets the sample threshold.
- Source and resource buckets are both `100`.
- `verification-report.md` reports `BUILD SUCCESS` and `Failure type: NONE`.
- `decompile-failures.md` reports zero failed decompilations.
- Trace samples also require `runtime-trace-report.md` exit code `0`, at least one runtime event, and runtime score `100`.

The WAR sample is intentionally `verify-only`; it is not expected to produce a runtime trace because a WAR needs a servlet container.

## Outputs

The script writes:

- `target/regression-samples/report/regression-summary.md`
- `target/regression-samples/report/regression-summary.csv`
- `target/regression-samples/report/*.cli.log`
- generated source projects under `target/regression-samples/sources/`
- restored jar2mp projects under `target/regression-samples/restored/`

Do not commit the generated artifacts or reports. Commit the script and this documentation only.

## GitHub Release Assets

`scripts/regression/run-github-release-assets-regression.sh` downloads fixed GitHub Release JAR/WAR assets and restores them with Maven package verification, runtime tracing where safe, raw artifact preservation, and byte-exact package verification enabled.

Run:

```bash
./scripts/regression/run-github-release-assets-regression.sh
```

The script writes:

- `target/release-assets-samples/report/github-release-assets-summary.md`
- `target/release-assets-samples/report/github-release-assets-summary.csv`
- `target/release-assets-samples/report/*.cli.log`
- downloaded release assets under `target/release-assets-samples/assets/`
- restored jar2mp projects under `target/release-assets-samples/restored/`

Each sample is marked `PASS` when the overall score meets the sample threshold, Maven package verification reports `BUILD SUCCESS` with `Failure type: NONE`, raw artifact preservation reports `exact_match=true`, byte-exact package comparison reports `byte_exact_package_exact=true`, guarded package-record restoration reports `package_record_restore_gate=PASS_EXACT`, and runtime evidence does not fail. A sample is marked `PASS_WITH_WARNINGS` when the required byte-exact gates pass but there are non-gating warnings such as raw-class fallback, decompile-failure records, runtime skip/warn status, source/resource buckets below `100`, or a guarded package-record restoration gap in exploratory matrices.

`STRICT_RELEASE_ASSETS=1` turns any remaining `GAP`, `RESTORE_FAILED`, or `DOWNLOAD_FAILED` row into a non-zero script exit. The default mode is exploratory and only fails when no sample reaches `PASS` or `PASS_WITH_WARNINGS`.

Use `--byte-exact-package` for the strict byte-level restoration path. It preserves the raw artifact as auxiliary evidence, uses the original artifact base name as Maven `finalName`, adds skip properties for common test and quality plugins, and skips package-transforming shade/assembly/repackage plugins from the original POM so the helper controls the final artifact restoration. jar2mp also writes a standalone package helper under `.jar2mp/byte-exact/` plus a `.jar2mp/byte-exact/raw-artifact/<original-name>` reference artifact, so the generated project can run `mvn package` or `mvn clean package`, prove the restored Maven project packages successfully, and then restore the final artifact from the original ZIP records, including entry order, empty directories, manifest/module-info/resource/class payloads, entry-set drift, and ZIP metadata. The final restored package is written under `target/byte-exact-package-restored/`. When combined with `--verify-build`, its default verification goal is `package` unless `--verify-goal` is set explicitly; jar2mp writes `target/byte-exact-package-check/` fidelity reports for the final package output. Use `--restore-package-records` for the guarded ordinary package path: it keeps the generated POM's normal `finalName` and plugin set, then the helper checks that rebuilt non-directory entries and content digests match the original before overwriting the final artifact from original ZIP records; reports go to `target/package-record-restore-check/`. Plain `--emit-raw-artifact` keeps the raw copy and reports exactness without changing the normal source-rebuild package output; normal source rebuilds preserve original class bytes, `META-INF/maven/**` metadata, manifests where compatible with the package type, build-info/SBOM resources, and nested `BOOT-INF/lib` / `WEB-INF/lib` archives as local system dependencies while disabling generated descriptors or metadata generators that would overwrite those entries. For Spring Boot executable JARs, the normal source-rebuild package also rewrites the final `BOOT-INF/lib` set from `src/main/original-libs/BOOT-INF/lib`, root loader classes from `src/main/original-boot-loader`, and root `META-INF/MANIFEST.MF` after repackage so plugin-generated dependency names, extra transitive libraries, or different loader/manifest bytes do not replace the original archive entries.

## Cached Ad-hoc Release Assets

`scripts/regression/run-cached-adhoc-release-assets-regression.sh` replays the cached binary release assets under `target/adhoc-github-release-assets/assets/`. It does not download artifacts, so it is useful for refreshing stale ad-hoc reports after a source fix.

Run:

```bash
./scripts/regression/run-cached-adhoc-release-assets-regression.sh
```

The fixed matrix expects these cached files:

- `picocli-4.7.7.jar`
- `picocli-codegen-4.7.7.jar`
- `undertow-core-2.4.1.Final.jar`
- `undertow-examples-2.4.1.Final.jar`
- `jmx_prometheus_standalone-1.5.0.jar`
- `opentelemetry-javaagent.jar`

Each sample is marked `PASS` only when:

- jar2mp exits successfully.
- `verification-report.md` reports `BUILD SUCCESS`, `Failure type: NONE`, and `Error count: 0`.
- `target/raw-artifact/artifact-fidelity-summary.csv` reports `exact_match=true`.
- The generated project runs `mvn package` successfully and the final packaged JAR/WAR reports `byte_exact_package_exact=true`.

The script writes:

- `target/adhoc-github-release-assets/report-current/adhoc-github-release-assets-summary.md`
- `target/adhoc-github-release-assets/report-current/adhoc-github-release-assets-summary.csv`
- `target/adhoc-github-release-assets/report-current/*.cli.log`
- restored jar2mp projects under `target/adhoc-github-release-assets/restored-current/`

Set `STRICT_CACHED_ADHOC_ASSETS=0` to keep the script exploratory when some cached assets are missing or expected to fail. By default, any non-`PASS` sample exits non-zero.

## OTC Admin Local Sample

`scripts/regression/run-otc-admin-regression.sh` replays the local OTC admin sample used for the current byte-level restoration work. The default sample is `/Users/jackma/ProjectCode/68集团/OTC/otc-admin.jar`, and the default reference project is `/Users/jackma/ProjectCode/68集团/OTC/OTC-Admin`.

Run:

```bash
./scripts/regression/run-otc-admin-regression.sh
```

Optional environment:

```bash
OTC_ADMIN_JAR=/path/to/otc-admin.jar \
OTC_ADMIN_REFERENCE_PROJECT=/path/to/OTC-Admin \
OTC_ADMIN_WORK_DIR=target/otc-admin-sample \
./scripts/regression/run-otc-admin-regression.sh
```

The script writes:

- `target/otc-admin-sample/report/otc-admin-summary.md`
- `target/otc-admin-sample/report/otc-admin-summary.csv`
- `target/otc-admin-sample/report/otc-admin-source-diff.txt`
- `target/otc-admin-sample/report/package-record.cli.log`
- `target/otc-admin-sample/report/byte-exact.cli.log`
- restored jar2mp projects under `target/otc-admin-sample/restored/`

The script runs two independent byte-level gates:

- `--restore-package-records --verify-build` keeps the ordinary generated Maven project shape, then requires guarded package-record restoration to produce a byte-identical final package.
- `--byte-exact-package --verify-build` runs the strict package-restoration path and requires the restored final package to be byte-identical to the original sample.

The script exits non-zero unless both modes report `BUILD SUCCESS`, `Failure type: NONE`, `exact_match=true`, content entries match, class-byte differences are `0`, nested-library differences are `0`, archive entry order matches, ZIP metadata differences are `0`, archive bytes match, and a rebuilt SHA-256 equals the original JAR SHA-256. It also enforces source coverage gates from each mode's `decompile-parity-report.md`: class parse failures must be `0`, and missing-source methods must be `0`. The summary records generated/reference Java file counts, current decompile-fallback counts, artifact fidelity detail columns, decompile parity risk counts, and `reference-only` / `generated-only` source file lists. The parity risk columns include HIGH/MEDIUM/LOW methods, missing-source methods, reflection methods, invokedynamic methods, and methods missing LocalVariableTable names. The source diff report marks whether each differing Java source has a corresponding `.class` entry in the original JAR (`root`, `BOOT-INF/classes`, or `WEB-INF/classes`), so reference-project local additions are not mistaken for jar2mp decompilation gaps. These non-gating parity counts are reported as progress evidence; final package byte equality is proven by the package-record and byte-exact artifact fidelity outputs, not by the runtime-observation portion of `restoration-score.md`.
