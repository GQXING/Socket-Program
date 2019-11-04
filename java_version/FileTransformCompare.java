package com.nio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileTransformCompare {
    private long transferFile(File source, File dest) throws IOException {
        long startTime = System.currentTimeMillis();

        if(!dest.exists())
            dest.createNewFile();

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(source));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest));

        //将数据从源读到目的文件
        byte[] bytes = new byte[1024];
        int len = 0;
        while ((len = bis.read(bytes))>0){
            bos.write(bytes, 0, len);
        }
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    private long transferFileFileWithNio(File source, File dest) throws IOException {
        long startTime = System.currentTimeMillis();

        if(!dest.exists())
            dest.createNewFile();

        RandomAccessFile sourceRAF = new RandomAccessFile(source, "rw");
        RandomAccessFile destRAF = new RandomAccessFile(dest, "rw");

        FileChannel readChannel = sourceRAF.getChannel();
        FileChannel writeChannel = destRAF.getChannel();

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024*1024); //1M缓冲区
        while (readChannel.read(byteBuffer) > 0){
            byteBuffer.flip();
            writeChannel.write(byteBuffer);
            byteBuffer.clear();
        }

        writeChannel.close();
        readChannel.close();
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    public static void main(String[] args) throws IOException {
        FileTransformCompare ftc = new FileTransformCompare();
//        File source = new File("F:\\apache-maven-3.6.2-bin.tar.gz");
//        File dest1 = new File("G:\\迅雷下载\\apache1.tar.gz");
//        File dest2 = new File("G:\\迅雷下载\\apache2.tar.gz");
        File source = new File("G:\\迅雷下载\\影视\\战争之王.BD1280超清国英双语中英双字.mp4");
        File dest1 = new File("G:\\迅雷下载\\test1.mp4");
        File dest2 = new File("G:\\迅雷下载\\test2.mp4");
        long time = ftc.transferFile(source, dest1);
        System.out.println("普通字节流时间: " + time);
        long timeNio = ftc.transferFileFileWithNio(source, dest2);
        System.out.println("NIO时间: " + timeNio);
    }
}
