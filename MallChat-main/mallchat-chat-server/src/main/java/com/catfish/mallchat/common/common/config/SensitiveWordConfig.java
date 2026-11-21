package com.catfish.mallchat.common.common.config;

import com.catfish.mallchat.common.common.algorithm.sensitiveWord.DFAFilter;
import com.catfish.mallchat.common.common.algorithm.sensitiveWord.SensitiveWordBs;
import com.catfish.mallchat.common.sensitive.MyWordFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SensitiveWordConfig 配置类
 */

@Configuration
public class SensitiveWordConfig {

    @Autowired
    private MyWordFactory myWordFactory;

    /**
     * 初始化引导类
     *
     * @return 初始化引导类
     */
    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        return SensitiveWordBs.newInstance()
                .filterStrategy(DFAFilter.getInstance())
                .sensitiveWord(myWordFactory)
                .init();
    }

}