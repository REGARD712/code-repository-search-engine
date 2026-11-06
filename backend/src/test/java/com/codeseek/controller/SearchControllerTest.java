package com.codeseek.controller;

import com.codeseek.index.model.RepositorySearchResult;
import com.codeseek.index.service.RepositoryIndexService;
import com.codeseek.repository.entity.Repository;
import com.codeseek.repository.mapper.RepositoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 搜索 HTTP 接口测试。
 */
@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryIndexService repositoryIndexService;

    @MockBean
    private RepositoryMapper repositoryMapper;

    /**
     * 验证 GET 请求体方式返回完整仓库字段。
     */
    @Test
    void shouldSearchByJsonBody() throws Exception {
        RepositorySearchResult result = new RepositorySearchResult(
                10210296L,
                "gou-xinjies/data-structure",
                "https://gitee.com/gou-xinjies/data-structure.git",
                2,
                1
        );
        Repository repository = new Repository(
                10210296L,
                "gou-xinjies/data-structure",
                "https://gitee.com/gou-xinjies/data-structure.git",
                "# java数据结构\r\n",
                37305308L
        );

        when(repositoryIndexService.search("数据结构", 1000)).thenReturn(List.of(result));
        when(repositoryMapper.selectByIdsInOrder(List.of(10210296L))).thenReturn(List.of(repository));

        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"数据结构\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10210296))
                .andExpect(jsonPath("$[0].fullName").value("gou-xinjies/data-structure"))
                .andExpect(jsonPath("$[0].htmlUrl").value("https://gitee.com/gou-xinjies/data-structure.git"))
                .andExpect(jsonPath("$[0].readme").value("# java数据结构\r\n"))
                .andExpect(jsonPath("$[0].repositoryId").value(37305308));
    }

    /**
     * 验证没有请求体时可以使用 query 查询参数。
     */
    @Test
    void shouldSearchByQueryParameter() throws Exception {
        when(repositoryIndexService.search("数据结构", 1000)).thenReturn(List.of());

        mockMvc.perform(get("/search").param("query", "数据结构"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
