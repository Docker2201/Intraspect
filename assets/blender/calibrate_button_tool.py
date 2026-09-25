"""Export the existing Blender button assembly in a measured cutting-centre frame.

Run inside Blender. Source components and materials come from chekator_tool_models.blend.
The insert is a round *plate*, not a sphere. Its 256-sided cutting rim and the NC
solver share the library radius; the shank is independently dimensioned in mm.
Only the exported insert's two section coordinates are normalized to radius one.
"""
import json
import math
import re
from pathlib import Path

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/tool-models'
SOURCE = ROOT / 'assets/blender/chekator_tool_models.blend'
NAMES = ['round_button_insert_button', 'round_button_insert_screw_head',
         'round_button_insert_screw_hex_socket', 'round_button_insert_chipbreaker_ring',
         'button_holder']

with bpy.data.libraries.load(str(SOURCE), link=False) as (source, target):
    target.objects = list(NAMES)
objects = dict(zip(NAMES, target.objects))
scene = bpy.data.scenes.new('Calibrated button tool — mm')
for obj in objects.values():
    scene.collection.objects.link(obj)
plate = objects[NAMES[0]]
centre = plate.location.copy()
radius = plate.dimensions.x / 2
thickness = plate.dimensions.z

# Correct the existing insert's coarse/bevelled working rim. Keep its object,
# material and fastening parts; the reference diameter remains the measured 14.4 mm.
count = 256
relief_degrees=7.0
back_radius=radius-thickness*math.tan(math.radians(relief_degrees))
verts = [(r*math.cos(i*math.tau/count), r*math.sin(i*math.tau/count), z)
         for z,r in [(-thickness/2,back_radius),(thickness/2,radius)] for i in range(count)]
faces = [tuple(reversed(range(count))), tuple(range(count,2*count))]
faces += [(i,(i+1)%count,(i+1)%count+count,i+count) for i in range(count)]
plate.modifiers.clear()
plate.data.clear_geometry()
plate.data.from_pydata(verts, [], faces)
plate.data.update()

holder = objects['button_holder']
holder_half_width = holder.dimensions.y / 2
holder_length = holder.dimensions.x
holder_depth = holder.dimensions.z

def material_lines(mat):
    color = mat.diffuse_color
    return [f'newmtl {re.sub(r"\.[0-9]+$", "", mat.name)}', 'Kd ' + ' '.join(f'{v:.6f}' for v in color[:3]),
            'Ks 0.22 0.22 0.22', 'Ns 48', 'd 1', 'illum 2', '']

def export(name, selected, is_insert):
    obj_lines = ['# Calibrated from existing Blender library, millimetres', f'mtllib {name}.mtl']
    mats = {}
    offset = 1
    # Evaluate the original bevels and bolts; apply the object's actual placement,
    # not a bounding-box corner or an arbitrary vertex at minimum X.
    with bpy.context.temp_override(scene=scene, view_layer=scene.view_layers[0]):
        scene.view_layers[0].update()
        deps = bpy.context.evaluated_depsgraph_get()
        for obj in selected:
            ev = obj.evaluated_get(deps)
            mesh = ev.to_mesh()
            mesh.calc_loop_triangles()
            matrix = obj.matrix_basis
            obj_lines.append(f'o {re.sub(r"\.[0-9]+$", "", obj.name)}')
            for vertex in mesh.vertices:
                p = matrix @ vertex.co
                if is_insert:
                    r = (p.y-centre.y)/radius
                    z = (p.x-centre.x)/radius
                    tangential = thickness/2 + centre.z-p.z
                else:
                    r = (p.y-holder.location.y)/holder_half_width
                    z = p.x-(holder.location.x-holder_length/2)
                    # The holder lies behind the insert's rake face.
                    tangential = thickness + holder.location.z + holder_depth/2-p.z
                obj_lines.append(f'v {r:.9f} {z:.9f} {tangential:.9f}')
            last = None
            for tri in mesh.loop_triangles:
                mat = mesh.materials[tri.material_index]
                mat_name=re.sub(r'\.[0-9]+$', '', mat.name)
                mats[mat_name] = mat
                if mat_name != last:
                    obj_lines.append(f'usemtl {mat_name}')
                    last = mat_name
                # Coordinate mapping (Y,X,-Z) has positive determinant.
                obj_lines.append('f ' + ' '.join(str(offset+i) for i in tri.vertices))
            offset += len(mesh.vertices)
            ev.to_mesh_clear()
    (OUT/f'{name}.obj').write_text('\n'.join(obj_lines)+'\n', encoding='utf-8')
    (OUT/f'{name}.mtl').write_text('\n'.join(line for mat in mats.values() for line in material_lines(mat)), encoding='utf-8')

export('calibrated_button_insert', [objects[n] for n in NAMES[:-1]], True)
export('calibrated_button_holder', [holder], False)
(OUT/'calibrated_button.properties').write_text(
    f'# Measured Blender reference dimensions (mm)\ninsertRadius={radius}\n'
    f'holderHalfWidth={holder_half_width}\nholderLength={holder_length}\n'
    f'insertThickness={thickness}\n', encoding='utf-8')

# Keep an editable, assembled reference in Blender, with its working centre at
# the origin and +X along the vertical ram. Original library file stays intact.
for name,obj in objects.items():
    obj.location -= centre
    if name == 'button_holder':
        obj.location.y = 0
        obj.location.x = holder_length/2 + radius*.6
        obj.location.z = -holder_depth/2-thickness
        obj.scale.y *= min(holder_half_width,radius*.6)/holder_half_width
scene.unit_settings.system='METRIC'
scene.unit_settings.scale_length=.001
scene['cutting_radius_mm']=radius
scene['insert_relief_degrees']=relief_degrees
scene['source_library']=SOURCE.name
scene['holder_half_width_mm']=holder_half_width
scene['runtime_section_axes']='radius = Blender Y; axial Z = Blender X; tangent = -Blender Z'
bpy.data.libraries.write(str(ROOT/'assets/blender/calibrated_button_tool.blend'), {scene}, fake_user=True)
print(json.dumps({'source':SOURCE.name,'radius_mm':radius,'holder_length_mm':holder_length,
                  'holder_width_mm':holder_half_width*2,'rim_segments':count,'exported':True}))
