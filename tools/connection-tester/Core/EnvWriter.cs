using System.Text;

namespace ConnectionTester.Core;

public enum WriteFailure
{
    None,
    FileMissing,
    ReadOnlyFile,
    NoPermission,
    FileLocked,
    Unknown
}

/// <summary>
/// 写入结果。Suggestion 是给用户看的「下一步怎么办」——
/// 光说「没有权限」等于把人晾在那儿，必须告诉他具体点哪里。
/// </summary>
public sealed record WriteOutcome(
    bool Ok,
    string Message,
    string BackupPath,
    WriteFailure Failure,
    string Suggestion);

public static class EnvWriter
{
    public const string Key = "VITE_API_BASE_URL";

    public static string BuildLine(string url) => $"{Key}={url}";

    public static string BuildUrl(string ip, int port) => $"http://{ip}:{port}/api";

    private static WriteOutcome Fail(WriteFailure kind, string message, string suggestion)
        => new(false, message, "", kind, suggestion);

    /// <summary>
    /// 只替换 .env.production 里以 VITE_API_BASE_URL 开头的那一行，其余内容（含注释）原样保留。
    /// 写之前先生成 .env.production.bak。使用 UTF-8 无 BOM 写回，避免 dotenv 解析出错。
    /// </summary>
    public static WriteOutcome TryWrite(string envPath, string url)
    {
        // ── 预检 1：路径为空 ────────────────────────────────────
        // 使用者打开工具时通常根本没有仓库，这一步会走到这里。
        // 不要让他去「找配置文件」，直接告诉他这步可以跳过。
        if (string.IsNullOrWhiteSpace(envPath))
            return Fail(WriteFailure.FileMissing, "你还没有选择配置文件",
                "如果你只是想测试连接，这一步不需要做 —— 直接点右边的「开始测试」就行。\r\n" +
                "只有当你需要重新构建小程序时，才点「浏览」选中 .env.production。");

        // ── 预检 2：文件是否存在 ──────────────────────────────
        if (!File.Exists(envPath))
            return Fail(WriteFailure.FileMissing, $"文件不存在：{envPath}",
                "路径可能已经失效（移动过仓库、换了电脑、或从别人的压缩包里解出来的）。\r\n" +
                "点「浏览」重新选一次 .env.production。");

        // ── 预检 3：只读属性（从 ZIP 解压或别人拷来的文件常见）──
        bool autoClearedReadOnly = false;
        try
        {
            var attr = File.GetAttributes(envPath);
            if ((attr & FileAttributes.ReadOnly) != 0)
            {
                File.SetAttributes(envPath, attr & ~FileAttributes.ReadOnly);
                autoClearedReadOnly = true;
            }
        }
        catch (UnauthorizedAccessException)
        {
            return Fail(WriteFailure.ReadOnlyFile, "文件被设为只读，自动取消失败",
                "手动处理：右键这个文件 → 属性 → 取消勾选「只读」→ 确定，然后重新点「写入配置」。\r\n" +
                "如果整个文件夹都是只读，在文件夹上右键 → 属性 → 取消只读 → 应用到所有子文件夹和文件。");
        }

        // ── 预检 4：能否读取 ──────────────────────────────────
        string raw;
        try
        {
            raw = File.ReadAllText(envPath, Encoding.UTF8);
        }
        catch (UnauthorizedAccessException)
        {
            return Fail(WriteFailure.NoPermission, "连读取都被拒绝，目录权限不足",
                "把整个仓库换到一个普通目录再试，例如 D:\\code\\class-assistant。\r\n" +
                "不要放在 C:\\Program Files、C:\\Windows 或需要管理员才能进入的位置。");
        }
        catch (IOException ex)
        {
            return Fail(WriteFailure.FileLocked, "文件被占用：" + ex.Message,
                "关掉正在打开这个文件的程序（VS Code、记事本、OneDrive / 坚果云同步）再试。");
        }

        // ── 替换目标行 ────────────────────────────────────────
        string newline = raw.Contains("\r\n") ? "\r\n" : "\n";
        var lines = raw.Replace("\r\n", "\n").Split('\n').ToList();

        bool replaced = false;
        for (int i = 0; i < lines.Count; i++)
        {
            if (lines[i].TrimStart().StartsWith(Key + "=", StringComparison.Ordinal))
            {
                lines[i] = BuildLine(url);
                replaced = true;
                break;
            }
        }
        if (!replaced) lines.Add(BuildLine(url));

        // ── 备份：失败不阻断写入，只降级为警告 ──────────────────
        // 目录不允许新建文件时（受保护目录 / 受控文件夹访问），
        // 卡在备份这一步会让用户完全没法往下走，所以这里不 return。
        string backupPath = envPath + ".bak";
        string? backupWarning = null;
        try
        {
            if (File.Exists(backupPath))
                File.SetAttributes(backupPath, FileAttributes.Normal);
            File.Copy(envPath, backupPath, overwrite: true);
        }
        catch (Exception ex)
        {
            backupPath = "";
            backupWarning = ex is UnauthorizedAccessException
                ? "所在目录不允许新建文件，已跳过备份"
                : "备份失败：" + ex.Message;
        }

        // ── 真正写入 ──────────────────────────────────────────
        var content = string.Join(newline, lines);
        try
        {
            File.WriteAllText(envPath, content, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        }
        catch (UnauthorizedAccessException)
        {
            // 恢复只读属性，保持原样，避免"改了一半"
            if (autoClearedReadOnly) TryRestoreReadOnly(envPath);
            return Fail(WriteFailure.NoPermission, "写入被系统拒绝",
                "按可能性排查：\r\n" +
                "① 目录在受保护位置（C:\\Program Files 等）→ 把仓库换到 D:\\code\r\n" +
                "② 文件被别的程序占用 → 关掉编辑器和同步盘再试\r\n" +
                "③ Windows 安全中心 → 病毒和威胁防护 → 勒索软件防护 →「受控文件夹访问」\r\n" +
                "   把这个 exe 加进允许列表，或临时关闭该功能\r\n" +
                "④ 上面都排除后，用记事本手动改这一行：" + BuildLine(url));
        }
        catch (IOException ex)
        {
            if (autoClearedReadOnly) TryRestoreReadOnly(envPath);
            return Fail(WriteFailure.FileLocked, "文件被占用：" + ex.Message,
                "关掉正在打开这个文件的程序（VS Code、记事本、OneDrive / 坚果云同步）再试。");
        }
        catch (Exception ex)
        {
            if (autoClearedReadOnly) TryRestoreReadOnly(envPath);
            return Fail(WriteFailure.Unknown, ex.Message,
                "用记事本手动改这一行：" + BuildLine(url));
        }

        // ── 成功 ──────────────────────────────────────────────
        var msg = replaced
            ? $"已替换 {Key} 一行"
            : $"原文件没有 {Key}，已在末尾追加一行";
        if (autoClearedReadOnly) msg += "（已自动取消该文件的只读属性）";
        if (backupWarning != null) msg += "；" + backupWarning;

        return new WriteOutcome(true, msg, backupPath, WriteFailure.None, "");
    }

    private static void TryRestoreReadOnly(string path)
    {
        try { File.SetAttributes(path, File.GetAttributes(path) | FileAttributes.ReadOnly); }
        catch { /* 恢复不了就算了，不影响主流程 */ }
    }

    public static string? ReadCurrentValue(string envPath)
    {
        try
        {
            if (!File.Exists(envPath)) return null;
            foreach (var line in File.ReadAllLines(envPath, Encoding.UTF8))
            {
                var t = line.Trim();
                if (t.StartsWith(Key + "=", StringComparison.Ordinal))
                    return t[(Key.Length + 1)..];
            }
        }
        catch { /* 读不到就算了 */ }
        return null;
    }
}
