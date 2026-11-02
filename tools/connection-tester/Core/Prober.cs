using System.Diagnostics;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography.X509Certificates;

namespace ConnectionTester.Core;

/// <summary>
/// 四段式探测：DNS → TCP → TLS(可选) → HTTP。
/// 每一段独立超时，失败时给出「卡在哪一段」，而不是笼统的「连不上」。
/// </summary>
public static class Prober
{
    private static readonly TimeSpan DnsTimeout = TimeSpan.FromSeconds(6);
    private static readonly TimeSpan TcpTimeout = TimeSpan.FromSeconds(6);
    private static readonly TimeSpan TlsTimeout = TimeSpan.FromSeconds(8);
    private static readonly TimeSpan HttpTimeout = TimeSpan.FromSeconds(10);

    public static async Task<ProbeReport> RunAsync(
        string scheme, string host, int port, string path,
        Action<string> log, CancellationToken ct = default)
    {
        scheme = string.IsNullOrWhiteSpace(scheme) ? "https" : scheme.Trim().ToLowerInvariant();
        host = (host ?? "").Trim();
        if (!path.StartsWith('/')) path = "/" + path;

        var report = new ProbeReport { Url = $"{scheme}://{host}:{port}{path}" };
        log($"目标: {report.Url}");

        if (string.IsNullOrWhiteSpace(host))
        {
            report.Level = ProbeLevel.Fail;
            report.Verdict = "没有填写域名/IP";
            report.Advice = "先在左侧填入隧道域名（如 frp-sea.com）或局域网 IP（如 10.0.0.5）。";
            return report;
        }

        // ── 1. DNS ─────────────────────────────────────────────
        var sw = Stopwatch.StartNew();
        var dns = report.AddStep("DNS 解析");
        IPAddress[] addresses;
        try
        {
            addresses = await Dns.GetHostAddressesAsync(host).WaitAsync(DnsTimeout, ct);
            dns.ElapsedMs = sw.ElapsedMilliseconds;
            if (addresses.Length == 0)
            {
                dns.Ok = false;
                dns.Detail = "解析结果为空";
            }
            else
            {
                dns.Ok = true;
                dns.Detail = string.Join(", ", addresses.Take(3).Select(a => a.ToString()))
                             + (addresses.Length > 3 ? $" 等 {addresses.Length} 个" : "");
            }
        }
        catch (TimeoutException)
        {
            dns.ElapsedMs = sw.ElapsedMilliseconds;
            dns.Ok = false;
            dns.Detail = "超时（6s）";
        }
        catch (Exception ex)
        {
            dns.ElapsedMs = sw.ElapsedMilliseconds;
            dns.Ok = false;
            dns.Detail = ex.Message;
        }

        log($"  [{(dns.Ok ? "OK" : "FAIL")}] {dns.Name}: {dns.Detail} ({dns.ElapsedMs}ms)");

        // IP 直连时 DNS 解析失败是正常的，不中断
        bool isIpLiteral = IPAddress.TryParse(host, out _);
        if (!dns.Ok && !isIpLiteral)
        {
            report.Level = ProbeLevel.Fail;
            report.Verdict = "域名解析失败";
            report.Advice = "检查域名是否拼错；隧道地址可能已变（SakuraFrp 每次重启端口会变）。";
            return report;
        }

        // ── 2. TCP ─────────────────────────────────────────────
        sw.Restart();
        var tcpStep = report.AddStep("TCP 连接");
        var tcp = new TcpClient();
        try
        {
            await tcp.ConnectAsync(host, port).WaitAsync(TcpTimeout, ct);
            tcpStep.Ok = true;
            tcpStep.ElapsedMs = sw.ElapsedMilliseconds;
            tcpStep.Detail = $"端口 {port} 可连";
        }
        catch (TimeoutException)
        {
            tcpStep.Ok = false;
            tcpStep.ElapsedMs = sw.ElapsedMilliseconds;
            tcpStep.Detail = "超时（6s）";
        }
        catch (SocketException ex)
        {
            tcpStep.Ok = false;
            tcpStep.ElapsedMs = sw.ElapsedMilliseconds;
            tcpStep.Detail = $"{ex.SocketErrorCode}";
        }
        catch (Exception ex)
        {
            tcpStep.Ok = false;
            tcpStep.ElapsedMs = sw.ElapsedMilliseconds;
            tcpStep.Detail = ex.Message;
        }

        log($"  [{(tcpStep.Ok ? "OK" : "FAIL")}] {tcpStep.Name}: {tcpStep.Detail} ({tcpStep.ElapsedMs}ms)");

        if (!tcpStep.Ok)
        {
            tcp.Dispose();
            report.Level = ProbeLevel.Fail;
            report.Verdict = "端口连不上";
            report.Advice = scheme == "http"
                ? "排查顺序：① 后端是否在跑（curl 127.0.0.1:8080）② Windows 防火墙是否放行该端口 ③ 手机和电脑是否同一网段 ④ IP 是否已变化"
                : "排查顺序：① 隧道是否在运行 ② 地址端口是否抄错 ③ 电脑防火墙是否拦了本地端口";
            return report;
        }

        // ── 3. TLS（仅 https）───────────────────────────────────
        if (scheme == "https")
        {
            sw.Restart();
            var tlsStep = report.AddStep("TLS 证书");
            X509Certificate2? cert = null;
            SslPolicyErrors policyErrors = SslPolicyErrors.None;

            try
            {
                using var ssl = new SslStream(tcp.GetStream(), false, (_, c, _, errors) =>
                {
                    policyErrors = errors;
                    if (c != null) cert = c as X509Certificate2 ?? new X509Certificate2(c!);
                    return true; // 一律放行，由我们自己判定并提示
                });

                await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
                {
                    TargetHost = host
                }).WaitAsync(TlsTimeout, ct);

                tlsStep.ElapsedMs = sw.ElapsedMilliseconds;

                if (cert == null)
                {
                    tlsStep.Ok = false;
                    tlsStep.Detail = "握手成功但拿不到证书";
                }
                else
                {
                    bool selfSigned = string.Equals(cert.Subject, cert.Issuer, StringComparison.OrdinalIgnoreCase);
                    bool untrusted = selfSigned || policyErrors != SslPolicyErrors.None;

                    report.CertUntrusted = untrusted;
                    report.CertSummary = $"subject={cert.Subject} | issuer={cert.Issuer} | 到期 {cert.NotAfter:yyyy-MM-dd}";

                    tlsStep.Ok = true;
                    tlsStep.Detail = selfSigned
                        ? $"自签证书（发行者=使用者），微信真机会拒"
                        : policyErrors != SslPolicyErrors.None
                            ? $"证书链有问题: {policyErrors}"
                            : $"受信任，到期 {cert.NotAfter:yyyy-MM-dd}";
                }
            }
            catch (TimeoutException)
            {
                tlsStep.Ok = false;
                tlsStep.ElapsedMs = sw.ElapsedMilliseconds;
                tlsStep.Detail = "握手超时（8s）";
            }
            catch (Exception ex)
            {
                tlsStep.Ok = false;
                tlsStep.ElapsedMs = sw.ElapsedMilliseconds;
                tlsStep.Detail = $"握手失败: {ex.GetBaseException().Message}";
            }

            log($"  [{(tlsStep.Ok ? "OK" : "FAIL")}] {tlsStep.Name}: {tlsStep.Detail} ({tlsStep.ElapsedMs}ms)");
            if (report.CertSummary.Length > 0) log($"        {report.CertSummary}");
        }

        tcp.Dispose();

        // ── 4. HTTP ────────────────────────────────────────────
        sw.Restart();
        var httpStep = report.AddStep("HTTP 请求");
        string body = "";
        try
        {
            using var handler = new HttpClientHandler
            {
                ServerCertificateCustomValidationCallback = (_, _, _, _) => true,
                AllowAutoRedirect = true,
                // 必须绕过系统代理：本工具要回答的是「手机能不能直连到我」，
                // 手机不会走电脑上的代理软件。若电脑开了梯子/抓包工具还照代理走，
                // 测通的结果是假的，反而误导排查。
                UseProxy = false
            };
            using var client = new HttpClient(handler) { Timeout = HttpTimeout };
            using var request = new HttpRequestMessage(HttpMethod.Get, report.Url);
            request.Headers.TryAddWithoutValidation("User-Agent", "ConnectionTester/1.0");

            using var resp = await client.SendAsync(request, ct).WaitAsync(HttpTimeout + TimeSpan.FromSeconds(2), ct);
            report.HttpStatus = (int)resp.StatusCode;
            body = await resp.Content.ReadAsStringAsync(ct);

            httpStep.Ok = report.HttpStatus < 500;
            httpStep.ElapsedMs = sw.ElapsedMilliseconds;
            httpStep.Detail = $"HTTP {report.HttpStatus} · {Trim(body, 90)}";
        }
        catch (TimeoutException)
        {
            httpStep.Ok = false;
            httpStep.ElapsedMs = sw.ElapsedMilliseconds;
            httpStep.Detail = "请求超时（10s）";
        }
        catch (Exception ex)
        {
            httpStep.Ok = false;
            httpStep.ElapsedMs = sw.ElapsedMilliseconds;
            httpStep.Detail = ex.GetBaseException().Message;
        }

        log($"  [{(httpStep.Ok ? "OK" : "FAIL")}] {httpStep.Name}: {httpStep.Detail} ({httpStep.ElapsedMs}ms)");
        if (body.Length > 0) log($"        响应体: {Trim(body, 200)}");

        // ── 5. 结论 ────────────────────────────────────────────
        BuildVerdict(report, body);
        log("");
        log($"结论: {report.Verdict}");
        if (report.Advice.Length > 0) log($"建议: {report.Advice}");
        return report;
    }

    private static void BuildVerdict(ProbeReport report, string body)
    {
        int code = report.HttpStatus;
        bool backendAlive = body.Contains("\"code\":1002", StringComparison.Ordinal)
                            || body.Contains("\"code\":0", StringComparison.Ordinal);

        if (code == 501)
        {
            report.Level = ProbeLevel.Fail;
            report.Verdict = "被中转节点拦截（501）";
            report.Advice = "SakuraFrp 的节点禁止明文 HTTP。在隧道设置里启用「自动 HTTPS」，并改用 https:// 访问。";
            return;
        }

        if (code == 200 || backendAlive)
        {
            report.Level = report.CertUntrusted ? ProbeLevel.Warn : ProbeLevel.Ok;
            report.Verdict = report.CertUntrusted
                ? "链路已通，但证书会被微信拒绝"
                : "链路已通";
            report.Advice = report.CertUntrusted
                ? "微信真机强制校验证书：自签证书会报 ERR_CERT_AUTHORITY_INVALID，开「打开调试」也不管用。需换成受信任证书（Cloudflare / cpolar 等）。"
                : "";
            return;
        }

        if (code == 404)
        {
            report.Level = ProbeLevel.Warn;
            report.Verdict = "服务已通，但路径不对（404）";
            report.Advice = "核对测试路径，例如 /api/teacher/home/overview（注意是 home 不是 homr）。";
            return;
        }

        if (code == 401 || code == 403)
        {
            report.Level = ProbeLevel.Warn;
            report.Verdict = $"服务已通，返回 {code}（未带登录态）";
            report.Advice = "后端活着，只是没有 token。换一个不需要登录的路径，或直接用抓到的业务响应做判断。";
            return;
        }

        if (code >= 500)
        {
            report.Level = ProbeLevel.Fail;
            report.Verdict = $"后端返回 {code}";
            report.Advice = "看后端控制台日志；确认数据库连接、配置文件是否正常。";
            return;
        }

        if (code == 0)
        {
            report.Level = ProbeLevel.Fail;
            report.Verdict = "HTTP 请求没发出去";
            report.Advice = "检查地址是否正确、协议是否与服务器匹配（http 连到 https 端口会失败）。";
            return;
        }

        report.Level = ProbeLevel.Warn;
        report.Verdict = $"返回 HTTP {code}";
        report.Advice = "服务可达，但响应不符合预期。核对状态码与响应体。";
    }

    private static string Trim(string s, int max)
    {
        s = s.Replace("\r", " ").Replace("\n", " ").Trim();
        return s.Length <= max ? s : s[..max] + "…";
    }
}
