package com.journey;

import java.util.List;

// record:一行声明一个不可变数据类(自动生成构造器、getter、equals、hashCode、toString)
record Person(String name, int age) {}

public class Day1 {

    // 方法:注意必须声明返回类型 String 和参数类型 Person —— 这就是"强类型"
    static String greet(Person p) {
        return "Hello %s, age %d".formatted(p.name(), p.age());
    }

    public static void main(String[] args) {
        var people = List.of(                       // var:类型由右边推断,但仍是静态类型
            new Person("Ada", 30),
            new Person("Lin", 25)
        );
        people.forEach(p -> System.out.println(greet(p)));   // lambda:类比旧语言的箭头函数
        System.out.println("成员数量:" + people.size());
    }
}
