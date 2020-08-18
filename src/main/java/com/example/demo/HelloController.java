package com.example.demo;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author VonXXGhost
 * @date 2020/8/17 下午 8:08
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/{id}")
    public String getHello(@PathVariable("id") Integer id,
            @RequestParam(required = false, defaultValue = "16") Integer core) throws Exception {
        System.out.println("-----------core:" + core);
        Random random = new Random();
        ExecutorService executorService = Executors.newFixedThreadPool(core);
        for (int i = 1; i <= id; i++) {
            int finalI = i;
            executorService.execute(
                    () -> {
                        try {
                            System.out.println(
                                    "[" + finalI + "] " + helloService.sayHelloLimit(random.nextInt(150) + 200));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
            );
        }
        System.out.println("-----------");
        return helloService.sayHelloLimit(random.nextInt(300) + 200);
    }
}
