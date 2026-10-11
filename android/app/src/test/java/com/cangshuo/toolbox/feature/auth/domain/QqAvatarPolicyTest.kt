package com.cangshuo.toolbox.feature.auth.domain

import org.junit.Assert.*
import org.junit.Test

class QqAvatarPolicyTest {
    @Test fun qqEmailUsesOnlyNumericAccount() {
        assertEquals("14329140", QqAvatarPolicy.fromEmail("14329140@qq.com"))
        assertEquals("14329140", QqAvatarPolicy.fromEmail("14329140@QQ.COM"))
        assertEquals("14329140", QqAvatarPolicy.fromEmail(" 14329140@qq.com "))
        assertNull(QqAvatarPolicy.fromEmail("name@qq.com"))
        assertNull(QqAvatarPolicy.fromEmail("14329140@example.com"))
        assertNull(QqAvatarPolicy.fromEmail("14329140@qq.com@example.com"))
    }
    @Test fun invalidNumbersCannotEnterUrl() {
        listOf("", "1234", "012345", "14329140&spec=0", "14329140/", "1234567890123").forEach {
            assertFalse(QqAvatarPolicy.valid(it))
        }
        assertTrue(QqAvatarPolicy.valid("123456789012"))
        assertEquals("https://q.qlogo.cn/headimg_dl?dst_uin=14329140&spec=640&img_type=jpg",QqAvatarPolicy.url("14329140"))
    }
}
