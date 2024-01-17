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
package cn.apzda.cloud.audit.service;

import com.apzda.cloud.audit.aop.AuditLog;
import com.apzda.cloud.gsvc.ext.GsvcExt;
import lombok.val;
import org.springframework.stereotype.Service;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@Service
public class DemoService {

    @AuditLog(activity = "test", template = "error message is {}", args = "#returnObj.errMsg")
    public GsvcExt.CommonRes hello() {
        val builder = GsvcExt.CommonRes.newBuilder();
        builder.setErrCode(1);
        builder.setErrMsg("error message");
        return builder.build();
    }

    @AuditLog(activity = "test", template = "error message is {}, arg is {}", args = { "#returnObj.errMsg", "#msg" })
    public GsvcExt.CommonRes hello(String msg) {
        val builder = GsvcExt.CommonRes.newBuilder();
        builder.setErrCode(1);
        builder.setErrMsg("error " + msg);
        return builder.build();
    }

}
