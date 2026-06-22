package com.journey;

public class TryWithResourcesDemo {
    // 自定义一个"资源":实现 AutoCloseable
    static class MyResource implements AutoCloseable {
        private final String name;
        MyResource(String name) {
            this.name = name;
            System.out.println("打开资源: " + name);
        }
        void use() { System.out.println("使用资源: " + name); }
        @Override public void close() {
            System.out.println("关闭资源: " + name);   // 自动被调用
        }
    }

    public static void main(String[] args) {
        try (MyResource a = new MyResource("A");
             MyResource b = new MyResource("B")) {
            a.use();
            b.use();
            throw new RuntimeException("中途出错!");   // 故意抛异常
        } catch (Exception e) {
            System.out.println("捕获: " + e.getMessage());
        }
    }
}
