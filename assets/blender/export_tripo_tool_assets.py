import json
import math
import shutil
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_DIR = ROOT / "src" / "main" / "resources" / "tool-models"
TEXTURE_DIR = RESOURCE_DIR / "textures"
ICON_DIR = ROOT / "src" / "main" / "resources" / "tool-icons"
LIVE_DIR = ROOT / "launch" / "CNC_Modeling" / "app" / "classes" / "tool-models"
OUT_DIR = ROOT / "out" / "production" / "CNC_Modeling_2.0" / "tool-models"
LIVE_ICON_DIR = ROOT / "launch" / "CNC_Modeling" / "app" / "classes" / "tool-icons"
OUT_ICON_DIR = ROOT / "out" / "production" / "CNC_Modeling_2.0" / "tool-icons"
REPORT_PATH = ROOT / "assets" / "blender" / "tripo_tool_export_report.json"
BLEND_PATH = ROOT / "assets" / "blender" / "tripo_normalized_tool_models.blend"


TOOL_CODES = [
    100, 110, 111, 120, 121, 130, 131, 140, 145, 150, 151, 155, 156, 157, 160,
    200, 205, 210, 220, 230, 231, 240, 241, 242, 250,
    500, 510, 520, 530, 540, 550, 560, 580, 585,
    700, 710, 711, 712, 713, 714, 725, 730, 731, 732, 900,
]


# Tripo/DSS imports keep anonymous object names, but texture names preserve the prompt.
# The same high-quality mesh is intentionally reused for visually duplicate tool types.
SOURCE_BY_CODE = {
    100: "tripo_node_a54dce32",
    110: "tripo_node_8852bc62",
    111: "tripo_node_afc9d59f",
    120: "tripo_node_79de3059",
    121: "tripo_node_08d57271",
    130: "tripo_node_afc9d59f",
    131: "tripo_node_afc9d59f",
    140: "tripo_node_64ff4977",
    145: "tripo_node_d3467eeb",
    150: "tripo_node_64ff4977",
    151: "tripo_node_64ff4977",
    155: "tripo_node_afc9d59f",
    156: "tripo_node_afc9d59f",
    157: "tripo_node_d3467eeb",
    160: "tripo_node_b3751a20",
    200: "tripo_node_493afa3f",
    205: "tripo_node_493afa3f",
    210: "tripo_node_9096a4ba",
    220: "tripo_node_b3751a20",
    230: "tripo_node_afc9d59f",
    231: "tripo_node_afc9d59f",
    240: "tripo_node_f51609ae",
    241: "tripo_node_f51609ae",
    242: "tripo_node_f51609ae",
    250: "tripo_node_d3467eeb",
    500: "tripo_node_9e9b0964",
    510: "tripo_node_4077c518",
    520: "tripo_node_8caeb1a0",
    530: "tripo_node_8caeb1a0",
    540: "tripo_node_4077c518",
    550: "tripo_node_aada2d4f",
    560: "tripo_node_a468722b",
    580: "tripo_node_96144f84",
    585: "tripo_node_f3bc6727",
    700: "tripo_node_64ff4977",
    710: "tripo_node_96144f84",
    711: "tripo_node_f3bc6727",
    712: "tripo_node_96144f84",
    713: "tripo_node_96144f84",
    714: "tripo_node_96144f84",
    725: "tripo_node_f3bc6727",
    730: "tripo_node_aada2d4f",
    731: "tripo_node_46714c32",
    732: "tripo_node_ebf055f7",
    900: "tripo_node_aada2d4f",
}


# Axis map means: new X uses old axis AXIS_MAP[0], new Y uses old axis AXIS_MAP[1], etc.
AXIS_LINEAR = (0, 1, 2)
AXIS_SAW = (2, 0, 1)
AXIS_STEADY_REST = (1, 0, 2)
AXIS_TAP_WRENCH = (0, 1, 2)


AXIS_BY_CODE = {
    140: AXIS_SAW,
    150: AXIS_SAW,
    151: AXIS_SAW,
    700: AXIS_SAW,
    732: AXIS_STEADY_REST,
    240: AXIS_TAP_WRENCH,
    241: AXIS_TAP_WRENCH,
    242: AXIS_TAP_WRENCH,
}


# True when the active end is currently at the mapped X max side.
TIP_AT_MAX_BY_SOURCE = {
    "tripo_node_f3bc6727": True,
    "tripo_node_46714c32": True,
    "tripo_node_a468722b": False,
    "tripo_node_96144f84": True,
    "tripo_node_aada2d4f": False,
    "tripo_node_afc9d59f": False,
    "tripo_node_b3751a20": False,
    "tripo_node_d3467eeb": False,
    "tripo_node_493afa3f": False,
}


# Disc cutters do not have the old procedural shank; keep realistic blade thickness.
SPAN_OVERRIDES = {
    140: (8.0, None, None),
    150: (5.0, None, None),
    151: (3.0, None, None),
    700: (3.0, None, None),
    732: (68.5, 33.0, 46.0),
}


FACE_LIMIT_BY_CODE = {
    151: 16000,
    700: 16000,
    732: 22000,
    560: 16000,
    580: 14000,
    710: 14000,
    712: 14000,
    713: 14000,
    714: 14000,
}
DEFAULT_FACE_LIMIT = 9000
TEXTURE_SIZE = 1024
ICON_SIZE = 256


def parse_existing_spans():
    spans = {}
    for code in TOOL_CODES:
        path = RESOURCE_DIR / f"tool_{code:03d}.obj"
        if not path.exists():
            continue
        xs, ys, zs = [], [], []
        for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if not line.startswith("v "):
                continue
            parts = line.split()
            if len(parts) < 4:
                continue
            xs.append(float(parts[1]))
            ys.append(float(parts[2]))
            zs.append(float(parts[3]))
        if xs:
            spans[code] = (
                max(xs) - min(xs),
                max(ys) - min(ys),
                max(zs) - min(zs),
            )
    return spans


def source_hint(obj):
    for slot in obj.material_slots:
        mat = slot.material
        if not mat or not mat.use_nodes:
            continue
        for node in mat.node_tree.nodes:
            if node.bl_idname == "ShaderNodeTexImage" and node.image:
                name = node.image.name
                for suffix in ("_basecolor", "_normal", "_roughness", "_metallic"):
                    index = name.find(suffix)
                    if index >= 0:
                        return name[:index]
                return name
    return obj.name


def basecolor_image(obj):
    fallback = None
    for slot in obj.material_slots:
        mat = slot.material
        if not mat or not mat.use_nodes:
            continue
        for node in mat.node_tree.nodes:
            if node.bl_idname != "ShaderNodeTexImage" or not node.image:
                continue
            name = node.image.name.lower()
            if fallback is None:
                fallback = node.image
            if "basecolor" in name or "base_color" in name or "diffuse" in name:
                return node.image
    return fallback


def save_basecolor_texture(obj, code):
    image = basecolor_image(obj)
    if image is None:
        return None
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    texture_path = TEXTURE_DIR / f"tool_{code:03d}_basecolor.jpg"
    copy = image.copy()
    try:
        if copy.size[0] != TEXTURE_SIZE or copy.size[1] != TEXTURE_SIZE:
            copy.scale(TEXTURE_SIZE, TEXTURE_SIZE)
        copy.file_format = "JPEG"
        copy.filepath_raw = str(texture_path)
        copy.save()
    finally:
        bpy.data.images.remove(copy)
    return texture_path


def mapped_coords_for_object(source, axis_map):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    evaluated = source.evaluated_get(depsgraph)
    mesh = bpy.data.meshes.new_from_object(evaluated, depsgraph=depsgraph)
    matrix = evaluated.matrix_world.copy()
    coords = []
    for vertex in mesh.vertices:
        world = matrix @ vertex.co
        raw = (world.x, world.y, world.z)
        coords.append(Vector((raw[axis_map[0]], raw[axis_map[1]], raw[axis_map[2]])))
    return mesh, coords


def normalize_mesh(code, source, spans):
    axis_map = AXIS_BY_CODE.get(code)
    if axis_map is None:
        # Most imported tools are long along their largest bounding-box axis.
        dims = source.dimensions
        primary = max(range(3), key=lambda i: dims[i])
        remaining = [i for i in range(3) if i != primary]
        axis_map = (primary, remaining[0], remaining[1])

    mesh, coords = mapped_coords_for_object(source, axis_map)
    flip_x = TIP_AT_MAX_BY_SOURCE.get(source.name, False)
    if flip_x:
        coords = [Vector((-co.x, co.y, co.z)) for co in coords]

    mn = Vector((min(co.x for co in coords), min(co.y for co in coords), min(co.z for co in coords)))
    mx = Vector((max(co.x for co in coords), max(co.y for co in coords), max(co.z for co in coords)))
    source_span = mx - mn
    base_span = list(spans.get(code, (90.0, 14.0, 14.0)))
    override = SPAN_OVERRIDES.get(code)
    if override:
        for index, value in enumerate(override):
            if value is not None:
                base_span[index] = value

    safe_span = [max(source_span[i], 1.0e-6) for i in range(3)]

    # Keep the original Tripo proportions. The earlier export used the old
    # placeholder mesh length for X and a separate cross-section scale for Y/Z;
    # that made holders and round tools look squeezed in Chekator. A single
    # scale based on the largest target/source span preserves the imported shape
    # while still fitting the existing tool envelope.
    target_span = max(base_span)
    source_span_max = max(safe_span)
    uniform_scale = target_span / source_span_max
    scale = [uniform_scale, uniform_scale, uniform_scale]

    for vertex, co in zip(mesh.vertices, coords):
        x = (co.x - mn.x) * scale[0]
        y = (co.y - (mn.y + mx.y) * 0.5) * scale[1]
        z = (co.z - (mn.z + mx.z) * 0.5) * scale[2]
        vertex.co = (x, y, z)

    mesh.update()
    obj = bpy.data.objects.new(f"tool_{code:03d}_tripo_normalized", mesh)
    for mat in source.data.materials:
        if mat.name not in obj.data.materials:
            obj.data.materials.append(mat)
    obj["chekator_tool_code"] = code
    obj["chekator_source_object"] = source.name
    obj["chekator_source_hint"] = source_hint(source)
    obj["chekator_axis_map"] = json.dumps(axis_map)
    obj["chekator_tip_at_max"] = flip_x
    return obj


def decimate_for_runtime(obj, code):
    face_limit = FACE_LIMIT_BY_CODE.get(code, DEFAULT_FACE_LIMIT)
    face_count = len(obj.data.polygons)
    if face_count <= face_limit:
        return face_count, face_count
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    modifier = obj.modifiers.new("codex_runtime_decimate", "DECIMATE")
    modifier.ratio = max(0.015, min(1.0, face_limit / face_count))
    modifier.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    return face_count, len(obj.data.polygons)


def export_obj(obj, path):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    try:
        bpy.ops.wm.obj_export(
            filepath=str(path),
            export_selected_objects=True,
            export_materials=True,
            export_uv=True,
            export_normals=True,
            apply_modifiers=True,
        )
    except Exception:
        bpy.ops.export_scene.obj(
            filepath=str(path),
            use_selection=True,
            use_materials=True,
            use_uvs=True,
            use_normals=True,
        )


def copy_runtime_files(obj_path, texture_path):
    for directory in (LIVE_DIR, OUT_DIR):
        directory.mkdir(parents=True, exist_ok=True)
        shutil.copy2(obj_path, directory / obj_path.name)
        mtl_path = obj_path.with_suffix(".mtl")
        if mtl_path.exists():
            shutil.copy2(mtl_path, directory / mtl_path.name)
        if texture_path and texture_path.exists():
            target = directory / "textures" / texture_path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(texture_path, target)


def copy_runtime_icon(icon_path):
    for directory in (LIVE_ICON_DIR, OUT_ICON_DIR):
        directory.mkdir(parents=True, exist_ok=True)
        shutil.copy2(icon_path, directory / icon_path.name)


def inject_texture_map(mtl_path, texture_path):
    if not mtl_path.exists():
        return
    texture_rel = texture_path.relative_to(RESOURCE_DIR).as_posix() if texture_path else None
    lines = mtl_path.read_text(encoding="utf-8", errors="ignore").splitlines()
    filtered = []
    inserted = False
    for line in lines:
        stripped = line.strip()
        lower = stripped.lower()
        if lower.startswith(("map_", "bump", "disp", "decal", "refl")):
            continue
        filtered.append(line)
        if texture_rel and lower.startswith("newmtl "):
            filtered.append("Kd 1.000000 1.000000 1.000000")
            filtered.append(f"map_Kd {texture_rel}")
            inserted = True
    if texture_rel and not inserted:
        filtered.append(f"map_Kd {texture_rel}")
    mtl_path.write_text("\n".join(filtered).rstrip() + "\n", encoding="utf-8")


def collection_named(name):
    existing = bpy.data.collections.get(name)
    if existing:
        for obj in list(existing.objects):
            bpy.data.objects.remove(obj, do_unlink=True)
        return existing
    collection = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(collection)
    return collection


def add_label(collection, code, obj, offset):
    font = bpy.data.curves.new(f"label_tool_{code:03d}", "FONT")
    font.body = f"{code}"
    font.align_x = "CENTER"
    font.size = 5.0
    label = bpy.data.objects.new(f"label_tool_{code:03d}", font)
    label.location = (offset[0] + 45.0, offset[1] - 30.0, offset[2])
    collection.objects.link(label)
    return label


def arrange_for_review(collection, objects):
    for index, obj in enumerate(objects):
        column = index % 5
        row = index // 5
        offset = (column * 135.0, row * -92.0, 0.0)
        obj.location.x += offset[0]
        obj.location.y += offset[1]
        obj.location.z += offset[2]
        add_label(collection, int(obj["chekator_tool_code"]), obj, offset)


def object_world_bbox(obj):
    corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    mn = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    mx = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    return mn, mx


def look_at(camera, target):
    direction = target - camera.location
    camera.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def prepare_icon_scene():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    try:
        scene.render.engine = "BLENDER_EEVEE_NEXT"
    except Exception:
        pass
    scene.render.resolution_x = ICON_SIZE
    scene.render.resolution_y = ICON_SIZE
    scene.render.film_transparent = True
    try:
        scene.view_settings.view_transform = "Filmic"
        scene.view_settings.look = "Medium High Contrast"
    except Exception:
        pass
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1

    camera = bpy.data.objects.get("chekator_tool_icon_camera")
    if camera is None:
        camera_data = bpy.data.cameras.new("chekator_tool_icon_camera")
        camera = bpy.data.objects.new("chekator_tool_icon_camera", camera_data)
        scene.collection.objects.link(camera)
    camera.data.type = "ORTHO"
    scene.camera = camera

    light = bpy.data.objects.get("chekator_tool_icon_key_light")
    if light is None:
        light_data = bpy.data.lights.new("chekator_tool_icon_key_light", "AREA")
        light = bpy.data.objects.new("chekator_tool_icon_key_light", light_data)
        scene.collection.objects.link(light)
    light.data.energy = 650.0
    light.data.size = 5.0
    return camera, light


def render_tool_icon(obj, camera, light):
    hidden = []
    for candidate in bpy.context.scene.objects:
        hidden.append((candidate, candidate.hide_render, candidate.hide_viewport))
        if candidate.type not in {"CAMERA", "LIGHT"}:
            candidate.hide_render = candidate is not obj
            candidate.hide_viewport = candidate is not obj

    mn, mx = object_world_bbox(obj)
    center = (mn + mx) * 0.5
    span = mx - mn
    view_span = max(span.x, span.y, span.z, 1.0)
    camera.data.ortho_scale = view_span * 1.32
    direction = Vector((1.2, -1.7, 0.85)).normalized()
    camera.location = center + direction * view_span * 3.0
    look_at(camera, center)
    light.location = center + Vector((view_span * 1.5, -view_span * 2.0, view_span * 2.3))
    look_at(light, center)

    code = int(obj["chekator_tool_code"])
    icon_path = ICON_DIR / f"tool_{code:03d}.png"
    bpy.context.scene.render.filepath = str(icon_path)
    try:
        bpy.ops.render.render(write_still=True)
    finally:
        for candidate, hide_render, hide_viewport in hidden:
            candidate.hide_render = hide_render
            candidate.hide_viewport = hide_viewport
    copy_runtime_icon(icon_path)
    return icon_path


def main():
    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    LIVE_DIR.mkdir(parents=True, exist_ok=True)
    LIVE_ICON_DIR.mkdir(parents=True, exist_ok=True)
    OUT_ICON_DIR.mkdir(parents=True, exist_ok=True)
    spans = parse_existing_spans()
    collection = collection_named("Chekator_Tripo_Normalized")
    created = []
    report = []

    missing = [name for name in sorted(set(SOURCE_BY_CODE.values())) if bpy.data.objects.get(name) is None]
    if missing:
        raise RuntimeError("Missing Tripo source objects: " + ", ".join(missing))

    for code in TOOL_CODES:
        source_name = SOURCE_BY_CODE[code]
        source = bpy.data.objects[source_name]
        obj = normalize_mesh(code, source, spans)
        collection.objects.link(obj)
        original_faces, final_faces = decimate_for_runtime(obj, code)
        obj_path = RESOURCE_DIR / f"tool_{code:03d}.obj"
        texture_path = save_basecolor_texture(obj, code)
        export_obj(obj, obj_path)
        inject_texture_map(obj_path.with_suffix(".mtl"), texture_path)
        copy_runtime_files(obj_path, texture_path)
        created.append(obj)
        report.append({
            "code": code,
            "source": source_name,
            "hint": source_hint(source),
            "faces_before": original_faces,
            "faces_after": final_faces,
            "obj": str(obj_path),
            "texture": str(texture_path) if texture_path else None,
        })

    camera, light = prepare_icon_scene()
    icons_by_code = {}
    for obj in created:
        icon_path = render_tool_icon(obj, camera, light)
        icons_by_code[int(obj["chekator_tool_code"])] = str(icon_path)

    arrange_for_review(collection, created)
    for item in report:
        item["icon"] = icons_by_code.get(item["code"])
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))
    print(f"Exported {len(created)} normalized Tripo tool assets")
    print(f"Report: {REPORT_PATH}")


if __name__ == "__main__":
    main()
