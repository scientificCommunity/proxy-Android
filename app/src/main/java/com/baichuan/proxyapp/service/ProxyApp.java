package com.baichuan.proxyapp.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kun
 * @date 2020-06-11 17:54
 */
@Slf4j
public class ProxyApp {
    public static void main(String[] args) {
        new ProxyServer().init().start(9004, eventExecutors);
    }

    private static EventLoopGroup eventExecutors;

    public static void start() {
        eventExecutors = new NioEventLoopGroup();
        new Thread(() -> new ProxyServer().init().start(9004, eventExecutors)).start();
    }

    public static void stop() {
        eventExecutors.shutdownGracefully();
    }

    private ProxyInfo mInfo;

    // 设置公共成员常量值
    public static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }


    // getField只能获取类的public 字段.
    public static Object getFieldObject(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    public static Object getDeclaredFieldObject(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    public static void setDeclareFeildObject(Object obj, String name, Object object) {
        Field f = null;
        try {
            f = obj.getClass().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        f.setAccessible(true);
        try {
            f.set(obj, object);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // 获取当前的Wifi连接
    public static WifiConfiguration getCurrentWifiConfiguration(WifiManager wifiManager) {
        if (!wifiManager.isWifiEnabled())
            return null;
        @SuppressLint("MissingPermission") List<WifiConfiguration> configurationList = wifiManager.getConfiguredNetworks();
        WifiConfiguration configuration = null;
        int cur = wifiManager.getConnectionInfo().getNetworkId();
        // Log.d("当前wifi连接信息",wifiManager.getConnectionInfo().toString());
        for (int i = 0; i < configurationList.size(); ++i) {
            WifiConfiguration wifiConfiguration = configurationList.get(i);
            if (wifiConfiguration.networkId == cur)
                configuration = wifiConfiguration;
        }
        return configuration;
    }

    /**
     * API 21设置代理
     * android.net.IpConfiguration.ProxySettings
     * {@hide}
     */
    public static final void setHttpProxySystemProperty(String host, String port, String exclList,
                                                        Context context) {

        WifiConfiguration config;
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        config = getCurrentWifiConfiguration(wifiManager);
        if (exclList != null) exclList = exclList.replace(",", "|");
        if (host != null) {
            String syshost = System.setProperty("http.proxyHost", host);
            String syshost1 = System.setProperty("https.proxyHost", host);
        } else {
            System.clearProperty("http.proxyHost");
            System.clearProperty("https.proxyHost");
        }
        if (port != null) {
            System.setProperty("http.proxyPort", port);
            System.setProperty("https.proxyPort", port);
        } else {
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyPort");
        }
        if (exclList != null) {
            System.setProperty("http.nonProxyHosts", exclList);
            System.setProperty("https.nonProxyHosts", exclList);
        } else {
            System.clearProperty("http.nonProxyHosts");
            System.clearProperty("https.nonProxyHosts");
        }
       /* if (!Uri.EMPTY.equals(pacFileUrl)) {
            ProxySelector.setDefault(new PacProxySelector());
        } else {
            ProxySelector.setDefault(sDefaultProxySelector);
        }*/


        wifiManager.updateNetwork(config);

        wifiManager.disconnect();
        wifiManager.reconnect();
    }

    /**
     * 设置代理信息 exclList是添加不用代理的网址用的
     */
    public void setHttpProxySetting(Context context, String host, int port, List<String> exclList)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, NoSuchFieldException {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration config = getCurrentWifiConfiguration(wifiManager);
            //mInfo = ProxyInfo.buildDirectProxy(host, port);
        mInfo = ProxyInfo.buildPacProxy(Uri.parse("http://localhost:90041/railway/file/pac/download"));

        if (config != null) {
            Class<?> clazz = Class.forName("android.net.wifi.WifiConfiguration");
            Class<?> parmars = Class.forName("android.net.ProxyInfo");
            Method method = clazz.getMethod("setHttpProxy", parmars);
            method.invoke(config, mInfo);
            Object mIpConfiguration = getDeclaredFieldObject(config, "mIpConfiguration");

            //setEnumField(mIpConfiguration, "STATIC", "proxySettings");
            setDeclareFeildObject(config, "mIpConfiguration", mIpConfiguration);

            boolean result = wifiManager.enableNetwork(config.networkId, true);
            //save the settings
            int i = wifiManager.updateNetwork(config);

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCommand(" adb shell settings put global http_proxy localhost:9004");
            }*/
            //wifiManager.disconnect();
            //wifiManager.reconnect();
        }
    }

    /**
     * 取消代理设置
     */
    public void unSetHttpProxy(Context context)
            throws ClassNotFoundException, InvocationTargetException, IllegalAccessException,
            NoSuchFieldException, NoSuchMethodException {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration configuration = getCurrentWifiConfiguration(wifiManager);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mInfo = ProxyInfo.buildDirectProxy(null, 0);
        }
        if (configuration != null) {
            Class<?> clazz = Class.forName("android.net.wifi.WifiConfiguration");
            Class<?> params = Class.forName("android.net.ProxyInfo");
            Method method = clazz.getMethod("setHttpProxy", params);
            method.invoke(configuration, mInfo);

            Object mIpConfiguration = getDeclaredFieldObject(configuration, "mIpConfiguration");
            setEnumField(mIpConfiguration, "NONE", "proxySettings");
            setDeclareFeildObject(configuration, "mIpConfiguration", mIpConfiguration);

            //save the settings
            wifiManager.updateNetwork(configuration);
            wifiManager.disconnect();
            wifiManager.reconnect();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String runCommand(String command) {
        Scanner input = null;
        String result = "";
        Process process = null;
        try {
            try {
                process = Runtime.getRuntime().exec(command);
                //等待命令执行完成
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("执行命令发生异常，cause:" + e);
            }
            InputStream is = process.getInputStream();
            input = new Scanner(is);
            while (input.hasNextLine()) {
                result += input.nextLine() + "\n";
            }
            //加上命令本身，打印出来
            result = command + "\n" + result;
        } finally {
            if (input != null) {
                input.close();
            }
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }
}
