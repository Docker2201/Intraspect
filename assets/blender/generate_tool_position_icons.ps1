Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms

$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$IconDirs = @(
    (Join-Path $Root "src\main\resources\tool-icons"),
    (Join-Path $Root "launch\CNC_Modeling\app\classes\tool-icons"),
    (Join-Path $Root "out\production\CNC_Modeling_2.0\tool-icons")
)

$Tools = @(
    @{ Code = 100; Category = "MILLING"; Positions = 4 },
    @{ Code = 110; Category = "MILLING"; Positions = 4 },
    @{ Code = 111; Category = "MILLING"; Positions = 4 },
    @{ Code = 120; Category = "MILLING"; Positions = 4 },
    @{ Code = 121; Category = "MILLING"; Positions = 4 },
    @{ Code = 130; Category = "MILLING"; Positions = 4 },
    @{ Code = 131; Category = "MILLING"; Positions = 4 },
    @{ Code = 140; Category = "MILLING"; Positions = 4 },
    @{ Code = 145; Category = "MILLING"; Positions = 4 },
    @{ Code = 150; Category = "MILLING"; Positions = 4 },
    @{ Code = 151; Category = "MILLING"; Positions = 4 },
    @{ Code = 155; Category = "MILLING"; Positions = 4 },
    @{ Code = 156; Category = "MILLING"; Positions = 4 },
    @{ Code = 157; Category = "MILLING"; Positions = 4 },
    @{ Code = 160; Category = "MILLING"; Positions = 4 },
    @{ Code = 200; Category = "DRILL"; Positions = 4 },
    @{ Code = 205; Category = "DRILL"; Positions = 4 },
    @{ Code = 210; Category = "DRILL"; Positions = 4 },
    @{ Code = 220; Category = "DRILL"; Positions = 4 },
    @{ Code = 230; Category = "DRILL"; Positions = 4 },
    @{ Code = 231; Category = "DRILL"; Positions = 4 },
    @{ Code = 240; Category = "DRILL"; Positions = 4 },
    @{ Code = 241; Category = "DRILL"; Positions = 4 },
    @{ Code = 242; Category = "DRILL"; Positions = 4 },
    @{ Code = 250; Category = "DRILL"; Positions = 4 },
    @{ Code = 500; Category = "TURNING"; Positions = 8 },
    @{ Code = 510; Category = "TURNING"; Positions = 8 },
    @{ Code = 520; Category = "TURNING"; Positions = 8 },
    @{ Code = 530; Category = "TURNING"; Positions = 8 },
    @{ Code = 540; Category = "TURNING"; Positions = 8 },
    @{ Code = 550; Category = "TURNING"; Positions = 8 },
    @{ Code = 560; Category = "TURNING"; Positions = 4 },
    @{ Code = 580; Category = "TURNING"; Positions = 4 },
    @{ Code = 585; Category = "TURNING"; Positions = 4 },
    @{ Code = 700; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 710; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 711; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 712; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 713; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 714; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 725; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 730; Category = "SPECIAL"; Positions = 1 },
    @{ Code = 731; Category = "SPECIAL"; Positions = 4 },
    @{ Code = 732; Category = "SPECIAL"; Positions = 1 },
    @{ Code = 900; Category = "SPECIAL"; Positions = 4 }
)

function New-Color($hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

$Dark = New-Color "#111827"
$Mid = New-Color "#334155"
$Light = New-Color "#cbd5e1"
$Insert = New-Color "#ffe74d"
$InsertStroke = New-Color "#0f172a"
$Red = New-Color "#ef4444"
$Blue = New-Color "#2563eb"
$White = [System.Drawing.Color]::White

function New-Brush($color) {
    return New-Object System.Drawing.SolidBrush($color)
}

function New-Pen2($color, [float]$width) {
    $pen = New-Object System.Drawing.Pen($color, $width)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    return $pen
}

function New-Point($x, $y) {
    return New-Object System.Drawing.PointF([float]$x, [float]$y)
}

function Get-RotatedRect($cx, $cy, $w, $h, $angle) {
    $cos = [Math]::Cos($angle)
    $sin = [Math]::Sin($angle)
    $points = @()
    foreach ($corner in @(@(-0.5, -0.5), @(0.5, -0.5), @(0.5, 0.5), @(-0.5, 0.5))) {
        $x = $corner[0] * $w
        $y = $corner[1] * $h
        $points += New-Point ($cx + $x * $cos - $y * $sin) ($cy + $x * $sin + $y * $cos)
    }
    return [System.Drawing.PointF[]]$points
}

function Get-Edge($pos) {
    switch ([Math]::Max(1, [Math]::Min(8, $pos))) {
        2 { return @(-1.0, 1.0) }
        3 { return @(-1.0, -1.0) }
        4 { return @(1.0, -1.0) }
        5 { return @(0.0, 1.0) }
        6 { return @(0.0, -1.0) }
        7 { return @(-1.0, 0.0) }
        8 { return @(1.0, 0.0) }
        default { return @(1.0, 1.0) }
    }
}

function Get-ScreenPointForEdge {
    param([double[]]$edge)
    $ex = [double]$edge[0]
    $ey = [double]$edge[1]
    return @((128.0 + $ex * 60.0), (128.0 - $ey * 60.0))
}

function Draw-CuttingMarker($g, $x, $y) {
    $redBrush = New-Brush $Red
    $whitePen = New-Pen2 $White 5
    $redPen = New-Pen2 $Red 8
    $g.DrawEllipse($whitePen, $x - 11, $y - 11, 22, 22)
    $g.FillEllipse($redBrush, $x - 8, $y - 8, 16, 16)
    $g.DrawLine($redPen, $x - 22, $y, $x + 22, $y)
    $redBrush.Dispose()
    $whitePen.Dispose()
    $redPen.Dispose()
}

function Draw-TurningIcon($g, $code, $pos) {
    $edge = Get-Edge $pos
    $p = Get-ScreenPointForEdge -edge $edge
    $dx = -$edge[0]
    $dy = $edge[1]
    $length = [Math]::Sqrt($dx * $dx + $dy * $dy)
    if ($length -lt 0.001) {
        $dx = -1.0
        $dy = 0.0
        $length = 1.0
    }
    $dx /= $length
    $dy /= $length
    $angle = [Math]::Atan2($dy, $dx)
    $holder = Get-RotatedRect ($p[0] + $dx * 78.0) ($p[1] + $dy * 78.0) 158 42 $angle
    $holderBrush = New-Brush $Dark
    $holderPen = New-Pen2 $Mid 5
    $g.FillPolygon($holderBrush, $holder)
    $g.DrawPolygon($holderPen, $holder)

    $insertBrush = New-Brush $Insert
    $insertPen = New-Pen2 $InsertStroke 5
    if ($code -eq 530) {
        $slot = Get-RotatedRect $p[0] $p[1] 22 66 ($angle + [Math]::PI / 2.0)
        $g.FillPolygon($insertBrush, $slot)
        $g.DrawPolygon($insertPen, $slot)
    } elseif ($code -eq 520) {
        $slot = Get-RotatedRect $p[0] $p[1] 50 32 $angle
        $g.FillPolygon($insertBrush, $slot)
        $g.DrawPolygon($insertPen, $slot)
    } elseif ($code -eq 540) {
        $points = [System.Drawing.PointF[]]@(
            (New-Point $p[0] ($p[1] - 31)),
            (New-Point ($p[0] + 33) ($p[1] + 26)),
            (New-Point ($p[0] - 33) ($p[1] + 26))
        )
        $g.FillPolygon($insertBrush, $points)
        $g.DrawPolygon($insertPen, $points)
    } elseif ($code -eq 550) {
        $g.FillEllipse($insertBrush, $p[0] - 28, $p[1] - 28, 56, 56)
        $g.DrawEllipse($insertPen, $p[0] - 28, $p[1] - 28, 56, 56)
    } else {
        $diamond = [System.Drawing.PointF[]]@(
            (New-Point $p[0] ($p[1] - 31)),
            (New-Point ($p[0] + 31) $p[1]),
            (New-Point $p[0] ($p[1] + 31)),
            (New-Point ($p[0] - 31) $p[1])
        )
        $g.FillPolygon($insertBrush, $diamond)
        $g.DrawPolygon($insertPen, $diamond)
    }
    Draw-CuttingMarker $g $p[0] $p[1]
    $holderBrush.Dispose()
    $holderPen.Dispose()
    $insertBrush.Dispose()
    $insertPen.Dispose()
}

function Draw-MillingIcon($g, $code, $pos) {
    $edge = Get-Edge $pos
    $p = Get-ScreenPointForEdge -edge $edge
    $bodyBrush = New-Brush $Dark
    $bodyPen = New-Pen2 $Mid 5
    if ($code -in @(140, 150, 151, 700)) {
        $g.FillEllipse($bodyBrush, 56, 46, 144, 144)
        $g.DrawEllipse($bodyPen, 56, 46, 144, 144)
        for ($i = 0; $i -lt 12; $i++) {
            $a = $i * [Math]::PI / 6.0
            $x1 = 128 + [Math]::Cos($a) * 46
            $y1 = 118 + [Math]::Sin($a) * 46
            $x2 = 128 + [Math]::Cos($a) * 82
            $y2 = 118 + [Math]::Sin($a) * 82
            $g.DrawLine($bodyPen, $x1, $y1, $x2, $y2)
        }
    } else {
        $rect = Get-RotatedRect 112 132 142 44 ([Math]::Atan2(-$edge[1], $edge[0]) * 0.35)
        $g.FillPolygon($bodyBrush, $rect)
        $g.DrawPolygon($bodyPen, $rect)
        $g.FillEllipse($bodyBrush, 151, 100, 58, 58)
        $g.DrawEllipse($bodyPen, 151, 100, 58, 58)
    }
    Draw-CuttingMarker $g $p[0] $p[1]
    $bodyBrush.Dispose()
    $bodyPen.Dispose()
}

function Draw-DrillIcon($g, $code, $pos) {
    $edge = Get-Edge $pos
    $p = Get-ScreenPointForEdge -edge $edge
    $dx = $edge[0]
    $dy = -$edge[1]
    if ([Math]::Abs($dx) + [Math]::Abs($dy) -lt 0.01) {
        $dx = 1.0
        $dy = 0.0
    }
    $angle = [Math]::Atan2($dy, $dx)
    $bodyBrush = New-Brush $Dark
    $bodyPen = New-Pen2 $Mid 5
    $tool = Get-RotatedRect 116 130 140 28 $angle
    $g.FillPolygon($bodyBrush, $tool)
    $g.DrawPolygon($bodyPen, $tool)
    $tip = [System.Drawing.PointF[]]@(
        (New-Point $p[0] $p[1]),
        (New-Point ($p[0] - $dx * 42 - $dy * 20) ($p[1] - $dy * 42 + $dx * 20)),
        (New-Point ($p[0] - $dx * 42 + $dy * 20) ($p[1] - $dy * 42 - $dx * 20))
    )
    $insertBrush = New-Brush $Insert
    $insertPen = New-Pen2 $InsertStroke 5
    $g.FillPolygon($insertBrush, $tip)
    $g.DrawPolygon($insertPen, $tip)
    Draw-CuttingMarker $g $p[0] $p[1]
    $bodyBrush.Dispose()
    $bodyPen.Dispose()
    $insertBrush.Dispose()
    $insertPen.Dispose()
}

function Draw-SpecialIcon($g, $code, $pos) {
    if ($code -eq 732) {
        $brush = New-Brush $Dark
        $pen = New-Pen2 $Mid 5
        $g.DrawArc($pen, 50, 34, 156, 156, 205, 130)
        $g.FillRectangle($brush, 70, 162, 28, 50)
        $g.FillRectangle($brush, 158, 162, 28, 50)
        $g.DrawRectangle($pen, 70, 162, 28, 50)
        $g.DrawRectangle($pen, 158, 162, 28, 50)
        Draw-CuttingMarker $g 128 80
        $brush.Dispose()
        $pen.Dispose()
        return
    }
    Draw-MillingIcon $g $code $pos
}

function Save-Icon($tool, $pos, $outputPath) {
    $bitmap = New-Object System.Drawing.Bitmap 256, 256, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.Clear([System.Drawing.Color]::Transparent)
    switch ($tool.Category) {
        "TURNING" {
            if ($tool.Code -in @(560, 580, 585)) {
                Draw-DrillIcon $g $tool.Code $pos
            } else {
                Draw-TurningIcon $g $tool.Code $pos
            }
        }
        "DRILL" { Draw-DrillIcon $g $tool.Code $pos }
        "SPECIAL" { Draw-SpecialIcon $g $tool.Code $pos }
        default { Draw-MillingIcon $g $tool.Code $pos }
    }
    $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bitmap.Dispose()
}

foreach ($dir in $IconDirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$primaryDir = $IconDirs[0]
$created = 0
foreach ($tool in $Tools) {
    for ($pos = 1; $pos -le [int]$tool.Positions; $pos++) {
        $name = "tool_{0:D3}_p{1}.png" -f [int]$tool.Code, $pos
        $path = Join-Path $primaryDir $name
        Save-Icon $tool $pos $path
        $created++
    }
    $base = Join-Path $primaryDir ("tool_{0:D3}.png" -f [int]$tool.Code)
    Copy-Item -LiteralPath (Join-Path $primaryDir ("tool_{0:D3}_p1.png" -f [int]$tool.Code)) -Destination $base -Force
}

foreach ($dir in $IconDirs[1..($IconDirs.Count - 1)]) {
    Get-ChildItem -LiteralPath $primaryDir -Filter "tool_*.png" | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $dir $_.Name) -Force
    }
}

Write-Host "Generated $created position PNG icons in $primaryDir"
