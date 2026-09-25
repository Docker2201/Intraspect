# Bundled components

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
