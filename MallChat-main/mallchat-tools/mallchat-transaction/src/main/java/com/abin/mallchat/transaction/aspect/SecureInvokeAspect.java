package com.abin.mallchat.transaction.aspect;

import cn.hutool.core.date.DateUtil;
import com.abin.mallchat.transaction.annotation.SecureInvoke;
import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.abin.mallchat.transaction.domain.entity.SecureInvokeRecord;
import com.abin.mallchat.transaction.service.SecureInvokeHolder;
import com.abin.mallchat.transaction.service.SecureInvokeService;
import com.abin.mallchat.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @SecureInvoke 的切面实现：把“方法调用”变成“一条可重试的数据库记录”。
 *
 * <p>完整流程（建议对着 SecureInvokeService 一起看）：
 * <ol>
 *     <li>拦截所有带 @SecureInvoke 的方法；</li>
 *     <li>如果当前没有事务（或已经在重放中），直接执行原方法，不做任何保障；</li>
 *     <li>如果处于事务中，把方法信息（类名/方法名/参数类型/参数值）序列化成 json，
 *         生成一条 SecureInvokeRecord 交给 SecureInvokeService；</li>
 *     <li>SecureInvokeService 将记录写库，并注册“事务提交后”回调；</li>
 *     <li>本切面直接 return null，业务代码以为方法调完了，实际上是延后执行的。
 * </ol>
 *
 * <p>注解里的 Order 设为最高优先级 + 1，是为了比事务切面更早进入、更晚退出，
 * 这样才能在事务开启之后拿到“已存在事务”的状态。
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)//确保最先执行
@Component
public class SecureInvokeAspect {
    @Autowired
    private SecureInvokeService secureInvokeService;

    /**
     * 环绕通知：拦截被 @SecureInvoke 标注的方法
     *
     * @param joinPoint     被拦截的方法调用信息
     * @param secureInvoke  方法上的注解实例（可以读到 maxRetryTimes、async 等配置）
     * @return 永远返回 null（该方法只能是 void）
     */
    @Around("@annotation(secureInvoke)")
    public Object around(ProceedingJoinPoint joinPoint, SecureInvoke secureInvoke) throws Throwable {
        boolean async = secureInvoke.async();
        // 判断当前线程是否处于 Spring 事务中（由 @Transactional 开启）
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        // 非事务状态，直接执行，不做任何保证。
        // SecureInvokeHolder.isInvoking() 的意义：重试任务重放时，会再次进入到这个切面，
        // 此时必须放行原方法，否则又会写入一条新记录，形成无限循环。
        if (SecureInvokeHolder.isInvoking() || !inTransaction) {
            return joinPoint.proceed();
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 事务内不会真正执行原方法（仅登记记录，等事务提交后重放），
        // 因此带返回值的方法必然得到 null。与其静默返回 null，不如直接拒绝这种误用。
        if (!Void.TYPE.equals(method.getReturnType())) {
            throw new IllegalStateException(String.format(
                    "@SecureInvoke 只能标注无返回值(void)的方法：%s#%s 的返回类型为 %s",
                    method.getDeclaringClass().getName(), method.getName(), method.getReturnType().getName()));
        }
        // 把“调用哪个类的哪个方法、参数类型是什么”记录下来，后续靠反射重放
        List<String> parameters = Stream.of(method.getParameterTypes()).map(Class::getName).collect(Collectors.toList());
        SecureInvokeDTO dto = SecureInvokeDTO.builder()
                // 参数值序列化成 json，注意参数对象必须能被 Jackson 正常序列化/反序列化
                .args(JsonUtils.toStr(joinPoint.getArgs()))
                .className(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameterTypes(JsonUtils.toStr(parameters))
                .build();
        // 组装“待执行记录”：第一次重试时间 = 当前时间 + 2 分钟
        SecureInvokeRecord record = SecureInvokeRecord.builder()
                .secureInvokeDTO(dto)
                .maxRetryTimes(secureInvoke.maxRetryTimes())
                .nextRetryTime(DateUtil.offsetMinute(new Date(), (int) SecureInvokeService.RETRY_INTERVAL_MINUTES))
                .build();
        // 交给 Service：写库 + 注册事务提交后的回调
        secureInvokeService.invoke(record, async);
        return null;
    }
}
