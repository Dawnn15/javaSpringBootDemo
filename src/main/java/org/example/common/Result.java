package org.example.common;

import lombok.Data;

/**
 * 统一响应体
 *
 * 所有接口都返回这个结构，前端只需要判断 code 就行：
 *   { "code": 200, "message": "success", "data": {...} }
 *
 * @param <T> data 字段的实际类型
 */
@Data
public class Result<T> {

    /** 业务状态码：200 成功，400 参数错误，500 服务端错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 真正的数据 */
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
