; Inno Setup Script for AANO Notes Windows Desktop
#define MyAppName "AANO Notes"
#define MyAppVersion "1.2.0-beta.1"
#define MyAppPublisher "Jam Amjad Rasheed"
#define MyAppURL "https://github.com/jamamjadalone"
#define MyAppExeName "AANO.exe"

[Setup]
AppId={{5C17DA10-6395-46B9-B3F2-817B92A7210E}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\AANO Notes
DisableProgramGroupPage=yes
OutputDir=..\dist\Installer
OutputBaseFilename=AANO-Setup-v1.2.0-beta.1
SetupIconFile=..\assets\aano.ico
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\dist\AANO\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\aano.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\aano.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
