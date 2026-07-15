# OmniEdge Agent 工作约束

本文件适用于仓库根目录及其所有子目录。它约束在本项目中执行分析、开发、测试和文档任务的 Agent。

## 1. 项目目标与事实基线

OmniEdge 是一个 Android/Compose 端侧 RAG 项目：文档内容经 Reader 抽取、切分、`all-MiniLM-L6-V2` 嵌入、ObjectBox 向量检索后交给本地或远程 LLM 问答。

多模态二次开发的实施契约是 [docs/multimodal-rag-development.md](docs/multimodal-rag-development.md)。开始任何 OCR、扫描 PDF 或音频转写实现前，必须先阅读该文档及以下现有代码：

- `app/src/main/java/com/ml/shubham0204/docqa/domain/readers/`
- `app/src/main/java/com/ml/shubham0204/docqa/ui/screens/docs/DocsViewModel.kt`
- `app/src/main/java/com/ml/shubham0204/docqa/data/{DataModels,DocumentsDB,ChunksDB}.kt`
- `app/src/main/java/com/ml/shubham0204/docqa/domain/{WhiteSpaceSplitter,SentenceEmbeddingProvider}.kt`
- `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/ml/shubham0204/docqa/di/AppModule.kt`

不要假设项目只支持 PDF/DOCX：它已有 PDF、DOCX、Markdown 和纯文本 Reader；`INTERNET` 权限也已存在。

## 2. 硬性边界

### 允许的变更范围

仅在已获用户批准的 Phase 内做最小必要改动。多模态开发通常可涉及：

- `domain/readers/`：复用/适配既有 Reader，新增 OCR、音频解码和 ASR 抽取器。
- `domain/ingestion/`：统一来源、抽取结果、导入编排和错误模型。只有在实施契约明确需要时才创建该目录。
- `data/`：为来源类型、抽取状态、模型版本和原子化写入做最小 ObjectBox 实体/仓储调整。
- `ui/screens/docs/` 与必要的通用 UI 组件：系统选择器入口、任务状态、进度、取消和失败重试。
- `app/build.gradle.kts`、`AndroidManifest.xml`、Koin 注解/Factory：仅为已批准的能力增加必要依赖、声明和注入。
- `docs/` 与针对改动的测试文件。

除非当前 Phase 或用户明确要求，不得改动以下能力的语义、提示词或模型列表：

- 聊天页面与 LLM 问答路径（`ui/screens/chat/`、`domain/llm/`）
- ObjectBox 初始化和向量检索算法（`ObjectBoxStore`、`ChunksDB.getSimilarChunks`）
- 本地 LLM 下载、凭据存储和 Gemini 调用
- 现有文档格式的用户可见行为

为满足中文多模态检索质量，可以受控修改切分策略、嵌入模型版本元数据和索引重建流程；不得在没有基准测试、迁移策略和明确提交说明时替换现有嵌入模型或批量重建索引。

### 禁止事项

- 不重建已有 `Reader.kt`，不复制已有读取器功能，也不绕过统一导入路径直接写 ObjectBox。
- 不进行与当前任务无关的重构、格式化、包名调整、依赖升级或 UI 改版。
- 不使用已归档的 `com.arthenica:ffmpeg-kit` 工件；不因“兼容格式更多”擅自引入大型原生库。
- 不添加新的第三方依赖、远程模型、遥测/分析 SDK、广告 SDK 或网络服务，除非用户明确批准其用途、许可证、包体积和隐私影响。
- 不申请 `READ_MEDIA_IMAGES`、`READ_MEDIA_AUDIO`、`READ_EXTERNAL_STORAGE` 等广泛媒体权限来实现导入；图片优先 Photo Picker，音频优先 SAF `OpenDocument`。
- 不扩展明文网络、打印 API Key/Hugging Face Token、提交 `local.properties`、密钥、模型二进制或用户媒体。
- 不执行 `git reset --hard`、`git clean`、强制覆盖用户改动或删除未确认文件。

## 3. 架构与实现规则

### 导入与数据一致性

- 所有来源先建模为统一的 `ContentSource`，抽取层返回包含文本、来源类型、MIME 与诊断信息的结构化结果；下游最终消费规范化文本。
- 不把 OCR/ASR 的耗时工作塞入当前同步 `Reader.readFromInputStream(): String?` 中。新路径必须可取消、可报告失败，并在后台调度器运行。
- 本地 URI 使用系统选择器并按需调用 `takePersistableUriPermission()`；对 URL/临时文件实行“落盘后重新打开”策略，绝不复用已消费的 `InputStream`。
- 完成抽取和全部嵌入后，再用单个数据库事务写入来源和全部 Chunk。失败或取消不得留下孤儿 `Document`、`Chunk` 或缓存文件。
- 删除来源必须同时删除关联 Chunk；实体字段、ObjectBox 模型和迁移影响必须在同一变更中审查。

### OCR 与扫描 PDF

- 一期 OCR 使用经批准的捆绑式 ML Kit 中文文本识别模型；不要同时加入捆绑模型和 Google Play 服务动态模型。
- 使用 `InputImage.fromFilePath()` 或流式/降采样策略，禁止无上限地解码完整 Bitmap。
- 扫描 PDF 必须先走现有文本提取；只有文本为空或低于定义阈值时才逐页渲染 OCR。必须限制页数、总像素和任务时间，并支持取消。

### 音频转写与模型管理

- 仅在 Vosk 真机 POC 的内存、耗时和中文检索质量达标后，才能把 ASR 接入正式导入链路。
- 优先使用 Android 平台解码能力；新增格式支持必须以实际设备测试为准，不能仅凭 MIME 或库宣传声明支持。
- 模型下载必须使用 HTTPS、临时文件、大小和 SHA-256 校验、安全解压与原子移动；模型未就绪、网络失败、存储不足和损坏都必须可见且可重试。
- 临时 PCM/音频文件只能位于 `cacheDir`，必须在 `finally` 清理。不得把用户原始媒体复制进应用持久目录，除非产品明确授权该功能。

### 中文 RAG 质量

- 当前 `WhiteSpaceSplitter` 不能直接作为中文 OCR/ASR 的最终切分方案。改动前先编写中英文样本测试；改动后报告检索 Recall@5、首条命中率和延迟。
- 当前 `all-MiniLM-L6-V2` 不应被假定为中文检索合格。若计划替换嵌入模型，必须先获得用户确认，并提供模型许可证、APK/存储增量、Android Runtime 兼容性、重建和回滚方案。
- 不混用不同嵌入模型生成的向量。持久化模型版本，并在版本变化时显式重建索引或隔离索引。

### Android 与 Compose

- 耗时 I/O、OCR、解码、ASR 和嵌入不得阻塞主线程；避免在 ViewModel/Compose 中使用全局可变弹窗状态承载任务生命周期。
- 使用项目既有的 Koin 注解风格（例如 `@Single`、`@KoinViewModel`、`@Factory`）。不要凭参考项目引入 Dagger/Hilt，也不要假设需要 `@Inject`。
- 保持 `minSdk = 26`、`targetSdk = 35` 和 Java/Kotlin 21 设置，除非用户明确授权升级，并完成兼容性说明与验证。

## 4. 依赖、许可证与网络规则

- 版本集中在 `gradle/libs.versions.toml` 时优先使用版本目录；现有直接声明依赖应保持现有风格，避免同一依赖出现两套版本来源。
- 每项新增依赖必须在变更说明中列出坐标、用途、许可证、APK/ABI 影响和移除理由；只保留实现当前 Phase 所需的最小集合。
- 优先使用 Android 平台 API、AndroidX 和已有依赖。新增原生库前必须考虑 arm64-v8a、armeabi-v7a、x86_64 的包体积与 R8/ProGuard 规则。
- 运行时下载模型或内容属于用户可见网络行为：必须有明确触发、进度、取消/重试和完整性校验。不得在应用启动时静默下载。

## 5. 工作方式

1. 先读取本文件、实施契约和受影响代码，再说明本 Phase 的改动范围、风险与验证计划。
2. 先做最小可验证切片；不要同时开始 OCR、ASR、实体迁移和 UI 重写。
3. 保持工作区中用户已有改动。提交前先检查 `git status`，只处理自身创建或被明确授权修改的文件。
4. 遇到下列情况必须暂停并向用户说明选项，而不是自行扩大范围：
   - 新依赖、模型、权限、远程服务或许可证选择；
   - 嵌入模型替换、全库索引重建、数据迁移/清库；
   - 要求支持超出已验证范围的音频格式、时长或低端设备；
   - 需求与本文档、实施契约或现有行为冲突。
5. 更新与实现同步的文档；不要把设计假设写成已验证事实。

## 6. 验证与交付

每个 Phase 至少执行与改动相称的验证，并在最终答复中列出实际执行的命令与结果：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

- 若命令因环境、网络或既有问题无法运行，记录完整失败原因和已完成的替代验证；不要把未运行的验证称为通过。
- 涉及 ObjectBox、文件访问、OCR、音频或模型下载时，补充对应的单元测试/仪器测试或真机验证证据。
- 交付必须说明：修改文件、用户可见变化、依赖与权限变化、性能/包体积观察、已知限制和未完成风险。
- 除非用户明确要求，不自动创建分支、提交、推送或打开 PR。
