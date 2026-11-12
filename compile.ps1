# TODO: Validate
param(
    [ValidateSet("release", "debug")]
    [string]$Variant = "release",
    [ValidateRange(1, 2100000000)]
    [int]$VersionCode
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Release signing secrets come from these generic credentials in Windows Credential Manager.
# The keystore credential holds the keystore path and password, and the signing key credential holds the key alias
# and key password.
$keystoreCredentialTarget = "StreamChanneler:AndroidKeystore"
$signingKeyCredentialTarget = "StreamChanneler:AndroidSigningKey"

# TODO: Validate
function Read-WindowsCredential {
    param([string]$CredentialTarget)
    # PowerShell has no built-in cmdlet that reads Credential Manager,
    # so this compiles a small wrapper around CredReadW.
    # Add-Type fails when the type already exists in the session, so the wrapper is only compiled once.
    if (-not ("StreamChanneler.CredentialManager" -as [type])) {
        Add-Type -Namespace StreamChanneler -Name CredentialManager -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
private struct NativeCredential {
    public int Flags;
    public int Type;
    public string TargetName;
    public string Comment;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
    public int CredentialBlobSize;
    public IntPtr CredentialBlob;
    public int Persist;
    public int AttributeCount;
    public IntPtr Attributes;
    public string TargetAlias;
    public string UserName;
}

[DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
private static extern bool CredRead(string target, int type, int flags, out IntPtr credentialPointer);

[DllImport("advapi32.dll")]
private static extern void CredFree(IntPtr credentialPointer);

public static string[] ReadGeneric(string target) {
    IntPtr credentialPointer;
    if (!CredRead(target, 1, 0, out credentialPointer)) return null;
    try {
        NativeCredential credential = (NativeCredential)Marshal.PtrToStructure(credentialPointer, typeof(NativeCredential));
        string secret = credential.CredentialBlobSize > 0
            ? Marshal.PtrToStringUni(credential.CredentialBlob, credential.CredentialBlobSize / 2)
            : "";
        return new string[] { credential.UserName, secret };
    } finally {
        CredFree(credentialPointer);
    }
}
'@
    }
    $userNameAndSecret = [StreamChanneler.CredentialManager]::ReadGeneric($CredentialTarget)
    if ($null -eq $userNameAndSecret) { return $null }
    [pscustomobject]@{ UserName = $userNameAndSecret[0]; Secret = $userNameAndSecret[1] }
}

# Gradle finds the Android SDK through ANDROID_HOME or local.properties.
# When neither is set, the script falls back to the folder where Android Studio installs the SDK.
if (-not $env:ANDROID_HOME -and -not (Test-Path "local.properties")) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (-not (Test-Path $sdk)) {
        Write-Error "Android SDK not found. Set ANDROID_HOME or install the SDK to $sdk."
    }
    $env:ANDROID_HOME = $sdk
}

$signingEnvironmentVariableNames = @("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
# build.gradle.kts reads the release signing key from these environment variables.
# When the caller hasn't set them, the script fills them in from Credential Manager for the length of the build.
$setsSigningEnvironmentVariables = $Variant -eq "release" -and -not $env:ANDROID_KEYSTORE_PATH
if ($setsSigningEnvironmentVariables) {
    $keystoreCredential = Read-WindowsCredential -CredentialTarget $keystoreCredentialTarget
    $signingKeyCredential = Read-WindowsCredential -CredentialTarget $signingKeyCredentialTarget
    if ($null -eq $keystoreCredential -or $null -eq $signingKeyCredential) {
        Write-Error "The release signing key isn't in Windows Credential Manager. Add the generic credentials $keystoreCredentialTarget (user name: keystore path, password: keystore password) and $signingKeyCredentialTarget (user name: key alias, password: key password)."
    }
    if (-not (Test-Path $keystoreCredential.UserName)) {
        Write-Error "The keystore in $keystoreCredentialTarget doesn't exist: $($keystoreCredential.UserName)"
    }
    $env:ANDROID_KEYSTORE_PATH = $keystoreCredential.UserName
    $env:ANDROID_KEYSTORE_PASSWORD = $keystoreCredential.Secret
    $env:ANDROID_KEY_ALIAS = $signingKeyCredential.UserName
    $env:ANDROID_KEY_PASSWORD = $signingKeyCredential.Secret
}

$task = if ($Variant -eq "debug") { "assembleDebug" } else { "assembleRelease" }
$gradleArguments = @($task)
if ($VersionCode) { $gradleArguments += "-PversionCode=$VersionCode" }
try {
    & .\gradlew.bat @gradleArguments
    $gradleExitCode = $LASTEXITCODE
} finally {
    if ($setsSigningEnvironmentVariables) {
        # Remove the secrets again so that they don't stay in the PowerShell session after the build.
        $signingEnvironmentVariableNames | ForEach-Object { Remove-Item "env:$_" -ErrorAction SilentlyContinue }
    }
}
if ($gradleExitCode -ne 0) { exit $gradleExitCode }

Get-ChildItem "build\outputs\apk\$Variant\*.apk" | ForEach-Object { $_.FullName }
