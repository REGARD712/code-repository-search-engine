package com.codeseek.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Python 语义检索客户端测试。
 */
class PythonSemanticSearchServiceTest {

    /**
     * 验证响应解析和语义结果顺序。
     */
    @Test
    void shouldParseSemanticIdsInResponseOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PythonSemanticSearchService service =
                new PythonSemanticSearchService(builder.build(), "http://127.0.0.1:8080");

        server.expect(requestTo("http://127.0.0.1:8080/search?q=%E5%A4%A7%E6%A8%A1%E5%9E%8B&topK=1000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"results\":[{\"id\":3},{\"id\":1},{\"id\":2}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<Long> ids = service.searchIds("大模型", 1000);

        assertThat(ids).containsExactly(3L, 1L, 2L);
        server.verify();
    }

    /**
     * 验证 HTTP 错误会转换为语义检索异常，便于上层降级。
     */
    @Test
    void shouldWrapHttpErrorAsSemanticSearchException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PythonSemanticSearchService service =
                new PythonSemanticSearchService(builder.build(), "http://127.0.0.1:8080");

        server.expect(requestTo("http://127.0.0.1:8080/search?q=Spring&topK=10"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.searchIds("Spring", 10))
                .isInstanceOf(SemanticSearchException.class);
        server.verify();
    }
}
