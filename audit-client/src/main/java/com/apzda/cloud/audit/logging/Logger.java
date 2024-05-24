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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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

    private final ObservationRegistry observationRegistry;

    public Logger(AuditService auditService, ObjectMapper objectMapper, @NonNull String activity,
            ObservationRegistry observationRegistry) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.observationRegistry = observationRegistry;
        this.builder = AuditLog.newBuilder();

        val userId = Optional.ofNullable(CurrentUserProvider.getCurrentUser().getUid()).orElse("0");
        val tenantId = TenantManager.tenantId("0");
        var ip = GsvcContextHolder.getRemoteIp();

        this.builder.setActivity(activity)
            .setUserid(userId)
            .setTenantId(tenantId)
            .setIp(ip)
            .setLevel("info")
            .setTimestamp(System.currentTimeMillis());
    }

    public Logger level(String level) {
        if (level != null) {
            builder.setLevel(level);
        }
        return this;
    }

    public Logger message(String message) {
        builder.setMessage(message);
        return this;
    }

    public Logger replace(Object oldVal, Object newVal) {
        return oldValue(oldVal).newValue(newVal);
    }

    public Logger oldValue(Object oldVal) {
        if (oldVal != null) {
            try {
                if (BeanUtils.isSimpleValueType(oldVal.getClass())) {
                    builder.setOldJsonValue(oldVal.toString());
                }
                else if (oldVal instanceof String) {
                    builder.setOldJsonValue((String) oldVal);
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
        return this;
    }

    public Logger newValue(Object newVal) {
        if (newVal != null) {
            try {
                if (BeanUtils.isSimpleValueType(newVal.getClass())) {
                    builder.setNewJsonValue(newVal.toString());
                }
                else if (newVal instanceof String) {
                    builder.setNewJsonValue((String) newVal);
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

    public Logger runas(String runas) {
        if (runas != null) {
            this.builder.setRunas(runas);
        }
        return this;
    }

    public Logger device(String device) {
        if (device != null) {
            this.builder.setDevice(device);
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
        val context = GsvcContextHolder.getContext();
        val observation = Observation.createNotStarted("async", this.observationRegistry);
        CompletableFuture.runAsync(() -> {
            try {
                context.restore();
                observation.observe(() -> {
                    log(auditService, objectMapper, builder);
                });
            }
            finally {
                GsvcContextHolder.clear();
            }
        });
    }

    public void log(boolean async) {
        if (async) {
            log();
        }
        else {
            log(auditService, objectMapper, builder);
        }
    }

    public static void log(AuditService auditService, ObjectMapper objectMapper, AuditLog.Builder builder) {
        try {
            val req = builder.build();
            val str = objectMapper.writeValueAsString(req);
            logger.info("Audit Event: {}", str);
            val rest = auditService.log(req);
            if (StringUtils.isNotBlank(rest.getErrMsg())) {
                log.warn("Cannot save audit log: {} - {}", str, rest.getErrMsg());
            }
        }
        catch (JsonProcessingException e1) {
            log.warn("Cannot serialize audit log: {} - {}", builder.build(), e1.getMessage());
        }
        catch (Exception e) {
            try {
                log.warn("Cannot send audit log: {} - {}", objectMapper.writeValueAsString(builder.build()),
                        e.getMessage());
            }
            catch (JsonProcessingException ignored) {
            }
        }
    }

}
