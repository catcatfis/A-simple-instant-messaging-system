package com.catfish.mallchat.common.sensitive;

import com.catfish.mallchat.common.common.algorithm.sensitiveWord.IWordFactory;
import com.catfish.mallchat.common.sensitive.dao.SensitiveWordDao;
import com.catfish.mallchat.common.sensitive.domain.SensitiveWord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MyWord 工厂
 */

@Component
public class MyWordFactory implements IWordFactory {
    @Autowired
    private SensitiveWordDao sensitiveWordDao;

    @Override
    public List<String> getWordList() {
        return sensitiveWordDao.list()
                .stream()
                .map(SensitiveWord::getWord)
                .collect(Collectors.toList());
    }
}
