# Now playing bridge of V1zir Visuals for Windows 10/11, started hidden by WindowsMediaSource.
# Prints one JSON line about every 300 ms from the system media transport controls (the Spotify
# session if there is one, else the current session) and runs playpause / next / previous read
# from stdin. It exits when stdin closes. Only ASCII is printed (everything else is \u escaped),
# so the console code page does not matter.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
$taskMethods = @{}

# Waits for a WinRT IAsyncOperation<type>; null when it takes longer than three seconds.
function Await($operation, [Type]$type) {
	$method = $taskMethods[$type]
	if ($null -eq $method) {
		$method = $asTask.MakeGenericMethod($type)
		$taskMethods[$type] = $method
	}
	$task = $method.Invoke($null, @($operation))
	if (-not $task.Wait(3000)) { return $null }
	return $task.Result
}

function Json([string]$text) {
	$builder = New-Object System.Text.StringBuilder ($text.Length + 2)
	[void]$builder.Append('"')
	foreach ($c in $text.ToCharArray()) {
		$code = [int]$c
		if ($code -eq 34 -or $code -eq 92) { [void]$builder.Append('\').Append($c) }
		elseif ($code -lt 32 -or $code -gt 126) { [void]$builder.Append('\u').Append($code.ToString('x4')) }
		else { [void]$builder.Append($c) }
	}
	[void]$builder.Append('"')
	return $builder.ToString()
}

function Find-Session {
	foreach ($candidate in $manager.GetSessions()) {
		if ($candidate.SourceAppUserModelId -like '*spotify*') { return $candidate }
	}
	return $manager.GetCurrentSession()
}

$md5 = [System.Security.Cryptography.MD5]::Create()
$artFile = ''
$artHash = ''
$artCount = 0

function Remove-Art {
	if ($script:artFile) { Remove-Item -LiteralPath $script:artFile -ErrorAction SilentlyContinue }
	$script:artFile = ''
}

# Saves the cover as a PNG in the temp folder (unless it is the one saved last) and deletes the previous file.
function Save-Art($thumbnail) {
	if ($null -eq $thumbnail) { return }
	$stream = Await ($thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
	if ($null -eq $stream) { return }
	$memory = New-Object System.IO.MemoryStream
	try {
		$source = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
		$source.CopyTo($memory)
		$source.Dispose()
	} finally {
		$stream.Dispose()
	}
	$bytes = $memory.ToArray()
	if ($bytes.Length -lt 8) { return }
	$hash = [Convert]::ToBase64String($md5.ComputeHash($bytes))
	if ($hash -ceq $script:artHash) { return }
	$script:artHash = $hash
	$script:artCount++
	$file = Join-Path ([System.IO.Path]::GetTempPath()) ('v1zir-art-' + $PID + '-' + $script:artCount + '.png')
	if ($bytes[0] -eq 0x89 -and $bytes[1] -eq 0x50) {
		[System.IO.File]::WriteAllBytes($file, $bytes)
	} else {
		Add-Type -AssemblyName System.Drawing
		$memory.Position = 0
		$image = [System.Drawing.Image]::FromStream($memory)
		try { $image.Save($file, [System.Drawing.Imaging.ImageFormat]::Png) } finally { $image.Dispose() }
	}
	Remove-Art
	$script:artFile = $file
}

$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $manager) { exit 1 }
$playingStatus = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing
$mediaType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$stdin = New-Object System.IO.StreamReader ([Console]::OpenStandardInput())
$pendingCommand = $stdin.ReadLineAsync()
$clock = [System.Diagnostics.Stopwatch]::StartNew()
$lastKey = $null
$artChecks = 0
$nextArtCheck = 0

while ($true) {
	while ($pendingCommand.IsCompleted) {
		$command = $pendingCommand.Result
		if ($null -eq $command) {
			Remove-Art
			exit
		}
		try {
			$session = Find-Session
			if ($null -ne $session) {
				switch ($command.Trim()) {
					'playpause' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
					'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
					'previous' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
				}
			}
		} catch { }
		$pendingCommand = $stdin.ReadLineAsync()
	}
	try {
		$line = '{"none":1}'
		$session = Find-Session
		if ($null -ne $session) {
			$media = Await ($session.TryGetMediaPropertiesAsync()) $mediaType
			if ($null -ne $media -and $media.Title) {
				$timeline = $session.GetTimelineProperties()
				$playing = $session.GetPlaybackInfo().PlaybackStatus -eq $playingStatus
				$duration = [Math]::Max(($timeline.EndTime - $timeline.StartTime).TotalMilliseconds, 0)
				$position = ($timeline.Position - $timeline.StartTime).TotalMilliseconds
				# The timeline only changes on events (play, pause, seek): move it on by the time since.
				if ($playing -and $timeline.LastUpdatedTime.Year -gt 2000) {
					$position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
				}
				if ($duration -gt 0) { $position = [Math]::Min($position, $duration) }
				$position = [Math]::Max($position, 0)
				$artist = $media.Artist
				if (-not $artist) { $artist = $media.AlbumArtist }
				$key = $media.Title + '|' + $artist + '|' + $media.AlbumTitle
				if ($key -cne $lastKey) {
					$lastKey = $key
					Remove-Art
					$artHash = ''
					# Players often publish the new cover a moment after the title: look again a few times.
					$artChecks = 4
					$nextArtCheck = 0
				}
				if ($artChecks -gt 0 -and $clock.ElapsedMilliseconds -ge $nextArtCheck) {
					$artChecks--
					$nextArtCheck = $clock.ElapsedMilliseconds + 900
					try { Save-Art $media.Thumbnail } catch { }
				}
				$line = '{"t":' + (Json $media.Title) + ',"a":' + (Json $artist) + ',"al":' + (Json $media.AlbumTitle) +
					',"p":' + [long]$position + ',"d":' + [long]$duration + ',"s":' + [int]$playing + ',"art":' + (Json $artFile) + '}'
			}
		}
		[Console]::Out.WriteLine($line)
		[Console]::Out.Flush()
	} catch { }
	# Sleeps until the next report, or until a command arrives.
	$null = $pendingCommand.Wait(300)
}
