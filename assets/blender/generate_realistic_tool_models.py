import math
import os
import shutil
from pathlib import Path

import bpy


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_DIR = ROOT / "src" / "main" / "resources" / "tool-models"
LIVE_DIR = ROOT / "launch" / "CNC_Modeling" / "app" / "classes" / "tool-models"
BLEND_PATH = ROOT / "assets" / "blender" / "chekator_tool_models.blend"


TOOL_NAMES = {
    100: "Фреза",
    110: "Шаровая концевая фреза",
    111: "Коническая шаровая фреза",
    120: "Концевая фреза",
    121: "Концевая радиусная фреза",
    130: "Угловая фреза",
    131: "Угловая радиусная фреза",
    140: "Торцевая фреза",
    145: "Резьбофреза",
    150: "Боковая фреза",
    151: "Дисковая пила",
    155: "Фасочная фреза",
    156: "Фасочная радиусная фреза",
    157: "Гравировальная резьбовая фреза",
    160: "Сверло-резьбофреза",
    200: "Спиральное сверло",
    205: "Твердосплавное сверло",
    210: "Расточная оправка",
    220: "Центровочное сверло",
    230: "Зенковка",
    231: "Цековка",
    240: "Метчик",
    241: "Чистовой метчик",
    242: "Метчик Whitworth",
    250: "Развертка",
    500: "Черновой токарный резец",
    510: "Чистовой токарный резец",
    520: "Канавочный резец",
    530: "Отрезной резец",
    540: "Резьбовой резец",
    550: "Круглая пластина",
    560: "Сверло в патроне",
    580: "Токарный 3D-щуп",
    585: "Калибровочный инструмент",
    700: "Дисковая пазовая фреза",
    710: "3D-щуп",
    711: "Кромкоискатель",
    712: "Одинарный щуп",
    713: "Г-образный щуп",
    714: "Звездчатый щуп",
    725: "Калибровочный инструмент",
    730: "Упор",
    731: "Оправка",
    732: "Люнет",
    900: "Вспомогательный инструмент",
}


REFERENCE_MODEL_OVERRIDES = {
    130: ("countersink.png", "schematic reference; rebuilt as angled/chamfer cutter, not an angle head"),
    131: ("countersink.png", "schematic reference; rebuilt as rounded angle cutter"),
    145: ("tap.jpg", "tap photo is not a thread mill; rebuilt as thread mill"),
    155: ("countersink.png", "schematic reference; rebuilt as compact chamfer mill"),
    156: ("countersink.png", "schematic reference; rebuilt as compact radius chamfer mill"),
    157: ("tap.jpg", "tap photo is not an engraving thread mill; rebuilt as engraving thread mill"),
    160: ("tap.jpg", "tap photo is not a drill thread mill; rebuilt as drill/thread mill combo"),
    250: ("reamer.webp", "low quality photo; rebuilt from reamer geometry"),
    560: ("drill_chuck_holder.jpg", "rebuilt as drill mounted in chuck/holder"),
    585: ("edge_finder.webp", "tiny/low quality photo; rebuilt as calibration setter"),
    711: ("edge_finder.webp", "tiny/low quality photo; rebuilt as edge finder"),
    725: ("edge_finder.webp", "tiny/low quality photo; rebuilt as calibration setter"),
}


MATERIALS = {}
CURRENT = []


def material(name, color, metallic=0.0, roughness=0.35):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = color
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        try:
            bsdf.inputs["Base Color"].default_value = color
            bsdf.inputs["Metallic"].default_value = metallic
            bsdf.inputs["Roughness"].default_value = roughness
        except Exception:
            pass
    MATERIALS[name] = mat
    return mat


def setup_materials():
    MATERIALS.clear()
    material("holder_dark_steel", (0.12, 0.16, 0.18, 1), 0.65, 0.28)
    material("holder_blue_steel", (0.22, 0.36, 0.46, 1), 0.55, 0.25)
    material("brushed_steel", (0.58, 0.64, 0.66, 1), 0.85, 0.22)
    material("ground_steel", (0.43, 0.49, 0.52, 1), 0.8, 0.22)
    material("black_pocket", (0.015, 0.018, 0.022, 1), 0.3, 0.5)
    material("carbide_gold", (0.86, 0.62, 0.16, 1), 0.75, 0.2)
    material("carbide_dark", (0.28, 0.30, 0.24, 1), 0.65, 0.24)
    material("edge_hot", (1.0, 0.12, 0.05, 1), 0.25, 0.18)
    material("screw_steel", (0.82, 0.84, 0.82, 1), 0.9, 0.18)
    material("coolant_blue", (0.08, 0.45, 0.95, 1), 0.15, 0.22)
    material("ruby", (0.92, 0.02, 0.08, 1), 0.05, 0.08)
    material("ceramic_white", (0.86, 0.88, 0.84, 1), 0.15, 0.28)
    material("brass", (0.82, 0.57, 0.20, 1), 0.85, 0.2)
    material("rubber", (0.035, 0.035, 0.04, 1), 0.0, 0.68)
    material("warning_yellow", (1.0, 0.88, 0.04, 1), 0.25, 0.25)
    material("coated_black", (0.045, 0.046, 0.040, 1), 0.75, 0.18)
    material("oxide_blue", (0.06, 0.18, 0.30, 1), 0.7, 0.22)
    material("etched_mark", (0.04, 0.05, 0.055, 1), 0.25, 0.55)


def mat(name):
    return MATERIALS[name]


def add_obj(obj):
    CURRENT.append(obj)
    return obj


def active(obj):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj


def shade_and_bevel(obj, bevel=0.0, segments=2):
    active(obj)
    try:
        bpy.ops.object.shade_smooth()
    except Exception:
        pass
    if bevel > 0:
        mod = obj.modifiers.new("small_real_edge_radius", "BEVEL")
        mod.width = bevel
        mod.segments = segments
        mod.affect = "EDGES"
        mod.profile = 0.5
        obj.modifiers.new("weighted_normals", "WEIGHTED_NORMAL")
    return obj


def box(name, dims, loc, material_name, bevel=0.0, rot=(0.0, 0.0, 0.0)):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dims
    obj.data.materials.append(mat(material_name))
    active(obj)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return add_obj(shade_and_bevel(obj, bevel, 3))


def cylinder(name, axis, radius, length, loc, material_name, vertices=48, bevel=0.0):
    rot = (0.0, 0.0, 0.0)
    if axis == "X":
        rot = (0.0, math.radians(90.0), 0.0)
    elif axis == "Y":
        rot = (math.radians(90.0), 0.0, 0.0)
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=length, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(mat(material_name))
    return add_obj(shade_and_bevel(obj, bevel, 2))


def cone(name, axis, r1, r2, length, loc, material_name, vertices=64, bevel=0.0):
    rot = (0.0, 0.0, 0.0)
    if axis == "X":
        rot = (0.0, math.radians(90.0), 0.0)
    elif axis == "Y":
        rot = (math.radians(90.0), 0.0, 0.0)
    bpy.ops.mesh.primitive_cone_add(vertices=vertices, radius1=r1, radius2=r2, depth=length, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(mat(material_name))
    return add_obj(shade_and_bevel(obj, bevel, 2))


def sphere(name, radius, loc, material_name, scale=(1.0, 1.0, 1.0), segments=48):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=max(12, segments // 2), radius=radius, location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    obj.data.materials.append(mat(material_name))
    active(obj)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return add_obj(shade_and_bevel(obj, 0.0, 1))


def torus_x(name, major, minor, loc, material_name, seg=96, minseg=12):
    bpy.ops.mesh.primitive_torus_add(
        major_segments=seg,
        minor_segments=minseg,
        major_radius=major,
        minor_radius=minor,
        location=loc,
        rotation=(0.0, math.radians(90.0), 0.0),
    )
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(mat(material_name))
    return add_obj(shade_and_bevel(obj, 0.0, 1))


def prism(name, points, thickness, material_name, loc=(0.0, 0.0, 0.0), rot_z=0.0, bevel=0.0):
    verts = []
    for x, y in points:
        verts.append((x, y, -thickness / 2.0))
    for x, y in points:
        verts.append((x, y, thickness / 2.0))
    n = len(points)
    faces = [tuple(range(n - 1, -1, -1)), tuple(range(n, 2 * n))]
    for i in range(n):
        faces.append((i, (i + 1) % n, (i + 1) % n + n, i + n))
    mesh = bpy.data.meshes.new(name + "_mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_euler[2] = math.radians(rot_z)
    obj.data.materials.append(mat(material_name))
    return add_obj(shade_and_bevel(obj, bevel, 2))


def tube(name, pts, radius, material_name, bevel_resolution=4):
    curve = bpy.data.curves.new(name + "_curve", "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 2
    curve.bevel_depth = radius
    curve.bevel_resolution = bevel_resolution
    spl = curve.splines.new("POLY")
    spl.points.add(len(pts) - 1)
    for p, co in zip(spl.points, pts):
        p.co = (co[0], co[1], co[2], 1.0)
    obj = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat(material_name))
    active(obj)
    try:
        bpy.ops.object.convert(target="MESH")
        obj = bpy.context.object
    except Exception:
        pass
    return add_obj(shade_and_bevel(obj, 0.0, 1))


def helix(name, start_x, length, radius, turns, phase, material_name, tube_radius=0.18, points=90):
    pts = []
    for i in range(points):
        t = i / (points - 1)
        x = start_x + length * t
        a = phase + turns * math.tau * t
        pts.append((x, math.cos(a) * radius, math.sin(a) * radius))
    return tube(name, pts, tube_radius, material_name, 3)


def helical_strip(name, start_x, length, radius, turns, phase, angular_width, material_name, points=140):
    verts = []
    faces = []
    for i in range(points):
        t = i / (points - 1)
        x = start_x + length * t
        a = phase + turns * math.tau * t
        for side in (-1.0, 1.0):
            aa = a + side * angular_width * 0.5
            verts.append((x, math.cos(aa) * radius, math.sin(aa) * radius))
    for i in range(points - 1):
        faces.append((i * 2, i * 2 + 1, i * 2 + 3, i * 2 + 2))
    mesh = bpy.data.meshes.new(name + "_mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat(material_name))
    return add_obj(shade_and_bevel(obj, 0.0, 1))


def screw(name, x, y, z, radius=2.0, thickness=1.2):
    cylinder(name + "_head", "Z", radius, thickness, (x, y, z), "screw_steel", 42, 0.08)
    box(name + "_slot", (radius * 1.55, radius * 0.18, thickness * 1.08), (x, y, z + thickness * 0.03), "black_pocket", 0.02)


def hex_socket_screw(name, x, y, z, radius=2.0, thickness=1.1):
    cylinder(name + "_head", "Z", radius, thickness, (x, y, z), "screw_steel", 52, 0.08)
    cylinder(name + "_hex_socket", "Z", radius * 0.42, thickness * 1.08, (x, y, z + thickness * 0.05), "black_pocket", 6, 0.0)


def clamp_wedge(name, x, y, z, length=17.0, width=7.0, height=3.0, rot_z=-8.0):
    box(name + "_top_clamp", (length, width, height), (x, y, z), "coated_black", 0.22, rot=(0.0, 0.0, math.radians(rot_z)))
    hex_socket_screw(name + "_clamp_screw", x + length * 0.12, y, z + height * 0.62, min(width * 0.22, 1.9), 0.75)


def serration_lines(prefix, x0, y0, z0, count=5, spacing=1.4, angle=-22.0):
    for i in range(count):
        x = x0 + i * spacing
        tube(
            f"{prefix}_serration_{i}",
            [
                (x, y0 - 2.2, z0),
                (x + math.cos(math.radians(angle)) * 3.0, y0 + 2.2, z0),
            ],
            0.035,
            "etched_mark",
            1,
        )


def gauge_mark_band(prefix, axis, center_x, radius, width, count=10):
    torus_x(prefix + "_front_mark_band", radius, 0.10, (center_x - width / 2.0, 0, 0), "etched_mark", 80, 6)
    torus_x(prefix + "_rear_mark_band", radius, 0.10, (center_x + width / 2.0, 0, 0), "etched_mark", 80, 6)
    for i in range(count):
        a = i * math.tau / count
        tube(
            f"{prefix}_etched_grip_{i}",
            [
                (center_x - width / 2.0, math.cos(a) * radius, math.sin(a) * radius),
                (center_x + width / 2.0, math.cos(a + 0.35) * radius, math.sin(a + 0.35) * radius),
            ],
            0.035,
            "etched_mark",
            1,
        )


def diamond_insert(name, length, width, thickness, material_name="carbide_gold", dark_chipbreaker=True, z_offset=0.0):
    pts = [(0.0, 0.0), (length * 0.42, -width * 0.50), (length, 0.0), (length * 0.42, width * 0.50)]
    prism(name, pts, thickness, material_name, loc=(0.0, 0.0, z_offset), bevel=0.08)
    tube(name + "_red_working_edge", [(0, 0, thickness * 0.56 + z_offset), (length * 0.40, -width * 0.48, thickness * 0.56 + z_offset)], 0.16, "edge_hot", 2)
    if dark_chipbreaker:
        prism(
            name + "_chipbreaker",
            [(length * 0.15, -width * 0.08), (length * 0.48, -width * 0.31), (length * 0.64, -width * 0.17), (length * 0.30, width * 0.06)],
            thickness + 0.18,
            "black_pocket",
            loc=(0.0, 0.0, thickness * 0.08 + z_offset),
            bevel=0.04,
        )
    serration_lines(name, length * 0.20, -width * 0.04, thickness * 0.72 + z_offset, 5, max(0.9, length * 0.055), -30.0)
    hex_socket_screw(name + "_screw", length * 0.46, 0.0, thickness * 0.74 + z_offset, max(1.4, width * 0.11), 0.9)


def triangle_insert(name, length, width, thickness, material_name="carbide_gold"):
    pts = [(0.0, 0.0), (length * 0.86, -width * 0.47), (length * 0.64, width * 0.55)]
    prism(name, pts, thickness, material_name, bevel=0.08)
    tube(name + "_v_edge_a", [(0, 0, thickness * 0.55), (length * 0.60, -width * 0.33, thickness * 0.55)], 0.15, "edge_hot", 2)
    tube(name + "_v_edge_b", [(0, 0, thickness * 0.55), (length * 0.44, width * 0.40, thickness * 0.55)], 0.15, "edge_hot", 2)
    serration_lines(name, length * 0.25, -width * 0.03, thickness * 0.72, 4, 1.3, -28.0)
    hex_socket_screw(name + "_screw", length * 0.46, width * 0.02, thickness * 0.72, 1.45, 0.8)


def round_insert(name, radius, thickness):
    cylinder(name + "_button", "Z", radius, thickness, (radius, 0.0, 0.0), "carbide_gold", 80, 0.07)
    tube(name + "_edge", [(0.0, 0.0, thickness * 0.55), (radius * 0.25, -radius * 0.78, thickness * 0.55)], 0.16, "edge_hot", 2)
    torus_x(name + "_chipbreaker_ring", radius * 0.50, 0.08, (radius, 0.0, thickness * 0.70), "black_pocket", 80, 6)
    hex_socket_screw(name + "_screw", radius, 0.0, thickness * 0.66, radius * 0.28, 0.85)


def coolant_nozzles(base_x=26, base_y=9, count=2):
    for i in range(count):
        y = base_y + i * 3.0
        cylinder(f"coolant_nozzle_{i}", "X", 0.8, 9.0, (base_x, y, 3.8), "coolant_blue", 20, 0.05)
        cone(f"coolant_tip_{i}", "X", 1.1, 0.45, 3.0, (base_x - 5.8, y, 3.8), "coolant_blue", 20, 0.02)


def turning_base(kind):
    if kind == "rough":
        box("iso_square_shank_25mm", (96, 22, 13), (58, 12.0, -6.0), "holder_dark_steel", 1.15, rot=(0, 0, math.radians(-4)))
        box("roughing_top_flat_machined", (82, 12, 0.45), (62, 12.0, 0.8), "ground_steel", 0.04, rot=(0, 0, math.radians(-4)))
        box("angled_head_block", (34, 24, 10), (18, 1.0, -4.6), "holder_dark_steel", 0.75, rot=(0, 0, math.radians(-13)))
        box("cnmg_insert_pocket", (31, 21, 1.2), (12.5, -0.7, 0.85), "black_pocket", 0.28, rot=(0, 0, math.radians(-13)))
        prism("cnmg_shim_plate", [(0, 0), (10.6, -8.9), (23.8, -2.0), (10.8, 7.6)], 1.2, "ground_steel", loc=(1.0, -0.15, 1.2), rot_z=-3.0, bevel=0.05)
        diamond_insert("cnmg_roughing_insert_80deg", 25.5, 20.5, 4.4, "carbide_dark", z_offset=2.8)
        tube("cnmg_visible_roughing_nose", [(0.0, 0.0, 5.5), (6.8, -3.2, 5.5)], 0.24, "edge_hot", 2)
        clamp_wedge("roughing", 20.5, 2.2, 7.4, 16.5, 5.2, 2.0, -11.0)
        box("roughing_seat_anvil", (18, 4.2, 2.0), (7.2, -7.8, 0.3), "ground_steel", 0.18, rot=(0, 0, math.radians(-18)))
        coolant_nozzles(30, 14, 2)
    elif kind == "finish":
        box("slim_finish_shank_20mm", (86, 16, 10.5), (52, 8.2, -5.0), "holder_dark_steel", 0.9, rot=(0, 0, math.radians(-2)))
        box("finish_top_flat_machined", (66, 8.5, 0.38), (56, 8.2, 0.55), "ground_steel", 0.03, rot=(0, 0, math.radians(-2)))
        box("slender_finish_head", (27, 16, 8.5), (14.5, 0.4, -3.8), "holder_dark_steel", 0.55, rot=(0, 0, math.radians(-17)))
        box("vnmg_insert_pocket", (25, 12.5, 1.0), (10.8, -0.2, 0.75), "black_pocket", 0.24, rot=(0, 0, math.radians(-18)))
        prism("vnmg_finishing_insert_35deg", [(0.0, 0.0), (10.5, -4.7), (25.0, 0.0), (10.5, 4.7)], 3.4, "carbide_gold", loc=(0, 0, 2.45), bevel=0.07)
        tube("vnmg_polished_cutting_edge", [(0.0, 0.0, 4.55), (8.8, -3.9, 4.55)], 0.17, "edge_hot", 2)
        prism("vnmg_chipbreaker_groove", [(5.6, -0.25), (12.0, -2.4), (18.0, -0.8), (11.0, 1.2)], 3.62, "black_pocket", loc=(0, 0, 2.85), bevel=0.035)
        hex_socket_screw("vnmg_center_screw", 11.8, -0.05, 4.9, 1.45, 0.72)
        clamp_wedge("finishing", 18.2, 1.3, 6.2, 11.5, 4.1, 1.6, -15.0)
        box("polished_nose_support", (9.5, 2.3, 1.8), (4.7, -5.2, 0.25), "ground_steel", 0.13, rot=(0, 0, math.radians(-18)))
        coolant_nozzles(25.5, 11.5, 1)
    elif kind == "plunge":
        box("rigid_grooving_holder", (82, 16, 14), (47, 8.5, 0), "holder_dark_steel", 0.8)
        box("thin_blade", (45, 4.0, 12), (21, 1.4, 0), "ground_steel", 0.25)
        prism("double_end_grooving_insert", [(0, -1.8), (9.5, -1.8), (11.3, 0.0), (9.5, 1.8), (0, 1.8)], 4.5, "carbide_gold", bevel=0.06)
        tube("plunge_edge", [(0, -1.8, 2.5), (0, 1.8, 2.5)], 0.14, "edge_hot", 2)
        box("grooving_top_clamp", (23, 5.2, 3.0), (18, 2.8, 6.5), "coated_black", 0.18)
        hex_socket_screw("blade_clamp_screw", 20, 2.5, 8.0, 1.6, 0.8)
    elif kind == "cutoff":
        box("parting_holder", (92, 18, 15), (54, 8.5, 0), "holder_dark_steel", 0.8)
        box("parting_blade", (58, 2.7, 14), (28, 0.7, 0), "ground_steel", 0.16)
        prism("sharp_parting_insert", [(0, -1.4), (11.5, -1.4), (12.2, 0.0), (11.5, 1.4), (0, 1.4)], 5.0, "carbide_gold", bevel=0.05)
        tube("parting_front_edge", [(0, -1.25, 2.65), (0, 1.25, 2.65)], 0.13, "edge_hot", 2)
        box("parting_upper_clamp", (33, 4.8, 3.4), (24, 2.7, 7.5), "coated_black", 0.20)
        for x in (24, 37):
            hex_socket_screw("parting_clamp_" + str(x), x, 3.0, 9.1, 1.5, 0.7)
    elif kind == "thread":
        box("threading_holder", (82, 17, 14), (48, 8.7, 0), "holder_dark_steel", 0.85, rot=(0, 0, math.radians(-3)))
        box("threading_pocket", (25, 16, 3.0), (13, 2.0, -0.2), "black_pocket", 0.3, rot=(0, 0, math.radians(-8)))
        triangle_insert("laydown_thread_insert", 20, 17, 3.6)
        clamp_wedge("threading", 18.0, 1.1, 4.6, 16.0, 5.6, 2.1, -8.0)
        for i in range(4):
            tube(f"thread_profile_line_{i}", [(2.0 + i * 3.0, -0.2, 2.05), (4.0 + i * 3.0, 1.2, 2.05)], 0.055, "black_pocket", 1)
        coolant_nozzles(25, 11, 1)
    elif kind == "button":
        box("button_holder", (80, 18, 14), (46, 8.8, 0), "holder_dark_steel", 0.85)
        box("button_seat", (24, 18, 3.0), (13, 0.0, -0.35), "black_pocket", 0.3)
        round_insert("round_button_insert", 7.2, 3.8)
        clamp_wedge("button", 17.5, 0.0, 4.8, 14.0, 5.0, 2.2, 0.0)
        coolant_nozzles(24, 12, 1)


def boring_bar():
    cylinder("boring_bar_shank", "X", 6.0, 78, (43, 5.5, 0), "holder_dark_steel", 48, 0.2)
    box("boring_bar_flat", (42, 7, 7), (19, 1.6, 0), "ground_steel", 0.3)
    diamond_insert("small_boring_insert", 14, 10, 3.0, "carbide_gold")
    clamp_wedge("boring", 11.8, 0.0, 3.8, 11.0, 3.8, 1.8, -10.0)
    hex_socket_screw("boring_screw", 8.2, 0.0, 2.5, 1.2, 0.6)


def add_fluted_cylinder(prefix, start_x, flute_length, shank_length, radius, flutes, material_name="brushed_steel", helix_turns=1.2):
    cylinder(prefix + "_cutting_core", "X", radius, flute_length, (start_x + flute_length / 2, 0, 0), material_name, 80, 0.06)
    cylinder(prefix + "_shank", "X", radius * 0.92, shank_length, (start_x + flute_length + shank_length / 2, 0, 0), "ground_steel", 72, 0.1)
    cylinder(prefix + "_neck_relief", "X", radius * 0.78, 6.0, (start_x + flute_length + 3.0, 0, 0), "ground_steel", 72, 0.04)
    gauge_mark_band(prefix + "_shank", "X", start_x + flute_length + shank_length * 0.64, radius * 0.93, 8.0, 12)
    box(prefix + "_laser_flat", (16.0, 0.14, radius * 0.95), (start_x + flute_length + shank_length * 0.58, radius * 0.90, radius * 0.18), "etched_mark", 0.01)
    for i in range(flutes):
        ph = i * math.tau / flutes
        helix(prefix + f"_polished_flute_{i}", start_x + 1.0, flute_length - 2.0, radius * 1.035, helix_turns, ph, "black_pocket", 0.12, 100)
        helix(prefix + f"_cutting_land_{i}", start_x + 0.6, flute_length - 1.2, radius * 1.07, helix_turns, ph + 0.34, "edge_hot", 0.055, 80)
        helix(prefix + f"_relief_land_{i}", start_x + 2.5, flute_length - 4.0, radius * 0.88, helix_turns, ph + 0.72, "ground_steel", 0.045, 80)


def end_mill(code, flutes=4, ball=False, taper=False, corner=False, chamfer=False):
    r = 5.8 if code not in (150, 151, 700) else 8.0
    if taper:
        cone("tapered_core", "X", r * 0.55, r, 48, (24, 0, 0), "brushed_steel", 80, 0.05)
        cylinder("tapered_shank", "X", r * 0.86, 45, (70.5, 0, 0), "ground_steel", 72, 0.08)
        for i in range(flutes):
            helix(f"tapered_flute_{i}", 2.0, 44, r * 0.86, 1.4, i * math.tau / flutes, "black_pocket", 0.12, 100)
    else:
        add_fluted_cylinder("endmill", 0, 52, 42, r, flutes, "brushed_steel", 1.55)
    if ball:
        sphere("ball_nose_end", r, (0.0, 0.0, 0.0), "brushed_steel", (0.55, 1.0, 1.0), 64)
        for i in range(flutes):
            helix(f"ball_edge_{i}", 0.0, 20.0, r * 1.04, 0.55, i * math.tau / flutes, "edge_hot", 0.06, 44)
    elif chamfer:
        cone("chamfered_tip", "X", r, r * 0.70, 8, (4, 0, 0), "brushed_steel", 64, 0.04)
    else:
        cylinder("flat_cutting_face", "X", r * 0.98, 1.5, (0.7, 0, 0), "carbide_gold", 72, 0.04)
    if corner:
        torus_x("corner_radius_ring", r * 0.86, 0.55, (1.2, 0, 0), "carbide_gold", 80, 8)


def face_mill():
    cylinder("face_mill_body", "X", 18, 24, (19, 0, 0), "holder_blue_steel", 96, 0.4)
    torus_x("face_mill_front_chamfer", 17.8, 0.35, (6.8, 0, 0), "brushed_steel", 96, 8)
    torus_x("face_mill_back_chamfer", 17.8, 0.35, (31.2, 0, 0), "brushed_steel", 96, 8)
    cylinder("face_mill_arbor", "X", 8, 38, (50, 0, 0), "ground_steel", 64, 0.2)
    gauge_mark_band("face_mill_arbor", "X", 50, 8.1, 8.0, 14)
    for i in range(8):
        a = i * math.tau / 8
        y = math.cos(a) * 17.5
        z = math.sin(a) * 17.5
        pocket = prism(f"face_mill_pocket_{i}", [(0, -4.2), (9.5, -3.0), (9.5, 3.0), (0, 4.2)], 2.3, "black_pocket", loc=(2.2, y * 0.98, z * 0.98), bevel=0.04)
        pocket.rotation_euler[0] = a
        obj = prism(f"face_mill_insert_{i}", [(0, -3.0), (8.0, -2.0), (8.0, 2.0), (0, 3.0)], 2.0, "carbide_gold", loc=(3.0, y, z), rot_z=0, bevel=0.04)
        obj.rotation_euler[0] = a
        tube(f"face_mill_edge_{i}", [(0.5, y, z + 1.2), (6.0, y, z + 1.2)], 0.07, "edge_hot", 2)
        hex_socket_screw(f"face_mill_screw_{i}", 11.0, y * 0.92, z * 0.92, 0.9, 0.45)


def side_or_saw(code, saw=False):
    radius = 18.0 if saw else 15.0
    thick = 4.0 if saw else 8.0
    cylinder("side_saw_disk", "X", radius, thick, (8, 0, 0), "brushed_steel", 128, 0.08)
    cylinder("hub", "X", radius * 0.34, thick + 5, (8, 0, 0), "holder_blue_steel", 80, 0.1)
    teeth = 24 if saw else 12
    for i in range(teeth):
        a = i * math.tau / teeth
        y = math.cos(a) * (radius + 1.3)
        z = math.sin(a) * (radius + 1.3)
        tip = prism(f"peripheral_tooth_{i}", [(0, -1.1), (3.6, 0.0), (0, 1.1)], 1.8, "carbide_gold", loc=(2.6, y, z), rot_z=math.degrees(a), bevel=0.02)
        tip.rotation_euler[0] = a
    cylinder("side_saw_shank", "X", 6.2, 46, (37, 0, 0), "ground_steel", 64, 0.1)


def chamfer_cutter(code, rounded=False, compact=False):
    prefix = "radius_chamfer_mill" if rounded else "chamfer_mill"
    r = 6.2 if compact else 8.8
    head_len = 18.0 if compact else 25.0
    shank_r = 4.3 if compact else 5.2
    flute_count = 4 if compact else 6
    cone(prefix + "_conical_cutting_head", "X", r, r * 0.22, head_len, (head_len / 2.0, 0, 0), "brushed_steel", 96, 0.03)
    cylinder(prefix + "_neck_relief", "X", shank_r * 0.78, 7.0, (head_len + 3.5, 0, 0), "ground_steel", 64, 0.04)
    cylinder(prefix + "_straight_shank", "X", shank_r, 44.0, (head_len + 29.0, 0, 0), "ground_steel", 72, 0.09)
    gauge_mark_band(prefix + "_shank_mark", "X", head_len + 37.0, shank_r * 1.02, 9.0, 10)
    torus_x(prefix + "_outer_cutting_lip", r * 0.96, 0.11, (1.0, 0, 0), "edge_hot", 96, 6)
    torus_x(prefix + "_shoulder_shadow", r * 0.82, 0.13, (head_len - 1.4, 0, 0), "black_pocket", 96, 6)
    if rounded:
        torus_x(prefix + "_honed_radius_corner", r * 0.78, 0.45 if compact else 0.62, (2.5, 0, 0), "carbide_gold", 96, 10)
    for i in range(flute_count):
        phase = i * math.tau / flute_count
        helix(prefix + f"_open_flute_{i}", 1.5, head_len - 3.0, r * 0.88, 0.32, phase, "black_pocket", 0.12, 32)
        helix(prefix + f"_bright_angle_edge_{i}", 0.9, head_len - 2.0, r * 1.02, 0.32, phase + 0.22, "edge_hot", 0.055, 30)


def thread_mill(code, engraving=False, drill_tap=False):
    prefix = "drill_thread_mill" if drill_tap else ("engraving_thread_mill" if engraving else "thread_mill")
    r = 2.65 if engraving else 3.35
    flute_count = 3 if engraving else 4
    if drill_tap:
        cone(prefix + "_drill_point", "X", r * 0.82, 0.0, 7.0, (3.5, 0, 0), "brushed_steel", 72, 0.02)
        add_fluted_cylinder(prefix + "_starter_drill", 7.0, 18.0, 4.0, r * 0.82, 2, "brushed_steel", 0.8)
        thread_start = 27.0
        thread_len = 32.0
    elif engraving:
        cone(prefix + "_fine_v_tip", "X", r * 0.95, 0.0, 8.0, (4.0, 0, 0), "brushed_steel", 72, 0.01)
        cylinder(prefix + "_small_neck", "X", r * 0.58, 7.0, (11.5, 0, 0), "ground_steel", 48, 0.02)
        thread_start = 14.5
        thread_len = 35.0
    else:
        cone(prefix + "_lead_chamfer", "X", r, r * 0.35, 7.0, (3.5, 0, 0), "brushed_steel", 72, 0.02)
        thread_start = 7.0
        thread_len = 42.0

    cylinder(prefix + "_thread_core", "X", r, thread_len, (thread_start + thread_len / 2.0, 0, 0), "brushed_steel", 96, 0.03)
    for i in range(flute_count):
        phase = i * math.tau / flute_count
        helix(prefix + f"_chip_flute_{i}", thread_start + 1.0, thread_len - 2.0, r * 0.98, 0.95, phase, "black_pocket", 0.14, 75)
        helix(prefix + f"_thread_cutting_edge_{i}", thread_start + 1.2, thread_len - 2.4, r * 1.08, 5.8, phase + 0.30, "edge_hot", 0.040, 150)
    tooth_count = 11 if engraving else 13
    for i in range(tooth_count):
        x = thread_start + 2.0 + i * ((thread_len - 4.0) / (tooth_count - 1))
        torus_x(prefix + f"_thread_tooth_{i:02d}", r * 0.98, 0.045, (x, 0, 0), "carbide_gold", 60, 5)

    shank_start = thread_start + thread_len
    cylinder(prefix + "_relieved_neck", "X", r * 0.72, 8.0, (shank_start + 4.0, 0, 0), "ground_steel", 64, 0.04)
    cylinder(prefix + "_shank", "X", 5.0, 42.0, (shank_start + 29.0, 0, 0), "ground_steel", 72, 0.08)
    gauge_mark_band(prefix + "_shank_mark", "X", shank_start + 35.0, 5.05, 9.0, 10)


def drill_chuck_holder():
    r = 3.9
    cone("chucked_drill_point_118deg", "X", r, 0.0, 8.0, (4.0, 0, 0), "brushed_steel", 80, 0.02)
    tube("chucked_drill_chisel_edge", [(0.25, -r * 0.25, 0.0), (0.25, r * 0.25, 0.0)], 0.07, "edge_hot", 2)
    add_fluted_cylinder("chucked_twist_drill", 8.0, 42.0, 12.0, r, 2, "brushed_steel", 1.75)
    cone("keyless_chuck_nose", "X", 5.0, 8.6, 12.0, (66.0, 0, 0), "coated_black", 80, 0.05)
    cylinder("keyless_chuck_body", "X", 8.6, 25.0, (82.5, 0, 0), "coated_black", 96, 0.13)
    cone("holder_adapter_taper", "X", 6.2, 8.2, 18.0, (104.0, 0, 0), "holder_blue_steel", 80, 0.08)
    cylinder("straight_holder_shank", "X", 6.2, 34.0, (130.0, 0, 0), "ground_steel", 72, 0.1)
    gauge_mark_band("keyless_chuck_knurl", "X", 82.5, 8.72, 21.0, 18)
    for i in range(3):
        a = i * math.tau / 3.0
        box(
            f"visible_chuck_jaw_{i}",
            (13.5, 0.95, 2.2),
            (60.0, math.cos(a) * 4.55, math.sin(a) * 4.55),
            "ground_steel",
            0.04,
            rot=(a, 0.0, 0.0),
        )
    torus_x("black_chuck_front_ring", 5.2, 0.25, (59.5, 0, 0), "black_pocket", 80, 8)
    torus_x("adapter_rear_ring", 6.4, 0.22, (113.0, 0, 0), "black_pocket", 80, 8)


def edge_finder_tool():
    cylinder("edge_finder_main_body", "X", 5.2, 44.0, (38.0, 0, 0), "ground_steel", 72, 0.12)
    cylinder("edge_finder_slip_tip", "X", 2.1, 22.0, (9.0, 0, 0), "brushed_steel", 54, 0.05)
    cylinder("edge_finder_contact_button", "X", 3.0, 4.0, (-3.0, 0, 0), "brushed_steel", 54, 0.04)
    torus_x("edge_finder_dark_split_line", 2.2, 0.16, (20.5, 0, 0), "black_pocket", 60, 6)
    torus_x("edge_finder_rear_collar", 5.3, 0.28, (59.0, 0, 0), "brass", 72, 8)
    gauge_mark_band("edge_finder_knurl", "X", 39.0, 5.25, 12.0, 16)


def calibration_setter(code):
    prefix = "lathe_calibration_setter" if code == 585 else "probe_calibration_setter"
    cylinder(prefix + "_ground_body", "X", 5.4, 42.0, (36.0, 0, 0), "ground_steel", 72, 0.12)
    cylinder(prefix + "_brass_reference_ring", "X", 6.5, 8.0, (14.0, 0, 0), "brass", 72, 0.08)
    torus_x(prefix + "_black_insulator", 5.7, 0.28, (20.0, 0, 0), "rubber", 72, 8)
    cylinder(prefix + "_precision_contact_pin", "X", 1.45, 18.0, (2.0, 0, 0), "brushed_steel", 44, 0.03)
    if code == 585:
        sphere(prefix + "_round_reference_ball", 3.3, (-8.0, 0, 0), "brushed_steel", segments=44)
    else:
        cylinder(prefix + "_flat_reference_pad", "X", 4.0, 3.0, (-8.5, 0, 0), "warning_yellow", 52, 0.04)
    gauge_mark_band(prefix + "_etched_grip", "X", 41.0, 5.48, 10.0, 14)


def angle_head(rounded=False):
    box("angle_head_block", (28, 22, 22), (22, 0, 0), "holder_blue_steel", 1.0)
    cylinder("angle_drive_body", "Y", 8, 34, (22, -20, 0), "holder_dark_steel", 64, 0.2)
    cylinder("angle_spindle", "Y", 4.5, 26, (22, -38, 0), "ground_steel", 64, 0.1)
    end_mill(100, 3, ball=rounded, taper=False, corner=rounded)
    for o in CURRENT[-(10 if rounded else 8):]:
        o.location.y -= 48
        o.rotation_euler[2] += math.radians(90)


def drill(code, solid=False):
    r = 4.8 if not solid else 5.5
    cone("drill_point_118deg_faceted", "X", r, 0.0, 11.5, (5.75, 0, 0), "brushed_steel", 96, 0.02)
    tube("drill_chisel_edge", [(0.15, -r * 0.26, 0.0), (0.15, r * 0.26, 0.0)], 0.09, "edge_hot", 2)
    tube("drill_split_point_a", [(0.35, -r * 0.58, 0.35), (5.7, 0.0, r * 0.56)], 0.08, "edge_hot", 2)
    tube("drill_split_point_b", [(0.35, r * 0.58, -0.35), (5.7, 0.0, -r * 0.56)], 0.08, "edge_hot", 2)
    add_fluted_cylinder("twist_drill", 10, 66, 42, r, 2, "brushed_steel", 2.35)
    for i in range(2):
        phase = i * math.pi + 0.22
        helical_strip(f"drill_deep_flute_valley_{i}", 11.0, 62.0, r * 1.045, 2.35, phase, 0.54, "black_pocket", 160)
        helical_strip(f"drill_bright_primary_land_{i}", 10.6, 63.0, r * 1.065, 2.35, phase + 0.46, 0.18, "ground_steel", 150)
    for i in range(2):
        helix(f"drill_secondary_land_{i}", 12, 59, r * 0.82, 2.2, i * math.pi + 0.75, "ground_steel", 0.08, 90)
    if solid:
        cylinder("coolant_hole_left", "X", 0.35, 70, (44, 1.8, 0.0), "coolant_blue", 12, 0)
        cylinder("coolant_hole_right", "X", 0.35, 70, (44, -1.8, 0.0), "coolant_blue", 12, 0)


def center_drill():
    cone("center_drill_tip", "X", 3.0, 0.0, 6, (3, 0, 0), "brushed_steel", 64, 0.02)
    cone("center_drill_body_cone", "X", 5.5, 3.0, 12, (12, 0, 0), "brushed_steel", 64, 0.04)
    add_fluted_cylinder("center_drill", 18, 22, 34, 5.5, 2, "brushed_steel", 0.8)


def countersink(counterbore=False):
    if counterbore:
        cylinder("counterbore_pilot", "X", 2.2, 10, (5, 0, 0), "brushed_steel", 48, 0.02)
        cylinder("counterbore_cutter", "X", 8.5, 16, (18, 0, 0), "brushed_steel", 80, 0.08)
        torus_x("counterbore_step_shadow", 8.3, 0.22, (10.5, 0, 0), "etched_mark", 80, 6)
        for i in range(4):
            helix(f"counterbore_flute_{i}", 11, 18, 8.65, 0.32, i * math.tau / 4, "black_pocket", 0.11, 30)
    else:
        cone("countersink_90deg_head", "X", 12.0, 2.2, 22, (11, 0, 0), "brushed_steel", 96, 0.04)
        torus_x("countersink_outer_edge", 11.8, 0.12, (0.8, 0, 0), "edge_hot", 96, 6)
        for i in range(6):
            helix(f"countersink_flute_{i}", 1.0, 20, 8.8, 0.18, i * math.tau / 6, "black_pocket", 0.12, 22)
    cylinder("countersink_shank", "X", 4.4, 45, (44, 0, 0), "ground_steel", 64, 0.08)


def tap(code):
    r = 4.4 if code == 241 else 4.8
    cylinder("tap_thread_core", "X", r, 58, (29, 0, 0), "brushed_steel", 96, 0.05)
    cylinder("tap_square_shank", "X", 3.9, 38, (77, 0, 0), "ground_steel", 64, 0.06)
    box("tap_square_drive", (9, 9, 9), (101, 0, 0), "ground_steel", 0.25)
    gauge_mark_band("tap_marking", "X", 78.5, 4.1, 7.0, 8)
    pitch_turns = 12 if code == 241 else 9
    for i in range(3):
        helix(f"tap_spiral_thread_{i}", 2, 54, r * 1.08, pitch_turns, i * math.tau / 3, "edge_hot", 0.045, 170)
        helix(f"tap_flute_{i}", 2, 54, r * 0.96, 1.15, i * math.tau / 3 + 0.45, "black_pocket", 0.18, 100)
    cone("tap_lead_chamfer", "X", r * 0.85, r * 0.35, 7, (3.5, 0, 0), "brushed_steel", 72, 0.02)


def reamer():
    r = 4.9
    cylinder("reamer_body", "X", r, 58, (29, 0, 0), "brushed_steel", 96, 0.04)
    cylinder("reamer_shank", "X", 4.2, 42, (79, 0, 0), "ground_steel", 72, 0.08)
    for i in range(8):
        a = i * math.tau / 8
        y = math.cos(a) * r * 1.03
        z = math.sin(a) * r * 1.03
        tube(f"straight_reamer_flute_{i}", [(3, y, z), (56, y, z)], 0.08, "black_pocket", 2)
        tube(f"reamer_cutting_edge_{i}", [(1.2, y, z), (10, y, z)], 0.05, "edge_hot", 2)
    cone("reamer_entry_chamfer", "X", r, r * 0.55, 5, (2.5, 0, 0), "brushed_steel", 72, 0.02)


def probe(code):
    if code in (714,):
        cylinder("star_probe_body", "X", 5.0, 42, (30, 0, 0), "holder_blue_steel", 56, 0.1)
        torus_x("star_probe_black_collar", 5.2, 0.35, (10, 0, 0), "rubber", 64, 8)
        sphere("central_ruby", 3.2, (0, 0, 0), "ruby", segments=48)
        for i, axis in enumerate(("Y", "Z")):
            cylinder(f"star_arm_pos_{i}", axis, 0.75, 30, (0, 0, 0), "ceramic_white", 24, 0.02)
            loc = (0, 15, 0) if axis == "Y" else (0, 0, 15)
            sphere(f"star_ruby_pos_{i}", 2.2, loc, "ruby", segments=32)
            loc2 = (0, -15, 0) if axis == "Y" else (0, 0, -15)
            sphere(f"star_ruby_neg_{i}", 2.2, loc2, "ruby", segments=32)
    elif code == 713:
        cylinder("l_probe_body", "X", 4.2, 50, (34, 0, 0), "holder_blue_steel", 48, 0.1)
        torus_x("l_probe_collar", 4.4, 0.35, (14, 0, 0), "rubber", 64, 8)
        cylinder("l_stylus_long", "Y", 0.65, 26, (0, -13, 0), "ceramic_white", 20, 0.01)
        cylinder("l_stylus_short", "X", 0.65, 18, (9, -26, 0), "ceramic_white", 20, 0.01)
        sphere("l_probe_ruby", 2.8, (18, -26, 0), "ruby", segments=40)
    elif code == 711:
        edge_finder_tool()
    elif code in (585, 725):
        calibration_setter(code)
    elif code in (580, 710, 712):
        cylinder("probe_body", "X", 5.5, 44, (35, 0, 0), "holder_blue_steel", 56, 0.12)
        torus_x("probe_black_collar", 5.6, 0.35, (15, 0, 0), "rubber", 64, 8)
        cylinder("ceramic_stylus", "X", 0.75, 28, (10, 0, 0), "ceramic_white", 22, 0.01)
        sphere("ruby_tip", 3.0, (0, 0, 0), "ruby", segments=48)
        torus_x("probe_collar", 5.9, 0.4, (17, 0, 0), "brass", 60, 8)
    else:
        cylinder("probe_body_generic", "X", 4.5, 48, (34, 0, 0), "holder_blue_steel", 48, 0.1)
        sphere("probe_tip_generic", 2.6, (0, 0, 0), "ruby", segments=36)


def stop_tool():
    cylinder("adjustable_stop_pin", "X", 5.0, 42, (22, 0, 0), "ground_steel", 64, 0.08)
    box("stop_block", (16, 26, 20), (52, 0, 0), "holder_dark_steel", 0.8)
    cylinder("knurled_stop_ring", "X", 8.0, 8, (42, 0, 0), "brass", 64, 0.1)
    for i in range(12):
        a = i * math.tau / 12
        tube(f"knurl_{i}", [(38, math.cos(a) * 8.1, math.sin(a) * 8.1), (46, math.cos(a + 0.28) * 8.1, math.sin(a + 0.28) * 8.1)], 0.04, "black_pocket", 1)


def mandrel():
    cone("mandrel_taper", "X", 4.0, 9.0, 58, (29, 0, 0), "ground_steel", 80, 0.08)
    cylinder("mandrel_drive", "X", 9.2, 32, (74, 0, 0), "holder_dark_steel", 72, 0.1)
    torus_x("mandrel_keyway_ring", 9.3, 0.45, (55, 0, 0), "black_pocket", 80, 8)


def steady_rest():
    frame_x = 32.0
    torus_x("steady_rest_upright_ring", 15.0, 1.65, (frame_x, 0, 0), "holder_dark_steel", 112, 10)
    box("steady_rest_level_base", (16.0, 39.0, 4.2), (frame_x, 0.0, -19.2), "holder_dark_steel", 0.55)
    box("steady_rest_left_foot", (17.5, 8.0, 3.4), (frame_x, -13.5, -21.8), "ground_steel", 0.35)
    box("steady_rest_right_foot", (17.5, 8.0, 3.4), (frame_x, 13.5, -21.8), "ground_steel", 0.35)
    box("steady_rest_center_web", (8.0, 10.0, 7.0), (frame_x, 0.0, -15.6), "holder_dark_steel", 0.35)
    cylinder("steady_rest_mounting_shank", "X", 5.5, 42.0, (69.0, 0, -19.2), "ground_steel", 48, 0.1)
    for i, angle_deg in enumerate((90.0, 210.0, 330.0)):
        a = math.radians(angle_deg)
        y_inner = math.cos(a) * 7.5
        z_inner = math.sin(a) * 7.5
        y_outer = math.cos(a) * 17.0
        z_outer = math.sin(a) * 17.0
        tube(
            f"steady_rest_adjuster_body_{i}",
            [(frame_x, y_outer, z_outer), (frame_x, y_inner, z_inner)],
            1.05,
            "holder_dark_steel",
            3,
        )
        tube(
            f"steady_rest_bright_screw_{i}",
            [(frame_x - 5.0, y_outer, z_outer), (frame_x - 1.4, y_inner, z_inner)],
            0.38,
            "screw_steel",
            3,
        )
        cylinder(f"steady_rest_contact_roller_{i}", "X", 1.9, 6.2, (frame_x - 6.8, y_inner, z_inner), "brushed_steel", 36, 0.04)
        sphere(f"steady_rest_knob_{i}", 1.8, (frame_x + 1.6, y_outer, z_outer), "rubber", segments=28)


def auxiliary():
    box("auxiliary_holder", (62, 16, 14), (38, 0, 0), "holder_dark_steel", 0.8)
    cylinder("auxiliary_gauge_pin", "X", 3.5, 22, (6, 0, 0), "brass", 48, 0.06)
    sphere("auxiliary_contact", 2.4, (0, 0, 0), "warning_yellow", segments=36)


def stamp_reference_metadata(code):
    source, note = REFERENCE_MODEL_OVERRIDES.get(code, ("generated_tool_references_manifest.csv", "matched local reference manifest"))
    for obj in CURRENT:
        obj["chekator_tool_code"] = code
        obj["chekator_reference_source"] = source
        obj["chekator_model_note"] = note


def build_model(code):
    if code == 500:
        turning_base("rough")
    elif code == 510:
        turning_base("finish")
    elif code == 520:
        turning_base("plunge")
    elif code == 530:
        turning_base("cutoff")
    elif code == 540:
        turning_base("thread")
    elif code == 550:
        turning_base("button")
    elif code == 560:
        drill_chuck_holder()
    elif code == 210:
        boring_bar()
    elif code == 200:
        drill(code, solid=False)
    elif code == 205:
        drill(code, solid=True)
    elif code == 220:
        center_drill()
    elif code == 230:
        countersink(False)
    elif code == 231:
        countersink(True)
    elif code in (240, 241, 242):
        tap(code)
    elif code == 145:
        thread_mill(code)
    elif code == 157:
        thread_mill(code, engraving=True)
    elif code == 160:
        thread_mill(code, drill_tap=True)
    elif code == 250:
        reamer()
    elif code in (100, 120):
        end_mill(code, flutes=4)
    elif code == 110:
        end_mill(code, flutes=4, ball=True)
    elif code == 111:
        end_mill(code, flutes=3, ball=True, taper=True)
    elif code == 121:
        end_mill(code, flutes=5, corner=True)
    elif code == 130:
        chamfer_cutter(code)
    elif code == 131:
        chamfer_cutter(code, rounded=True)
    elif code == 140:
        face_mill()
    elif code in (150, 151, 700):
        side_or_saw(code, saw=code in (151, 700))
    elif code == 155:
        chamfer_cutter(code, compact=True)
    elif code == 156:
        chamfer_cutter(code, rounded=True, compact=True)
    elif code in (580, 585, 710, 711, 712, 713, 714, 725):
        probe(code)
    elif code == 730:
        stop_tool()
    elif code == 731:
        mandrel()
    elif code == 732:
        steady_rest()
    else:
        auxiliary()


def select_current():
    bpy.ops.object.select_all(action="DESELECT")
    for obj in CURRENT:
        obj.select_set(True)
    if CURRENT:
        bpy.context.view_layer.objects.active = CURRENT[0]


def export_selected_obj(path):
    select_current()
    try:
        bpy.ops.wm.obj_export(
            filepath=str(path),
            export_selected_objects=True,
            export_materials=True,
            export_uv=False,
            export_normals=False,
            apply_modifiers=True,
        )
    except Exception:
        bpy.ops.export_scene.obj(
            filepath=str(path),
            use_selection=True,
            use_materials=True,
            use_uvs=False,
            use_normals=False,
        )


def add_label(code, offset):
    font_curve = bpy.data.curves.new(f"label_{code}", "FONT")
    font_curve.body = f"{code} {TOOL_NAMES[code]}"
    font_curve.size = 4.0
    font_curve.align_x = "CENTER"
    obj = bpy.data.objects.new(f"label_{code}", font_curve)
    bpy.context.collection.objects.link(obj)
    obj.location = (offset[0] + 45, offset[1] - 30, offset[2])
    obj.data.materials.append(mat("ceramic_white"))


def offset_current(offset):
    for obj in CURRENT:
        obj.location.x += offset[0]
        obj.location.y += offset[1]
        obj.location.z += offset[2]


def write_manifest():
    text = ["# Chekator Blender tool model manifest", "# typeCode=displayName"]
    text.extend(f"{code}={TOOL_NAMES[code]}" for code in sorted(TOOL_NAMES))
    payload = "\n".join(text) + "\n"
    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    LIVE_DIR.mkdir(parents=True, exist_ok=True)
    (RESOURCE_DIR / "manifest.properties").write_text(payload, encoding="utf-8")
    (LIVE_DIR / "manifest.properties").write_text(payload, encoding="utf-8")


def selected_tool_codes():
    raw = os.environ.get("CHEKATOR_TOOL_CODES", "").strip()
    if not raw:
        return sorted(TOOL_NAMES)
    result = []
    for token in raw.replace(";", ",").split(","):
        token = token.strip()
        if not token:
            continue
        code = int(token)
        if code not in TOOL_NAMES:
            raise ValueError(f"Unknown Chekator tool code: {code}")
        result.append(code)
    return sorted(dict.fromkeys(result))


def main():
    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    LIVE_DIR.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()
    setup_materials()
    write_manifest()

    all_codes = selected_tool_codes()
    for index, code in enumerate(all_codes):
        global CURRENT
        CURRENT = []
        build_model(code)
        stamp_reference_metadata(code)
        out_obj = RESOURCE_DIR / f"tool_{code:03d}.obj"
        export_selected_obj(out_obj)
        out_mtl = RESOURCE_DIR / f"tool_{code:03d}.mtl"
        shutil.copy2(out_obj, LIVE_DIR / out_obj.name)
        if out_mtl.exists():
            shutil.copy2(out_mtl, LIVE_DIR / out_mtl.name)

        col = index % 5
        row = index // 5
        offset = (col * 135.0, row * -90.0, 0.0)
        offset_current(offset)
        add_label(code, offset)

    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))


if __name__ == "__main__":
    main()
