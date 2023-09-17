package com.simplerasp.handlers;

import com.simplerasp.annotations.RaspAfter;
import com.simplerasp.annotations.RaspBefore;
import com.simplerasp.annotations.RaspHandler;
import com.simplerasp.exceptions.RaspException;
import sun.misc.IOUtils;

/**
 * 命令注入
 */
@RaspHandler(className = "java.lang.Runtime", methodName = "exec", parameterTypes = {String.class})
public class RuntimeExecHandler {

    @RaspBefore
    public static Object[] handleBefore(Object obj, Object[] params) {
        System.out.println("before");

        String cmd = (String) params[0];
        System.out.println("try to exec: " + cmd);
        if (cmd.contains("Calculator")) {
            throw new RaspException("Reject malicious command execution attempt");
        }
        return params;
    }

    @RaspAfter
    public static Object handleAfter(Object obj, Object result) throws Exception {
        System.out.println("after");

        Process p = (Process) result;
        // 这么头铁的嘛，JDK不对直接就报错了吧...这兼容性基本没考虑啊...
        String output = new String(IOUtils.readAllBytes(p.getInputStream()));
        // 人家不一定非得是读取/etc/passwd啊老铁...
        if (output.contains("uid=")) {
            throw new RaspException("Reject malicious command execution output");
        }
        return result;
    }

}
