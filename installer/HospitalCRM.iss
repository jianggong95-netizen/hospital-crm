; =====================================================================
; Hospital CRM Windows installer (Inno Setup 6)
; Build via release\build-release.ps1, which writes installer\generated.iss
; with the MyAppVersion + Staging defines (this ISCC build does not accept
; /D command-line defines, so values are injected through an include file).
; AppId is FIXED forever so upgrades overwrite the same program.
; Keep this file ASCII-only: ISCC reads .iss as ANSI unless a UTF-8 BOM
; is present, so non-ASCII wizard text can render as mojibake.
; =====================================================================

#define MyAppPublisher "Hospital CRM"
#define MyAppName "Hospital CRM"

#if FileExists("generated.iss")
#include "generated.iss"
#else
  ; Fallbacks for compiling this file directly (e.g. from the Inno IDE).
  #define MyAppVersion "0.0.0"
  #define Staging "..\dist\staging"
  #define MyCompression "lzma2/ultra64"
#endif

[Setup]
; Fixed GUID: upgrades overwrite the same program instead of coexisting.
AppId={{B7F3C1A2-4D6E-4F8A-9C2B-1A2B3C4D5E6F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={sd}\HospitalCRM
DisableProgramGroupPage=yes
DisableWelcomePage=no
PrivilegesRequired=admin
OutputDir=..\dist\installer
OutputBaseFilename=HospitalCRM-Setup-{#MyAppVersion}
Compression={#MyCompression}
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
VersionInfoVersion={#MyAppVersion}
UninstallDisplayName={#MyAppName} {#MyAppVersion}
CloseApplications=force

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Dirs]
; The service runs as the installing user (never as SYSTEM), so that user must be able
; to write logs, pgdata, backups and replace the jar during upgrades.
Name: "{app}"; Permissions: authusers-modify
Name: "{app}\config"; Permissions: authusers-modify
Name: "{app}\logs"; Permissions: authusers-modify
Name: "{app}\backups"; Permissions: authusers-modify
Name: "{app}\run"; Permissions: authusers-modify
Name: "{app}\app"; Permissions: authusers-modify
Name: "{app}\bin"; Permissions: authusers-modify

[Files]
Source: "{#Staging}\app\crm-api.jar"; DestDir: "{app}\app"; Flags: ignoreversion
Source: "{#Staging}\version.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#Staging}\env.template"; DestDir: "{app}\config"; DestName: "env.template"; Flags: ignoreversion
Source: "{#Staging}\bin\*.ps1"; DestDir: "{app}\bin"; Flags: ignoreversion
Source: "{#Staging}\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist
Source: "{#Staging}\pgsql\*"; DestDir: "{app}\pgsql"; Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist
Source: "{#Staging}\README.txt"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

[Icons]
Name: "{autoprograms}\{#MyAppName}\Start"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\start.ps1"""; WorkingDir: "{app}"
Name: "{autoprograms}\{#MyAppName}\Stop"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\stop.ps1"""; WorkingDir: "{app}"
Name: "{autoprograms}\{#MyAppName}\Status"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\status.ps1"""; WorkingDir: "{app}"
Name: "{autodesktop}\{#MyAppName} (Start)"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\start.ps1"""; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\init-db.ps1"" -DbName ""{code:GetCfgDbName}"" -DbUser ""{code:GetCfgDbUser}"" -DbPassword ""{code:GetCfgDbPass}"" -DbPort ""{code:GetCfgDbPort}"""; \
  StatusMsg: "Initializing bundled database ..."; Check: ShouldInitDb; Flags: runhidden runasoriginaluser
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\start.ps1"""; \
  Description: "Start Hospital CRM now"; WorkingDir: "{app}"; \
  Flags: postinstall nowait runhidden runasoriginaluser

[UninstallRun]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\bin\stop.ps1"""; \
  WorkingDir: "{app}"; Flags: runhidden; RunOnceId: "StopCrm"

[Code]
var
  CfgPage: TInputQueryWizardPage;
  DbModePage: TInputOptionWizardPage;
  GIsUpgrade: Boolean;
  GJwtKey: String;
  GAesKey: String;
  GPagesReady: Boolean;
  IdxPort, IdxDbPort, IdxDbName, IdxDbUser, IdxDbPass, IdxDbHost, IdxAdminUser, IdxAdminPass: Integer;

// Generate the JWT signing key (hex, 32 bytes) and the AES-256 data-encryption
// key (Base64 of 32 bytes) with the Windows cryptographic RNG. Pascal Script in
// this Inno build has no CSPRNG, so delegate to PowerShell.
function GenerateSecrets(): Boolean;
var
  tmpFile, ps, params, content: String;
  lines: TArrayOfString;
  rc, sep: Integer;
begin
  Result := False;
  GJwtKey := '';
  GAesKey := '';
  tmpFile := ExpandConstant('{%TEMP}') + '\hospitalcrm-keys.tmp';
  if FileExists(tmpFile) then DeleteFile(tmpFile);
  params := '-NoProfile -ExecutionPolicy Bypass -Command "' +
    '$b=New-Object byte[] 32;' +
    '[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b);' +
    '$h=($b|ForEach-Object{$_.ToString(''x2'')}) -join '''';' +
    'Set-Content -LiteralPath ''' + tmpFile + ''' -Value ($h+''|''+[Convert]::ToBase64String($b)) -Encoding ascii"';
  ps := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
  if not Exec(ps, params, '', SW_HIDE, ewWaitUntilTerminated, rc) or (rc <> 0) then
  begin
    Log('Secret generation failed: PowerShell exit=' + IntToStr(rc));
    Exit;
  end;
  if (not LoadStringsFromFile(tmpFile, lines)) or (GetArrayLength(lines) < 1) then
  begin
    Log('Secret generation failed: cannot read ' + tmpFile);
    Exit;
  end;
  DeleteFile(tmpFile);
  content := Trim(lines[0]);
  sep := Pos('|', content);
  if (sep <= 1) or (sep >= Length(content)) then Exit;
  GJwtKey := Copy(content, 1, sep - 1);
  GAesKey := Copy(content, sep + 1, Length(content) - sep);
  // 32 random bytes -> 64 hex chars (JWT secret, used as raw UTF-8 bytes) and 44 base64 chars (AES-256 key)
  Result := (Length(GJwtKey) = 64) and (Length(GAesKey) = 44);
  if not Result then Log('Secret generation produced unexpected output.');
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  if not GenerateSecrets() then
  begin
    MsgBox('Hospital CRM could not generate the installation secrets ' +
      '(PowerShell is required). Aborting.', mbError, MB_OK);
    Result := False;
  end;
end;

function GetCfgValue(Index: Integer; Default: String): String;
var
  line: String;
begin
  Result := Default;
  if not GPagesReady then Exit;
  line := CfgPage.Values[Index];
  if line <> '' then Result := line;
end;

// The four below are called from [Run] through {code:...}, which requires the
// (Param: String): String prototype.
function GetCfgDbName(Param: String): String; begin Result := GetCfgValue(IdxDbName, 'crm'); end;
function GetCfgDbUser(Param: String): String; begin Result := GetCfgValue(IdxDbUser, 'crm'); end;
function GetCfgDbPass(Param: String): String; begin Result := GetCfgValue(IdxDbPass, ''); end;
function GetCfgDbPort(Param: String): String; begin Result := GetCfgValue(IdxDbPort, '5432'); end;
function GetCfgServerPort(): String; begin Result := GetCfgValue(IdxPort, '8080'); end;
function GetCfgAdminUser(): String;  begin Result := GetCfgValue(IdxAdminUser, 'admin'); end;
function GetCfgAdminPass(): String;  begin Result := GetCfgValue(IdxAdminPass, ''); end;
function GetDbHost(): String;        begin Result := GetCfgValue(IdxDbHost, '127.0.0.1'); end;

function UseInternalDb(): Boolean;
begin
  if not GPagesReady then Result := True else Result := DbModePage.Values[0];
end;

function InternalDbFlagText(): String;
begin
  if UseInternalDb() then Result := 'true' else Result := 'false';
end;

function ShouldInitDb(): Boolean;
begin
  Result := (not GIsUpgrade) and UseInternalDb()
    and (not FileExists(ExpandConstant('{app}\pgdata\PG_VERSION')));
end;

function GetExistingInstallDir(): String;
var
  s: String;
begin
  Result := '';
  if RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{B7F3C1A2-4D6E-4F8A-9C2B-1A2B3C4D5E6F}_is1', 'InstallLocation', s) then
    Result := s
  else if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{B7F3C1A2-4D6E-4F8A-9C2B-1A2B3C4D5E6F}_is1', 'InstallLocation', s) then
    Result := s;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  dir, ps: String;
  rc: Integer;
begin
  Result := '';
  dir := GetExistingInstallDir();
  if (dir <> '') and FileExists(dir + '\bin\stop.ps1') then
  begin
    Log('Existing install at ' + dir + '; stopping old service before upgrade.');
    ps := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
    Exec(ps, '-NoProfile -ExecutionPolicy Bypass -File "' + dir + '\bin\stop.ps1"', dir, SW_HIDE, ewWaitUntilTerminated, rc);
  end;
end;

// Command-line overrides so the installer also works unattended:
//   /PORT=8080 /DBPORT=5432 /DBNAME=crm /DBUSER=crm /DBPASS=secret
//   /DBHOST=127.0.0.1 /DBMODE=external /ADMINUSER=admin /ADMINPASS=secret
function ParamOr(const Name: String; const Def: String): String;
begin
  Result := ExpandConstant('{param:' + Name + '|' + Def + '}');
end;

function IsSilent(): Boolean;
var
  tail: String;
begin
  tail := Uppercase(GetCmdTail);
  Result := (Pos('/SILENT', tail) > 0) or (Pos('/VERYSILENT', tail) > 0);
end;

procedure InitializeWizard();
var
  idx: Integer;
begin
  // Upgrade detection must not touch {app} here (it is not initialized until the
  // directory has been confirmed), so use the registered install location.
  GIsUpgrade := FileExists(GetExistingInstallDir() + '\config\.env');

  DbModePage := CreateInputOptionPage(wpSelectDir,
    'Database', 'Choose the database for Hospital CRM',
    'For demo / small single-server deployments use the bundled database. If the hospital already runs PostgreSQL, choose the external option.', False, False);
  DbModePage.Add('Use bundled PostgreSQL (shipped with the installer, auto-initialized)');
  DbModePage.Add('Connect to an existing PostgreSQL server');
  DbModePage.Values[0] := (Uppercase(ParamOr('DBMODE', 'internal')) <> 'EXTERNAL');

  CfgPage := CreateInputQueryPage(DbModePage.ID,
    'Runtime configuration', 'Set database connection, port and admin account',
    'These values are written to config\.env (first install only; upgrades keep the existing file).');
  idx := CfgPage.Add('HTTP port', False);                 IdxPort := idx;      CfgPage.Values[idx] := ParamOr('PORT', '8080');
  idx := CfgPage.Add('Database port', False);             IdxDbPort := idx;    CfgPage.Values[idx] := ParamOr('DBPORT', '5432');
  idx := CfgPage.Add('Database name', False);             IdxDbName := idx;    CfgPage.Values[idx] := ParamOr('DBNAME', 'crm');
  idx := CfgPage.Add('Database user', False);             IdxDbUser := idx;    CfgPage.Values[idx] := ParamOr('DBUSER', 'crm');
  idx := CfgPage.Add('Database password', True);          IdxDbPass := idx;    CfgPage.Values[idx] := ParamOr('DBPASS', '');
  idx := CfgPage.Add('External DB host (only if using an existing server)', False); IdxDbHost := idx; CfgPage.Values[idx] := ParamOr('DBHOST', '127.0.0.1');
  idx := CfgPage.Add('Admin username', False);            IdxAdminUser := idx; CfgPage.Values[idx] := ParamOr('ADMINUSER', 'admin');
  idx := CfgPage.Add('Admin initial password', True);     IdxAdminPass := idx; CfgPage.Values[idx] := ParamOr('ADMINPASS', '');
  GPagesReady := True;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if GIsUpgrade and ((PageID = DbModePage.ID) or (PageID = CfgPage.ID)) then
    Result := True;
end;



function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if (not GIsUpgrade) and (CurPageID = CfgPage.ID) then
  begin
    if UseInternalDb() and (GetCfgDbPass('') = '') then
    begin
      MsgBox('A database password is required when using the bundled database.', mbError, MB_OK);
      Result := False; Exit;
    end;
    if GetCfgAdminPass() = '' then
    begin
      MsgBox('Please set an initial admin password.', mbError, MB_OK);
      Result := False; Exit;
    end;
  end;
end;

procedure WriteEnvFile();
var
  f, dbUrl, cors: String;
begin
  f := ExpandConstant('{app}\config\.env');
  if FileExists(f) then
  begin
    Log('Existing .env kept (upgrade): ' + f);
    Exit;
  end;

  if UseInternalDb() then
    dbUrl := 'jdbc:postgresql://127.0.0.1:' + GetCfgDbPort('') + '/' + GetCfgDbName('')
  else
    dbUrl := 'jdbc:postgresql://' + GetDbHost() + ':' + GetCfgDbPort('') + '/' + GetCfgDbName('');
  cors := 'http://localhost:' + GetCfgServerPort() + ',http://127.0.0.1:' + GetCfgServerPort();

  SaveStringToFile(f,
    '# Generated by Hospital CRM installer ' + GetDateTimeString('yyyy-mm-dd hh:nn:ss', ':', '-') + #13#10 +
    'CRM_SERVER_PORT=' + GetCfgServerPort() + #13#10 +
    'CRM_INTERNAL_DB=' + InternalDbFlagText() + #13#10 +
    'CRM_DB_NAME=' + GetCfgDbName('') + #13#10 +
    'CRM_DB_USERNAME=' + GetCfgDbUser('') + #13#10 +
    'CRM_DB_PASSWORD=' + GetCfgDbPass('') + #13#10 +
    'CRM_DB_PORT=' + GetCfgDbPort('') + #13#10 +
    'CRM_DB_URL=' + dbUrl + #13#10 +
    'CRM_JWT_SIGNING_KEY=' + GJwtKey + #13#10 +
    'CRM_DATA_ENCRYPTION_KEY=' + GAesKey + #13#10 +
    'CRM_BOOTSTRAP_ADMIN_ENABLED=true' + #13#10 +
    'CRM_BOOTSTRAP_ADMIN_USERNAME=' + GetCfgAdminUser() + #13#10 +
    'CRM_BOOTSTRAP_ADMIN_PASSWORD=' + GetCfgAdminPass() + #13#10 +
    'CRM_CORS_ALLOWED_ORIGINS=' + cors + #13#10 +
    'CRM_OPS_METRICS_PUBLIC=true' + #13#10 +
    'CRM_HIS_ENABLED=false' + #13#10 +
    'CRM_LOG_PATH=' + #13#10, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
    GIsUpgrade := FileExists(ExpandConstant('{app}\config\.env'));
  if CurStep = ssPostInstall then
    WriteEnvFile();
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  keep: Boolean;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if IsSilent() then
      keep := (Uppercase(ParamOr('KEEPDATA', 'true')) <> 'FALSE')
    else
      keep := MsgBox('Keep database data and configuration (config\.env, pgdata, backups, logs)?' + #13#10 +
        'Choose Yes to keep them, No to delete everything.', mbConfirmation, MB_YESNO) = IDYES;
    if not keep then
    begin
      DelTree(ExpandConstant('{app}\config'), True, True, True);
      DelTree(ExpandConstant('{app}\pgdata'), True, True, True);
      DelTree(ExpandConstant('{app}\backups'), True, True, True);
      DelTree(ExpandConstant('{app}\logs'), True, True, True);
      DelTree(ExpandConstant('{app}\run'), True, True, True);
      // Inno removes {app} before usPostUninstall runs, so retry the now-empty dirs here.
      RemoveDir(ExpandConstant('{app}\config'));
      RemoveDir(ExpandConstant('{app}'));
    end;
  end;
end;

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\bin"
Type: filesandordirs; Name: "{app}\jre"
Type: filesandordirs; Name: "{app}\run"
Type: files; Name: "{app}\version.json.prev"
Type: files; Name: "{app}\upgrade-*.log"
