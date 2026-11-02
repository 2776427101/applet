using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace ConnectionTester.Core;

public sealed record NicInfo(string Name, string Ip, string Kind, bool Virtual);

public static class NetInfo
{
    private static readonly string[] VirtualKeywords =
    {
        "vmware", "vmnet", "virtualbox", "vbox", "hyper-v", "vethernet",
        "radmin", "tap", "tun", "letstap", "wsl", "docker", "loopback", "vpn"
    };

    /// <summary>枚举本机 IPv4 地址，默认隐藏虚拟网卡。</summary>
    public static List<NicInfo> GetIPv4(bool includeVirtual)
    {
        var list = new List<NicInfo>();

        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            if (ni.NetworkInterfaceType == NetworkInterfaceType.Tunnel) continue;

            IPInterfaceProperties props;
            try { props = ni.GetIPProperties(); }
            catch { continue; }

            foreach (var ua in props.UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                var ip = ua.Address.ToString();
                if (ip.StartsWith("127.") || ip.StartsWith("169.254.")) continue;

                var name = ni.Name;
                var kind = ni.NetworkInterfaceType switch
                {
                    NetworkInterfaceType.Wireless80211 => "无线",
                    NetworkInterfaceType.Ethernet => "有线",
                    NetworkInterfaceType.GigabitEthernet => "有线",
                    _ => ni.NetworkInterfaceType.ToString()
                };

                var lower = name.ToLowerInvariant();
                bool isVirtual = VirtualKeywords.Any(k => lower.Contains(k));

                if (isVirtual && !includeVirtual) continue;

                list.Add(new NicInfo(name, ip, kind, isVirtual));
            }
        }

        // 真实网卡优先，无线优先
        return list
            .OrderBy(n => n.Virtual ? 1 : 0)
            .ThenBy(n => n.Kind == "无线" ? 0 : 1)
            .ToList();
    }

    /// <summary>从 exe 所在目录逐级向上找 .env.production，方便免配置定位。</summary>
    public static string? GuessEnvPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.Parent)
        {
            // 情况一：工具在仓库内部（class-assistant/tools/connection-tester/dist/）
            var inside = Path.Combine(
                dir.FullName, "frontend", "uni-app", "my-mp", ".env.production");
            if (File.Exists(inside)) return inside;

            // 情况二：工具在仓库旁边（与 class-assistant 平级）
            var sibling = Path.Combine(
                dir.FullName, "class-assistant", "frontend", "uni-app", "my-mp", ".env.production");
            if (File.Exists(sibling)) return sibling;
        }
        return null;
    }
}
