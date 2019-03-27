package com.xmin.mvcdemo.mvc.api;

import com.xmin.mvcdemo.mvc.biz.IBizTestService;
import com.xmin.mvcdemo.mywork.annotation.MyAutowired;
import com.xmin.mvcdemo.mywork.annotation.MyController;
import com.xmin.mvcdemo.mywork.annotation.MyRequestMapping;
import com.xmin.mvcdemo.mywork.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by lixiaomin on 2019/3/26.
 */
@MyController
@MyRequestMapping("/test")
public class TestController {
    @MyAutowired
    private IBizTestService bizTestService;

    @MyRequestMapping("/get")
    public String get(@MyRequestParam("name") String name) {
        return bizTestService.get(name);
    }

    @MyRequestMapping("/aaa")
    public String aaa(@MyRequestParam("id") Integer id) {
        return bizTestService.get(id);
    }

    @MyRequestMapping("/bbb")
    public String bbb(HttpServletRequest req, HttpServletResponse resp, @MyRequestParam("name") String name, @MyRequestParam("id") Integer id) {
        return bizTestService.get(id) + bizTestService.get(name);
    }

    @MyRequestMapping("/ccc")
    public String ccc() {
        return bizTestService.get();
    }

}
