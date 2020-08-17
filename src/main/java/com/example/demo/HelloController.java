package com.example.demo;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author VonXXGhost
 * @date 2020/8/17 下午 8:08
 */
@RestController
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/hello/{id}")
    public String getHello(@PathVariable("id") Integer id) throws Exception {
        Random random = new Random();
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        for (int i = 1; i <= id; i++) {
            System.out.println(helloService.sayHelloLimit(random.nextInt(300) + 200));
        }
        return helloService.sayHelloLimit(random.nextInt(300) + 200);
    }
}
