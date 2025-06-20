# OneTV 2.0 贡献指南

感谢您考虑为OneTV 2.0项目做出贡献！这份文档将帮助您了解贡献流程。

## 目录

- [行为准则](#行为准则)
- [如何贡献](#如何贡献)
  - [报告Bug](#报告bug)
  - [功能请求](#功能请求)
  - [代码贡献](#代码贡献)
- [开发环境设置](#开发环境设置)
- [代码规范](#代码规范)
- [提交PR流程](#提交pr流程)
- [版本控制规范](#版本控制规范)

## 行为准则

本项目采用[贡献者公约](https://www.contributor-covenant.org/zh-cn/version/2/0/code_of_conduct/)。参与本项目，即表示您同意遵守其条款。

## 如何贡献

### 报告Bug

如果您发现了Bug，请通过GitHub Issues提交报告，并包含以下信息：

1. 问题的简要描述
2. 重现步骤
3. 预期行为
4. 实际行为
5. 截图（如适用）
6. 设备信息（型号、Android版本等）
7. 应用版本

使用以下模板创建Issue：

```markdown
**问题描述**
简要描述问题

**重现步骤**
1. 前往 '...'
2. 点击 '....'
3. 滚动到 '....'
4. 出现错误

**预期行为**
对预期应该发生的事情的清晰描述

**截图**
如果适用，添加截图

**设备信息**
- 设备: [例如 小米11]
- OS: [例如 Android 12]
- 应用版本: [例如 2.0.0]

**附加信息**
任何其他有关问题的信息
```

### 功能请求

如果您想要提出新功能或改进建议，请使用GitHub Issues并使用"功能请求"标签。请详细描述您希望的功能，以及它将如何改善应用体验。

### 代码贡献

1. Fork项目仓库
2. 创建新的分支（`git checkout -b feature/amazing-feature`）
3. 提交您的更改（`git commit -m 'Add some amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 打开一个Pull Request

## 开发环境设置

1. **环境要求**

   - Android Studio 2023.3.1 或更高版本
   - JDK 17+
   - Gradle 8.6+
   - Kotlin 2.1.10
2. **克隆仓库**

   ```bash
   git clone https://github.com/your-username/OneTV_Supabase.git
   cd OneTV_Supabase
   ```
3. **配置本地环境**

   - 见应用内
   - 见应用内:

   ```properties
   见应用内
   ```
4. **同步Gradle项目**

   - 在Android Studio中打开项目
   - 等待Gradle同步完成

## 代码规范

### Kotlin代码风格

- 遵循[Kotlin编码约定](https://kotlinlang.org/docs/coding-conventions.html)
- 使用4个空格缩进
- 文件最大长度不超过100个字符
- 类名使用PascalCase
- 函数和变量名使用camelCase
- 常量使用UPPER_SNAKE_CASE

### 注释规范

- 为公共API添加KDoc注释
- 复杂的业务逻辑应添加适当的注释
- 使用中文注释，确保清晰表达意图

### 提交信息规范

提交信息应遵循以下格式：

```
<类型>: <描述>

[可选的详细描述]

[可选的问题引用]
```

类型包括：

- `feat`: 新功能
- `fix`: 错误修复
- `docs`: 文档更改
- `style`: 格式更改（不影响代码功能）
- `refactor`: 代码重构
- `perf`: 性能优化
- `test`: 添加或修改测试
- `chore`: 构建过程或辅助工具的变动

例如：

```
feat: 添加用户自定义主题功能

实现了允许用户自定义应用主题颜色的功能。
用户可以从预设的颜色方案中选择，或创建自己的自定义颜色。

Closes #123
```

## 提交PR流程

1. **确保您的分支是最新的**

   ```bash
   git remote add upstream https://github.com/original-owner/OneTV_Supabase.git
   git fetch upstream
   git checkout main
   git merge upstream/main
   git checkout your-branch
   git rebase main
   ```
2. **确保代码通过所有测试**

   - 运行单元测试
   - 运行UI测试（如适用）
3. **创建Pull Request**

   - 前往GitHub仓库页面
   - 点击"New pull request"
   - 选择您的分支和目标分支
   - 填写PR模板中的相关信息
   - 标记相关的Issues
4. **PR审核流程**

   - 至少需要一位维护者批准
   - 所有CI检查必须通过
   - 可能需要根据反馈进行修改

## 版本控制规范

本项目使用[语义化版本控制](https://semver.org/lang/zh-CN/)：

- **主版本号**：当进行不兼容的API更改时
- **次版本号**：当添加功能但保持向后兼容时
- **修订号**：当进行向后兼容的问题修复时

## 发布流程

1. 从 `develop`分支创建 `release/vX.Y.Z`分支
2. 在该分支上进行版本相关的最终调整
3. 合并到 `main`分支并标记发布
4. 将发布更改合并回 `develop`分支

## 感谢

再次感谢您考虑为OneTV 2.0项目做出贡献！您的贡献将帮助我们打造更好的应用体验。
