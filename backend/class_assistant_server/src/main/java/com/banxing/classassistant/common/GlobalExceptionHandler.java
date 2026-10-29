package com.banxing.classassistant.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Lombok 注解：自动生成 log 日志对象，类中可直接使用 log.info()/log.error() 打日志
@Slf4j
// 全局异常处理器：捕获所有 Controller 抛出的异常，并以 JSON 格式统一返回结果
@RestControllerAdvice
// 有了这个，业务代码里只管 `throw`，不用每个方法写 try-catch，统一在方法内处理。
public class GlobalExceptionHandler {

    // 当 Controller 抛出 BizException 时，由本方法统一处理
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        // 链式反应，调用status获取当前状态码装入body最后封装成Result
        return ResponseEntity.status(statusOf(e.getCode())).body(Result.error(e.getCode(),e.getMessage()));
    }

    // 处理 @Valid 参数校验失败的异常（如 DTO 字段不满足 @NotBlank/@Size 等规则时自动抛出）参数不满足规则如为null或""情况
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // var是局部变量自动推断，getBindingResult获取“校验结果单”，即错误详细信息，.getFieldError取出第一个出错字段的详情，有null的可能性，所以下一行判断
        var fieldError = e.getBindingResult().getFieldError();
        // 有具体字段错误则取其提示信息（注解里写的 message），否则用兜底文案即校验注解里写的默认message
        String msg = fieldError == null ? "参数不合法" : fieldError.getDefaultMessage();
        // ResponseEntity.badRequest()是快捷写法，等价于400，HTTP状态码400（给网络层/框架看的粗分类）+body里的业务码100（给前端JS看的细分类。小程序端一般看body里的code和message来弹提示HTTP状态码则用于拦截器等基础设施判断。
        return ResponseEntity.badRequest().body(Result.error(1001,msg));
    }

    // 处理请求体解析失败：JSON 写错、日期格式不对（"2026/10/25"）、body 是空字符串……
    // 不加这个方法的话会掉进下面的 Exception 兜底，返回 5000「服务器开小差了」，
    // 但这是调用方的输入问题，不是服务器出错，前端也不该提示用户「稍后再试」
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(1001, "请求参数格式不正确"));
    }

    // 处理"接口路径不存在"的异常。必须放在 Exception 兜底之前，
    // 否则访问一个写错的 URL 会被下面的 Exception 方法吃掉，返回 200 + 5000，
    // 排查时会误以为是服务器出错，其实是路径打错了
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        // 1004 对应契约里的"资源未找到"，HTTP 状态返回 404
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(1004, "接口不存在"));
    }

    // 处理路径变量 / 查询参数类型对不上：/student/abc 里的 abc 转不成 Long。
    // 不加这个方法会掉进 Exception 兜底返回 5000「服务器开小差了」，
    // 但这是调用方把 URL 拼错了，不是服务器坏了。
    // 注意 MethodArgumentTypeMismatchException 是 TypeMismatchException 的子类，
    // 只处理这一个就够，@RequestParam 传错类型也走这里
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} = {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(Result.error(1001, "参数格式不正确"));
    }

    // 上传的文件超过 spring.servlet.multipart.max-file-size 时，Spring 在进 Controller 之前就抛这个异常。
    // 不加这个方法会掉进 Exception 兜底返回 5000「服务器开小差了」，
    // 但其实是老师传了个大文件，应该直接告诉他「文件太大了」
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(1001, "文件太大了，请控制在 5MB 以内"));
    }

    // 处理其他未预见的异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleother(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.ok(Result.error(5000,"服务器开小差了"));
    }

    private HttpStatus statusOf(int code){
        return switch (code) {
            case 1002 -> HttpStatus.UNAUTHORIZED; // 未登录/token失效
            case 1003,3001,3002 -> HttpStatus.FORBIDDEN; // 无访问权限
            case 1001,2001 -> HttpStatus.BAD_REQUEST; // 参数不合法
            case 1004 -> HttpStatus.NOT_FOUND; // 资源未找到
            case 1006 -> HttpStatus.CONFLICT; // 冲突：同名考试已存在等
            case 1005 -> HttpStatus.TOO_MANY_REQUESTS; // 请求过于频繁
            case 2002 -> HttpStatus.BAD_GATEWAY; // 服务端错误
            default -> HttpStatus.OK; // 其他情况返回200
        };
    }
}