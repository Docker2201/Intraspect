# Quick start

[← README](../README.md) · [Русский](quick-start.ru.md)

## Download and open

On Windows x64, download the [ready-to-run ZIP](https://github.com/Docker2201/Intraspect/releases/latest/download/Intraspect-Windows-x64.zip), choose **Extract all**, then open `Intraspect.exe`. Keep the EXE together with its `app` and `runtime` folders.

A writable folder or USB drive is enough. No Java, Python, Git or Visual Studio installation is needed to run the app.

The current v1.0 release is **unsigned**. SmartScreen may warn about an unknown publisher. If you trust the release downloaded from this repository, use **More info → Run anyway**. [Read about the warning](windows-signing.md).

## Your first part

1. Choose **File → Open** and select `samples/first-part.nc` from the extracted folder.
2. If diagnostics report missing **T1 D1**, apply the suggested tool fix or add it in **Settings → Tools**. For this sample, use a finishing tool with a 0.4 mm nose radius, position 3 and zero X/Z lengths.
3. Press **START** to draw the toolpath. With a two-channel program, choose which channels to display.
4. Open **3D simulation**. **3D** shows the result; **Cycle** shows tool movement and material removal. Use play, pause, step and speed controls to inspect it.

![Editor and sample toolpath](images/editor.png)

The sample uses Ø80 × 160 mm stock from `WORKPIECE`. X coordinates are diameters (`DIAMON`). It is a viewing example, not a production machine program.

![Sample part in 3D](images/simulation.png)

## Your own program

In **Settings**, select the machine: horizontal for shafts or vertical turning for wheels. Check stock dimensions, G54… offsets and the tool library for that machine mode. Tool **T**, edge **D**, radius, position and lengths must match your tooling.

For external variables, use **Parameters → Parameter program…**. For a complete set of parameters and two channels for each side, use **File → Open file set…**. Both channels machine a shared stock; two-sided cycle playback includes a workpiece flip.

Red diagnostics indicate errors. Yellow diagnostics indicate warnings or model assumptions. For example, accurate tool contact requires accurate tool geometry.

## Common controls

| Setting | Action |
|---|---|
| Dark theme | **Theme → Dark theme** |
| Interface language | **Language**, then restart |
| Interface size | **+ / −** in the menu bar |
| Stock geometry and tools | **Settings** |
| Toolpaths in 3D | **Toolpath** in the 3D window |
| AI connection | [MCP guide](mcp.md) |

## If it does not start

- Extract the full ZIP. `app` and `runtime` must be next to the EXE.
- Run `Run_with_log.cmd` in the release folder. Check `intraspect-start.log` beside the EXE.
- In a [bug report](https://github.com/Docker2201/Intraspect/issues), include the version, steps and log with personal information removed. Do not include your MCP key.
