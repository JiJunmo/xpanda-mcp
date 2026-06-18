package jmp0.abc;

import jmp0.abc.file.PandaFile;
import jmp0.abc.util.HapUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;

public class TestHap {
    @Test
    public void testHapParsing() {
        String hapPath = "/Users/jixiaokui/Documents/projects/hap/HwWallet/wallet-default.hap";
        File file = new File(hapPath);
        
        try {
            System.out.println("Parsing file: " + file.getAbsolutePath());
            InputStream is;
            if (file.getName().endsWith(".hap") || file.getName().endsWith(".zip")) {
                is = HapUtils.getAbcInputStreamFromHap(file);
                System.out.println("Extracted ABC from HAP successfully.");
            } else {
                is = new FileInputStream(file);
            }
            
            PandaFile pandaFile = new PandaFile(is);
            System.out.println("PandaFile parsed successfully.");
            System.out.println("Number of Region/Index Headers: " + pandaFile.getIndexHeaders().length);
            
            int classCount = 0;
            for (int i = 0; i < pandaFile.getIndexHeaders().length; i++) {
                classCount += pandaFile.getIndexHeaders()[i].getPandaClasses().length;
            }
            System.out.println("Total classes found: " + classCount);
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test failed", e);
        }
    }
}
