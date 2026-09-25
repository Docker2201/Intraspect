


<img width="960" height="648" alt="ezgif com-optimize" src="https://github.com/user-attachments/assets/717aa48c-86ef-463f-9768-6a42f482e514" />




# Intraspect

Check G-code, inspect toolpaths and preview **Siemens SINUMERIK turning programs** in 3D.
Portable Windows app. Works offline and from a USB drive. Open source under the **MIT license**.

**[Download Windows app](https://github.com/Docker2201/Intraspect/releases/latest/download/Intraspect-Windows-x64.zip)** · **[Download source code](https://github.com/Docker2201/Intraspect/archive/refs/heads/main.zip)** · [Quick start](docs/quick-start.md) · [Connect AI via MCP](docs/mcp.md) · [Русский](README.ru.md)

The ready-to-run app is also in this repository: [`launch/Intraspect-Windows-x64`](launch/Intraspect-Windows-x64) — run `Intraspect.exe` from that folder.

![G-code editor and toolpath](docs/images/editor.png)

## Start in a minute

1. Download the **Windows app** ZIP above.
2. Choose **Extract all**. A writable folder or USB drive will do.
3. Open **`Intraspect/Intraspect.exe`**.

Java, libraries and the native 3D runtime are included. No separate installs or recurring library downloads are needed.

**Windows warning:** the current v1.0 EXE is unsigned, so SmartScreen may show **Unknown publisher**. If you trust this release from this repository, choose **More info → Run anyway**. Do not disable Windows protection. [Details and signing status](docs/windows-signing.md).

## What you can do

| Task | Where |
|---|---|
| Open and edit G-code | **File → Open** |
| Check errors and draw the toolpath | **START**; select channels for a two-channel program |
| See the finished part or material removal | **3D simulation → 3D / Cycle** |
| Set the machine, stock, work offsets and tools | **Settings** |
| Load parameters, two channels and both sides | **File → Open file set…** |
| Connect an AI assistant | **MCP → Connection window → Connect AI model** |

Horizontal and vertical turning, two-sided machining with a workpiece flip, tool libraries, dark and light themes. Interface languages: English, Russian and Ukrainian; change **Language**, then restart.

![3D result of the sample turning program](docs/images/simulation.png)

Try **`samples/first-part.nc`** through **File → Open**. If T1 is missing, add it using the suggested fix or the tool library. [Follow the sample step by step →](docs/quick-start.md)

## Portable by design

Move the **whole `Intraspect` folder**, not just the EXE. Settings, tools and history stay in `data` beside the app. Old data in `C:\Chekator` is ignored.

To update, close the app, extract the new release and copy your existing **`data`** folder into it. Keep the old copy until you have checked the new one. Update one archive; there is no separate library update procedure.

MCP is optional. The simulator runs locally; your AI client may need internet access. AI access to editing is enabled separately. [MCP setup with a screenshot →](docs/mcp.md)

## Modify it yourself — or with AI

**This repository contains the source code, not just a launcher.** You can inspect, change, build and redistribute Intraspect under the [MIT license](LICENSE), retaining its copyright and license notice. Third-party components keep their own licenses.

- **Windows app ZIP:** the complete application with its runtime, ready to use.
- **Source code ZIP / Git clone:** Java code, native bridge, Blender tool generators and tests, ready to edit.

Download the source ZIP above and extract it, or clone the repository:

```sh
git clone https://github.com/Docker2201/Intraspect.git
cd Intraspect
```

On Windows, run **`build.bat`**, then **`run.bat`**. The first build downloads its dependencies; later builds reuse the cache. [Build instructions and source map →](docs/development.md)

For an AI coding assistant, open the extracted source folder and ask:

> Read README.md and docs/development.md. Implement [describe my change], keep the app portable and its data local, run the relevant tests, then build it with build.bat.

To contribute improvements, fork the repository and open a pull request. For a bug, [open an issue](https://github.com/Docker2201/Intraspect/issues) with the version, steps and a small reproducible example.

Geometry depends on the NC program, offsets, stock and tools. Intraspect does not fully emulate a machine's PLC, chuck or all machine movements.


### 💬 Need help?

Have a specific question or need help with Intraspect?  
Feel free to reach out:

📧 **intraspect.help@gmail.com**
