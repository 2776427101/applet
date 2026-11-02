namespace ConnectionTester.Core;

/// <summary>探测过程中的单个环节（DNS / TCP / TLS / HTTP）。</summary>
public sealed class ProbeStep
{
    public string Name { get; init; } = "";
    public bool Ok { get; set; }
    public string Detail { get; set; } = "";
    public long ElapsedMs { get; set; }
    public bool Skipped { get; set; }
}

public enum ProbeLevel
{
    Ok,
    Warn,
    Fail
}

/// <summary>一次完整探测的结论。</summary>
public sealed class ProbeReport
{
    public List<ProbeStep> Steps { get; } = new();
    public ProbeLevel Level { get; set; } = ProbeLevel.Fail;
    public string Verdict { get; set; } = "";
    public string Advice { get; set; } = "";

    /// <summary>证书是否不受信任（自签 / 链不完整 / 域名不匹配）。</summary>
    public bool CertUntrusted { get; set; }
    public string CertSummary { get; set; } = "";

    public int HttpStatus { get; set; }
    public string Url { get; set; } = "";

    public void Add(ProbeStep step) => Steps.Add(step);

    public ProbeStep AddStep(string name)
    {
        var s = new ProbeStep { Name = name };
        Steps.Add(s);
        return s;
    }
}
