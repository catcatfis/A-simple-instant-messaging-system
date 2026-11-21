package com.abin.mallchat.transaction.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可靠调用参数 DTO：描述“要执行哪一个方法调用”。
 * 它会被序列化成 json 存到 secure_invoke_record.secure_invoke_json 字段，
 * 重试时再反序列化出来，通过反射重放。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureInvokeDTO {
    /** 目标类全类名，例如 com.abin.mallchat.transaction.service.MQProducer */
    private String className;
    /** 目标方法名，例如 sendSecureMsg */
    private String methodName;
    /** 参数类型的全类名列表（json 数组字符串），用于反射找到唯一确定的那个重载方法 */
    private String parameterTypes;
    /** 实际参数值（json 数组字符串） */
    private String args;
}
