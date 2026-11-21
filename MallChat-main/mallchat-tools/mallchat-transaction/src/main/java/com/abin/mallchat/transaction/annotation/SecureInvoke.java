package com.abin.mallchat.transaction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可靠调用的标记注解（“这件事必须在事务提交后一定做成”）。
 *
 * <p>典型用法：业务方法里写库 + 发 MQ。如果直接发 MQ，可能出现“库写成功、MQ 没发出去”的情况；
 * 加上这个注解后，方法体不会在事务里真正执行，而是先把“要调用谁、传什么参数”写进
 * secure_invoke_record 表，等事务提交后再由切面重新执行。
 *
 * <p>方法被标注后会被 {@link com.abin.mallchat.transaction.aspect.SecureInvokeAspect} 拦截，
 * 因此只能用在 <b>返回 void</b> 的方法上；在事务外部调用则会退化为普通调用（直接执行）。
 */
@Retention(RetentionPolicy.RUNTIME)//运行时生效
@Target(ElementType.METHOD)//作用在方法上
public @interface SecureInvoke {

    /**
     * 默认重试 3 次（包含第一次正常执行）。
     * 超过次数后记录状态会被标成“已失败”，不再自动重试。
     *
     * @return 最大重试次数
     */
    int maxRetryTimes() default 3;

    /**
     * 是否异步执行，默认 true。
     *
     * <p>异步：先把记录入库，事务提交后丢给线程池执行，不阻塞主线程（发 MQ 属于这类）；
     * 同步：事务提交后在当前线程执行，链路追踪不会因为切线程而断开，
     * 适合 MQ 消费这种本身就不关心耗时的场景。
     *
     * @return 是否异步执行
     */
    boolean async() default true;
}
