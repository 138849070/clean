Add-Type -AssemblyName System.Drawing

function New-Icon([int]$size, [string]$path, [bool]$round) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $bg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 181, 120))
    $rect = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    if ($round) {
        $g.FillEllipse($bg, $rect)
    } else {
        $radius = $size * 0.22
        $path2 = New-Object System.Drawing.Drawing2D.GraphicsPath
        $d = $radius * 2
        $path2.AddArc(0, 0, $d, $d, 180, 90)
        $path2.AddArc($size - $d, 0, $d, $d, 270, 90)
        $path2.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
        $path2.AddArc(0, $size - $d, $d, $d, 90, 90)
        $path2.CloseFigure()
        $g.FillPath($bg, $path2)
    }

    # 白色圆环
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, $size * 0.10)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $ring = New-Object System.Drawing.RectangleF($size * 0.22, $size * 0.22, $size * 0.56, $size * 0.56)
    $g.DrawArc($pen, $ring, -90, 300)

    # 中心绿点
    $dotBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 181, 120))
    $dot = New-Object System.Drawing.RectangleF($size * 0.38, $size * 0.38, $size * 0.24, $size * 0.24)
    $g.FillEllipse($dotBrush, $dot)

    $pen.Dispose()
    $bg.Dispose()
    $dotBrush.Dispose()
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$base = "C:\Users\93539\Doubao\chats\2026-09-17\new-chat\clean\app\src\main\res"
New-Icon 48 "$base\mipmap-mdpi\ic_launcher.png" $false
New-Icon 72 "$base\mipmap-hdpi\ic_launcher.png" $false
New-Icon 96 "$base\mipmap-xhdpi\ic_launcher.png" $false
New-Icon 144 "$base\mipmap-xxhdpi\ic_launcher.png" $false
New-Icon 192 "$base\mipmap-xxxhdpi\ic_launcher.png" $false
New-Icon 48 "$base\mipmap-mdpi\ic_launcher_round.png" $true
New-Icon 72 "$base\mipmap-hdpi\ic_launcher_round.png" $true
New-Icon 96 "$base\mipmap-xhdpi\ic_launcher_round.png" $true
New-Icon 144 "$base\mipmap-xxhdpi\ic_launcher_round.png" $true
New-Icon 192 "$base\mipmap-xxxhdpi\ic_launcher_round.png" $true
Write-Output "icons done"
