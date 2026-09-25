#include <windows.h>

// Loaded before chekator_occt.dll so Windows can resolve TKernel/tbb/jemalloc from this folder.
BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason != DLL_PROCESS_ATTACH) {
        return TRUE;
    }
    wchar_t path[MAX_PATH];
    const DWORD length = GetModuleFileNameW(module, path, MAX_PATH);
    if (length == 0 || length >= MAX_PATH) {
        return TRUE;
    }
    for (DWORD index = length; index > 0; --index) {
        if (path[index] == L'\\' || path[index] == L'/') {
            path[index + 1] = L'\0';
            SetDllDirectoryW(path);
            break;
        }
    }
    return TRUE;
}
