[English README](README.en.md)
## xpanda
这是一款鸿蒙的反编译器。
<br>目前支持到`13.0.1.0`,因为个人原因没有继续维护，现在将其开源，目前有很多不完善的地方，有能力的帮忙进一步完善，一起为鸿蒙安全做出贡献。
![展示gui](assert/example.png)
<br>下面是我想的，可以做的一些事:

## prepare to use
需要node环境，请从[nodejs](https://nodejs.org)下载，并添加到环境变量。
<br>建议使用[Graalvm JDK](https://www.graalvm.org/) 版本17以上。

## something can do:
1. 完善测试用例，保证反编译器质量。
2. 想进一步增强结构化分析,输出更准确的结果。
3. 基于[react-compiler](https://github.com/facebook/react/tree/main/compiler)的[xpanda-optimizer](https://github.com/asmjmp0/xpanda-optimizer)，作为代码优化器，获得更快更好的优化效果。
4. 一个勉强够用的ui。
5. 通过asm库静态修改字节码，可以修改回编译，以便做安全研究。
6. 是否能支持其他js-base虚拟机字节码？做到反编译的。

## Thanks
[Coober-Ding](https://github.com/Coober-Ding)是一个不错的全栈工程师，感谢他在[api](api)的编写中做的贡献。email:287135737@qq.com