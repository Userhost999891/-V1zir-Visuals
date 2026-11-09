# Now playing bridge of V1zir Visuals for Windows 10/11, started hidden by WindowsMediaSource.
# Prints one JSON line about every 300 ms from the system media transport controls (the Spotify
# session if there is one, else the current session) and runs playpause / next / previous read
# from stdin. It exits when stdin closes. Only ASCII is printed (everything else is \u escaped),
# so the console code page does not matter.
# A session manager kept for long goes stale: it can keep reporting a song that changed by itself
# (Spotify's next song) until playback is paused or resumed. So it is requested afresh every second,
# and at once on a refresh command, which the game sends when Spotify's window names another song.
# When even that does not catch up, the game starts the bridge again: a new process sees what the
# players report now. The game runs it in a multi-threaded apartment (-MTA), so the media controls'
# notifications reach it without a message loop.
# The cover often comes after the title (or only with the player's next update), so it is looked for
# until there is one, every 1.5 seconds, and then every 5 seconds in case it changes; reading it never
# holds up the reports. "as" tells how it went: 0 the player gives none, 1 reading, 2 saved, 3 failed.
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
$artState = 0
# The cover being read: its OpenReadAsync task, for which song, and since when.
$artTask = $null
$artTaskKey = ''
$artTaskStart = 0
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]

function Remove-Art {
	if ($script:artFile) { Remove-Item -LiteralPath $script:artFile -ErrorAction SilentlyContinue }
	$script:artFile = ''
}

# Saves the cover in the temp folder as the player gives it (PNG or JPEG; the game decodes either), unless it is
# the one saved last, and deletes the previous file.
function Save-Bytes([byte[]]$bytes) {
	if ($null -eq $bytes -or $bytes.Length -lt 8) {
		if (-not $script:artFile) { $script:artState = 3 }
		return
	}
	$script:artState = 2
	$hash = [Convert]::ToBase64String($md5.ComputeHash($bytes))
	if ($hash -ceq $script:artHash) { return }
	$script:artHash = $hash
	$script:artCount++
	$extension = '.img'
	if ($bytes[0] -eq 0x89 -and $bytes[1] -eq 0x50) { $extension = '.png' }
	elseif ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0xD8) { $extension = '.jpg' }
	$file = Join-Path ([System.IO.Path]::GetTempPath()) ('v1zir-art-' + $PID + '-' + $script:artCount + $extension)
	[System.IO.File]::WriteAllBytes($file, $bytes)
	Remove-Art
	$script:artFile = $file
}

# Starts reading the player's cover without waiting for it (End-Art picks it up later).
function Begin-Art($thumbnail, [string]$forKey) {
	if ($null -eq $thumbnail) {
		if (-not $script:artFile) { $script:artState = 0 }
		return
	}
	$method = $taskMethods[$streamType]
	if ($null -eq $method) {
		$method = $asTask.MakeGenericMethod($streamType)
		$taskMethods[$streamType] = $method
	}
	$script:artTask = $method.Invoke($null, @($thumbnail.OpenReadAsync()))
	$script:artTaskKey = $forKey
	$script:artTaskStart = $clock.ElapsedMilliseconds
	if (-not $script:artFile) { $script:artState = 1 }
}

# A cover read that finished is saved if it belongs to the song still playing; one that hangs is given up.
function End-Art {
	$task = $script:artTask
	if ($null -eq $task) { return }
	if (-not $task.IsCompleted) {
		if ($clock.ElapsedMilliseconds - $script:artTaskStart -gt 20000) { $script:artTask = $null }
		return
	}
	$script:artTask = $null
	if ($task.Status -ne [System.Threading.Tasks.TaskStatus]::RanToCompletion -or $null -eq $task.Result) {
		if (-not $script:artFile) { $script:artState = 3 }
		return
	}
	$stream = $task.Result
	$memory = New-Object System.IO.MemoryStream
	try {
		$source = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
		$source.CopyTo($memory)
		$source.Dispose()
	} finally {
		$stream.Dispose()
	}
	if ($script:artTaskKey -cne $script:lastKey) { return }
	Save-Bytes $memory.ToArray()
}

$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $manager) { exit 1 }
$playingStatus = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing
$mediaType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$stdin = New-Object System.IO.StreamReader ([Console]::OpenStandardInput())
$pendingCommand = $stdin.ReadLineAsync()
$clock = [System.Diagnostics.Stopwatch]::StartNew()
$lastKey = $null
$nextArtCheck = 0
$nextRefresh = 1000

while ($true) {
	while ($pendingCommand.IsCompleted) {
		$command = $pendingCommand.Result
		if ($null -eq $command) {
			Remove-Art
			exit
		}
		try {
			if ($command.Trim() -eq 'refresh') {
				$nextRefresh = 0
				# The cover is looked at again too; an unchanged one is not saved again (its hash matches).
				$nextArtCheck = 0
			} else {
				$session = Find-Session
				if ($null -ne $session) {
					switch ($command.Trim()) {
						'playpause' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
						'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
						'previous' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
					}
				}
			}
		} catch { }
		$pendingCommand = $stdin.ReadLineAsync()
	}
	if ($clock.ElapsedMilliseconds -ge $nextRefresh) {
		# A fresh manager sees what the players report now.
		$nextRefresh = $clock.ElapsedMilliseconds + 1000
		try {
			$fresh = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
			if ($null -ne $fresh) { $manager = $fresh }
		} catch { }
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
					$artState = 1
					# A read still going is for the previous song: its result is dropped (End-Art checks the key).
					$artTask = $null
					$nextArtCheck = 0
				}
				try { End-Art } catch { $artTask = $null }
				if ($null -eq $artTask -and $clock.ElapsedMilliseconds -ge $nextArtCheck) {
					# Until there is a cover, look again soon; after that now and then, in case it changes.
					if ($artFile) { $nextArtCheck = $clock.ElapsedMilliseconds + 5000 }
					else { $nextArtCheck = $clock.ElapsedMilliseconds + 1500 }
					try { Begin-Art $media.Thumbnail $key } catch { $artTask = $null; if (-not $artFile) { $artState = 3 } }
				}
				$line = '{"t":' + (Json $media.Title) + ',"a":' + (Json $artist) + ',"al":' + (Json $media.AlbumTitle) +
					',"p":' + [long]$position + ',"d":' + [long]$duration + ',"s":' + [int]$playing + ',"art":' + (Json $artFile) +
					',"as":' + $artState + '}'
			}
		}
		[Console]::Out.WriteLine($line)
		[Console]::Out.Flush()
	} catch { }
	# Sleeps until the next report, or until a command arrives.
	$null = $pendingCommand.Wait(300)
}
