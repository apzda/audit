/*
 * Copyright (C) 2023-2023 Fengz Ning (windywany@gmail.com)
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
package com.apzda.cloud.audit.server;

import com.apzda.cloud.audit.proto.AuditServiceGsvc;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.lang.annotation.*;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PropertySource("classpath:apzda.audit.service.yaml")
@Import({ AuditServiceGsvc.class })
@ComponentScan(basePackages = { "com.apzda.cloud.audit.facade", "com.apzda.cloud.audit.domain" })
@EnableJpaRepositories(basePackages = { "com.apzda.cloud.audit.domain.repository" })
@EntityScan("com.apzda.cloud.audit.domain.entity")
@Documented
public @interface EnableAuditServer {

}
