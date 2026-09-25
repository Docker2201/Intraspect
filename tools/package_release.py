"""Package an existing Windows app without user data. Python 3, standard library only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import zipfile


def pe_imports(path):
    """Read regular and delay-load DLL imports; no dumpbin or third-party module needed."""
    data = path.read_bytes()
    u16 = lambda p: struct.unpack_from('<H', data, p)[0]
    u32 = lambda p: struct.unpack_from('<I', data, p)[0]
    pe = u32(0x3c)
    if data[:2] != b'MZ' or data[pe:pe + 4] != b'PE\0\0':
        raise ValueError(f'Not a PE binary: {path}')
    opt = pe + 24
    is64 = u16(opt) == 0x20b
    directories = opt + (112 if is64 else 96)
    section_start = opt + u16(pe + 20)
    sections = []
    for i in range(u16(pe + 6)):
        s = section_start + 40 * i
        sections.append((u32(s + 12), max(u32(s + 8), u32(s + 16)), u32(s + 20)))

    def offset(rva):
        for start, size, raw in sections:
            if start <= rva < start + size:
                return raw + rva - start
        raise ValueError(f'Unmapped RVA in {path.name}: {rva:x}')

    names = set()
    for index, stride, name_at in ((1, 20, 12), (13, 32, 4)):
        rva, size = struct.unpack_from('<II', data, directories + index * 8)
        if not rva:
            continue
        start = offset(rva)
        for p in range(start, start + size, stride):
            if not any(data[p:p + stride]):
                break
            name_rva = u32(p + name_at)
            if index == 13 and not (u32(p) & 1):
                image_base = struct.unpack_from('<Q' if is64 else '<I', data, opt + (24 if is64 else 28))[0]
                name_rva -= image_base
            n = offset(name_rva)
            names.add(data[n:data.index(b'\0', n)].decode('ascii').lower())
    return names


def native_files(app):
    source = app / 'app/occt/win64'
    available = {p.name.lower(): p for p in source.glob('*.dll')}
    if 'chekator_occt.dll' not in available:
        raise ValueError('Build the OCCT bridge before packaging the complete public release.')
    bundled = {p.name.lower() for p in (app / 'runtime').rglob('*.dll')}
    system = Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32'
    pending = ['chekator_occt.dll', 'chekator_occt_winpath.dll']
    selected = {}
    while pending:
        name = pending.pop()
        if name in selected:
            continue
        if name not in available:
            raise ValueError(f'Missing native library: {name}')
        dll = available[name]
        selected[name] = dll
        for dependency in pe_imports(dll):
            if dependency in available:
                pending.append(dependency)
            elif dependency in bundled or dependency.startswith(('api-ms-', 'ext-ms-')) or (system / dependency).is_file():
                continue
            else:
                raise ValueError(f'{dll.name} needs an unavailable DLL: {dependency}')
    return list(selected.values())


def package(app, output, root):
    app, output, root = app.resolve(), output.resolve(), root.resolve()
    files, extras = {}, {}

    def add(path, name):
        if not path.is_file():
            raise FileNotFoundError(path)
        files[name] = path

    for name in ('Intraspect.exe', 'icon.ico', 'app/CNC_Modeling.jar', 'app/Intraspect.cfg'):
        add(app / name, name)
    if not (app / 'runtime/bin/server/jvm.dll').is_file():
        raise ValueError('The bundled Java runtime is incomplete.')
    for directory in ('runtime', 'app/lib'):
        for path in (app / directory).rglob('*'):
            if path.is_file():
                add(path, path.relative_to(app).as_posix())
    native = native_files(app)
    for path in native:
        add(path, path.relative_to(app).as_posix())
    for directory in ('docs', 'samples', 'licenses'):
        for path in (root / directory).rglob('*'):
            if path.is_file():
                add(path, path.relative_to(root).as_posix())
    for name in ('README.md', 'README.en.md', 'README.ru.md', 'LICENSE'):
        add(root / name, name)

    occt = root / 'launch/cache/occt/opencascade-8.0.0-vc14-64'
    for name in ('LICENSE_LGPL_21.txt', 'OCCT_LGPL_EXCEPTION.txt'):
        add(occt / name, 'licenses/occt/' + name)
    third_party = root / 'launch/cache/occt/3rdparty-vc14-64'
    # Only ship notices for third-party native components actually selected above.
    if any(p.name.lower().startswith('tbb') for p in native):
        licenses = list(third_party.glob('tbb*/LICENSE*'))
        if not licenses:
            raise ValueError('oneTBB license was not found.')
        for path in licenses:
            add(path, 'licenses/onetbb/' + path.name)
    if any(p.name.lower().startswith('jemalloc') for p in native):
        add(third_party / 'jemalloc-vc14-64/share/jemalloc/copyright', 'licenses/jemalloc/COPYRIGHT')
    for jar in (app / 'app/lib').glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if not name.endswith('/') and any(word in Path(name).name.lower() for word in ('license', 'notice', 'copying')):
                    extras['licenses/' + jar.stem + '/' + Path(name).name] = z.read(name)

    extras['THIRD-PARTY-NOTICES.md'] = b'''# Bundled components

Intraspect and its native bridge: MIT (LICENSE).
Java / JavaFX: license and notices are included in runtime/legal.
RichTextFX, Flowless, ReactFX, UndoFX, WellBehavedFX: their licenses are in licenses/.
Open CASCADE 8.0.0: LGPL 2.1 with exception, see licenses/occt/.
Sources: https://github.com/Open-Cascade-SAS/OCCT/tree/V8_0_0
oneTBB, when required by the native bridge: Apache 2.0, see licenses/onetbb/.
Sources: https://github.com/uxlfoundation/oneTBB
jemalloc, when required by OCCT: BSD license, see licenses/jemalloc/.

Native libraries are dynamically linked in app/occt/win64 and can be replaced
with compatible builds. Intraspect bridge sources are in native/occt-bridge
in the corresponding release source code.
'''
    extras['Run_with_log.cmd'] = b'@echo off\r\ncd /d "%~dp0"\r\nset "PATH=%~dp0app\\occt\\win64;%PATH%"\r\n"%~dp0Intraspect.exe"\r\necho Log: %~dp0intraspect-start.log\r\npause\r\n'
    extras['START-HERE.txt'] = ('Intraspect\n\n1. Extract the entire ZIP. / Распакуйте ZIP целиком.\n'
        '2. Open Intraspect.exe. / Откройте Intraspect.exe.\n'
        '3. File > Open > samples/first-part.nc. / Файл > Открыть > samples/first-part.nc.\n\n'
        'No separate Java or library installation. / Java и библиотеки уже внутри.\n'
        'Keep the whole folder together. / Переносите всю папку, не один EXE.\n'
        'Guide: https://github.com/Docker2201/Intraspect#readme\n'
        'MCP: https://github.com/Docker2201/Intraspect/blob/main/docs/mcp.md\n').encode('utf-8-sig')
    # Deliberately no data/, logs, editor state or client configuration.
    forbidden = [n for n in files if n.startswith('data/') or n.endswith(('.log', '.bak', '.pdb'))]
    if forbidden:
        raise ValueError(f'Unexpected private/debug files: {forbidden}')
    output.mkdir(parents=True, exist_ok=True)
    archive = output / 'Intraspect-Windows-x64.zip'
    manifest = {n: hashlib.sha256(p.read_bytes()).hexdigest() for n, p in files.items()}
    extras['release-files.json'] = json.dumps(manifest, indent=2, ensure_ascii=False).encode('utf-8')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, path in sorted(files.items()):
            z.write(path, 'Intraspect/' + name)
        for name, content in sorted(extras.items()):
            z.writestr('Intraspect/' + name, content)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / 'SHA256SUMS.txt').write_text(f'{digest}  {archive.name}\n', encoding='ascii')
    print(f'Native DLLs: {len(native)} (from {len(list((app / "app/occt/win64").glob("*.dll")))})')
    print(f'{archive}: {archive.stat().st_size / 1024**2:.1f} MiB; {len(files)} files + notices')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=Path('out/release'))
    args = parser.parse_args()
    package(args.app, args.out, Path(__file__).resolve().parents[1])
