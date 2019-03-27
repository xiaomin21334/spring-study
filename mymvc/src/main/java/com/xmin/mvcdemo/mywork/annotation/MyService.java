package com.xmin.mvcdemo.mywork.annotation;

import java.lang.annotation.*;

/**
 * Created by lixiaomin on 2019/3/26.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyService {
    String value() default "";
}
