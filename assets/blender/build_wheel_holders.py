"""Editable wheel-tool library, millimetres, cutting circle centre at origin.

Visual reference: https://www.youtube.com/watch?v=ecut7q1kHMk (UZTS KS1114-02.SM).
Clear views: 00:53, 01:13 and 02:13. Stepped axial coupling and faceted blade.
Insert dimensions: Boehlerit Railway wheel machining, RCMX.
RCMX 2006: D20 S6.35; RCMX 3209: D32 S9.52, positive 7 degree relief.
Holder bodies are representative mounted assemblies, not certified RQQ drawings.
The cranked assembly has a fixed 48 mm radial offset; it is NOT fitted to stock.
Run in Blender via MCP. Original library and open scenes are left intact.
"""
from pathlib import Path
import math, json
import bpy
import bmesh

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/tool-models'
scene=bpy.data.scenes.new('Wheel tools - KS1114 video reference')
scene.unit_settings.system='METRIC'
scene.unit_settings.scale_length=.001

def mat(name,color,metal,rough):
    m=bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.diffuse_color=(*color,1);m.use_nodes=True
    p=m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value=(*color,1)
    p.inputs['Metallic'].default_value=metal;p.inputs['Roughness'].default_value=rough
    return m
steel=mat('Wheel holder - oxide steel',(.105,.125,.15),.8,.32)
machined=mat('Wheel holder - machined steel',(.34,.38,.43),.8,.26)
gold=mat('Wheel insert - carbide coating',(.66,.46,.13),.75,.26)
screw=mat('Wheel insert - fastener',(.27,.30,.34),.85,.24)
dark=mat('Wheel insert - socket',(.022,.025,.03),.4,.45)

def mesh(name,vertices,faces,material,bevel=0):
    m=bpy.data.meshes.new(name);m.from_pydata(vertices,[],faces);m.update()
    bm=bmesh.new();bm.from_mesh(m)
    bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-7)
    bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=1e-8)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(m);bm.free()
    o=bpy.data.objects.new(name,m);scene.collection.objects.link(o);m.materials.append(material)
    if bevel:
        b=o.modifiers.new('Machined edge breaks','BEVEL');b.width=bevel;b.segments=2
    return o

def prism(name,outline,z0,z1,material,bevel=0):
    n=len(outline)
    return mesh(name,[(x,y,z) for z in (z0,z1) for x,y in outline],
                [tuple(reversed(range(n))),tuple(range(n,n*2))]+
                [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],material,bevel)

def ring(name,levels,material,count=128):
    vertices=[(r*math.cos(i*math.tau/count),r*math.sin(i*math.tau/count),z)
              for r,z in levels for i in range(count)]
    faces=[]
    for k in range(len(levels)-1):
        for i in range(count):
            a=k*count+i;b=k*count+(i+1)%count
            faces.append((a,b,b+count,a+count))
    # Profile runs clockwise from outside front, towards hole and around the back.
    faces=[tuple(reversed(f)) for f in faces]
    obj=mesh(name,vertices,faces,material)
    for p in obj.data.polygons:p.use_smooth=True
    # Preserve shoulders while smoothing around the circumference.
    bm=bmesh.new();bm.from_mesh(obj.data)
    for e in bm.edges:
        if len(e.link_faces)==2 and e.calc_face_angle()>math.radians(30):e.smooth=False
    bm.to_mesh(obj.data);bm.free()
    return obj

def blade(name,levels,material):
    """Closed eight-sided tapered sections: y, x-min/max, front/back tangent."""
    vertices=[]
    for y,lo,hi,front,back in levels:
        c=min(2.8,(hi-lo)*.18,(back-front)*.18)
        vertices.extend((x,y,z) for x,z in [(lo+c,front),(hi-c,front),
            (hi,front+c),(hi,back-c),(hi-c,back),(lo+c,back),(lo,back-c),(lo,front+c)])
    faces=[tuple(range(7,-1,-1))]
    for k in range(len(levels)-1):
        for j in range(8):
            a=k*8+j;b=k*8+(j+1)%8;faces.append((a,b,b+8,a+8))
    faces.append(tuple(range((len(levels)-1)*8,len(levels)*8)))
    return mesh(name,vertices,faces,material)

def axial_coupling(name,cx,cz):
    # Visual proportions only: the video does not identify a certified taper size.
    # Axis is NC +Z (Blender Y), rather than the insert screw's tangential axis.
    profile=[(0,176),(28,176),(28,180),(37,184),(40,188),(40,195),
             (35,197),(35,201),(40,203),(40,212),(31,215),(31,228),
             (29,230),(29,242),(0,242),(0,176)]
    obj=ring(name+' stepped coupling',profile,machined,64)
    for v in obj.data.vertices:
        x,y,z=v.co;v.co=(cx+x,z,cz-y) # proper rotation, preserves winding
    collar=ring(name+' locking collar',[(31.2,216),(31.2,222),(31,222),(31,216),(31.2,216)],steel,64)
    for v in collar.data.vertices:
        x,y,z=v.co;v.co=(cx+x,z,cz-y)
    # Driver flats on the top sleeve, as visible on the modular tool interface.
    for v in obj.data.vertices:
        if v.co.y>=230:v.co.x=max(cx-25,min(cx+25,v.co.x))
    return [obj,collar]

def coolant_port(name,x,y,z):
    # A shallow, recessed-looking outlet in the blade, separate editable pieces.
    rim=ring(name+' coolant outlet rim',[(4.2,z),(3.6,z-.5),(2.8,z-.5),
             (2.8,z+.15),(4.2,z+.15),(4.2,z)],machined,24)
    bore=ring(name+' coolant outlet recess',[(2.8,z-.45),(0,z-.45)],dark,24)
    for o in (rim,bore):
        for v in o.data.vertices:v.co.x+=x;v.co.y+=y
    return [rim,bore]

def export(name,objects):
    lines=['# Blender wheel-tool library; mm; X radial, Y axial, Z tangent',f'mtllib {name}.mtl'];offset=1;normal_offset=1;mats={}
    with bpy.context.temp_override(scene=scene,view_layer=scene.view_layers[0]):
        scene.view_layers[0].update();deps=bpy.context.evaluated_depsgraph_get()
        for o in objects:
            o.data.update()
            e=o.evaluated_get(deps);m=e.to_mesh();m.calc_loop_triangles()
            lines.append('o '+o.name.replace(' ','_'))
            for v in m.vertices: lines.append('v '+' '.join(f'{c:.8f}' for c in v.co))
            for n in m.corner_normals:lines.append('vn '+' '.join(f'{c:.8f}' for c in n.vector))
            material=o.data.materials[0];mn=material.name.replace(' ','_');mats[mn]=material
            lines.append('usemtl '+mn)
            for t in m.loop_triangles: lines.append('f '+' '.join(f'{offset+v}//{normal_offset+l}' for v,l in zip(t.vertices,t.loops)))
            offset+=len(m.vertices);normal_offset+=len(m.loops);e.to_mesh_clear()
    (OUT/(name+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf8')
    (OUT/(name+'.mtl')).write_text('\n'.join(f'newmtl {n}\nKd '+ ' '.join(str(x) for x in m.diffuse_color[:3])+'\nKs 0.25 0.25 0.25\nNs 64\nillum 2\n' for n,m in mats.items()),encoding='utf8')

for radius,thickness,hole in [(10,6.35,3.25),(16,9.52,4.75)]:
    back=radius-thickness*math.tan(math.radians(7))
    # Detailed recessed chipbreaker and central fixing hole; exact working rim at z=0.
    insert=[ring(f'RCMX D{radius*2} carbide',[(radius,0),(radius-.45,.22),(radius*.76,.9),
                 (hole+1.1,.35),(hole,.35),(hole,thickness),(back,thickness),(radius,0)],gold,256),
            ring(f'D{radius*2} fixing screw',[(hole*.91,.7),(hole*.84,.32),(hole*.42,.32),
                 (hole*.42,1.15),(hole*.91,1.15),(hole*.91,.7)],screw,64),
            ring(f'D{radius*2} hex socket',[(hole*.42,1.12),(0,1.12)],dark,6)]
    export(f'wheel_insert_{radius*2}',insert)
    for cranked in (False,True):
        name=f'wheel_{"cranked" if cranked else "straight"}_{radius*2}'
        if cranked:
            # Retain the relieved working neck; flare to the modular coupling.
            # Fixed tool geometry, independent of the wheel/NC contour.
            levels=[(radius*.6,0,radius*.4,thickness,thickness+22),
                    (radius+8,16,24,thickness,thickness+22),
                    (radius+24,32,48,thickness+1,thickness+24),
                    (radius+40,32,64,thickness+1,thickness+30),
                    (118,32,66,thickness+2,thickness+32),
                    (155,26,68,thickness+3,thickness+36),
                    (178,26,68,thickness+3,thickness+36)]
            cx=47;port_x=48;port_z=thickness+1.5
        else:
            levels=[(radius*.45,-radius*.45,radius*.45,thickness,thickness+14),
                    (20,-9,9,thickness,thickness+14),
                    (80,-9,9,thickness+1,thickness+16),
                    (132,-18,18,thickness+2,thickness+25),
                    (178,-23,23,thickness+3,thickness+33)]
            cx=0;port_x=0;port_z=thickness+.9
        holder=[blade(name+' faceted blade',levels,machined)]
        holder+=axial_coupling(name,cx,thickness+18)
        holder+=coolant_port(name,port_x,76,port_z)
        # Carbide backing seat stays within the projected cutting circle.
        seat=ring(name+' seat',[(back-.5,thickness+.05),(back-.5,thickness+1.5),
                               (0,thickness+1.5),(0,thickness+.05),(back-.5,thickness+.05)],steel,128)
        holder.append(seat)
        export(name,holder)
        for o in holder: o.location.x+=(170 if cranked else 0)+(390 if radius==16 else 0)
    for o in insert: o.location.x+=(390 if radius==16 else 0)
    # Duplicate the insert for the cranked assembly in the editable reference scene.
    for o in insert:
        c=o.copy();c.data=o.data.copy();scene.collection.objects.link(c);c.location.x+=170

scene['reference']='https://www.youtube.com/watch?v=ecut7q1kHMk — KS1114-02.SM, 00:53 / 01:13 / 02:13'
scene['dimensions_note']='Insert D20/32 from library. Coupling and blade proportions estimated from video, not certified machine dimensions.'
scene['cutting_frame']='X radial outward; Y NC +Z; Z tangential behind rake face; mm'

# Save a useful editable camera and a reproducible overview alongside the assets.
from mathutils import Vector,Matrix
cam_data=bpy.data.cameras.new('Wheel tools overview');cam=bpy.data.objects.new('Wheel tools overview',cam_data)
scene.collection.objects.link(cam);cam.location=(440,210,-1250)
direction=(Vector((306,110,18))-cam.location).normalized()
right=direction.cross(Vector((0,1,0))).normalized();up=right.cross(direction).normalized()
cam.rotation_euler=Matrix((right,up,-direction)).transposed().to_euler()
cam.data.type='ORTHO';cam.data.ortho_scale=750;cam.data.clip_end=5000;scene.camera=cam
label_mat=mat('Wheel library labels',(.72,.78,.86),0,.8)
for x,label in [(0,'D20 | Straight'),(170,'D20 | Cranked'),(390,'D32 | Straight'),(560,'D32 | Cranked')]:
    font=bpy.data.curves.new(label,'FONT');font.body=label;font.size=10;font.align_x='CENTER'
    o=bpy.data.objects.new(label,font);scene.collection.objects.link(o);o.location=(x,-38,-2);o.rotation_euler=(0,math.pi,0);font.materials.append(label_mat)
scene.render.engine='BLENDER_WORKBENCH';scene.display.shading.light='STUDIO';scene.display.shading.color_type='MATERIAL'
scene.display.shading.show_shadows=True;scene.display.shading.show_cavity=True;scene.display.shading.cavity_type='BOTH'
scene.world=bpy.data.worlds.new('Wheel library background');scene.world.color=(.055,.065,.085)
scene.display.shading.background_type='WORLD'
scene.render.resolution_x=1600;scene.render.resolution_y=860;scene.render.resolution_percentage=100
preview=ROOT/'out/video-tools';preview.mkdir(parents=True,exist_ok=True)
scene.render.image_settings.file_format='PNG';scene.render.filepath=str(preview/'tool-library-preview.png')
bpy.ops.render.render(write_still=True,scene=scene.name)
bpy.data.libraries.write(str(ROOT/'assets/blender/wheel_tool_library.blend'),{scene},fake_user=True)
print(json.dumps({'scene':scene.name,'objects':len(scene.objects),'exported':True}))
