param(
    [ValidateSet('.env', '.env.production')]
    [string]$FileName = '.env'
)

$ErrorActionPreference = 'Stop'
$repositoryDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$environmentPath = Join-Path $repositoryDirectory $FileName
if ([IO.File]::Exists($environmentPath)) {
    throw "$FileName already exists. Preserve its credentials and edit it directly if needed."
}

function New-DeploymentSecret {
    $secretBytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($secretBytes) }
    finally { $random.Dispose() }
    return [BitConverter]::ToString($secretBytes).Replace('-', '').ToLowerInvariant()
}

$environmentText = [IO.File]::ReadAllText((Join-Path $repositoryDirectory '.env.example'))
if ($FileName -eq '.env.production') {
    $environmentText = $environmentText.Replace('COMPOSE_PROJECT_NAME=cangshuo-toolbox-local',
            'COMPOSE_PROJECT_NAME=cangshuo-toolbox-production')
}
foreach ($secretName in @('MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD', 'MINIO_ROOT_PASSWORD', 'JWT_SECRET')) {
    $environmentText = $environmentText.Replace(($secretName + '='), ($secretName + '=' + (New-DeploymentSecret)))
}

$stream = [IO.File]::Open($environmentPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $environmentBytes = [Text.UTF8Encoding]::new($false).GetBytes($environmentText)
    $stream.Write($environmentBytes, 0, $environmentBytes.Length)
} finally {
    $stream.Dispose()
}
Write-Output "Created $FileName with random credentials. Keep this file private."
