package com.codeseek;

import com.codeseek.gitee.api.GiteeApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Spring Boot 上下文加载测试。
 */
@SpringBootTest
class SearchEngineApplicationTests {

    @Autowired
    private GiteeApi giteeApi;

    /**
     * 验证searchUser方法是否正常工作。
     */
    @Test
    void testSearchUser() {
        List<String> users = giteeApi.searchUserNames("oo");
        users.forEach(System.out::println);
    }
}
