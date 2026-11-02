using ConnectionTester.Core;

namespace ConnectionTester;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        // 提权子进程入口：--fw add|query|remove --port 8080 --out <结果文件>
        if (args.Length > 0 && args[0] == "--fw")
            return FirewallCli.Run(args);

        // 无界面自检：--probe --url https://host:port/api/x --out <结果文件>
        if (args.Length > 0 && args[0] == "--probe")
            return ProbeCli.Run(args);

        Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new MainForm());
        return 0;
    }
}
