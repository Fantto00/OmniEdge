# OmniEdge 简体中文 UI 改造 ToDo 与 Agent 行为边界

## 目标

将 OmniEdge 的用户界面改为简体中文，同时保持现有模型、RAG 检索、文件导入、OCR、ASR、模型下载和页面调度的行为完全不变。

本任务是**表现层中文化**，不是架构重构、功能开发或算法优化任务。产品名称默认显示为 `OmniEdge`；模型名、文件名、URL、API Key、用户输入、用户文档内容和 LLM 输出不翻译。

> [!IMPORTANT]
> `app/src/main/res/values/strings.xml` 中的检索提示词用于构造 LLM 输入，不属于 UI 文案。不得翻译、改写、移动或重命名它；应标注为 `translatable="false"`，防止后续中文化误改其语义。

## 不变量

以下项目必须在开始前、每个阶段结束后和交付前保持不变：

- 嵌入模型、模型版本、模型列表、模型下载地址、模型文件、模型加载逻辑。
- RAG prompt、检索 Top-K、向量生成、ObjectBox 初始化、写入和删除逻辑。
- Reader、`ContentIngestionUseCase`、OCR、扫描 PDF、ASR、模型下载/校验/解压逻辑。
- 页面路由、导航目标、事件类型、UI State 字段、协程 `Job`、`Dispatchers`、取消和重试语义。
- Android 权限、依赖、SDK 版本、Koin 注入、Manifest、构建配置和应用包名。
- 现有 PDF、DOCX、Markdown、纯文本、图片 OCR 和音频流程的用户可见功能顺序。

不能通过“顺带修复”“清理代码”“优化体验”的理由修改上述项目。

## 允许改动范围

除本文档外，Agent 只能修改下列路径中的文件：

```text
app/src/main/res/values*/
app/src/main/java/com/ml/shubham0204/docqa/ui/components/
app/src/main/java/com/ml/shubham0204/docqa/ui/screens/
app/src/main/java/com/ml/shubham0204/docqa/ui/theme/
app/src/test/java/com/ml/shubham0204/docqa/ui/
app/src/androidTest/java/com/ml/shubham0204/docqa/ui/
docs/
```

`ui/screens/*/*ViewModel.kt` 虽位于 UI 包，但属于高风险边界：只允许替换或映射**已经展示给用户的文案**。不得改变任何分支、事件、StateFlow、协程、任务调用、异常控制流、导航或数据库/网络调用。

## 禁止改动

- 不修改 `domain/`、`data/`、`di/`、`MainActivity.kt`、`AndroidManifest.xml`、`build.gradle.kts`、`gradle/libs.versions.toml`。
- 不添加依赖、权限、字体文件、图片资源、网络请求、遥测或远程服务。
- 不把 UI 中文化扩展为嵌入模型替换、中文检索优化、索引重建、模型下载重做或多模态功能重构。
- 不修改 `prompt_1` 的文本、占位符或调用方式。
- 不修改模型名称、模型描述所对应的模型能力、下载 URL、文件名或 Hugging Face Token 逻辑。
- 不替换文件选择器、导入器、数据库事务、OCR/ASR 任务或它们的回调时序。
- 不因深色模式、布局、弹窗状态或代码风格问题重构全局对话框、导航或 ViewModel 架构。
- 不创建分支、提交、推送、清理工作区或覆盖用户已有改动。

## 实施清单

### 0. 开工门禁

- [ ] 运行 `git status --short`，确认并记录已有改动；不得覆盖它们。
- [ ] 运行 `git diff --name-only`，建立本任务变更基线。
- [ ] 列出所有用户可见英文：标题、按钮、输入提示、空状态、Toast、对话框、下载/导入状态、错误提示和 `contentDescription`。
- [ ] 将字符串分为三类：可翻译 UI、不可翻译技术标识、不可触碰的 LLM prompt。
- [ ] 确认默认目标语言为简体中文；若提出应用内语言切换或英文回退，暂停并请求单独授权。

### 1. 资源与术语

- [ ] 在 `values/` 中建立按页面分组的中文资源：`screen_chat_*`、`screen_docs_*`、`screen_models_*`、`screen_credentials_*`、`action_*`、`status_*`、`error_*`、`a11y_*`。
- [ ] 对带变量的文案使用格式参数，例如 `%1$d%%`、`%1$d/%2$d`；不要拼接中英文句子。
- [ ] 仅在确有数量语义时使用 `plurals.xml`。
- [ ] 保留协议名和产品名：`Gemini`、`Hugging Face`、`OCR`、`ASR`、`PDF`、`DOCX`、`Markdown`、`URL`、`API Key`、模型名称。
- [ ] 将 `prompt_1` 标为不可翻译，不改动其实际内容。

### 2. Compose 页面中文化

- [ ] 聊天页：标题、空状态、输入提示、回答/参考资料、分享、发送、错误对话框和无障碍描述全部中文化。
- [ ] 文档页：标题、来源类型、索引状态、删除确认、文档类型选择、URL 导入、详情、分享、图片导入、语音 POC 与音频导入入口全部中文化。
- [ ] 凭据页：标题、两个输入字段、保存操作、保存结果和无障碍描述全部中文化。
- [ ] 本地模型页：标题、状态标签、下载/加载操作、下载进度、预检错误和无障碍描述全部中文化；模型名称及 URL 不变。
- [ ] 共享组件只接受已解析的显示文本或资源文本；不改变其显示时机、可取消性或回调行为。
- [ ] 使用 `stringResource()` 读取 Compose 内的静态 UI 文案；不在 Composable 中写新的用户可见硬编码文本。

### 3. ViewModel 展示文案的最小处理

- [ ] 仅修改当前已展示给用户的 Toast、对话框标题/按钮、状态描述和通用失败提示。
- [ ] 保持已有事件、状态字段类型和函数签名不变。
- [ ] 对来自领域层的英文进度文本，仅在 `ui/screens/docs/` 内增加纯展示映射；映射不得调用领域层、数据库、网络或创建协程。
- [ ] 未识别的底层异常不得直接展示 `exception.message`；显示固定中文失败提示，同时保留原有异常流程。若这需要改变异常处理或任务控制流，立即停止并请求授权。
- [ ] 不为了资源化而向 ViewModel 注入 `Context`、修改 Koin 配置或变更 UI State 结构；遇到此类需求，记录为后续可选工作，不在本任务中实现。

### 4. 仅限视觉层的适配

- [ ] 允许在 `ui/theme/` 和 Compose modifier 中调整中文行高、字间距、换行、间距和语义色，以避免截断或深色模式不可读。
- [ ] 不引入新字体、图标、图片或品牌资源。
- [ ] 不改变信息架构、页面入口、按钮数量、按钮启用条件或操作顺序。
- [ ] 在 320dp 宽度、字体缩放 1.3 倍、浅色/深色主题下检查长中文文本不重叠、不裁切且仍可点击。

## Agent 停止条件

出现任意一种情况时，Agent 必须停止修改并向用户报告，不得自行扩大范围：

1. 中文化需要改动 `domain/`、`data/`、`di/`、构建配置、Manifest 或导航。
2. 中文化需要修改模型、prompt、检索、嵌入、数据库、OCR、ASR、下载或任务调度代码。
3. 发现现有英文来自第三方库、系统文件选择器、模型输出、用户内容或未分类的异常，且无法仅用 UI 映射处理。
4. 需要新增依赖、权限、字体、远程翻译服务或应用内语言切换。
5. 测试失败且原因可能不是 UI 文案/布局改动。

## 验收与交付

### 变更边界验证

```powershell
git diff --name-only
git diff -- app/src/main/java/com/ml/shubham0204/docqa/domain
git diff -- app/src/main/java/com/ml/shubham0204/docqa/data
git diff -- app/src/main/java/com/ml/shubham0204/docqa/di
git diff -- app/src/main/java/com/ml/shubham0204/docqa/MainActivity.kt
git diff -- app/build.gradle.kts app/src/main/AndroidManifest.xml gradle/libs.versions.toml
```

除 `docs/ui-chinese-refactor-todo.md` 外，第一条命令的结果必须完全属于“允许改动范围”；其余命令必须没有输出。

### 构建与回归验证

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

- [ ] 三条命令逐条记录实际结果；未运行或失败不得写为通过。
- [ ] 手动回归聊天、本地模型加载/下载、Gemini 提示、文档导入、图片 OCR、音频 POC、音频导入和返回导航。
- [ ] 验证所有触发这些流程的条件、加载状态、取消操作和页面跳转与改造前一致。
- [ ] 搜索并复核 UI 包中的用户可见英文，排除 URL、MIME、日志、模型标识、文件扩展名和不可翻译 prompt。
- [ ] 最终交付列出：实际修改文件、中文化范围、未处理的系统/第三方文案、验证命令及其结果。

> [!CAUTION]
> 本文档的优先级高于“顺手优化”的工程判断。只要功能边界与 UI 中文化发生冲突，优先保护现有模型质量和页面调度，并请求用户决定下一步。
