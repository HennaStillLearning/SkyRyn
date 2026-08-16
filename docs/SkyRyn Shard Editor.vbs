' Запуск SkyRyn Shard / Guide Editor как отдельного окна-приложения (движок Edge, app mode).
' Двойной клик открывает редактор шардов и локализации. Работает офлайн.

Set fso = CreateObject("Scripting.FileSystemObject")
Set sh  = CreateObject("WScript.Shell")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
htmlPath  = fso.BuildPath(scriptDir, "shard-editor.html")
url = "file:///" & Replace(htmlPath, "\", "/")
udd = sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\SkyRynShardEditor"

edge = ""
cands = Array( _
  "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", _
  "C:\Program Files\Microsoft\Edge\Application\msedge.exe")
For Each c In cands
  If fso.FileExists(c) Then edge = c : Exit For
Next

If edge = "" Then
  MsgBox "Не нашёл msedge.exe. Открой shard-editor.html вручную в браузере.", 48, "SkyRyn Shard Editor"
  WScript.Quit
End If

cmd = """" & edge & """ --app=""" & url & """ --user-data-dir=""" & udd & """ --window-size=1500,950"
sh.Run cmd, 1, False
