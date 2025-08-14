import jmp0.abc.PandaParseException;
import jmp0.abc.decompiler.PandaDecompiler;
import jmp0.abc.disasm.block.PandaIRCFG;
import jmp0.abc.util.DrawCFGTool;
import jmp0.abc.file.PandaFile;
import jmp0.abc.file.clazz.PandaClass;
import jmp0.abc.file.desc.IndexHeader;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

/**
 * @Author: jmp00
 * @Email: jmp0@qq.com
 */
public class MainTest {
    @Test
    public void test() throws IOException, PandaParseException, InterruptedException {
        InputStream inputStream = this.getClass().getResource("md5.abc").openStream();
        PandaFile pandaFile = new PandaFile(inputStream);
//        pandaFile.parseAssembly();
        File outDir = new File("build/core");
        PandaDecompiler disCompiler = new PandaDecompiler();
        for (IndexHeader indexHeader : pandaFile.getIndexHeaders()) {
            for (PandaClass pandaClass : indexHeader.getPandaClasses()) {
                System.out.println(pandaClass.getName());
                if (pandaClass.getName().getContent().startsWith("")){
                    String out = disCompiler.decompileClass(pandaClass);
                    System.out.println(out);
//                    PandaDecompiler.saveFile(outDir,pandaClass,out);
                }
            }
        }

    }

}
