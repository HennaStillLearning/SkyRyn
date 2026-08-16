' Запуск SkyRyn GUI Designer как отдельного окна-приложения (движок Edge, app mode).
' Двойной клик по этому файлу открывает дизайнер без вкладок и адресной строки.
' Работает офлайн, ничего ставить не нужно.

Set fso = CreateObject("Scripting.FileSystemObject")
Set sh  = CreateObject("WScript.Shell")

' Путь к designer.html рядом с этим .vbs
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
htmlPath  = fso.BuildPath(scriptDir, "designer.html")
url = "file:///" & Replace(htmlPath, "\", "/")

' Отдельный профиль — чтобы окно было изолированным приложением, а не вкладкой в общем Edge
udd = sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\SkyRynDesigner"

' Ищем msedge.exe в стандартных местах
edge = ""
cands = Array( _
  "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", _
  "C:\Program Files\Microsoft\Edge\Application\msedge.exe")
For Each c In cands
  If fso.FileExists(c) Then edge = c : Exit For
Next

If edge = "" Then
  MsgBox "Не нашёл msedge.exe. Открой designer.html вручную в браузере.", 48, "SkyRyn Designer"
  WScript.Quit
End If

cmd = """" & edge & """ --app=""" & url & """ --user-data-dir=""" & udd & """ --window-size=1440,900"
sh.Run cmd, 1, False
