package com.abin.mallchat.transaction.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.abin.mallchat.transaction.dao.SecureInvokeRecordDao;
import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.abin.mallchat.transaction.domain.entity.SecureInvokeRecord;
import com.abin.mallchat.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 可靠调用的“执行器”：负责把记录写库、事务提交后执行、失败后定时重试。
 *
 * <p>这是本地消息表方案的核心。可以把它理解成一个“待办事项清单”：
 * <ul>
 *     <li>{@link #invoke}：把待办写进清单（secure_invoke_record 表），并约定事务提交后去做；</li>
 *     <li>{@link #doInvoke}：根据记录里保存的类名/方法名/参数，用反射把方法重新调一遍；</li>
 *     <li>{@link #retry}：定时任务，把还没做成功的待办拿出来重做。</li>
 * </ul>
 *
 * <p>为什么能保证不丢？因为“业务数据”和“待办记录”写在同一个数据库事务里，
 * 要么一起成功，要么一起回滚；只要事务成功，待办就一定存在，后面迟早会被执行。
 *
 * <p>与 MQ 的关系：MQ 本身也有重试，但“把消息发出去”这个动作可能失败（比如 Broker 重启）。
 * 本类保证的是“消息最终一定被投递到 MQ”，MQ 再保证“消息最终一定被消费者消费”。
 */
@Slf4j
@AllArgsConstructor
public class SecureInvokeService {

    /**
     * 重试间隔基数（分钟）：重试时间按指数上涨 2m、4m、8m、16m……
     */
    public static final double RETRY_INTERVAL_MINUTES = 2D;

    /**
     * 重试任务的分布式锁 key：保证集群内同一时刻只有一个节点在扫描并重放
     */
    private static final String RETRY_LOCK_KEY = "mallchat:secure:invoke:retry:lock";

    /**
     * 锁租期（秒）：必须大于单轮扫描+投递耗时，避免任务执行到一半锁释放
     */
    private static final long RETRY_LOCK_LEASE_SECONDS = 60L;

    private final SecureInvokeRecordDao secureInvokeRecordDao;

    /**
     * 执行重试任务的线程池，由 ThreadPoolConfig 提供（可通过 SecureInvokeConfigurer 自定义）
     */
    private final Executor executor;

    /**
     * Redisson 客户端，用于重试任务的分布式锁
     */
    private final RedissonClient redissonClient;

    /**
     * 定时重试：每 5 秒扫一次表，把到时间还没执行成功的记录重新投递。
     *
     * <p>注意这个任务不需要事务，也不需要人工触发，应用启动后就会自动跑。
     */
    @Scheduled(cron = "*/5 * * * * ?")
    public void retry() {
        // 多实例部署时每个节点都会触发该定时任务，
        // 若不加锁会重复重放同一条记录（例如重复投递 MQ 消息）。
        RLock lock = redissonClient.getLock(RETRY_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, RETRY_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            // 查询“状态=待执行、已到重试时间、且入库至少 2 分钟”的记录
            List<SecureInvokeRecord> secureInvokeRecords = secureInvokeRecordDao.getWaitRetryRecords();
            for (SecureInvokeRecord secureInvokeRecord : secureInvokeRecords) {
                // 丢到线程池执行，避免一条卡住影响后面的记录
                doAsyncInvoke(secureInvokeRecord);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("SecureInvoke 重试任务获取锁被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 保存待执行记录（和业务操作在同一个事务内）
     */
    public void save(SecureInvokeRecord record) {
        secureInvokeRecordDao.save(record);
    }

    /**
     * 执行失败时更新记录：次数 +1，并计算下次重试时间；超过上限则标记为失败。
     *
     * @param record   原记录
     * @param errorMsg 失败原因（便于排查）
     */
    private void retryRecord(SecureInvokeRecord record, String errorMsg) {
        Integer retryTimes = record.getRetryTimes() + 1;
        SecureInvokeRecord update = new SecureInvokeRecord();
        update.setId(record.getId());
        update.setFailReason(errorMsg);
        update.setNextRetryTime(getNextRetryTime(retryTimes));
        if (retryTimes > record.getMaxRetryTimes()) {
            update.setStatus(SecureInvokeRecord.STATUS_FAIL);
        } else {
            update.setRetryTimes(retryTimes);
        }
        secureInvokeRecordDao.updateById(update);
    }

    /**
     * 计算下次重试时间：指数退避，避免一直重试把对方（比如 MQ）打死。
     * 第 1 次失败等 2 分钟，第 2 次 4 分钟，第 3 次 8 分钟……
     */
    private Date getNextRetryTime(Integer retryTimes) {//或者可以采用退避算法
        double waitMinutes = Math.pow(RETRY_INTERVAL_MINUTES, retryTimes);//重试时间指数上升 2m 4m 8m 16m
        return DateUtil.offsetMinute(new Date(), (int) waitMinutes);
    }

    /**
     * 执行成功后删除记录（表只保留“未完成”的待办，不会无限膨胀）
     */
    private void removeRecord(Long id) {
        secureInvokeRecordDao.removeById(id);
    }

    /**
     * 登记一个“待执行任务”。这是事务内的入口。
     *
     * <p>步骤：
     * <ol>
     *     <li>不在事务中：什么都不做（调用方须自己保证）；</li>
     *     <li>先把记录 insert 进表（此时还在业务事务里）；</li>
     *     <li>注册事务同步回调，在 afterCommit（业务数据已经真正落库）后才执行；</li>
     *     <li>若业务事务回滚，记录也一起回滚，不会产生无效任务。</li>
     * </ol>
     *
     * @param record 待执行记录
     * @param async  是否丢线程池异步执行
     */
    public void invoke(SecureInvokeRecord record, boolean async) {
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        //非事务状态，直接执行，不做任何保证。
        if (!inTransaction) {
            return;
        }
        //保存执行数据
        save(record);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @SneakyThrows
            @Override
            public void afterCommit() {
                //事务后执行
                if (async) {
                    doAsyncInvoke(record);
                } else {
                    doInvoke(record);
                }
            }
        });
    }

    /**
     * 异步执行：丢给线程池，不阻塞当前线程
     */
    public void doAsyncInvoke(SecureInvokeRecord record) {
        executor.execute(() -> doInvoke(record));
    }

    /**
     * 真正执行记录里保存的那个方法（靠反射重放）。
     *
     * <p>
     * 1. 把记录里存的 json 还原成参数；<br>
     * 2. 从 Spring 容器拿到 bean（而不是直接 new，否则依赖注入全部失效）；<br>
     * 3. 反射调用目标方法；<br>
     * 4. 成功 -> 删记录；失败 -> 更新重试次数，等下次定时任务重做。
     */
    public void doInvoke(SecureInvokeRecord record) {
        SecureInvokeDTO secureInvokeDTO = record.getSecureInvokeDTO();
        try {
            // 标记“当前线程正在重放”，使 @SecureInvoke 切面放行，避免重复入库死循环
            SecureInvokeHolder.setInvoking();
            Class<?> beanClass = Class.forName(secureInvokeDTO.getClassName());
            Object bean = SpringUtil.getBean(beanClass);
            List<String> parameterStrings = JsonUtils.toList(secureInvokeDTO.getParameterTypes(), String.class);
            List<Class<?>> parameterClasses = getParameters(parameterStrings);
            Method method = ReflectUtil.getMethod(beanClass, secureInvokeDTO.getMethodName(), parameterClasses.toArray(new Class[]{}));
            Object[] args = getArgs(secureInvokeDTO, parameterClasses);
            //执行方法
            method.invoke(bean, args);
            //执行成功更新状态（删除待办记录）
            removeRecord(record.getId());
        } catch (Throwable e) {
            log.error("SecureInvokeService invoke fail", e);
            //执行失败，等待下次执行
            retryRecord(record, e.getMessage());
        } finally {
            // 无论成功失败都要清掉标记，避免污染线程池后续任务
            SecureInvokeHolder.invoked();
        }
    }

    /**
     * 把 json 数组形式的参数还原成方法需要的对象数组
     */
    @NotNull
    private Object[] getArgs(SecureInvokeDTO secureInvokeDTO, List<Class<?>> parameterClasses) {
        JsonNode jsonNode = JsonUtils.toJsonNode(secureInvokeDTO.getArgs());
        Object[] args = new Object[jsonNode.size()];
        for (int i = 0; i < jsonNode.size(); i++) {
            Class<?> aClass = parameterClasses.get(i);
            args[i] = JsonUtils.nodeToValue(jsonNode.get(i), aClass);
        }
        return args;
    }

    /**
     * 把参数类型的全类名字符串还原成 Class 对象
     */
    @NotNull
    private List<Class<?>> getParameters(List<String> parameterStrings) {
        return parameterStrings.stream().map(name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                log.error("SecureInvokeService class not fund", e);
            }
            return null;
        }).collect(Collectors.toList());
    }
}
