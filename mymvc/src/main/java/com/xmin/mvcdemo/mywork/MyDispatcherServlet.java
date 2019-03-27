package com.xmin.mvcdemo.mywork;

import com.xmin.mvcdemo.mywork.annotation.MyAutowired;
import com.xmin.mvcdemo.mywork.annotation.MyController;
import com.xmin.mvcdemo.mywork.annotation.MyRequestMapping;
import com.xmin.mvcdemo.mywork.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * Created by lixiaomin on 2019/3/26.
 */
public class MyDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private String scanPackage = "com.xmin.mvcdemo.mvc";
    //保存Contrller中所有Mapping的对应关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();
    //IOC容器，实例化的类
    private Map<String, Object> ioc = new HashMap<String, Object>();
    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<String>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String requestURI = req.getRequestURI();
        if (handlerMapping.containsKey(requestURI)) {
            Method method = handlerMapping.get(requestURI);
            try {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                if (method.getParameterTypes().length > 0) {
                    resp.getWriter().write(new String("暂不支持参数".getBytes(),"UTF-8"));
                    return;
                }
                String beanName = toLowerFirstCase(method.getDeclaringClass().getName());
                Object result = method.invoke(ioc.get(beanName), new Object[]{});
                resp.getWriter().write(String.valueOf(result));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
//        resp.getWriter().write("aaaaaaaaaaaaa");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        //1加载配置
        initConfig(contextConfigLocation);
        //2、加载文件
        initPackage(contextConfig.getProperty("scanPackage"));
        //3、IOC容器，初始化所有相关的类的实例
        initIoc();
        //4、完成依赖注入
        initAutowired();
        //5、handler
        initHandlerMapping();

        System.out.println("MyDispatcherServlet finsh");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            //必须有MyController
            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }

            //Controller url,可以为空
            String ControllerUrl = "";
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                ControllerUrl = clazz.getAnnotation(MyRequestMapping.class).value();
            }

            //方法url
            String methodUrl = "";
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }
                methodUrl = method.getAnnotation(MyRequestMapping.class).value();
                String url = ("/" + ControllerUrl + "/" + methodUrl).replaceAll("/+", "/");
                handlerMapping.put(url, method);

                System.out.println("maped " + url + "," + method);
            }

        }

    }

    private void initAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    MyAutowired annotation = field.getAnnotation(MyAutowired.class);
                    //自定义名称
                    String beanName = annotation.value();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getName();//类型名称
                    }
                    field.setAccessible(true);//设置private属性的访问权限
                    //注入
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
    }

    private void initIoc() {
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(MyController.class)) {
                    Object instance = clazz.newInstance();
                    //类型
                    ioc.put(clazz.getName(), instance);
                }
                if (clazz.isAnnotationPresent(MyService.class)) {
                    Object instance = clazz.newInstance();
                    //类型
                    ioc.put(clazz.getName(), instance);

                    //首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    //自定义名称
                    String value = clazz.getAnnotation(MyService.class).value();
                    if (!"".equals(value)) {
                        beanName = value;
                    }
                    //名称
                    ioc.put(beanName, instance);

                    //接口类型
                    for (Class<?> cl : clazz.getInterfaces()) {
                        if (ioc.containsKey(cl.getName())) {
                            throw new Exception(cl.getName() + "重复");
                        }
                        ioc.put(cl.getName(), instance);
                    }
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void initPackage(String scanPackage) {
        String path = "/" + scanPackage.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource(path);
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                initPackage(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(className);
            }
        }

    }

    private void initConfig(String contextConfigLocation) {
        InputStream fis = null;
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            //1、读取配置文件
            contextConfig.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != fis) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    private String toLowerFirstCase(String s) {

        return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
    }


}
