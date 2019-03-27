package com.xmin.mvcdemo.mywork;

import com.xmin.mvcdemo.mywork.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * Created by lixiaomin on 2019/3/26.
 */
public class MyDispatcherServlet2 extends HttpServlet {

    private Properties contextConfig = new Properties();
    //保存Contrller中所有action的对应关系
    private List<MyHandler> handlerMapping = new ArrayList<MyHandler>();
    //IOC容器，实例化的类
    private Map<String, Object> ioc = new HashMap<String, Object>();
    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<String>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            MyHandler handler = getHandler(req);
            if (handler == null) {
                resp.getWriter().write("404 Not Found");
                return;
            }

            //参数
            Map<String, String[]> params = req.getParameterMap();

            //参数类型
            Class<?>[] paramTypes = handler.method.getParameterTypes();
            Object[] paramValues = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].getName().equals(HttpServletRequest.class.getName())) {
                    paramValues[i] = req;
                } else if (paramTypes[i].getName().equals(HttpServletResponse.class.getName())) {
                    paramValues[i] = resp;
                }
            }
            //参数注解
            Annotation[][] parameterAnnotations = handler.method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a : parameterAnnotations[i]) {
                    if (a instanceof MyRequestParam) {
                        String name = ((MyRequestParam) a).value();
                        if ("".equals(name)) {
                            continue;
                        }
                        if (!params.containsKey(name)) {
                            continue;
                        }
                        String value = Arrays.toString(params.get(name)).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                        paramValues[i] = convert(paramTypes[i], value);
                    }
                }
            }

            Object obj = handler.method.invoke(handler.clazz, paramValues);
            resp.getWriter().write(String.valueOf(obj));
        } catch (Exception ex) {
            resp.getWriter().write(ex.toString());
        }

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

    private void initHandlerMapping() throws ServletException {
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

                for (MyHandler handler : handlerMapping) {
                    if (handler.url.equals(url)) {
                        throw new ServletException(url + " already exists");
                    }
                }
                handlerMapping.add(new MyHandler(entry.getValue(), method, url));

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



    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    private MyHandler getHandler(HttpServletRequest req) {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (MyHandler handler : handlerMapping) {
            if (handler.url.equals(url)) {
                return handler;
            }
        }
        return null;
    }

    private String toLowerFirstCase(String s) {

        return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
    }

    private class MyHandler {
        protected Object clazz;     //实例
        protected Method method;        //方法
        protected String url; //地址
        protected Map<String, Integer> params;    //参数,带顺序

        public MyHandler(Object clazz, Method method, String url) {
            this.clazz = clazz;
            this.method = method;
            this.url = url;
            setParams(method);
        }


        public void setParams(Method method) {
            params = new HashMap<String, Integer>();

            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                params.put(type.getName(), i);

            }
        }
    }
}
