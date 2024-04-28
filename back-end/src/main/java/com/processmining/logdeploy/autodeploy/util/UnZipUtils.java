package com.processmining.logdeploy.autodeploy.util;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnZipUtils
{
    public static void unZipFiles(String zipPath, String descDir) throws IOException {
        unZipFiles(new File(zipPath), descDir);
    }

    public static void unZipFiles(File zipFile, String descDir) throws IOException {
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        ZipFile zip = new ZipFile(zipFile, Charset.forName("GBK"));
        for (Enumeration entries = zip.entries(); entries.hasMoreElements();) {
            ZipEntry entry = (ZipEntry)entries.nextElement();
            String zipEntryName = entry.getName();
            InputStream in = zip.getInputStream(entry);
            String outPath = (descDir+zipEntryName).replaceAll("\\*", "/");

            File file = new File(outPath.substring(0, outPath.lastIndexOf('/')));
            if (!file.exists()) {
                file.mkdirs();
            }

            if (new File(outPath).isDirectory()) {
                continue;
            }

            OutputStream out = new FileOutputStream(outPath);
            int len;
            byte[] buf1 = new byte[1024];
            while ((len = in.read(buf1)) > 0) {
                out.write(buf1, 0, len);
            }
            in.close();
            out.close();
        }
    }

    public static void main(String[] args) throws IOException {
        File zipFile = new File("E:\\srp\\testUnzip\\log4j-trunk.zip");
        String path = "E:\\srp\\testUnzip\\";
        unZipFiles(zipFile, path);
    }
}