using System.Text;

namespace ConnectionTester.Core;

/// <summary>
/// 提权子进程的入口：--fw add|query|remove --port 8080 --out 结果文件
/// 结果写文件是因为 runas 启动的进程无法重定向标准输出。
/// </summary>
public static class FirewallCli
{
    public static int Run(string[] args)
    {
        string op = args.Length > 1 ? args[1] : "";
        int port = 8080;
        string? outFile = null;

        for (int i = 2; i < args.Length; i++)
        {
            switch (args[i])
            {
                case "--port" when i + 1 < args.Length:
                    int.TryParse(args[++i], out port);
                    break;
                case "--out" when i + 1 < args.Length:
                    outFile = args[++i];
                    break;
            }
        }

        FirewallResult result;
        try
        {
            result = op switch
            {
                "add" => FirewallService.DoAdd(port),
                "remove" => FirewallService.DoRemove(port),
                "query" => FirewallService.DoQuery(port),
                _ => new FirewallResult(false, $"未知操作：{op}")
            };
        }
        catch (Exception ex)
        {
            result = new FirewallResult(false, "执行异常：" + ex.Message);
        }

        if (!string.IsNullOrEmpty(outFile))
        {
            try { File.WriteAllText(outFile, result.Message, new UTF8Encoding(false)); }
            catch { /* 写不进去就只能靠退出码 */ }
        }

        return result.Ok ? 0 : 1;
    }
}
