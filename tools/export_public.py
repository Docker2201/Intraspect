"""Copy a curated source tree into the separate Intraspect checkout (never its Git history)."""
import argparse
from pathlib import Path
import shutil
import subprocess

ROOT_FILES = {'.gitattributes', '.gitignore', 'README.md', 'README.en.md', 'README.ru.md', 'LICENSE',
             'run.bat', 'build.bat', 'icon.ico', 'back_icon.png', 'delete_icon.png',
             'delete.ico', 'undo icon.ico'}
LAUNCH_FILES = {'build.ps1', 'build-occt-bridge.ps1', 'test-geometry.ps1'}
ASSET_FILES = {'export_tripo_tool_assets.py', 'generate_realistic_tool_models.py',
               'generate_tool_position_icons.ps1', 'build_wheel_holders.py',
               'calibrate_button_tool.py', 'wheel_tool_library.blend'}
TOOL_FILES = {'package_release.py', 'export_public.py', 'DocumentationScreenshots.java'}


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args]).decode('utf-8')


def allowed(name):
    p = Path(name)
    if name in ROOT_FILES:
        return True
    if name.startswith(('src/main/', 'src/test/', 'native/occt-bridge/', 'docs/', 'licenses/')):
        return p.suffix not in ('.class', '.log', '.pdf', '.pdb')
    if name.startswith('samples/'):
        return p.name in ('sample-shaft.nc', 'first-part.nc')
    return ((p.parent.as_posix() == 'launch' and p.name in LAUNCH_FILES)
            or (p.parent.as_posix() == 'assets/blender' and p.name in ASSET_FILES)
            or (p.parent.as_posix() == 'tools' and p.name in TOOL_FILES))


def export(source, destination):
    source, destination = source.resolve(), destination.resolve()
    if source == destination or not (destination / '.git').exists():
        raise ValueError('Destination must be a separate, existing Git checkout.')
    remote = git(destination, 'remote', 'get-url', 'origin').strip().lower().removesuffix('.git')
    if remote not in ('https://github.com/docker2201/intraspect', 'git@github.com:docker2201/intraspect'):
        raise ValueError('Destination is not the public Intraspect checkout.')
    names = {n for n in git(source, 'ls-files', '-z').split('\0') if n and allowed(n)}
    old = {n for n in git(destination, 'ls-files', '-z').split('\0') if n}
    for name in sorted(old - names):
        target = (destination / name).resolve()
        if not target.is_relative_to(destination) or target.is_relative_to(destination / '.git'):
            raise ValueError(f'Invalid obsolete path: {name}')
        if target.is_file():
            target.unlink()
    for name in sorted(names):
        target = (destination / name).resolve()
        if not target.is_relative_to(destination) or (source / name).is_symlink():
            raise ValueError(f'Invalid source path: {name}')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, target)
    ignore = destination / '.gitignore'
    with ignore.open('a', encoding='utf-8') as f:
        f.write('\n# Public repository: source only; applications are Release assets.\n'
                'launch/CNC_Modeling/\nDocumentation/\nMD/\nPhoto of instruments/\n'
                '.claude/\n.hermes/\n.agents/\nexample/\nexample.txt\n*.pdf\n')
    print(f'Exported {len(names)} source files; removed {len(old - names)} obsolete tracked files.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    export(Path(__file__).resolve().parents[1], args.destination)
