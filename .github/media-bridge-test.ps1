# End-to-end check of now_playing.ps1 on Windows (run by the "Windows media bridge" CI job, in Windows PowerShell 5.1).
# A fake media player (a window with system media transport controls: title, artist, album, timeline and a cover)
# plays a song, moves on to a second one whose cover only comes three seconds after its title, and pauses, while
# the bridge runs as the game runs it. Everything the bridge reports is printed with the time it arrived, so the log
# shows how soon a song and its cover reach the game. Informative: it always exits with 0.
param([string]$Script = 'src\main\resources\assets\v1zir\media\now_playing.ps1', [string]$Apartment = 'MTA')
$ErrorActionPreference = 'Stop'
try {
	Add-Type -AssemblyName System.Runtime.WindowsRuntime
	Add-Type -AssemblyName System.Windows.Forms
	Add-Type -AssemblyName System.Drawing
	$null = [Windows.Media.SystemMediaTransportControls, Windows.Media, ContentType = WindowsRuntime]
	$null = [Windows.Media.SystemMediaTransportControlsTimelineProperties, Windows.Media, ContentType = WindowsRuntime]
	$null = [Windows.Media.MediaPlaybackStatus, Windows.Media, ContentType = WindowsRuntime]
	$null = [Windows.Media.MediaPlaybackType, Windows.Media, ContentType = WindowsRuntime]
	$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
	$null = [Windows.Storage.Streams.RandomAccessStreamReference, Windows.Storage.Streams, ContentType = WindowsRuntime]

	Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;

[ComImport, Guid("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"), InterfaceType(ComInterfaceType.InterfaceIsIInspectable)]
public interface ISmtcInterop {
    [return: MarshalAs(UnmanagedType.IInspectable)]
    object GetForWindow(IntPtr appWindow, [In] ref Guid riid);
}

public static class FakePlayer {
    [DllImport("shell32.dll", PreserveSig = false)]
    public static extern void SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string id);

    public static object Controls(IntPtr window, Type controlsType) {
        ISmtcInterop interop = (ISmtcInterop) WindowsRuntimeMarshal.GetActivationFactory(controlsType);
        Guid iid = new Guid("99FA3FF4-1742-42A6-902E-087D41F965EC");
        return interop.GetForWindow(window, ref iid);
    }
}
'@

	$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
		$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
	} | Select-Object -First 1

	function Await($operation, [Type]$type) {
		$task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
		if (-not $task.Wait(5000)) { throw 'An operation of the fake player timed out' }
		return $task.Result
	}

	function New-Cover([string]$path, [System.Drawing.Color]$color) {
		$bitmap = New-Object System.Drawing.Bitmap 300, 300
		$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
		$graphics.Clear($color)
		$graphics.FillEllipse([System.Drawing.Brushes]::White, 75, 75, 150, 150)
		$graphics.Dispose()
		$bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
		$bitmap.Dispose()
	}

	$temp = [System.IO.Path]::GetTempPath()
	$coverA = Join-Path $temp 'v1zir-fake-cover-a.jpg'
	$coverB = Join-Path $temp 'v1zir-fake-cover-b.jpg'
	New-Cover $coverA ([System.Drawing.Color]::FromArgb(224, 69, 123))
	New-Cover $coverB ([System.Drawing.Color]::FromArgb(60, 120, 220))
	Get-ChildItem $temp -Filter 'v1zir-art-*' -ErrorAction SilentlyContinue | Remove-Item -ErrorAction SilentlyContinue

	# Spotify in the name, so the bridge picks this session as it picks Spotify's.
	[FakePlayer]::SetCurrentProcessExplicitAppUserModelID('Spotify.FakePlayer')
	$form = New-Object System.Windows.Forms.Form
	$form.Text = 'Fake player'
	$form.ShowInTaskbar = $false
	$form.Opacity = 0
	$form.Show()
	$controls = [Windows.Media.SystemMediaTransportControls]([FakePlayer]::Controls($form.Handle, [Windows.Media.SystemMediaTransportControls]))
	$controls.IsEnabled = $true
	$controls.IsPlayEnabled = $true
	$controls.IsPauseEnabled = $true
	$controls.IsNextEnabled = $true
	$controls.IsPreviousEnabled = $true

	function Set-Cover([string]$coverPath) {
		$file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($coverPath)) ([Windows.Storage.StorageFile])
		$controls.DisplayUpdater.Thumbnail = [Windows.Storage.Streams.RandomAccessStreamReference]::CreateFromFile($file)
		$controls.DisplayUpdater.Update()
	}

	function Set-Song([string]$title, [string]$artist, [string]$album, [string]$coverPath, [double]$seconds, [double]$at) {
		$updater = $controls.DisplayUpdater
		$updater.ClearAll()
		$updater.Type = [Windows.Media.MediaPlaybackType]::Music
		$updater.MusicProperties.Title = $title
		$updater.MusicProperties.Artist = $artist
		$updater.MusicProperties.AlbumTitle = $album
		$updater.Update()
		if ($coverPath) { Set-Cover $coverPath }
		$timeline = New-Object Windows.Media.SystemMediaTransportControlsTimelineProperties
		$timeline.StartTime = [TimeSpan]::Zero
		$timeline.MinSeekTime = [TimeSpan]::Zero
		$timeline.EndTime = [TimeSpan]::FromSeconds($seconds)
		$timeline.MaxSeekTime = [TimeSpan]::FromSeconds($seconds)
		$timeline.Position = [TimeSpan]::FromSeconds($at)
		$controls.UpdateTimelineProperties($timeline)
	}

	Set-Song 'First Song' 'Artist A' 'Album A' $coverA 214 83
	$controls.PlaybackStatus = [Windows.Media.MediaPlaybackStatus]::Playing
	Write-Host 'Fake player: "First Song" by Artist A plays, with its cover'

	$ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
	$info = New-Object System.Diagnostics.ProcessStartInfo $ps
	$info.Arguments = '-NoLogo -NoProfile -NonInteractive -' + $Apartment + ' -ExecutionPolicy Bypass -File "' + $Script + '"'
	$info.RedirectStandardInput = $true
	$info.RedirectStandardOutput = $true
	$info.RedirectStandardError = $true
	$info.UseShellExecute = $false
	$info.CreateNoWindow = $true
	$bridge = [System.Diagnostics.Process]::Start($info)
	$pending = $bridge.StandardOutput.ReadLineAsync()
	$watch = [System.Diagnostics.Stopwatch]::StartNew()
	$step = 0
	$reports = 0
	$ended = $false
	$last = ''
	$repeats = 0
	while (-not $ended -and $watch.ElapsedMilliseconds -lt 16000) {
		[System.Windows.Forms.Application]::DoEvents()
		$t = $watch.ElapsedMilliseconds
		if ($step -eq 0 -and $t -ge 5000) {
			$step = 1
			Write-Host ('--- ' + $t + ' ms: "Second Song - Slowed" starts, its cover not there yet')
			Set-Song 'Second Song - Slowed' 'Artist B, Guest' 'Album B' '' 173 0
		}
		if ($step -eq 1 -and $t -ge 8000) {
			$step = 2
			Write-Host ('--- ' + $t + ' ms: the second song gets its cover')
			Set-Cover $coverB
		}
		if ($step -eq 2 -and $t -ge 12000) {
			$step = 3
			Write-Host ('--- ' + $t + ' ms: paused')
			$controls.PlaybackStatus = [Windows.Media.MediaPlaybackStatus]::Paused
		}
		while ($pending.IsCompleted) {
			$line = $pending.Result
			if ($null -eq $line) {
				$ended = $true
				break
			}
			$reports++
			# The position moves on in every report: lines that differ in nothing else are counted, not printed.
			$shape = $line -replace '"p":\d+', '"p":_'
			if ($shape -ceq $last) {
				$repeats++
			} else {
				if ($repeats -gt 0) { Write-Host ('       (and ' + $repeats + ' more like it)') }
				$repeats = 0
				$last = $shape
				Write-Host ($t.ToString().PadLeft(6) + ' ' + $line)
			}
			$pending = $bridge.StandardOutput.ReadLineAsync()
		}
		Start-Sleep -Milliseconds 25
	}
	if ($repeats -gt 0) { Write-Host ('       (and ' + $repeats + ' more like it)') }
	Write-Host ('Reports: ' + $reports + ' in ' + $watch.ElapsedMilliseconds + ' ms' + $(if ($ended) { ' (the bridge ended by itself)' } else { '' }))
	Get-ChildItem $temp -Filter 'v1zir-art-*' -ErrorAction SilentlyContinue | ForEach-Object {
		$bytes = [System.IO.File]::ReadAllBytes($_.FullName)
		Write-Host ('Cover file of the bridge: ' + $_.Name + ', ' + $bytes.Length + ' bytes, starts with ' + ('{0:X2}{1:X2}' -f $bytes[0], $bytes[1]))
	}
	Write-Host ('Covers of the fake player: ' + (Get-Item $coverA).Length + ' and ' + (Get-Item $coverB).Length + ' bytes')
	try { $bridge.StandardInput.Close() } catch { }
	if (-not $bridge.WaitForExit(5000)) { $bridge.Kill() }
	$errors = $bridge.StandardError.ReadToEnd()
	if ($errors) { Write-Host ('Bridge stderr: ' + $errors) }
	$left = @(Get-ChildItem $temp -Filter 'v1zir-art-*' -ErrorAction SilentlyContinue).Count
	Write-Host ('Cover files left after the bridge ended: ' + $left)
	$form.Close()
} catch {
	Write-Host ('FAKE PLAYER FAILED: ' + $_.Exception.ToString())
	Write-Host $_.ScriptStackTrace
}
exit 0
