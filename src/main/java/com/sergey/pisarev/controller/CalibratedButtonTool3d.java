package com.sergey.pisarev.controller;

import java.io.IOException;
import java.util.Properties;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.transform.Scale;

/** Measured components exported from assets/blender/chekator_tool_models.blend.
 * The cutting centre is fixed at (0,0,0), exactly as in LatheCyclePath. Only the
 * round plate's section scales with R; the existing holder keeps its axial size.
 */
final class CalibratedButtonTool3d {
    private static final double HOLDER_HALF_WIDTH=holderHalfWidth();
    private CalibratedButtonTool3d() { }

    static Group create(double radius,String modelId) {
        if (!"round_straight".equals(modelId) && !"round_cranked".equals(modelId)) return create(radius);
        if (!Double.isFinite(radius)||radius<=0) return null;
        int diameter=radius<13?20:32;
        String body="wheel_"+("round_cranked".equals(modelId)?"cranked":"straight")+"_"+diameter+".obj";
        var insert=ToolAssetModelLoader.load("wheel_insert_"+diameter+".obj",550);
        var holder=ToolAssetModelLoader.load(body,550);
        if(insert==null||holder==null) return null;
        insert.getProperties().put("cutting-insert",true);
        holder.getProperties().put("holder",true);
        var assembly=new Group(insert,holder);
        // Library assemblies are measured D20/D32 families. Other radii scale the
        // complete family coherently; never fit or translate a holder to stock.
        double scale=2*radius/diameter;
        assembly.getTransforms().add(new Scale(scale,scale,scale,0,0,0));
        assembly.getProperties().put("tool-asset",body);
        assembly.getProperties().put("cutting-centre",Point3D.ZERO);
        assembly.getProperties().put("front-tip",new Point3D(0,-radius,0));
        return assembly;
    }

    static Group create(double radius) {
        if(!Double.isFinite(radius)||radius<=0)return null;
        var insert=ToolAssetModelLoader.load("calibrated_button_insert.obj",550);
        var holder=ToolAssetModelLoader.load("calibrated_button_holder.obj",550);
        if(insert==null||holder==null)return null;
        // Exported insert is radius one in the X/Y section, with real thickness.
        insert.getTransforms().add(new Scale(radius,radius,1,0,0,0));
        insert.getProperties().put("cutting-insert",true);
        holder.getTransforms().add(new Scale(Math.min(HOLDER_HALF_WIDTH,radius*.6),1,1,0,0,0));
        holder.setTranslateY(radius*.6);
        holder.getProperties().put("holder",true);
        var assembly=new Group(insert,holder);
        assembly.getProperties().put("tool-asset","calibrated_button_insert.obj");
        assembly.getProperties().put("cutting-centre",Point3D.ZERO);
        assembly.getProperties().put("front-tip",new Point3D(0,-radius,0));
        return assembly;
    }

    private static double holderHalfWidth() {
        var properties=new Properties();
        try(var input=CalibratedButtonTool3d.class.getResourceAsStream("/tool-models/calibrated_button.properties")) {
            if(input==null)throw new IllegalStateException("Missing measured button-tool dimensions");
            properties.load(input);
            return Double.parseDouble(properties.getProperty("holderHalfWidth"));
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
}
