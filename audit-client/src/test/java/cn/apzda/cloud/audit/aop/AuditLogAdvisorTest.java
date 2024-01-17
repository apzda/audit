/*
 * Copyright (C) 2023-2024 Fengz Ning (windywany@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.apzda.cloud.audit.aop;

import cn.apzda.cloud.audit.AuditApp;
import cn.apzda.cloud.audit.controller.DemoController;
import cn.apzda.cloud.audit.service.DemoService;
import com.apzda.cloud.audit.autoconfig.AuditAutoConfiguration;
import com.apzda.cloud.audit.proto.AuditLog;
import com.apzda.cloud.audit.proto.AuditService;
import com.apzda.cloud.gsvc.ext.GsvcExt;
import com.apzda.cloud.gsvc.utils.ResponseUtils;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@SpringBootTest
@ContextConfiguration(classes = AuditApp.class)
@ImportAutoConfiguration(
        classes = { AuditAutoConfiguration.class, AopAutoConfiguration.class, SecurityAutoConfiguration.class })
@ComponentScan(basePackages = { "cn.apzda.cloud.audit" })
@TestPropertySource(properties = { "logging.level.com.apzda.cloud=trace" })
public class AuditLogAdvisorTest {

    @Autowired
    private DemoController demoController;

    @Autowired
    private DemoService demoService;

    @MockBean
    private AuditService auditService;

    @BeforeAll
    static void setup() {
        ResponseUtils.config();
    }

    @Test
    void advisor_should_be_work_ok() throws InterruptedException {
        // given
        val map = new HashMap<String, String>();
        given(auditService.log(any())).willAnswer((invocation) -> {
            val argument = invocation.getArgument(0, AuditLog.class);
            map.put("message", argument.getMessage());
            return GsvcExt.CommonRes.newBuilder().build();
        });
        // when
        val str = demoController.shouldBeAudited("123");
        // then
        assertThat(str).isEqualTo("hello ya:123");
        // when
        TimeUnit.MILLISECONDS.sleep(500);
        // then
        assertThat(map).isNotEmpty();
        assertThat(map).containsKeys("message");
        assertThat(map.get("message")).isEqualTo("you are get then id is: 123, then result is:hello ya:123");

    }

    @Test
    void advisor_should_be_work_ok2() throws InterruptedException {
        // given
        val map = new HashMap<Integer, String>();
        given(auditService.log(any())).willAnswer((invocation) -> {
            val argument = invocation.getArgument(0, AuditLog.class);
            map.put(argument.getArg(0).getIndex(), argument.getArg(0).getValue());
            return GsvcExt.CommonRes.newBuilder().build();
        });
        // when
        val str = demoService.hello();
        // then
        assertThat(str.getErrMsg()).isEqualTo("error message");
        // when
        TimeUnit.MILLISECONDS.sleep(500);
        // then
        assertThat(map).isNotEmpty();
        assertThat(map).containsKeys(0);
        assertThat(map.get(0)).isEqualTo("error message");
    }

    @Test
    void advisor_should_be_work_ok3() throws InterruptedException {
        // given
        val map = new HashMap<Integer, String>();
        given(auditService.log(any())).willAnswer((invocation) -> {
            val argument = invocation.getArgument(0, AuditLog.class);
            map.put(argument.getArg(0).getIndex(), argument.getArg(0).getValue());
            map.put(argument.getArg(1).getIndex(), argument.getArg(1).getValue());
            map.put(3, String.valueOf(argument.getTemplate()));
            return GsvcExt.CommonRes.newBuilder().build();
        });
        // when
        val str = demoService.hello("hi");
        // then
        assertThat(str.getErrMsg()).isEqualTo("error hi");
        // when
        TimeUnit.MILLISECONDS.sleep(500);
        // then
        assertThat(map).isNotEmpty();
        assertThat(map).containsKeys(0, 1);
        assertThat(map.get(1)).isEqualTo("hi");
        assertThat(map.get(0)).isEqualTo("error hi");
        assertThat(map.get(3)).isEqualTo("true");
    }

}
