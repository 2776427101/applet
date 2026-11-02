using System.Diagnostics;
using ConnectionTester.Core;

namespace ConnectionTester;

public sealed class MainForm : Form
{
    // ① 连接方式
    private readonly GroupBox _gbMode = new() { Text = "① 连接方式", Left = 12, Top = 10, Width = 500, Height = 396 };
    private readonly RadioButton _rbTunnel = new() { Text = "内网穿透隧道（手输域名）", Left = 18, Top = 26, Width = 210, Height = 24, Checked = true };
    private readonly RadioButton _rbLan = new() { Text = "局域网 IP 直连（本机）", Left = 236, Top = 26, Width = 220, Height = 24 };

    private readonly Panel _pnlTunnel = new() { Left = 12, Top = 58, Width = 476, Height = 326 };
    private readonly Label _lblHost = new() { Text = "域名 / IP", Left = 0, Top = 0, Width = 240 };
    private readonly TextBox _txtHost = new() { Left = 0, Top = 22, Width = 240, Text = "frp-sea.com" };
    private readonly Label _lblPort = new() { Text = "端口", Left = 256, Top = 0, Width = 100 };
    private readonly NumericUpDown _numPort = new() { Left = 256, Top = 22, Width = 100, Minimum = 1, Maximum = 65535, Value = 23200 };
    private readonly Label _lblScheme = new() { Text = "协议", Left = 372, Top = 0, Width = 100 };
    private readonly ComboBox _cboScheme = new() { Left = 372, Top = 22, Width = 100, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Label _lblTunnelHint = new() { Left = 0, Top = 66, Width = 476, Height = 250 };

    // ② 局域网
    private readonly Panel _pnlLan = new() { Left = 12, Top = 58, Width = 476, Height = 326, Visible = false };
    private readonly Label _lblNic = new() { Text = "本机网卡 / IP", Left = 0, Top = 0, Width = 300 };
    private readonly ComboBox _cboIp = new() { Left = 0, Top = 22, Width = 330, DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly CheckBox _chkAll = new() { Text = "显示虚拟网卡", Left = 340, Top = 24, Width = 136 };
    private readonly Label _lblLanPort = new() { Text = "端口", Left = 0, Top = 62, Width = 80 };
    private readonly NumericUpDown _numLanPort = new() { Left = 0, Top = 84, Width = 100, Minimum = 1, Maximum = 65535, Value = 8080 };
    private readonly Label _lblEnv = new() { Text = "配置文件 · 仅构建小程序时需要", Left = 112, Top = 62, Width = 364 };
    private readonly TextBox _txtEnv = new() { Left = 112, Top = 84, Width = 300, ReadOnly = true };
    private readonly Button _btnBrowse = new() { Text = "浏览", Left = 418, Top = 83, Width = 58, Height = 25 };
    private readonly Label _lblPreviewTitle = new() { Text = "【仅构建小程序时需要】将写入下面这一行，改前自动备份 .bak", Left = 0, Top = 124, Width = 476 };
    private readonly Label _lblPreview = new() { Left = 0, Top = 146, Width = 476, Height = 44, BorderStyle = BorderStyle.FixedSingle, TextAlign = ContentAlignment.MiddleLeft, BackColor = Color.White, Font = new Font("Consolas", 9.5f) };
    private readonly Button _btnWrite = new() { Text = "写入配置", Left = 0, Top = 200, Width = 150, Height = 32 };
    private readonly Button _btnOpenFolder = new() { Text = "打开所在目录", Left = 160, Top = 200, Width = 150, Height = 32 };
    private readonly Label _lblFw = new() { Text = "防火墙（入站 TCP，需要管理员权限）", Left = 0, Top = 246, Width = 476 };
    private readonly Button _btnFwQuery = new() { Text = "查询规则", Left = 0, Top = 268, Width = 150, Height = 32 };
    private readonly Button _btnFwAdd = new() { Text = "开放端口", Left = 160, Top = 268, Width = 150, Height = 32 };
    private readonly Button _btnFwRemove = new() { Text = "关闭端口", Left = 320, Top = 268, Width = 150, Height = 32 };

    // ③ 测试
    private readonly GroupBox _gbAction = new() { Text = "② 测试", Left = 524, Top = 10, Width = 484, Height = 396 };
    private readonly Label _lblPath = new() { Text = "测试路径（两种模式共用）", Left = 16, Top = 24, Width = 452 };
    private readonly TextBox _txtPath = new() { Left = 16, Top = 46, Width = 452, Text = "/api/teacher/home/overview" };
    private readonly Button _btnTest = new() { Text = "开始测试", Left = 16, Top = 86, Width = 452, Height = 52 };
    private readonly Button _btnCopy = new() { Text = "复制诊断信息", Left = 16, Top = 150, Width = 215, Height = 34 };
    private readonly Button _btnClear = new() { Text = "清空输出", Left = 253, Top = 150, Width = 215, Height = 34 };
    private readonly Label _lblVerdict = new() { Left = 16, Top = 196, Width = 452, Height = 84, BorderStyle = BorderStyle.FixedSingle, TextAlign = ContentAlignment.MiddleLeft, Padding = new Padding(10, 6, 10, 6), BackColor = Color.White, Text = "尚未测试" };
    private readonly Label _lblActionHint = new() { Left = 16, Top = 292, Width = 452, Height = 92, ForeColor = Color.FromArgb(95, 94, 90) };

    // ④ 输出
    private readonly GroupBox _gbLog = new() { Text = "③ 诊断输出", Left = 12, Top = 412, Width = 996, Height = 320, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom };
    private readonly RichTextBox _log = new() { Left = 12, Top = 22, Width = 972, Height = 288, ReadOnly = true, BorderStyle = BorderStyle.None, Font = new Font("Consolas", 9f), BackColor = Color.White, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom };
    private readonly StatusStrip _status = new();
    private readonly ToolStripStatusLabel _statusLabel = new() { Text = "就绪" };

    private readonly List<string> _ipList = new();

    public MainForm()
    {
        Text = "班级助手 · 连接测试工具  v1.0.0";
        ClientSize = new Size(1020, 756);
        MinimumSize = new Size(1040, 800);
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Microsoft YaHei UI", 9f);
        BackColor = Color.FromArgb(250, 250, 248);

        _cboScheme.Items.AddRange(new object[] { "https", "http" });
        _cboScheme.SelectedIndex = 0;

        _lblTunnelHint.Text =
            "用来验证别人给你的穿透地址能不能用。\r\n\r\n" +
            "· 域名填隧道提供方给的（例：frp-sea.com）\r\n" +
            "· 端口填冒号后面那段（例：23200）\r\n" +
            "· 协议要和隧道设置一致：开了「自动 HTTPS」就选 https\r\n\r\n" +
            "本模式只读取、不修改任何本机设置。";
        _lblTunnelHint.ForeColor = Color.FromArgb(95, 94, 90);

        _lblActionHint.Text =
            "测试会依次检查：DNS → TCP → TLS 证书 → HTTP。\r\n" +
            "哪一段断掉，就说明问题出在哪里。\r\n\r\n" +
            "注意：即使链路全通，如果证书是自签的，微信真机\r\n" +
            "仍会报 ERR_CERT_AUTHORITY_INVALID —— 这里会提前告诉你。";
        _lblActionHint.ForeColor = Color.FromArgb(95, 94, 90);

        BuildLayout();

        _rbTunnel.CheckedChanged += (_, _) => UpdateMode();
        _rbLan.CheckedChanged += (_, _) => UpdateMode();
        _chkAll.CheckedChanged += (_, _) => LoadNics();
        _cboIp.SelectedIndexChanged += (_, _) => UpdatePreview();
        _numLanPort.ValueChanged += (_, _) => UpdatePreview();
        _btnBrowse.Click += OnBrowse;
        _btnWrite.Click += OnWrite;
        _btnOpenFolder.Click += OnOpenFolder;
        _btnTest.Click += OnTest;
        _btnCopy.Click += (_, _) => { if (_log.TextLength > 0) Clipboard.SetText(_log.Text); };
        _btnClear.Click += (_, _) => { _log.Clear(); SetVerdict("尚未测试", null); };
        _btnFwQuery.Click += async (_, _) => await RunFw("query", "查询防火墙规则");
        _btnFwAdd.Click += async (_, _) => await RunFw("add", "开放端口");
        _btnFwRemove.Click += async (_, _) => await RunFw("remove", "关闭端口");

        _txtEnv.Text = NetInfo.GuessEnvPath() ?? "";
        LoadNics();
        UpdateMode();

        if (string.IsNullOrEmpty(_txtEnv.Text))
        {
            Append("提示：这台机器上没找到 .env.production。", Color.FromArgb(133, 79, 11));
            Append("     只是测试连接的话不用管它，选好网卡直接点「开始测试」就行。", Color.FromArgb(95, 94, 90));
            Append("     要重新构建小程序，才点「浏览」选中那个文件。", Color.FromArgb(95, 94, 90));
        }
        else
        {
            var cur = EnvWriter.ReadCurrentValue(_txtEnv.Text);
            if (cur != null) Append($"当前配置：{EnvWriter.Key}={cur}", Color.FromArgb(95, 94, 90));
        }
    }

    private void BuildLayout()
    {
        _gbMode.Controls.AddRange(new Control[]
        {
            _rbTunnel, _rbLan, _pnlTunnel, _pnlLan
        });
        _pnlTunnel.Controls.AddRange(new Control[]
        {
            _lblHost, _txtHost, _lblPort, _numPort, _lblScheme, _cboScheme, _lblTunnelHint
        });
        _pnlLan.Controls.AddRange(new Control[]
        {
            _lblNic, _cboIp, _chkAll, _lblLanPort, _numLanPort, _lblEnv, _txtEnv, _btnBrowse,
            _lblPreviewTitle, _lblPreview, _btnWrite, _btnOpenFolder, _lblFw,
            _btnFwQuery, _btnFwAdd, _btnFwRemove
        });
        _gbAction.Controls.AddRange(new Control[]
        {
            _lblPath, _txtPath, _btnTest, _btnCopy, _btnClear, _lblVerdict, _lblActionHint
        });
        _gbLog.Controls.Add(_log);

        _btnTest.Font = new Font("Microsoft YaHei UI", 12f, FontStyle.Bold);
        _btnTest.BackColor = Color.FromArgb(230, 241, 251);
        _btnTest.FlatStyle = FlatStyle.System;

        _statusLabel.Spring = true;
        _statusLabel.TextAlign = ContentAlignment.MiddleLeft;
        _status.Items.Add(_statusLabel);

        Controls.AddRange(new Control[] { _gbMode, _gbAction, _gbLog, _status });
    }

    private void UpdateMode()
    {
        bool tunnel = _rbTunnel.Checked;
        _pnlTunnel.Visible = tunnel;
        _pnlLan.Visible = !tunnel;

        _btnOpenFolder.Enabled = !tunnel;
        _btnFwQuery.Enabled = !tunnel;
        _btnFwAdd.Enabled = !tunnel;
        _btnFwRemove.Enabled = !tunnel;

        UpdatePreview();

        _statusLabel.Text = tunnel
            ? "隧道模式：只做连通性探测，不改本机设置"
            : "局域网模式：只测连接的话，选好网卡点「开始测试」即可，不必写配置";
    }

    private void LoadNics()
    {
        _ipList.Clear();
        _cboIp.Items.Clear();

        var nics = NetInfo.GetIPv4(_chkAll.Checked);
        foreach (var n in nics)
        {
            _ipList.Add(n.Ip);
            _cboIp.Items.Add($"{n.Ip}    ({n.Name} · {n.Kind}){(n.Virtual ? " · 虚拟" : "")}");
        }

        if (_cboIp.Items.Count > 0) _cboIp.SelectedIndex = 0;
        UpdatePreview();
    }

    private string CurrentIp => _cboIp.SelectedIndex >= 0 && _cboIp.SelectedIndex < _ipList.Count
        ? _ipList[_cboIp.SelectedIndex]
        : "";

    private void UpdatePreview()
    {
        var ip = CurrentIp;
        var envPath = _txtEnv.Text.Trim();
        bool hasEnv = envPath.Length > 0 && File.Exists(envPath);

        var line = ip.Length == 0
            ? "（没选到网卡）"
            : EnvWriter.BuildLine(EnvWriter.BuildUrl(ip, (int)_numLanPort.Value));

        _lblPreview.Text = hasEnv ? line : line + "     ← 本机没有配置文件，可跳过";

        // 没有仓库的机器上，「写入配置」点了也只会告诉他可以跳过。
        // 让按钮文字自己把这个信息带出来，比禁用之后无法解释要清楚。
        _btnWrite.Text = hasEnv ? "写入配置" : "写入配置（可跳过）";
        _btnWrite.Enabled = !_rbTunnel.Checked;
    }

    private void OnBrowse(object? sender, EventArgs e)
    {
        using var dlg = new OpenFileDialog
        {
            Title = "选择 .env.production",
            Filter = "env 文件|*.production;*.env|所有文件|*.*"
        };
        if (dlg.ShowDialog(this) == DialogResult.OK)
        {
            _txtEnv.Text = dlg.FileName;
            UpdatePreview();
        }
    }

    private void OnOpenFolder(object? sender, EventArgs e)
    {
        var path = _txtEnv.Text.Trim();
        if (path.Length == 0) return;
        try
        {
            if (File.Exists(path)) Process.Start("explorer.exe", $"/select,\"{path}\"");
            else Process.Start("explorer.exe", Path.GetDirectoryName(path) ?? ".");
        }
        catch { /* 忽略 */ }
    }

    private void OnWrite(object? sender, EventArgs e)
    {
        var path = _txtEnv.Text.Trim();

        // 使用者（只测连接、不构建小程序）通常根本没有这个文件。
        // 以前这里只说「先点浏览」，等于让他去找一个不存在的东西，得先说清楚可以跳过。
        if (path.Length == 0)
        {
            Append("[跳过] 你没有指定配置文件。", Color.FromArgb(133, 79, 11));
            Append("       如果你只是想测试连接，这一步不用做 —— 直接点右边「开始测试」。", Color.FromArgb(95, 94, 90));
            Append("       只有需要重新构建小程序时，才点「浏览」选中 .env.production。", Color.FromArgb(95, 94, 90));
            return;
        }

        var ip = CurrentIp;
        if (ip.Length == 0)
        {
            Append("[FAIL] 没有可用网卡。", Color.FromArgb(163, 45, 45));
            return;
        }

        var url = EnvWriter.BuildUrl(ip, (int)_numLanPort.Value);
        var line = EnvWriter.BuildLine(url);

        var confirm = MessageBox.Show(
            $"将写入：\r\n{path}\r\n\r\n{line}\r\n\r\n原文件会备份为 .env.production.bak。继续？",
            "确认写入", MessageBoxButtons.OKCancel, MessageBoxIcon.Question);
        if (confirm != DialogResult.OK) return;

        var outcome = EnvWriter.TryWrite(path, url);

        if (outcome.Ok)
        {
            Append($"[OK] 配置已写入：{outcome.Message}", Color.FromArgb(39, 80, 10));
            if (outcome.BackupPath.Length > 0)
                Append($"     备份文件：{outcome.BackupPath}", Color.FromArgb(95, 94, 90));
            Append("     下一步：npm run build:mp-weixin 重新构建，然后在开发者工具里点「编译」和「预览」。", Color.FromArgb(95, 94, 90));
            return;
        }

        // 失败：先说清是什么原因，再逐条给出可操作的下一步
        Append("[FAIL] " + outcome.Message, Color.FromArgb(163, 45, 45));
        foreach (var l in outcome.Suggestion.Split('\n'))
        {
            var t = l.TrimEnd('\r');
            if (t.Length > 0) Append("       " + t, Color.FromArgb(133, 79, 11));
        }

        // 兜底：写入走不通时，把这一行塞进剪贴板，用户至少能手动粘进文件
        Append($"       配置行：{line}", Color.FromArgb(95, 94, 90));
        try
        {
            Clipboard.SetText(line);
            Append("       已复制到剪贴板，可以自己打开 .env.production 粘贴。", Color.FromArgb(95, 94, 90));
        }
        catch { /* 剪贴板被别的程序占着，忽略 */ }
    }

    private async void OnTest(object? sender, EventArgs e)
    {
        _btnTest.Enabled = false;
        _btnTest.Text = "测试中…";
        _log.Clear();
        SetVerdict("正在测试…", ProbeLevel.Warn);

        try
        {
            string scheme, host;
            int port;
            var path = _txtPath.Text.Trim();

            if (_rbTunnel.Checked)
            {
                scheme = _cboScheme.SelectedItem?.ToString() ?? "https";
                host = _txtHost.Text.Trim();
                port = (int)_numPort.Value;
            }
            else
            {
                scheme = "http";
                host = CurrentIp;
                port = (int)_numLanPort.Value;
                if (host.Length == 0)
                {
                    Append("[FAIL] 没有可用网卡，请先选择本机 IP。", Color.FromArgb(163, 45, 45));
                    SetVerdict("没有可用网卡", ProbeLevel.Fail);
                    return;
                }
            }

            var report = await Prober.RunAsync(scheme, host, port, path, LogSink);
            var text = report.Verdict;
            if (report.Advice.Length > 0) text += "\r\n" + report.Advice;
            SetVerdict(text, report.Level);
            _statusLabel.Text = $"测试完成 · {report.Url} · HTTP {report.HttpStatus}";
        }
        catch (Exception ex)
        {
            Append("[FAIL] 测试异常：" + ex.Message, Color.FromArgb(163, 45, 45));
            SetVerdict("测试异常：" + ex.Message, ProbeLevel.Fail);
        }
        finally
        {
            _btnTest.Enabled = true;
            _btnTest.Text = "开始测试";
        }
    }

    private async Task RunFw(string op, string title)
    {
        int port = (int)_numLanPort.Value;

        if (op == "remove")
        {
            var c = MessageBox.Show($"确认删除规则「{FirewallService.RuleName(port)}」？\r\n删除后其他设备将无法访问 TCP {port}。",
                "确认关闭端口", MessageBoxButtons.OKCancel, MessageBoxIcon.Warning);
            if (c != DialogResult.OK) return;
        }

        Append($"→ {title}（TCP {port}），需要管理员权限…", Color.FromArgb(95, 94, 90));
        var result = await Task.Run(() => FirewallService.RunElevated(op, port));

        if (result.Ok)
        {
            foreach (var line in result.Message.Split('\n'))
                Append("[OK] " + line.TrimEnd(), Color.FromArgb(39, 80, 10));
        }
        else
        {
            Append("[FAIL] " + result.Message, Color.FromArgb(163, 45, 45));
        }
    }

    private void SetVerdict(string text, ProbeLevel? level)
    {
        _lblVerdict.Text = text;
        switch (level)
        {
            case ProbeLevel.Ok:
                _lblVerdict.BackColor = Color.FromArgb(234, 243, 222);
                _lblVerdict.ForeColor = Color.FromArgb(39, 80, 10);
                break;
            case ProbeLevel.Warn:
                _lblVerdict.BackColor = Color.FromArgb(250, 238, 218);
                _lblVerdict.ForeColor = Color.FromArgb(133, 79, 11);
                break;
            case ProbeLevel.Fail:
                _lblVerdict.BackColor = Color.FromArgb(252, 235, 235);
                _lblVerdict.ForeColor = Color.FromArgb(121, 31, 31);
                break;
            default:
                _lblVerdict.BackColor = Color.White;
                _lblVerdict.ForeColor = Color.FromArgb(60, 60, 60);
                break;
        }
    }

    private void LogSink(string line)
    {
        if (InvokeRequired) { BeginInvoke(new Action<string>(LogSink), line); return; }

        var color = line.Contains("[FAIL]") ? Color.FromArgb(163, 45, 45)
                  : line.Contains("[OK]") ? Color.FromArgb(39, 80, 10)
                  : line.StartsWith("结论") ? Color.FromArgb(12, 68, 124)
                  : Color.FromArgb(60, 60, 60);

        Append(line, color);
    }

    private void Append(string line, Color? color = null)
    {
        _log.SelectionStart = _log.TextLength;
        _log.SelectionColor = color ?? Color.FromArgb(60, 60, 60);
        _log.AppendText(line + Environment.NewLine);
        _log.SelectionColor = _log.ForeColor;
        _log.ScrollToCaret();
    }
}
