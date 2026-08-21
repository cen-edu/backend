# 이미지를 빌드해 Docker Hub 에 올리고 EC2 에 배포한다.
#
#   .\scripts\release.ps1 1.0.1                 백엔드만
#   .\scripts\release.ps1 1.0.1 -Frontend       백엔드 + 프론트(같은 태그)
#   .\scripts\release.ps1 1.0.1 -FrontendOnly   프론트만
#   .\scripts\release.ps1 1.0.1 -SkipDeploy     빌드·푸시까지만 (EC2 는 건드리지 않는다)
#
# 설정은 아래 기본값을 쓰고, 환경 변수가 있으면 그쪽이 이긴다. 인스턴스를 다시 만들어
# 주소가 바뀌면 CEN_EDU_HOST 만 바꾸면 된다.
#
#   $env:CEN_EDU_HOST = "ec2-user@1.2.3.4"

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Tag,

    [switch]$Frontend,
    [switch]$FrontendOnly,
    [switch]$SkipDeploy,

    # 이미 올린 태그를 덮어쓸 때만 쓴다. 기본적으로 막는다 — 같은 태그를 덮어쓰면
    # EC2 가 이미 받아 둔 이미지를 그대로 써서 "고쳤는데 안 바뀐다" 가 된다.
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Get-Setting($name, $fallback) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) { return $fallback }
    return $value
}

$DockerHubUser = Get-Setting 'DOCKERHUB_USER' 'suhwan1117'
$RemoteHost    = Get-Setting 'CEN_EDU_HOST'   'ec2-user@54.180.102.43'
$KeyPath       = Get-Setting 'CEN_EDU_KEY'    "$env:USERPROFILE\Documents\cen-edu.ppk"
$FrontendRepo  = Get-Setting 'CEN_EDU_FRONTEND_REPO' "$env:USERPROFILE\Documents\cen-edu-frontend"
$HealthUrl     = Get-Setting 'CEN_EDU_HEALTH_URL' 'https://d2u1d13c5vp4n5.cloudfront.net/actuator/health'
$Plink         = Get-Setting 'CEN_EDU_PLINK' 'C:\Program Files\PuTTY\plink.exe'

$BackendRepoRoot = Split-Path -Parent $PSScriptRoot

# 도커가 꺼져 있으면 빌드 도중에야 알게 되고, 그때 나오는 메시지는 npipe 경로 이야기라
# 원인을 짚기 어렵다. 먼저 확인하고 사람이 읽을 수 있는 말로 세운다.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker 데몬에 연결하지 못했다. Docker Desktop 을 실행한 뒤 다시 시도한다."
}

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Invoke-Checked($what, [scriptblock]$block) {
    & $block
    if ($LASTEXITCODE -ne 0) {
        throw "$what 실패 (exit $LASTEXITCODE)"
    }
}

# 이미 Docker Hub 에 있는 태그인지 본다. manifest inspect 는 없으면 0 이 아닌 코드를 준다.
function Test-TagExists($image) {
    docker manifest inspect $image *> $null
    return ($LASTEXITCODE -eq 0)
}

function Show-SourceState($repoPath, $label) {
    Push-Location $repoPath
    try {
        $sha = (git rev-parse --short HEAD).Trim()
        $branch = (git rev-parse --abbrev-ref HEAD).Trim()
        $dirty = (git status --porcelain)
        Write-Host "    $label : $branch @ $sha"
        if ($dirty) {
            # 커밋하지 않은 변경도 이미지에 들어간다. 나중에 "이 이미지가 어느 커밋인지"
            # 를 SHA 로 되짚을 수 없게 되므로 알려 준다.
            Write-Host "    경고: 커밋되지 않은 변경이 이미지에 포함된다" -ForegroundColor Yellow
        }
    } finally {
        Pop-Location
    }
}

function Publish-Image($repoPath, $imageName, $label, [string[]]$buildArgs) {
    $image = "$DockerHubUser/$imageName" + ":" + $Tag

    if (-not $Force -and (Test-TagExists $image)) {
        throw "$image 는 이미 Docker Hub 에 있다. 태그를 올리거나 -Force 를 쓴다."
    }

    Show-SourceState $repoPath $label

    Write-Step "$label 빌드 — $image"
    Push-Location $repoPath
    try {
        $args = @('build', '-t', $image)
        foreach ($a in $buildArgs) { $args += @('--build-arg', $a) }
        $args += '.'
        Invoke-Checked "$label 빌드" { docker @args }
    } finally {
        Pop-Location
    }

    Write-Step "$label push"
    Invoke-Checked "$label push" { docker push $image }
}

# --- 빌드와 push -------------------------------------------------------------

if (-not $FrontendOnly) {
    Publish-Image $BackendRepoRoot 'cen-edu-backend' '백엔드' @()
}

if ($Frontend -or $FrontendOnly) {
    if (-not (Test-Path $FrontendRepo)) {
        throw "프론트 저장소를 찾지 못했다: $FrontendRepo (CEN_EDU_FRONTEND_REPO 로 지정한다)"
    }

    # VITE_ 값은 빌드 시점에 번들에 박힌다. 키가 없으면 필기 입력 화면만 동작하지 않는다.
    $frontArgs = @()
    if ($env:MYSCRIPT_APP_KEY)  { $frontArgs += "VITE_MYSCRIPT_APPLICATION_KEY=$env:MYSCRIPT_APP_KEY" }
    if ($env:MYSCRIPT_HMAC_KEY) { $frontArgs += "VITE_MYSCRIPT_HMAC_KEY=$env:MYSCRIPT_HMAC_KEY" }
    if ($frontArgs.Count -eq 0) {
        Write-Host "    참고: MyScript 키가 없어 필기 입력 없이 빌드한다" -ForegroundColor Yellow
    }

    Publish-Image $FrontendRepo 'cen-edu-frontend' '프론트' $frontArgs
}

# --- EC2 배포 ---------------------------------------------------------------

if ($SkipDeploy) {
    Write-Step "완료 — push 까지만 했다. EC2 에서 ./deploy.sh $Tag 를 실행한다"
    return
}

if (-not (Test-Path $Plink)) {
    throw "plink 를 찾지 못했다: $Plink (PuTTY 설치 경로를 CEN_EDU_PLINK 로 지정한다)"
}
if (-not (Test-Path $KeyPath)) {
    throw "키 파일을 찾지 못했다: $KeyPath (CEN_EDU_KEY 로 지정한다)"
}

# deploy.sh 는 백엔드 태그와 프론트 태그를 따로 받는다. '-' 는 그대로 두라는 뜻이다.
$backendArg  = if ($FrontendOnly) { '-' } else { $Tag }
$frontendArg = if ($Frontend -or $FrontendOnly) { $Tag } else { '' }

Write-Step "EC2 배포 — $RemoteHost"
Invoke-Checked 'EC2 배포' {
    & $Plink -batch -i $KeyPath $RemoteHost "cd ~/app && ./deploy.sh $backendArg $frontendArg"
}

# --- 확인 -------------------------------------------------------------------

Write-Step "헬스체크 — $HealthUrl"
try {
    $response = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 30
    Write-Host "    status = $($response.status)" -ForegroundColor Green
    if ($response.status -ne 'UP') { throw "status 가 UP 이 아니다" }
} catch {
    Write-Host "    헬스체크 실패: $_" -ForegroundColor Red
    Write-Host "    EC2 에서 로그를 본다: docker compose -f docker-compose.prod.yml logs --tail 50 backend"
    exit 1
}

Write-Step "배포 완료 — $Tag"
