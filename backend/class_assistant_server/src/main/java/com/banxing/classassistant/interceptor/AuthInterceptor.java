package com.banxing.classassistant.interceptor;

import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 登录鉴权拦截器
 * 在请求进入 Controller 之前校验 JWT 令牌，确认用户已登录并解析出身份
 *
 * <p>现在有两类身份，靠 token 里的 role 区分：
 * <ul>
 *   <li>role=1 老师（t_user，有账号）</li>
 *   <li>role=2 学生（t_student，花名册里的一行，没有账号）</li>
 * </ul>
 *
 * <p>学生这一类里还分两种：<b>已入班</b>（带 classId）和<b>未入班</b>（v0.41，没有 classId）。
 * 后者只能访问 {@link #GUEST_ALLOWED} 里的两个接口，其余一律 1009 ——
 * 没有 classId 的 token 本来也查不出任何班级数据，但拦在这里能给学生一句准确的话，
 * 而不是让他看到"登录失效"。
 *
 * <p><b>按路径双向卡死</b>，不只是"解析出 id 就放行"：
 * <ul>
 *   <li>/api/teacher/** 只许 role=1</li>
 *   <li>/api/student/** 只许 role=2</li>
 * </ul>
 * 为什么必须这样：教师接口到处在用 {@code request.getAttribute("userId")}，
 * 如果学生 token 也往 userId 里塞学生 id，学生就能以"用户 121"的身份调教师接口 ——
 * 而 t_user 里恰好也可能存在 id=121 的老师。这类越权最难发现，因为接口全"正常返回"。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 还没入班的学生能访问的接口。白名单，不是黑名单 ——
     * 将来新增学生接口时，默认是"进不去"，而不是"忘了拦"。
     */
    private static final Set<String> GUEST_ALLOWED = Set.of(
            "/api/student/join",
            "/api/student/me"
    );

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BizException(1002, "请先登录");
        }

        Claims claims = jwtUtil.parseClaims(auth.substring(7));
        Integer role = claims.get("role", Integer.class);
        int r = role == null ? 1 : role;

        String path = request.getRequestURI();

        if (path.startsWith("/api/teacher")) {
            if (r != 1) {
                throw new BizException(3001, "这是教师端的功能");
            }
            /* 只给老师塞 userId。学生 token 走到这里已经被上面拦掉了，
               再塞一次没有意义，反而给未来的分支留了坑 */
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            return true;
        }

        if (path.startsWith("/api/student")) {
            if (r != 2) {
                throw new BizException(1002, "请先用学号登录学生端");
            }

            /* 学号和姓名两个 claim 是 v0.41 加的：还没入班的学生只有这两样，
               入班时要靠它们决定"给谁建花名册行"。老 token 没有这两个 claim，
               取出来是 null —— 那种 token 一定有 classId，走不到下面的入班分支 */
            String studentNo = claims.get("studentNo", String.class);
            String name = claims.get("name", String.class);
            if (studentNo != null) {
                request.setAttribute("studentNo", studentNo);
            }
            if (name != null) {
                request.setAttribute("name", name);
            }

            Number classId = claims.get("classId", Number.class);

            /* 没有 classId = 这个学生还没进任何班（登录时花名册里查不到他）。
               这种 token 只能用来入班和问"我是谁"，其余接口全部 1009。

               为什么不在各个业务方法里判断：那些方法签名都是 (classId, studentId)，
               classId 为 null 时会先在 requireStudent 里抛 1002 —— 而 1002 在前端
               等于"登录失效，踢回入口页"。学生明明登录成功了却被踢出去，
               是最让人困惑的一种失败。在这里挡住，前端能拿到一句准确的话。 */
            if (classId == null) {
                if (!GUEST_ALLOWED.contains(path)) {
                    throw new BizException(1009, "你还没加入班级，先填班主任给的邀请码");
                }
                return true;
            }

            request.setAttribute("studentId", Long.valueOf(claims.getSubject()));
            request.setAttribute("classId", classId.longValue());
            return true;
        }

        /* 其余 /api/**（例如 /api/auth/me）沿用老行为：按老师处理 */
        if (r != 1) {
            throw new BizException(3001, "请使用对应的端登录");
        }
        request.setAttribute("userId", Long.valueOf(claims.getSubject()));
        return true;
    }
}
