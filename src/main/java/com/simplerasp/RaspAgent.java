package com.simplerasp;

import com.simplerasp.annotations.RaspAfter;
import com.simplerasp.annotations.RaspBefore;
import com.simplerasp.annotations.RaspHandler;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * 整个rasp程序的入口
 */
public class RaspAgent {

    /**
     * jvm的-javaagent参数启动的时候执行的方法
     *
     * @param args
     * @param inst
     * @throws Exception
     */
    public static void premain(String args, Instrumentation inst) throws Exception {
        System.out.println("premain");

        // 哈，总算看到一个不是使用的openrasp的工具类来干这个事的了...
        // 解决双亲委派问题, 使得 Hook 使用 BootstrapClassLoader 加载的类时, 能够正常加载到我们自定义的 handler
        String jarPath = RaspAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        inst.appendToBootstrapClassLoaderSearch(new JarFile(jarPath));
//        inst.appendToSystemClassLoaderSearch(new JarFile(jarPath));

        // 扫描注解获取所有 handler class 以及 method
        Reflections ref = new Reflections("com.simplerasp", new TypeAnnotationsScanner(), new MethodAnnotationsScanner());
        Set<Class<?>> handlerClasses = ref.getTypesAnnotatedWith(RaspHandler.class);
        Set<Method> beforeMethods = ref.getMethodsAnnotatedWith(RaspBefore.class);
        Set<Method> afterMethods = ref.getMethodsAnnotatedWith(RaspAfter.class);

        // 遍历使用了 @RaspHandler 注解的类
        for (Class<?> handlerClass : handlerClasses) {
            RaspHandler handlerAnnotation = handlerClass.getAnnotation(RaspHandler.class);
            String beforeName = null;
            String afterName = null;

            // 寻找使用了 @RaspBefore 注解的方法
            for (Method m : beforeMethods) {
                if (m.getDeclaringClass() == handlerClass) {
                    beforeName = m.getName();
                }
            }
            // 寻找使用了 @RaspAfter 注解的方法
            for (Method m : afterMethods) {
                if (m.getDeclaringClass() == handlerClass) {
                    afterName = m.getName();
                }
            }

            // 获取 @RaspHandler 注解的信息
            String className = handlerAnnotation.className();
            String methodName = handlerAnnotation.methodName();
            boolean isConstructor = handlerAnnotation.isConstructor();
            Class[] parameterTypes = handlerAnnotation.parameterTypes();

            // 在 transform 前先加载一遍, 防止 Javaassist 获取不到类
            Class clazz = Thread.currentThread().getContextClassLoader().loadClass(className);

            // 存放 handler 的相关相关信息, 用于 Javaassist
            Map<String, String> handlerMap = new HashMap<>();
            handlerMap.put("className", handlerClass.getName());
            handlerMap.put("beforeName", beforeName);
            handlerMap.put("afterName", afterName);

            // 我草不是吧兄弟？这活似乎有点糙？这岂不是意味着每多一个rasp handler都会把所有类retransform一次，要是handler多了这直接跑不动了啊...
            // 添加 transformer 并 retransform
            RaspTransformer transformer = new RaspTransformer(className, methodName, isConstructor, parameterTypes);
            transformer.setHandlerMap(handlerMap);
            inst.addTransformer(transformer, true);
            inst.retransformClasses(clazz);
        }
    }

    /**
     * 看来是不支持attach模式的
     *
     * @param args
     * @param inst
     * @throws Exception
     */
    public static void agentmain(String args, Instrumentation inst) throws Exception {
        System.out.println("agentmain");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("main");
    }

}
