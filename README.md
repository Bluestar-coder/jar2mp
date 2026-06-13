# jar2mp

JAR to Maven Project Converter - 将 JAR/WAR 文件自动还原为标准 Maven 项目，支持批量处理。

## 功能特性

- **批量处理**: 同时选择多个 JAR/WAR 文件或整个目录进行批量分析和构建
- **双模式运行**: 支持 GUI 图形界面 和 CLI 命令行两种使用方式
- **自动依赖检测**: 4 层检测策略（嵌入 POM > MANIFEST.MF > 字节码扫描 > 文件名启发式）
- **自动反编译**: 集成 CFR、JD-Core、JADX、Fernflower 多引擎仲裁，将 .class 文件还原为 .java 源码
- **智能坐标识别**: 自动提取 groupId / artifactId / version
- **80+ 常见库映射**: 内置 Google、Apache Commons、Spring、Jackson、Alibaba 等常见库的包名→Maven 坐标映射
- **WAR 文件支持**: 自动识别 WAR 包并处理 WEB-INF 结构
- **可编辑依赖**: GUI 中可手动增删、编辑、勾选检测到的依赖
- **pom.xml 预览**: 生成前可预览和编辑 pom.xml，切换文件时自动缓存编辑内容
- **还原报告**: 输出资源清单、启动手册、字节码 parity 报告和验证结果
- **主题切换**: GUI 支持 FlatLaf 多主题（Light / Dark / Darcula / IntelliJ / Mac）

## 编译构建

```bash
mvn clean package
```

构建产物位于 `target/jar2mp-1.0-jar-with-dependencies.jar`。

## 使用方式

### GUI 模式

直接双击 JAR 包或无参数启动：

```bash
java -jar jar2mp-1.0-jar-with-dependencies.jar
```

操作流程：
1. 点击 **添加文件** 选择一个或多个 JAR/WAR 文件，或点击 **添加目录** 自动扫描目录中的 jar/war 文件
2. 设置输出目录
3. 按需在 **高级构建选项** 中启用字节级打包、原始构件保真副本、Maven 构建验证或运行时 Trace
4. 点击 **分析全部** 批量分析所有文件的 JAR 结构和依赖
5. 在文件列表中点击切换不同文件，查看各自的分析结果、构建验证、构建错误数、编译回退类数、源码重编译 class 字节摘要、字节级 package 保真、反编译失败数、运行状态及失败原因、恢复评分和依赖
6. 在 **依赖管理** 标签页中编辑选中文件的依赖
7. 点击 **生成 pom.xml** 预览当前选中文件的 pom.xml
8. 点击 **构建全部** 批量生成所有文件的 Maven 项目；启用的高级选项会同步生成对应报告和保真构件，并刷新当前选中文件的 gate 摘要

![GUI](images/gui-main.png)

![Dependencies](images/gui-dependencies.png)

![Pom Analysis](images/gui-pom.png)

### CLI 模式

带参数启动即进入 CLI 模式，支持多文件和目录输入：

```bash
java -jar jar2mp-1.0-jar-with-dependencies.jar [options] <jar-or-war-files...>
```

#### 常用示例

```bash
# 单个文件
java -jar jar2mp.jar target/app.jar

# 批量处理多个文件
java -jar jar2mp.jar a.jar b.jar c.jar --verbose

# 自动扫描目录中所有 jar/war 文件
java -jar jar2mp.jar /path/to/libs/ -o /tmp/output

# 指定输出目录和坐标
java -jar jar2mp.jar -o /tmp/project -g com.example -a myapp target/app.jar

# 详细输出模式
java -jar jar2mp.jar --verbose lib.jar

# 跳过反编译（仅复制 .class 文件）
java -jar jar2mp.jar --no-decompile lib.jar

# 导出检测到的依赖到文件
java -jar jar2mp.jar --export-deps deps.txt app.jar

# 覆盖已存在的输出目录
java -jar jar2mp.jar -f app.jar
```

<!-- CLI 运行截图 -->
![CLI](images/cli-run.png)

#### 完整参数列表

```
Usage: java -jar jar2mp.jar [options] <jar-or-war-files...>

Arguments:
  <jar-or-war-files...>           JAR/WAR 文件或目录路径（支持多个，目录自动扫描）

Options:
  -o, --output <dir>              输出目录（默认当前目录）
  -g, --groupId <groupId>         覆盖检测到的 groupId
  -a, --artifactId <artifactId>   覆盖检测到的 artifactId
  -v, --version <version>         覆盖检测到的 version
  -j, --java-version <version>    目标 Java 版本（默认自动检测）
  -p, --packaging <type>          打包类型: jar / war（默认自动检测）
      --no-decompile              跳过反编译，直接复制 .class 文件
      --no-dependencies           跳过依赖检测
      --no-resources              跳过资源文件提取
      --mapping-file <file>       自定义包名映射文件
      --aggressive-scan           激进扫描模式
      --include-synthetic         包含合成/桥接方法
      --export-deps <file>        导出依赖到文件
      --import-deps <file>        从文件导入依赖
      --verify-build              构建后运行 Maven 验证
      --verify-goal <goal>        验证使用的 Maven goal（默认 compile）
      --trace-runtime             启用运行时追踪报告
      --trace-args <args>         指定运行时追踪参数
      --trace-timeout <seconds>   设置运行时追踪超时（默认 120 秒）
      --smoke-only                启用运行时追踪并跳过 Maven 验证
      --emit-raw-artifact         在 target/raw-artifact/ 生成原始归档的字节保真副本
      --byte-exact-package        为 Maven package 产物安装字节级保真修复与验证
      --restore-package-records   内容一致后回放原始 ZIP records 使 package 产物字节一致
      --compare-artifact <file>   将输入原始归档与指定重建归档做字节保真度对比
  -f, --force                     覆盖已存在的输出目录
  -q, --quiet                     静默模式
      --verbose                   详细输出
  -h, --help                      显示帮助
      --version                   显示版本号
```

## 依赖检测策略

工具按以下优先级依次检测 Maven 依赖：

| 优先级 | 策略 | 说明 | 置信度 |
|--------|------|------|--------|
| 1 | 嵌入 POM | 读取 `META-INF/maven/**/pom.properties` 和 `pom.xml` | HIGH |
| 2 | MANIFEST.MF | 解析 `Class-Path`、`Implementation-*` 等属性 | MEDIUM |
| 3 | 字节码扫描 | 解析 .class 常量池中的包引用，匹配映射数据库 | LOW |
| 4 | 文件名启发式 | 从 JAR 文件名猜测 `artifactId-version` | GUESS |

## 还原与报告

每个项目会生成以下报告：

- `restoration-report.md` - 还原结果总览
- `resource-inventory.md` - 资源分类与目标路径
- `decompile-parity-report.md` - 字节码与源码对照，并汇总 HIGH/MEDIUM/LOW 方法级反编译风险；`Risk method index` 会在逐类明细前列出 HIGH/MEDIUM 方法，并区分 `reflection call detected`、`lambda metafactory invokedynamic`、`invokedynamic`、`missing debug names` 等原因；反射风险按 `java/lang/Class`、`java/lang/reflect` 和常见反射工具 owner 识别，避免把普通业务 `getMethod()`/`getField()` 方法名误报为反射；invokedynamic 明细会记录 bootstrap 方法及参数，用于定位 lambda 实现目标和字符串拼接 recipe；纯 `StringConcatFactory` 字符串拼接仍记录为 invokedynamic 事实但不进入 MEDIUM 风险；LocalVariableTable 缺失计数只统计确实有用户参数或本地变量写入、但缺少可恢复变量名的方法，并排除 synthetic enum switch-map、bridge method、enum support method、lambda deserialization support method、outer-this constructor 和 monitor temporaries 这类编译器支撑结构
- `restoration-score.md` - 源码、资源、运行时观测与构建验证的综合评分；存在 source rebuild fidelity CSV 时会内嵌源码重编译 class 字节一致性摘要；最终 package 字节一致性以 artifact fidelity 报告为准，存在 package fidelity CSV 时会内嵌 exact/archive/content 摘要
- `gap-summary.md` - 主要缺口与非扣分观察项汇总；存在 source rebuild fidelity CSV 时同样会显示源码重编译 class 字节一致性摘要；存在 package fidelity CSV 时同样会显示最终 package exact/archive/content 摘要；运行时启动失败缺口或环境观察项会包含 failure message 和首个 `Caused by:` 原因
- `source-rebuild-fidelity-report.md` / `source-rebuild-fidelity-summary.csv` - 启用 `--verify-build` 且 Maven 构建成功时生成，把原始归档中的应用 `.class` 与生成项目 `target/classes` 的 `.class` 做字节级对比；compile fallback class 会单独计数，不计为纯源码重编译一致
- `runtime-trace-report.md` - 运行时追踪报告（启用运行时追踪时生成），Run summary 会显示 run status、failure message 和从 `Caused by:` 提取的 failure cause
- `RUNBOOK.md` - 启动候选与运行方式
- `verification-report.md` - 启用 `--verify-build` 时的 Maven 验证摘要
- `verification-errors.md` - 启用 `--verify-build` 时解析出的逐文件编译错误明细
- `decompile-failures.md` - 反编译失败条目和原始 class 退回位置；synthetic enum switch-map 等外层源码已覆盖的编译器支撑 class 会原样保留但不计为失败
- `artifact-fidelity-report.md` / `artifact-fidelity-summary.csv` - 启用 `--compare-artifact` 时的原始/重建 artifact 对比；如果内容一致但 ZIP entry 顺序、空目录 entry 集合或可原位恢复的 ZIP 元数据不同，还会生成 `archive-order-restored/` 候选和对应保真报告
- `target/byte-exact-package-check/artifact-fidelity-report.md` - 启用 `--byte-exact-package --verify-build` 且 package 生命周期运行时的最终产物保真报告
- `target/package-record-restore-check/artifact-fidelity-report.md` - 启用 `--restore-package-records --verify-build` 且 package 生命周期运行时的受保护 ZIP record 回放产物保真报告

当输入归档包含仅大小写不同的 class 路径时，jar2mp 不会把这些 class 展开到普通目录；它会生成 `target/compiler-fallback-classes.jar` 并在 `pom.xml` 中加入 system-scope 依赖，避免大小写不敏感文件系统破坏 Maven 编译类路径。

推荐验证流程：

1. 先看 `restoration-report.md` 和 `resource-inventory.md`
2. 再看 `RUNBOOK.md` 确认启动方式
3. 用 `decompile-parity-report.md` 的 Risk summary 和 Risk method index 定位高风险方法，再下钻逐方法明细
4. 看 `restoration-score.md` 和 `gap-summary.md` 了解源码、资源、运行时观测与构建验证缺口；0 impact 的外部环境问题会单独作为 observation/环境观察，不计入 remaining restoration gaps；如启用了 `--verify-build` 且构建成功，两份报告都会展示 source rebuild class 字节一致性摘要
5. 如需确认可编译性和源码重编译 class 字节一致性，启用 `--verify-build` 并查看 `source-rebuild-fidelity-report.md`；如需确认最终包字节一致性，查看对应 artifact fidelity 报告

## 样本回归验证

仓库提供本地样本回归脚本，用于生成普通 Maven JAR、Spring Boot JAR、WAR、MyBatis、Shiro、Spring Security、ProGuard 混淆 JAR、无 debug 信息 JAR，并逐类运行还原评分：

```bash
./scripts/regression/run-sample-regression.sh
```

汇总报告写入 `target/regression-samples/report/regression-summary.md` 和 `target/regression-samples/report/regression-summary.csv`。样本矩阵、阈值和 PASS/FAIL 规则见 `docs/regression-samples.md`。

也可以运行真实 GitHub 项目回归集，脚本会下载固定 ref 的 Spring Boot、Spring Security、MyBatis WAR、Shiro 样本，构建原始产物后再用 jar2mp 做 verify-only 还原验证：

```bash
./scripts/regression/run-github-realworld-regression.sh
```

汇总报告写入 `target/realworld-samples/report/github-realworld-summary.md` 和 `target/realworld-samples/report/github-realworld-summary.csv`。realworld 矩阵会分别记录 source rebuild artifact fidelity、raw artifact exact、byte-exact package 门禁和 package-record restore 证据；样本来源、固定 ref、阈值和已知非门禁候选见 `docs/github-realworld-regression.md`。

也可以运行下载型 GitHub Release 二进制样本矩阵，脚本会下载固定 release asset 并验证还原项目的编译门禁与 raw artifact 保真：

```bash
./scripts/regression/run-github-release-assets-regression.sh
```

汇总报告写入 `target/release-assets-samples/report/github-release-assets-summary.md` 和 `target/release-assets-samples/report/github-release-assets-summary.csv`。`PASS_WITH_WARNINGS` 表示 Maven package 验证、raw artifact exact 与 byte-exact package 门禁通过，但仍存在 raw-class fallback、运行时跳过/告警、源码分数未满分或 package-record restore 未精确通过。

严格字节级还原使用 `--byte-exact-package`：它会隐式启用 `--emit-raw-artifact`，在生成的 `pom.xml` 中使用原始归档文件名作为 `finalName`、写入常见测试/质量插件的 skip properties，并跳过原 POM 中会在 `package` 阶段重写主 artifact 的 shade/assembly/repackage 等插件。jar2mp 还会在生成项目的 `.jar2mp/byte-exact/` 写入 standalone ZIP record helper 和 `.jar2mp/byte-exact/raw-artifact/<original-name>` 参考原包；后续 `mvn package` 或 `mvn clean package` 会先生成正常 package 产物作为 Maven 项目可打包性的门禁，再由 helper 以原始归档作为最终 ZIP record 的规范字节来源，回放原始 entry 顺序、空目录 entry、manifest/module-info/resource/class 字节、entry 集合和 ZIP 元数据，最终写入 `target/byte-exact-package-restored/<original-name>`。和 `--verify-build` 一起使用时，默认验证目标会从 `compile` 提升为 `package`，并在 `target/byte-exact-package-check/` 写入最终 package 产物的字节保真报告；显式 `--verify-goal` 仍可覆盖。若需要保留普通源码重构包语义、但在内容 entry 已一致时把最终 `mvn package` 产物提升到字节一致，可使用 `--restore-package-records`；该模式不会改 `finalName` 或跳过 package-transforming 插件，会在 `.jar2mp/package-records/` 写入受保护 helper 和参考原包，helper 先校验 Maven 产物的非目录 entry 集合与内容摘要一致，再回放原始 ZIP records，验证报告写入 `target/package-record-restore-check/`。普通 `--emit-raw-artifact` 只保留 `target/raw-artifact/` 原始副本和报告，不改变 `mvn package` 的源码重构产物；普通源码重构包会在 `process-classes` 阶段回填原始 class bytes，用于隔离剩余 ZIP 容器层差异。Spring Boot 可执行 JAR 的普通 package 还会在 repackage 后用 `src/main/original-libs/BOOT-INF/lib` 重建最终 `BOOT-INF/lib` 集合，用 `src/main/original-boot-loader` 回填原始 root Spring Boot loader classes，并把原始 `META-INF/MANIFEST.MF` 覆盖回最终包，避免 system-scope 依赖被 Spring Boot 插件改名、补入传递依赖，或由当前插件版本注入不同 loader/manifest 字节。

对于已经下载到 `target/adhoc-github-release-assets/assets/` 的临时 GitHub Release 二进制样本，可以运行离线缓存矩阵来刷新当前源码的编译、raw artifact 与 byte-exact package 门禁结果：

```bash
./scripts/regression/run-cached-adhoc-release-assets-regression.sh
```

汇总报告写入 `target/adhoc-github-release-assets/report-current/adhoc-github-release-assets-summary.md` 和 `target/adhoc-github-release-assets/report-current/adhoc-github-release-assets-summary.csv`。固定缓存样本、PASS 规则和输出目录见 `docs/regression-samples.md`。

针对本地 OTC admin 样本，可以运行聚焦回归脚本。默认样本为 `/Users/jackma/ProjectCode/68集团/OTC/otc-admin.jar`，默认参考项目为 `/Users/jackma/ProjectCode/68集团/OTC/OTC-Admin`：

```bash
./scripts/regression/run-otc-admin-regression.sh
```

脚本会分别运行 `--restore-package-records --verify-build` 和 `--byte-exact-package --verify-build`，并要求两条链路的 Maven 验证为 `BUILD SUCCESS`、最终 package `exact_match=true`、重建 SHA-256 与原始 JAR 一致，同时要求 `source-rebuild-fidelity-summary.csv` 中 source-recompiled class bytes same 为 `true`、`decompile-parity-report.md` 中 class parse failure 和源码缺失方法数均为 `0`。汇总报告写入 `target/otc-admin-sample/report/otc-admin-summary.md` 和 `target/otc-admin-sample/report/otc-admin-summary.csv`，并展开 `restoration-score.md` 的 overall/source/resource/runtime/verification bucket、source rebuild class 字节一致性、remaining restoration gap count/category、non-penalizing observation count/category，以及 build verification、source coverage、byte package、runtime observation 四个 gate 状态；设置 `OTC_ADMIN_TRACE_RUNTIME=1` 时，会把 `OTC_ADMIN_TRACE_ARGS` 和默认 30 秒的 `OTC_ADMIN_TRACE_TIMEOUT` 传给运行时追踪，并在报告中记录 runtime launch support、run status、failure message、failure cause 和按 `kind/owner/target/value` 去重后的事件数。自动 runtime smoke run 会把 trace agent scope 限制到入口主类包名和 `org.springframework.boot.loader.`，降低大型 Spring Boot 样本启动期对三方框架的转换开销；手动直接使用 trace agent 且不设置 `jar2mp.traceIncludes` 时仍保持 broad 采集。runtime observation 默认显示 `NOT_RUN`，完整追踪可显示 `PASS_EXIT_ZERO`，通过本地 HTTP startup probe 验证健康但超过追踪超时的长运行应用会显示 `PASS_HEALTHY_TIMEOUT`；只有收集到事件但没有健康响应证据的超时才显示 `WARN_STARTED_TIMEOUT`；输出里已出现 Spring Boot 启动失败信号的超时或非零退出，run status 会记录为 `STARTUP_FAILED_TIMEOUT` 或 `STARTUP_FAILED_EXIT`，runtime observation gate 会显示对应的 `FAIL_STARTUP_FAILED_*`，不会和字节级 package 成功混在一起；Redis/连接拒绝等外部依赖启动失败会归类为 0 impact 的 `runtime_environment` 环境观察项，不降低 byte-level restoration score，也不计入 remaining restoration gaps，其他启动失败状态会作为 runtime bucket 的终局缺口，避免把未执行到的静态 file/socket/resource 路径再列成独立还原缺口。报告也展开 artifact fidelity 细项，包括内容 entry 是否一致、class byte 差异数、嵌套依赖差异数、entry 顺序、ZIP metadata 差异和整包字节一致性；同时汇总 `decompile-parity-report.md` 的 HIGH/MEDIUM/LOW、源码缺失、反射、invokedynamic、risk reason breakdown 和需要变量名但缺少 LocalVariableTable 名称的方法数；反射风险按 `java/lang/Class`、`java/lang/reflect` 和常见反射工具 owner 识别，避免把普通业务 `getMethod()`/`getField()` 方法名误报为反射；invokedynamic 明细会记录 bootstrap 方法及参数，纯 `StringConcatFactory` 字符串拼接仍记录为 invokedynamic 事实但不进入 MEDIUM 风险；LVT 缺失统计会跳过 synthetic enum switch-map、bridge method、enum support method、lambda deserialization support method、outer-this constructor 和 monitor temporaries 这类源码语义已覆盖的编译器支撑结构。报告正文还会用 Risk method index 列出 HIGH/MEDIUM 方法及具体原因，并在汇总中按原因聚合计数；当 build、source coverage、byte package 和 source-recompiled class-byte gates 均通过时，这些 decompile parity risk 仅作为源码审阅信号，不计为 remaining restoration gaps。源码清单差异写入 `target/otc-admin-sample/report/otc-admin-source-diff.txt`，用于区分参考项目本地补充文件和 jar2mp 生成文件，并标注差异源码对应的 class 是否存在于原始 JAR；共享 Java 文件的内容差异写入 `target/otc-admin-sample/report/otc-admin-source-content-diff.txt`，并按 format-only、import-only、decompiler-artifact、byte-equivalent-text、substantive 分类，其中 decompiler-artifact 用于拆出显式 cast、泛型擦除和 CFR whole-class-analysis 注释等反编译器噪声，byte-equivalent-text 用于标注文本仍不同但生成源码重编译 class bytes 已与原始 JAR 一致的文件，substantive 用于继续定位同路径源码仍不同且缺少 class-byte 等价证据的泛型、switch 或流程控制修复点；resolved shared Java content differences 汇总前四类非 substantive 证据，便于直接看到文本差异中已有多少被格式、导入、反编译器噪声或 class-byte 等价证明闭环。可用 `OTC_ADMIN_JAR`、`OTC_ADMIN_REFERENCE_PROJECT`、`OTC_ADMIN_WORK_DIR`、`JAR2MP_JAR`、`BUILD_JAR2MP`、`MVN`、`JAVA_CMD`、`JAR_CMD` 覆盖默认值。

能还原的主要内容：

- Java 源码、资源文件、WEB-INF 结构、常见配置、Maven 坐标、原始 class bytes、原始 `META-INF/maven/**` metadata、原始 manifest、build-info/SBOM 等可保留构建元数据

不能保证完全还原的内容：

- 反射调用的真实运行时行为
- 混淆/损坏的 class
- 缺失调试信息导致的局部变量名恢复
- 运行时生成内容、外部服务依赖和部署环境

## 生成项目结构

每个输入文件在输出目录下生成独立的项目：

```
{output}/
├── {artifactId-1}/
│   ├── pom.xml
│   ├── restoration-report.md
│   ├── resource-inventory.md
│   ├── decompile-parity-report.md
│   ├── restoration-score.md
│   ├── gap-summary.md
│   ├── source-rebuild-fidelity-report.md   ← 启用构建验证且构建成功时生成
│   ├── source-rebuild-fidelity-summary.csv ← source rebuild class 字节一致性摘要
│   ├── runtime-trace-report.md   ← 启用运行时追踪时生成
│   ├── RUNBOOK.md
│   ├── verification-report.md   ← 启用构建验证时生成
│   ├── verification-errors.md   ← 启用构建验证且存在可解析错误时生成
│   ├── decompile-failures.md
│   ├── .jar2mp/
│   │   ├── byte-exact/          ← byte-exact package helper 与参考原包（启用 --byte-exact-package）
│   │   └── package-records/     ← 受保护 package record helper 与参考原包（启用 --restore-package-records）
│   ├── target/
│   │   ├── original-classes/   ← 反编译失败或编译器支撑 class 需要保留时的原始 class
│   │   ├── byte-exact-package-check/  ← byte-exact + 构建验证 package 阶段保真报告
│   │   ├── package-record-restore-check/  ← package-record + 构建验证 package 阶段保真报告
│   │   └── compiler-fallback-classes.jar  ← 大小写冲突 class 的编译 fallback jar
│   └── src/
│       ├── main/
│       │   ├── java/          ← 反编译后的 .java 文件（保留包结构）
│       │   ├── resources/     ← 非类文件资源 + META-INF/services
│       │   ├── original-classes/ ← clean package 时回填原始 class bytes 和 entry mtime
│       │   ├── original-libs/  ← clean package 时提供原始 BOOT-INF/lib / WEB-INF/lib 编译 classpath
│       │   ├── original-boot-loader/ ← Spring Boot package 时回填原始 root loader classes
│       │   └── webapp/        ← WAR 根资源与 WEB-INF 相关资源
│       └── test/
│           ├── java/
│           └── resources/
├── {artifactId-2}/
│   ├── pom.xml
│   └── src/
│       └── ...
└── ...
```

## 项目技术栈

- **Java 8** - 目标兼容版本
- **FlatLaf** - GUI 主题框架
- **CFR / JD-Core / JADX / Fernflower** - 交叉反编译与质量仲裁
- **Gson** - JSON 处理
- **Maven Assembly Plugin** - 打包为可执行 fat JAR

## License

MIT
