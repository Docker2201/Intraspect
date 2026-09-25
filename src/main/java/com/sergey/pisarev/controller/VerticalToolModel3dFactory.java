package com.sergey.pisarev.controller;

import com.sergey.pisarev.model.CncToolDefinition;
import com.sergey.pisarev.service.LatheCyclePath;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/** Mounts the complete library mesh, including its existing insert. The origin
 * is the cutting nose centre; local Y is NC +Z, local X is radial outward.
 * OBJ shanks run along +X and their insert face is in the X/Z plane.
 */
final class VerticalToolModel3dFactory {
    private VerticalToolModel3dFactory() { }

    static Group create(CncToolDefinition tool, boolean dark) {
        var mounted = new Group();
        if(tool!=null && tool.getTypeCode()==550) {
            var calibrated=CalibratedButtonTool3d.create(tool.getRadius(),tool.getModelId());
            if(calibrated==null)mounted.getProperties().put("missing-tool-model",true);
            else {
                mounted.getChildren().add(calibrated);
                mounted.getProperties().putAll(calibrated.getProperties());
                mounted.getProperties().put("nose-radius-mm",tool.getRadius());
                mounted.getProperties().put("vertical-mount",true);
                mounted.getProperties().put("calibrated-nose",true);
            }
            return mounted;
        }
        Group asset = tool == null ? null : ToolAssetModelLoader.load(tool.getTypeCode());
        if (asset == null) {
            // No invented sphere/box cutter for an unknown T/D or missing asset.
            mounted.getProperties().put("missing-tool-model", true);
            return mounted;
        }
        Point3D tip = (Point3D)asset.getProperties().get("front-tip");
        double radius = Math.max(0,tool.getRadius());
        // Centre the exported working end before rotating: an off-centre OBJ
        // nose otherwise runs beside the swept cutting circle. The lowest
        // cutting point is one nose radius below the centre, not above it.
        asset.getTransforms().addAll(new Rotate(90,Rotate.Y_AXIS),
                new Rotate(90,Rotate.Z_AXIS),
                new Translate(-tip.getX()-radius,-tip.getY(),-tip.getZ()));
        mounted.getChildren().add(asset);
        mounted.getProperties().put("tool-asset",asset.getProperties().get("tool-asset"));
        mounted.getProperties().put("nose-radius-mm",radius);
        mounted.getProperties().put("vertical-mount",true);
        mounted.getProperties().put("front-tip",new Point3D(0,-radius,0));
        return mounted;
    }

    /** The generic library mesh is a visual tool, not a measured holder solid.
     * Register its exported working point to the active point on the library
     * nose circle. This avoids showing the cut one nose radius away from the
     * visible tip when the contact changes from a face to an axial wall. */
    static void alignContact(Group mounted,LatheCyclePath.Pose pose) {
        // A measured round insert stays rigidly attached to its compensated
        // centre. Its contact travels around the rim, never by moving the holder.
        if(Boolean.TRUE.equals(mounted.getProperties().get("calibrated-nose")))return;
        if (mounted.getChildren().isEmpty()) return;
        double radius=(double)mounted.getProperties().get("nose-radius-mm");
        var contour=pose.contour();var centre=pose.centre();double t=pose.fraction();
        double dr=((contour.startX()-centre.startX())*(1-t)+(contour.endX()-centre.endX())*t)*.5;
        double dz=(contour.startZ()-centre.startZ())*(1-t)+(contour.endZ()-centre.endZ())*t;
        double length=Math.hypot(dr,dz);
        if (length>1e-9) {dr*=radius/length;dz*=radius/length;}
        else {dr=0;dz=-radius;}
        var asset=mounted.getChildren().get(0);
        asset.setTranslateX(dr);asset.setTranslateY(dz+radius);
        mounted.getProperties().put("contact-offset",new Point3D(dr,dz,0));
    }
}
