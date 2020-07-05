package com.baichuan.proxyapp.utils;

/**
 * @author tangkun
 * @date 2019-09-24
 */
public class ThreadUtils {

    public static void sleep(long timeMillions) {
        try {
            Thread.sleep(timeMillions);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
