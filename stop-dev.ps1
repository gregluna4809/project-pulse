$ports = @(8080, 5173)

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue

    foreach ($connection in $connections) {
        $processId = $connection.OwningProcess

        if ($processId -and $processId -ne 0) {
            Write-Host "Stopping process on port $port with PID $processId"
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "ProjectPulse dev services stopped."