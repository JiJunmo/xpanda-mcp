# xpanda-mcp (鸿蒙反编译 MCP 服务)

`xpanda-mcp` 是一个基于鸿蒙反编译器项目 [xpanda](https://github.com/asmjmp0/xpanda) 扩展构建的 **Model Context Protocol (MCP)** 服务。

该项目允许大语言模型（如 Claude, GPT）直接调用鸿蒙/ArkTS 反编译与分析引擎，对 `.hap`、`.hsp`、`.app` 包以及 raw `.abc` 二二进制文件进行自动化深度的反编译、交叉引用分析、元数据检索以及全局文本与符号搜索。

---

## 功能特性

- **HAP 自动提取与解析**：直接加载 `.hap` 包，自动定位并解压其中的核心 `.abc` (ArkTS Bytecode) 二进制文件。
- **高保真反编译与 Smali 还原**：支持将字节码一键反编译为伪 JavaScript (Fake JS) 代码，或者输出易读的 Smali 汇编格式。
- **智能交叉引用 (Xrefs)**：
  - **Class/Method/Field Xrefs**：自动建立全局调用图与数据流追踪。
  - **ArkTS 动态特征感知**：支持对 `ldlazymodulevar`、基于字面量原型的动态调用（启发式算法）进行跟踪，解决传统静态分析工具在此类环境下的断层问题。
- **继承链推导**：基于静态声明与字符串初始化特征，还原类与 Ability 的继承层次（如 `BaseUiAbility` -> `MainAbility`）。
- **全局搜索引擎**：
  - **符号搜索**：支持通过正则表达式在全球范围内查找类名、方法名或字段名。
  - **文本搜索**：支持在全局字符串池（String Pool）及反编译字节码段中进行极速匹配。

---

## MCP 工具列表 (Tools)

本服务共向大语言模型暴露了 **14** 个专业的逆向分析工具：

### 1. 归档与项目加载 (Archive & Project)
* `open_archive(path)`: 加载指定的 HAP/HSP 或 ABC 文件，在后台自动完成多达数万个类的全局索引构建与解包。
* `get_project_tree()`: 返回该项目的包结构树，便于了解模块分布与类分布。
* `get_manifest_info()`: 解析并格式化包内的 `module.json5` 或 `app.json5` 配置。

### 2. 反编译与源码查看 (Decompilation & Bytecode)
* `get_class_source(className)`: 获取指定类（包含所有方法）的反编译伪 JS 源码。
* `get_method_source(className, methodName)`: 获取指定方法的反编译伪 JS 源码。
* `get_bytecode(className, methodName)`: 获取指定方法的原始字节码指令（Smali 汇编格式）。
* `get_class_info(className)`: 快速列出类内定义的所有方法及其参数个数，不触发反编译。

### 3. 全局分析与引用 (Global Analysis)
* `get_xrefs_to_class(className)`: 查找有哪些类或方法引用了该类。
* `get_xrefs_to_method(className, methodName)`: 查找该方法的调用者（含静态调用与动态字面量启发式匹配）。
* `get_xrefs_to_field(className, fieldName)`: 查找有哪些方法读取或修改了该字段。
* `get_inheritance_hierarchy(className)`: 还原该类的继承链（父类、子类列表）。

### 4. 搜索引擎 (Search Engine)
* `search_classes(keyword)`: 快速模糊匹配包含指定关键字的类名。
* `search_symbol(namePattern, type)`: 按照正则表达式在全局搜索匹配的类名、方法名、或字段名（支持 `class`、`method`、`field` 或 `all` 过滤）。
* `search_text(query, searchIn)`: 全局文本特征搜索（可选择在 `string` 字符串字面量池中搜索、在 `code` 字节码指令文本中搜索，或全部搜索）。

---

## 运行环境与要求

1. **Node.js**：请从 [nodejs.org](https://nodejs.org) 下载，并确保 `node` 命令已添加到系统环境变量中。
2. **Java JDK 17+**：推荐使用 [GraalVM JDK](https://www.graalvm.org/)。

---

## 编译与部署指南

### 1. 编译项目
在项目根目录下，使用 Gradle 包装器进行编译和分发打包：
```bash
# 编译项目（跳过测试）
./gradlew :mcp:build -x test

# 将 MCP 服务安装到本地分发目录
./gradlew :mcp:installDist
```
编译完成后，可执行程序将生成在：
`mcp/build/install/mcp/bin/mcp` (macOS/Linux) 或 `mcp.bat` (Windows)

### 2. 在 MCP 客户端中配置

你可以将此服务注册到 Claude Desktop、Cursor 或其它兼容 MCP 的客户端中。

以 **Claude Desktop** 为例，修改 `claude_desktop_config.json`（通常位于 `~/Library/Application Support/Claude/claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "xpanda-mcp": {
      "command": "/绝对路径/to/xpanda-mcp/mcp/build/install/mcp/bin/mcp",
      "args": []
    }
  }
}
```

重新启动 Claude Desktop，即可直接通过自然语言命令大模型对鸿蒙应用进行逆向破解、漏洞扫描与逻辑链分析。

---

## 引用与感谢

- 底层核心反编译引擎源自开源项目：[xpanda](https://github.com/asmjmp0/xpanda)（作者: asmjmp0）。
- 本 MCP 项目对此反编译引擎进行了面向 AI Agent 交互的重构，并针对鸿蒙组件交互痛点（如 `ldlazymodulevar`）强化了分析能力。