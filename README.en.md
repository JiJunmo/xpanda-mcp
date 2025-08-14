[中文 README](README.md)
<br>translated by DeepL
## xpanda
This is a Hongmeng decompiler.
<br>Currently support to `13.0.1.0`, because of personal reasons did not continue to maintain, now open source, there are many imperfections, the ability to help further improve, together for the Hongmeng security contributions.
![showgui](assert/example.png)
<br>Here are some things I think we can do.

## prepare to use
A node environment is required, download it from [nodejs](https://nodejs.org) and add it to the environment variables.
<br>It is recommended to use [Graalvm JDK](https://www.graalvm.org/) version 17 or above.

## something can do.
1. improve the test cases to ensure the quality of the decompiler.
2. want to further enhance the structured analysis, output more accurate results. 3. based on [recompiler].
3. Based on [xpanda-optimizer](https://github.com/asmjmp0/xpanda-optimizer) of [react-compiler](https://github.com/facebook/react/tree/main/compiler). optimizer), as a code optimizer to get faster and better optimization results.
4. a barely adequate ui.
5. static modification of bytecode via asm library, which can be modified back to compilation for doing security research.
6. can support other js-base virtual machine bytecode? Do decompile it.

## Thanks
[Coober-Ding](https://github.com/Coober-Ding) is a nice full-stack engineer, thanks for his contribution in writing [api](api). email:287135737@qq.com