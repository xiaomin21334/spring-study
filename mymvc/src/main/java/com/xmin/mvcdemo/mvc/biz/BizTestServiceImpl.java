package com.xmin.mvcdemo.mvc.biz;

import com.xmin.mvcdemo.mywork.annotation.MyService;

/**
 * Created by lixiaomin on 2019/3/26.
 */
@MyService
public class BizTestServiceImpl implements IBizTestService {

    public String get() {
        return "111111";
    }

    @Override
    public String get(String name) {
        return "name=" + name;
    }

    @Override
    public String get(Integer id) {
        return "id=" + id;
    }
}
