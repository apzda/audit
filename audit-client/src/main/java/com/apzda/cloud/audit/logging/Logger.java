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
package com.apzda.cloud.audit.logging;

import com.apzda.cloud.audit.proto.Arg;
import com.apzda.cloud.audit.proto.AuditLog;
import com.apzda.cloud.audit.proto.AuditService;
import com.apzda.cloud.gsvc.context.CurrentUserProvider;
import com.apzda.cloud.gsvc.context.TenantManager;
import com.apzda.cloud.gsvc.core.GsvcContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@Slf4j
public class Logger {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger("audit");

    private final AuditService auditService;

    private final ObjectMapper objectMapper;

    private final AuditLog.Builder builder;

    public Logger(AuditService auditService, ObjectMapper objectMapper, @NonNull String activity) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.builder = AuditLog.newBuilder();

        val userId = Optional.ofNullable(CurrentUserProvider.getCurrentUser().getUid()).orElse("0");
        val tenantId = TenantManager.tenantId("0");
        val request = GsvcContextHolder.getRequest();
        var ip = "";
        if (request.isPresent()) {
            ip = request.get().getRemoteAddr();
        }
        this.builder.setActivity(activity)
            .setUserid(userId)
            .setTenantId(tenantId)
            .setIp(ip)
            .setLevel("info")
            .setTimestamp(System.currentTimeMillis());
    }

    public Logger level(String level) {
        builder.setLevel(level);
        return this;
    }

    public Logger message(String message) {
        builder.setMessage(message);
        return this;
    }

    public <T> Logger replace(T oldVal, T newVal) {
        if (oldVal != null) {
            try {
                if (BeanUtils.isSimpleValueType(oldVal.getClass())) {
                    builder.setOldJsonValue(oldVal.toString());
                }
                else {
                    val oldStr = objectMapper.writeValueAsString(oldVal);
                    builder.setOldJsonValue(oldStr);
                }
            }
            catch (JsonProcessingException e) {
                log.warn("Cannot serialize old value: {} - {}", oldVal, e.getMessage());
            }
        }
        if (newVal != null) {
            try {
                if (BeanUtils.isSimpleValueType(newVal.getClass())) {
                    builder.setNewJsonValue(newVal.toString());
                }
                else {
                    val newStr = objectMapper.writeValueAsString(newVal);
                    builder.setNewJsonValue(newStr);
                }
            }
            catch (JsonProcessingException e) {
                log.warn("Cannot serialize new value: {} - {}", newVal, e.getMessage());
            }
        }
        return this;
    }

    public Logger arg(Arg.Builder builder) {
        this.builder.addArg(builder);
        return this;
    }

    public Logger arg(Arg arg) {
        this.builder.addArg(arg);
        return this;
    }

    public Logger template(boolean template) {
        this.builder.setTemplate(template);
        return this;
    }

    public void log() {
        CompletableFuture.runAsync(() -> {
            try {
                val req = builder.build();
                val str = objectMapper.writeValueAsString(req);
                logger.info("Audit Event: {}", str);
                val rest = auditService.log(req);
                if (StringUtils.isNotBlank(rest.getErrMsg())) {
                    log.warn("Cannot save audit log: {} - {}", str, rest.getErrMsg());
                }
            }
            catch (Exception e) {
                try {
                    log.warn("Cannot send audit log: {} - {}", objectMapper.writeValueAsString(builder.build()),
                            e.getMessage());
                }
                catch (JsonProcessingException e1) {
                    log.warn("Cannot send audit log: {} - {}", builder.build(), e1.getMessage());
                }
            }
        });
    }

}
