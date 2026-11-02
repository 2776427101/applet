using System.Diagnostics;
using System.Security.Principal;
using System.Text;
using System.Text.Json;

namespace ConnectionTester.Core;

public sealed record FirewallResult(bool Ok, string Message);

/// <summary>
/// 防火墙操作全部封装成「独立进程 + 提权」的方式：
/// 主程序不需要管理员权限，只有真正改规则的那一刻才弹 UAC。
/// </summary>
public static class FirewallService
{
    public static bool IsAdmin
    {
        get
        {
            using var id = WindowsIdentity.GetCurrent();
            return new WindowsPrincipal(id).IsInRole(WindowsBuiltInRole.Administrator);
        }
    }

    public static string RuleName(int port) => $"班级助手-{port}";

    // ── 实际的执行体（在提权子进程里调用）──────────────────────

    public static FirewallResult DoAdd(int port)
    {
        if (!IsAdmin) return new FirewallResult(false, "子进程没有拿到管理员权限");

        var name = RuleName(port);
        // 先删同名规则，避免重复添加后一片混乱
        RunNetsh($"advfirewall firewall delete rule name=\"{name}\"");
        var (code, output) = RunNetsh(
            $"advfirewall firewall add rule name=\"{name}\" dir=in action=allow protocol=TCP localport={port}");

        return code == 0
            ? new FirewallResult(true, $"已开放入站 TCP {port}（规则名：{name}）")
            : new FirewallResult(false, $"添加失败（exit={code}）：{output}");
    }

    public static FirewallResult DoRemove(int port)
    {
        if (!IsAdmin) return new FirewallResult(false, "子进程没有拿到管理员权限");

        var name = RuleName(port);
        var (code, output) = RunNetsh($"advfirewall firewall delete rule name=\"{name}\"");
        return code == 0
            ? new FirewallResult(true, $"已删除规则：{name}")
            : new FirewallResult(false, $"删除失败（exit={code}）：{output}");
    }

    public static FirewallResult DoQuery(int port)
    {
        if (!IsAdmin) return new FirewallResult(false, "子进程没有拿到管理员权限");

        var script =
            "[Console]::OutputEncoding=[Text.Encoding]::UTF8; " +
            "$ErrorActionPreference='SilentlyContinue'; " +
            $"$items = Get-NetFirewallPortFilter -Protocol TCP | Where-Object {{ $_.LocalPort -eq {port} }} | " +
            "ForEach-Object { $r = $_ | Get-NetFirewallRule; " +
            "if ($r -and $r.Direction -eq 'Inbound') { " +
            "[pscustomobject]@{ Name=$r.DisplayName; Enabled=[string]$r.Enabled; Action=[string]$r.Action; Profile=[string]$r.Profile } } }; " +
            "if ($items) { $items | ConvertTo-Json -Compress } else { '[]' }";

        var (code, output) = RunPowerShell(script);
        if (code != 0)
            return new FirewallResult(false, $"查询失败（exit={code}）：{output}");

        output = output.Trim();
        if (output.Length == 0 || output == "[]")
            return new FirewallResult(true, $"没有找到针对 TCP {port} 的入站规则");

        try
        {
            var lines = new List<string>();
            using var doc = JsonDocument.Parse(output);
            var root = doc.RootElement;
            if (root.ValueKind == JsonValueKind.Array)
            {
                foreach (var e in root.EnumerateArray()) lines.Add(Format(e));
            }
            else
            {
                lines.Add(Format(root));
            }

            var sb = new StringBuilder();
            sb.AppendLine($"找到 {lines.Count} 条针对 TCP {port} 的入站规则：");
            foreach (var l in lines) sb.AppendLine("  • " + l);
            return new FirewallResult(true, sb.ToString().TrimEnd());
        }
        catch (Exception ex)
        {
            return new FirewallResult(true, "原始输出：" + output + "（解析失败：" + ex.Message + "）");
        }

        static string Format(JsonElement e)
        {
            string Get(string n) => e.TryGetProperty(n, out var v) ? v.ToString() : "?";
            return $"{Get("Name")}  [启用={Get("Enabled")} 动作={Get("Action")} 配置文件={Get("Profile")}]";
        }
    }

    // ── 提权调度（在主进程里调用）──────────────────────────────

    /// <summary>
    /// 以管理员身份重启自身执行 --fw 子命令，结果通过临时文件回传。
    /// 用户取消 UAC 时返回 Ok=false，不抛异常。
    /// </summary>
    public static FirewallResult RunElevated(string op, int port)
    {
        var exe = Environment.ProcessPath;
        if (string.IsNullOrEmpty(exe))
            return new FirewallResult(false, "拿不到自身路径，无法提权");

        var outFile = Path.Combine(Path.GetTempPath(), $"ct-fw-{Guid.NewGuid():N}.txt");
        var psi = new ProcessStartInfo(exe, $"--fw {op} --port {port} --out \"{outFile}\"")
        {
            UseShellExecute = true,
            Verb = "runas",
            WindowStyle = ProcessWindowStyle.Hidden
        };

        try
        {
            using var p = Process.Start(psi);
            if (p == null) return new FirewallResult(false, "提权进程启动失败");
            p.WaitForExit(90_000);

            string text = "";
            try { if (File.Exists(outFile)) text = File.ReadAllText(outFile, Encoding.UTF8).Trim(); }
            catch { /* 忽略 */ }
            finally { try { File.Delete(outFile); } catch { } }

            if (text.Length == 0) text = p.ExitCode == 0 ? "完成（无输出）" : $"失败（exit={p.ExitCode}）";
            return new FirewallResult(p.ExitCode == 0, text);
        }
        catch (System.ComponentModel.Win32Exception)
        {
            return new FirewallResult(false, "已取消：没有获得管理员权限（UAC 被拒绝）");
        }
        catch (Exception ex)
        {
            return new FirewallResult(false, "提权失败：" + ex.Message);
        }
    }

    // ── 进程调用工具 ──────────────────────────────────────────

    private static (int code, string output) RunNetsh(string arguments)
        => RunProcess("netsh", arguments);

    private static (int code, string output) RunPowerShell(string script)
        => RunProcess("powershell.exe",
            "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"" + script.Replace("\"", "\\\"") + "\"");

    private static (int code, string output) RunProcess(string file, string args)
    {
        try
        {
            var psi = new ProcessStartInfo(file, args)
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };

            using var p = Process.Start(psi);
            if (p == null) return (-1, "进程启动失败");

            var stdout = p.StandardOutput.ReadToEnd();
            var stderr = p.StandardError.ReadToEnd();
            p.WaitForExit(60_000);

            var text = (stdout + (stderr.Length > 0 ? "\n" + stderr : "")).Trim();
            return (p.ExitCode, text.Length > 800 ? text[..800] : text);
        }
        catch (Exception ex)
        {
            return (-1, ex.Message);
        }
    }
}
