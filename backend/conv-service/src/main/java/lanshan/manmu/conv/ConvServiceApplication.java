package lanshan.manmu.conv;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
@MapperScan("lanshan.manmu.conv.mapper")
public class ConvServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConvServiceApplication.class, args);
    }
}
