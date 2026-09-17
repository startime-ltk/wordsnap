# 词拍 WordSnap

一个**完全离线**的安卓识屏答题引擎，用来学习和研究「文字识别 → 题干解析 → 多策略答案匹配」这条算法链路。

> 用途说明：本项目的目标是**研究算法**（OCR、字符串相似度、多策略加权融合、置信度估计），
> 不是用来在联网人人对战里开挂。自动点击功能默认关闭，且必须手动配置白名单才会生效。

---

## 一、它做什么

| 环节 | 实现 |
| --- | --- |
| 1. 截屏 | `MediaProjection` + `ImageReader` + `VirtualDisplay`，一次授权后可持续取帧 |
| 2. 文字识别 | Google ML Kit **端上离线** OCR（`text-recognition:16.0.1`，模型打包进 APK，**不联网**） |
| 3. 题干解析 | 标签式（`A. xxx`）+ 圈号式（`① xxx`）+ 按语种启发式切分 |
| 4. 答案匹配 | **四路策略各自打分 → 加权融合**（见下） |
| 5. 结果输出 | 顶部悬浮卡片显示答案 + 每个策略的原始分与置信度 |
| 6. 自动作答（可选，默认关） | `AccessibilityService` 按白名单点选项 |

## 二、四路匹配策略

| 策略 | 类 | 权重 | 思路 |
| --- | --- | --- | --- |
| 词典直查 | `DictMatchStrategy` | 0.45 | 题干词进词库查释义，与选项做相似度 |
| 反查释义 | `ReverseLookupStrategy` | 0.25 | 中文题干 → 在所有词的释义里反查 |
| 词干模糊 | `StemFuzzyStrategy` | 0.20 | 词形还原（复数 / -ed / -ing / -er / -est / -ly）后再查 |
| 排除法 | `EliminationStrategy` | 0.10 | 不用词典，算选项之间的「组内亲和度」，离群的排除 |

**融合公式**：`final[i] = Σ(raw[k][i] × w[k]) / Σw[k]`

**置信度**：`conf = 0.6 × 最高分 + 0.4 × (最高分 − 次高分)`

**安全阀（重要）**：每个策略上报一个 `recognized` 标记，表示「这次有没有真在词典里找到证据」。
若四路全都没找到证据（八成是 OCR 认错了字），置信度直接 ×0.5，让它跨不过 0.35 的自动作答门槛——
宁可弃答，也不「自信地猜错」。

**相似度算法**：英文用编辑距离（滚动数组，O(min(n,m)) 空间），中文用二元组 Jaccard，
中英混合按 0.7 / 0.3 加权。

## 三、目录结构

```
app/src/main/java/com/litukang/wordsnap/
├── MainActivity.java              入口
├── capture/
│   ├── ScreenCaptureService.java  截屏服务（画词）
│   └── QuizCaptureService.java    实时答题链路：截屏→OCR→解析→求解→悬浮显示
├── ocr/WordScanner.java           ML Kit 封装（识别整屏 / 只要文本行）
├── solver/                        ★ 算法核心
│   ├── Algo.java                  相似度工具箱（编辑距离 / Jaccard / 词形）
│   ├── Question.java              题干 + 选项模型
│   ├── QuestionParser.java        题干解析
│   ├── Strategy.java              策略基类（含 recognized 标记）
│   ├── DictMatchStrategy.java     策略①
│   ├── ReverseLookupStrategy.java 策略②
│   ├── StemFuzzyStrategy.java     策略③
│   ├── EliminationStrategy.java   策略④
│   ├── AnswerEngine.java          融合 + 置信度 + 安全阀
│   └── Answer.java                结果模型（含 explain() 可读打分明细）
├── service/AutoAnswerService.java 无障碍自动作答（三道闸门）
├── data/
│   ├── WordRepository.java        cet4.csv 载入 + 词形变体
│   ├── WordBookStore.java         生词本
│   └── AllowList.java             自动作答开关 + 包名白名单
└── ui/
    ├── SolverActivity.java        ★ 算法实验台（输入题干，看每路策略打分明细）
    ├── QuizOverlay.java           顶部悬浮答案卡
    ├── TrainingActivity.java      计时训练
    ├── WordBookActivity.java      生词本
    └── FloatingResult.java / ResultActivity.java / util/Ui.java

app/src/main/assets/cet4.csv      四级词库（含词形变体，145 KB）
tools/                            离线评测脚本（Python 复刻 Java 算法，无需真机）
```

## 四、离线评测（不用手机就能测准确率）

`tools/sim_check.py` 用 Python 复刻了整个 Java 求解器，直接读 `app/src/main/assets/cet4.csv`，
随机造 4 选 1 题目并统计准确率。

```bash
python tools/sim_check.py          # 基础
python tools/sim_check.py hard     # 干扰项挑语义最接近的（更难）
python tools/sim_check.py noise    # 模拟 30% OCR 认错字
python tools/sim_check.py short    # 释义只取第一义项
```

**实测结果（400 题）**：

| 场景 | 整体正确率 | 已作答中的正确率 | 弃答 | 答错 |
| --- | --- | --- | --- | --- |
| OCR 干净 + 难干扰项 | 98.2% | 99.0% | 少 | 少 |
| OCR 30% 错字 | 66.5% | 99.3% | 132 | 2 |

**结论**：瓶颈在 **OCR 识别准确率**，不在匹配算法。加了安全阀后，噪声场景下的错答从 27 题降到 2 题。

## 五、自己编译

环境要求（本机实测可用组合，别乱升）：

| 项目 | 版本 |
| --- | --- |
| JDK | **21**（JDK 25 会编译失败） |
| Gradle | 8.9 |
| AGP | 8.7.3 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| NDK abiFilters | **arm64-v8a**（只打这一个架构，包才小） |

> ⚠️ **本仓库没有 `gradlew`**（wrapper 的 jar 和脚本没提交）。
> 请用本机已装好的 Gradle 8.9 直接编，**不要**去跑 `gradlew`——
> wrapper 会联网下载 Gradle，国内常常直接超时。

```powershell
$proj = "<项目绝对路径>"
Set-Location $proj
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
& "F:\_tools\gradle-8.9\bin\gradle.bat" :app:assembleRelease --no-daemon
# 产物：app\build\outputs\apk\release\app-release.apk

# local.properties 必须存在，内容：
#   sdk.dir=D\:\\Android\\Sdk
```

另外两个必须的配置（已写在 `gradle.properties` 里，换机器时别丢）：

```properties
android.overridePathCheck=true            # 工程路径含中文必须放行
android.suppressUnsupportedCompileSdk=36  # AGP 8.7.3 官方只支持到 35
```

**签名**：release 用项目里的 `wordsnap-release.jks`（别名 `wordsnap`）。
**这张证书绝不能换**——换了以后用户必须卸载重装，不能覆盖升级。
它已被 `.gitignore` 排除，**不会**出现在本仓库里；本地留一份、`F:\_tools\keystore\` 再备份一份。

**覆盖安装三原则**：同一张证书 / 每次更新必升 `versionCode` / 永远不发 debug 包。

## 六、下载安装包

安装包**不在本仓库**，全部统一放在 → **https://github.com/startime-ltk/apk**

## 七、版本

| 版本 | 说明 |
| --- | --- |
| v1.2 | 识屏答题引擎（四策略融合 + 安全阀 + 算法实验台）；14.92 MB，versionCode 3 |
| v1.1 | 截屏识词、生词本、计时训练；首次 release 瘦身（44 MB → 15 MB） |
