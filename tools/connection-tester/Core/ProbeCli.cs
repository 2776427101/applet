using System.Text;

namespace ConnectionTester.Core;

/// <summary>
/// 无界面自检入口：--probe --url "https://host:port/api/xxx" --out 结果文件
/// 用于批量验证 / CI 自检，也方便排查时把结果直接贴出来。
/// </summary>
public static class ProbeCli
{
    public static int Run(string[] args)
    {
        string? url = null, outFile = null;

        for (int i = 1; i < args.Length; i++)
        {
            switch (args[i])
            {
                case "--url" when i + 1 < args.Length:
                    url = args[++i];
                    break;
                case "--out" when i + 1 < args.Length:
                    outFile = args[++i];
                    break;
            }
        }

        var sb = new StringBuilder();

        if (string.IsNullOrWhiteSpace(url) || !Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            sb.AppendLine("用法: ConnectionTester.exe --probe --url \"https://host:port/api/path\" --out 结果文件");
            Write(sb.ToString(), outFile);
            return 2;
        }

        var lines = new List<string>();
        ProbeReport report;
        try
        {
            report = Prober.RunAsync(
                    uri.Scheme,
                    uri.Host,
                    uri.Port,
                    uri.PathAndQuery,
                    l => lines.Add(l))
                .GetAwaiter().GetResult();
        }
        catch (Exception ex)
        {
            sb.AppendLine("探测异常：" + ex.Message);
            Write(sb.ToString(), outFile);
            return 3;
        }

        // 结论/建议在下面统一输出，这里跳过，避免重复
        foreach (var l in lines)
        {
            if (l.StartsWith("结论") || l.StartsWith("建议")) continue;
            sb.AppendLine(l);
        }
        sb.AppendLine();
        sb.AppendLine($"结果: {report.Level}");
        sb.AppendLine($"结论: {report.Verdict}");
        if (report.Advice.Length > 0) sb.AppendLine($"建议: {report.Advice}");
        if (report.CertSummary.Length > 0) sb.AppendLine($"证书: {report.CertSummary}");

        Write(sb.ToString(), outFile);
        return report.Level == ProbeLevel.Fail ? 1 : 0;
    }

    private static void Write(string text, string? outFile)
    {
        if (!string.IsNullOrEmpty(outFile))
        {
            try { File.WriteAllText(outFile, text, new UTF8Encoding(false)); } catch { }
        }
        else
        {
            Console.Out.Write(text);
        }
    }
}
