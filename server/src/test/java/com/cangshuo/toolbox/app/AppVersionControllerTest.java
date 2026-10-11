package com.cangshuo.toolbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AppVersionControllerTest {

    @Test void exposesConfiguredReleaseMetadata() {
        var controller = new AppVersionController(7, "0.2.0", "https://tool.zhzgo.cn/download", "修复与优化");
        var response = controller.version(new MockHttpServletRequest());
        assertEquals(0, response.code());
        assertEquals(7, response.data().versionCode());
        assertEquals("0.2.0", response.data().versionName());
        assertEquals("https://tool.zhzgo.cn/download", response.data().url());
        assertEquals("修复与优化", response.data().notes());
    }
}
