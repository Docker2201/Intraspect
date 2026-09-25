# Build and customize Intraspect

[← README](../README.md) · [Русский](development.ru.md)

For normal use, download the [Windows release](https://github.com/Docker2201/Intraspect/releases/latest). To change the app yourself or with an AI coding assistant, use this repository's source code.

## Get the source

Download and extract the [source ZIP](https://github.com/Docker2201/Intraspect/archive/refs/heads/main.zip), or:

```sh
git clone https://github.com/Docker2201/Intraspect.git
cd Intraspect
```

The source ZIP works without Git. Open its extracted folder in your editor or AI coding assistant. Source for a particular released version is available from its tag on the [Releases page](https://github.com/Docker2201/Intraspect/releases).

## Build and run

On Windows, run **`build.bat`** from the repository root. It finds or downloads a JDK, JavaFX and the editor libraries. The first build needs internet access; downloads stay in `launch/cache` and are reused. There is no manual library installation step.

Run **`run.bat`** to open the result. A fresh checkout builds into `launch/Intraspect`. If `launch/Intraspect_Extended_3.96` already exists, the build updates it in place and preserves `data`. Close the app before rebuilding. To select the output folder explicitly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File launch/build.ps1 -OutputDirectory launch/Intraspect
```

## Find the code

| Folder | Contents |
|---|---|
| `src/main/java` | NC parser, geometry, simulation, editor and MCP server |
| `src/main/resources` | JavaFX layouts, styles, translations and tool models |
| `src/test` | Regression checks |
| `assets/blender` | Tool model generators and Blender library |
| `native/occt-bridge` | Open CASCADE native geometry bridge |
| `launch` | Build and test scripts |
| `samples` | Synthetic NC examples |

## Test changes

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File launch/test-geometry.ps1
```

Add `-WithOcct` when the native bridge is installed. Checks cover NC parsing, compensation, portable storage, both setups, tool geometry and MCP.

The ready-to-run ZIP includes the native 3D module. Building or changing that module needs Visual Studio C++, CMake and OCCT; use `launch/build-occt-bridge.ps1`. Java changes can be built with the normal script; the built-in surface generator is available without the native module.

## Contribute

Fork the repository, make your change and open a pull request. Describe the resulting behavior and how you checked it. Keep `data`, credentials, build output, production NC and customer drawings out of Git; use small synthetic examples for tests.

The [MIT license](../LICENSE) allows modification and redistribution, including private customized builds. Retain the copyright and license notice. Third-party libraries retain their own licenses.

## Package a Windows release

Build the app, then run the packager with Python 3 (standard library only):

```powershell
python tools/package_release.py --app launch/Intraspect --out out/release
```

For an existing installation, pass its folder in `--app`. The packager includes the program, Java runtime, required native DLLs, examples and documentation. Personal `data`, logs, history and MCP keys are excluded. A complete public package requires the OCCT bridge.

It produces `Intraspect-Windows-x64.zip` and `SHA256SUMS.txt`. Test the extracted ZIP in a fresh folder before uploading both files to a versioned GitHub Release. Keep that ZIP filename: the README download button uses it.

For a signed release, sign the application binaries **before** packaging and computing hashes. See [Windows signing](windows-signing.md). Do not describe an unsigned package as signed or silently replace it after publishing its checksum.
