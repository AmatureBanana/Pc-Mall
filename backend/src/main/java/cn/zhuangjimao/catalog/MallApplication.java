package cn.zhuangjimao.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.zhuangjimao")
@org.mybatis.spring.annotation.MapperScan(basePackages = "cn.zhuangjimao", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MallApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
    }
}
