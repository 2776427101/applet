using System.Text;

namespace ConnectionTester.Core;

public static class EnvWriter
{
    public const string Key = "VITE_API_BASE_URL";

    public static string BuildLine(string url) => $"{Key}={url}";

    public static string BuildUrl(string ip, int port) => $"http://{ip}:{port}/api";

    /// <summary>
    /// 只替换 .env.production 里以 VITE_API_BASE_URL 开头的那一行，其余内容（含注释）原样保留。
    /// 写之前先生成 .env.production.bak。使用 UTF-8 无 BOM 写回，避免 dotenv 解析出错。
    /// </summary>
    public static bool TryWrite(string envPath, string url, out string message, out string backupPath)
    {
        backupPath = "";
        message = "";

        if (string.IsNullOrWhiteSpace(envPath) || !File.Exists(envPath))
        {
            message = $"配置文件不存在：{envPath}";
            return false;
        }

        try
        {
            var raw = File.ReadAllText(envPath, Encoding.UTF8);
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

            backupPath = envPath + ".bak";
            File.Copy(envPath, backupPath, overwrite: true);

            var content = string.Join(newline, lines);
            File.WriteAllText(envPath, content, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));

            message = replaced
                ? $"已替换 {Key} 一行"
                : $"原文件没有 {Key}，已在末尾追加一行";
            return true;
        }
        catch (UnauthorizedAccessException)
        {
            message = "没有写入权限（文件被占用或以只读方式打开）";
            return false;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
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
