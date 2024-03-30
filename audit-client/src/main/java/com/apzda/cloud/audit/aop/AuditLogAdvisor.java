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
package com.apzda.cloud.audit.aop;

import com.apzda.cloud.audit.proto.AuditService;
import com.apzda.cloud.gsvc.context.CurrentUserProvider;
import com.apzda.cloud.gsvc.context.TenantManager;
import com.apzda.cloud.gsvc.core.GsvcContextHolder;
import com.apzda.cloud.gsvc.utils.ResponseUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@Component
@Aspect
@Slf4j
@Order
public class AuditLogAdvisor {

    private static final Logger logger = LoggerFactory.getLogger("audit");

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    private final ParserContext parserContext = new TemplateParserContext();

    private final AuditService auditService;

    private final ObjectMapper objectMapper;

    public AuditLogAdvisor(AuditService auditService) {
        this.auditService = auditService;
        this.objectMapper = ResponseUtils.OBJECT_MAPPER;
    }

    @Around("@annotation(com.apzda.cloud.audit.aop.AuditLog)")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        val args = pjp.getArgs();

        val signature = (MethodSignature) pjp.getSignature();
        val method = signature.getMethod();
        val ann = method.getAnnotation(AuditLog.class);
        val activity = StringUtils.defaultIfBlank(ann.value(), ann.activity());

        if (StringUtils.isBlank(activity)) {
            throw new IllegalArgumentException("activity is blank");
        }

        val retObj = pjp.proceed(args);

        val userId = Optional.ofNullable(CurrentUserProvider.getCurrentUser().getUid()).orElse("0");
        val tenantId = TenantManager.tenantId("0");
        val ip = GsvcContextHolder.getRemoteIp();
        val builder = com.apzda.cloud.audit.proto.AuditLog.newBuilder();

        builder.setTimestamp(System.currentTimeMillis());
        builder.setUserid(userId);
        builder.setActivity(activity);
        builder.setTenantId(tenantId);
        builder.setIp(ip);
        builder.setLevel(StringUtils.defaultIfBlank(ann.level(), "info"));
        val context = new StandardEvaluationContext(pjp.getTarget());
        context.setVariable("returnObj", retObj);
        val parameters = method.getParameters();
        var i = 0;
        for (Parameter parameter : parameters) {
            val name = parameter.getName();
            context.setVariable(name, args[i++]);
        }
        if (ann.async()) {
            CompletableFuture.runAsync(() -> {
                audit(ann, context, builder);
            });
        }
        else {
            audit(ann, context, builder);
        }
        return retObj;
    }

    private void audit(AuditLog ann, StandardEvaluationContext context,
            com.apzda.cloud.audit.proto.AuditLog.Builder builder) {
        try {
            val template = ann.template();
            val message = ann.message();
            if (StringUtils.isNotBlank(message)) {
                val evaluate = message.startsWith("#{") && message.endsWith("}");
                if (evaluate) {
                    val expression = expressionParser.parseExpression(message, parserContext);
                    val msg = expression.getValue(context, String.class);
                    builder.setMessage(msg);
                }
                else {
                    builder.setMessage(message);
                }
            }
            else if (StringUtils.isNotBlank(template)) {
                builder.setTemplate(true);
                builder.setMessage(template);
                val arg = ann.args();
                var idx = 0;
                for (String value : arg) {
                    val expression = expressionParser.parseExpression(value);
                    builder.addArg(com.apzda.cloud.audit.proto.Arg.newBuilder()
                        .setIndex(idx++)
                        .setValue(expression.getValue(context, String.class)));
                }
            }
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
    }

}
