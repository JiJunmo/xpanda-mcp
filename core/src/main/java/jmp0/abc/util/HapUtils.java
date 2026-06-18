package jmp0.abc.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class HapUtils {
    public static InputStream getAbcInputStreamFromHap(File hapFile) throws IOException {
        try (ZipFile zipFile = new ZipFile(hapFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                // We look for any .abc file. In HarmonyOS, it's typically ets/modules.abc
                if (entry.getName().endsWith(".abc")) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        byte[] data = is.readAllBytes();
                        return new ByteArrayInputStream(data);
                    }
                }
            }
        }
        throw new FileNotFoundException("No .abc file found in .hap archive: " + hapFile.getName());
    }

    public static String getManifestFromHap(File hapFile, String fileName) throws IOException {
        try (ZipFile zipFile = new ZipFile(hapFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                // Match exact file name or file name within a directory (e.g., module.json or ets/module.json)
                if (entry.getName().equals(fileName) || entry.getName().endsWith("/" + fileName)) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        return new String(is.readAllBytes(), "UTF-8");
                    }
                }
            }
        }
        return null;
    }
}
