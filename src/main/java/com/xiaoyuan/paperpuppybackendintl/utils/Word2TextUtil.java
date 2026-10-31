package com.xiaoyuan.paperpuppybackendintl.utils;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import com.aspose.words.TxtSaveOptions;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;



public final class Word2TextUtil {
    public static String convertWordToText(File file, String out) {
        hack();
        if (file == null) throw new IllegalArgumentException("文件不能为空");
        if (!file.exists() || !file.isFile()) throw new IllegalArgumentException("文件不存在或不是一个有效的文件: " + file);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            Document doc = new Document(in);
            return doc.getText();
        } catch (IOException e) {
            throw new RuntimeException("转换word时出现io错误: " + file, e);
        } catch (Exception e) {
            throw new RuntimeException("aspose解析异常 " + file, e);
        }
    }
    private static void hack() {
        try {
            Class<?> zzodClass = Class.forName("com.aspose.words.zzod");
            Constructor<?> constructors = zzodClass.getDeclaredConstructors()[0];
            constructors.setAccessible(true);
            Object instance = constructors.newInstance(null, null);
            Field zzWws = zzodClass.getDeclaredField("zzWws");
            zzWws.setAccessible(true);
            zzWws.set(instance, 1);
            Field zzVZC = zzodClass.getDeclaredField("zzVZC");
            zzVZC.setAccessible(true);
            zzVZC.set(instance, 1);

            Class<?> zz83Class = Class.forName("com.aspose.words.zz83");
            constructors.setAccessible(true);
            constructors.newInstance(null, null);

            Field zzZY4 = zz83Class.getDeclaredField("zzZY4");
            zzZY4.setAccessible(true);
            ArrayList<Object> zzwPValue = new ArrayList<>();
            zzwPValue.add(instance);
            zzZY4.set(null, zzwPValue);

            Class<?> zzXuRClass = Class.forName("com.aspose.words.zzXuR");
            Field zzWE8 = zzXuRClass.getDeclaredField("zzWE8");
            zzWE8.setAccessible(true);
            zzWE8.set(null, 128);
            Field zzZKj = zzXuRClass.getDeclaredField("zzZKj");
            zzZKj.setAccessible(true);
            zzZKj.set(null, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}