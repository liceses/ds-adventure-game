# check_script.ps1 —— 剧本脚本校对器：路由闭合 + 黑名单 + 结构检查
# 用法：pwsh -File tools/check_script.ps1 [-ScriptDir <目录>]
param([string]$ScriptDir = 'D:\developing\javaFX\ds-adventure\docs\ds-adventrue\剧本')
$ErrorActionPreference = 'Stop'
$dir = $ScriptDir
$files = Get-ChildItem $dir -Filter '*.txt' | Sort-Object Name

# ---- 全局标签收集 ----
$globalLabels = @{}
foreach ($f in $files) {
    foreach ($l in (Get-Content $f.FullName -Encoding UTF8)) {
        if ($l -match '^\s*@label\s+(\S+)') { $globalLabels[$matches[1]] = $f.Name }
    }
}

# ---- 黑名单（v0.3 守则：学术词 + 现代经济词） ----
$black = '资本|剩余价值|拜物教|异化|原始积累|地租|剥削|阶级|使用价值|交换价值|商品化|证券化|对价|复利|理财|提现|年化|充值|会员|订阅|广告|抽成|分期|优惠|平台|APP|上层建筑|生产资料'
# 白名单例外：司秤吏的现代腔每章≤1处且必须被吐槽（当前全篇仅允许序章用「理财」gag）
$allowed = @{ '序章_404之夜.txt' = @('理财') }

$report = foreach ($f in $files) {
    $lines = Get-Content $f.FullName -Encoding UTF8
    $targets = @()
    foreach ($l in $lines) {
        if ($l -match '(?<![A-Za-z])goto\s+([A-Za-z0-9_]+)') { $targets += $matches[1] }
        if ($l -match 'on(Win|Lose):([A-Za-z0-9_]+)') { $targets += $matches[2] }
    }
    $missing = @($targets | Where-Object { -not $globalLabels.ContainsKey($_) } | Select-Object -Unique)

    $badHits = @(); $i = 0
    foreach ($l in $lines) {
        $i++
        foreach ($m in [regex]::Matches($l, $black)) {
            $word = $m.Value
            $skip = $false
            if ($allowed.ContainsKey($f.Name) -and $allowed[$f.Name] -contains $word_check) { }
            if ($allowed.ContainsKey($f.Name)) {
                foreach ($a in $allowed[$f.Name]) { if ($l.Contains($a)) { $skip = $true } }
            }
            if (-not $skip) { $badHits += ("L{0}:{1}" -f $i, $m.Value) }
        }
    }

    $hasPieces = @($lines | Where-Object { $_ -match '@flag\s+pieces\s+\+1' }).Count -gt 0
    $hasMinigame = @($lines | Where-Object { $_ -match '@minigame' }).Count
    $hasSave = @($lines | Where-Object { $_ -match '@save' }).Count -gt 0

    [pscustomobject]@{
        文件 = $f.Name
        行数 = $lines.Count
        选项数 = @($lines | Where-Object { $_ -match '^\s*>\s' }).Count
        小游戏 = $hasMinigame
        存档点 = $hasSave
        鳞回收 = $hasPieces
        缺失跳转 = ($missing -join ',')
        黑名单命中 = ($badHits -join ' ')
    }
}
$report | Format-Table -AutoSize -Wrap
Write-Output ('全局标签数: ' + $globalLabels.Count)