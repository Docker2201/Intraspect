package com.sergey.pisarev.controller;

import javafx.collections.ObservableFloatArray;
import javafx.scene.shape.TriangleMesh;

/** Applies a worker snapshot to an FX-owned mesh without invalidating its index
 * buffer when topology is unchanged. Prism can then update vertex/normal ranges
 * instead of rebuilding all tangent-space and vertex-index maps. */
final class CycleMeshBuffers {
    private CycleMeshBuffers() { }

    static TriangleMesh update(TriangleMesh target,TriangleMesh source) {
        boolean sameTopology=target.getVertexFormat()==source.getVertexFormat()
                && target.getPoints().size()==source.getPoints().size()
                && target.getNormals().size()==source.getNormals().size()
                && target.getTexCoords().size()==source.getTexCoords().size()
                && target.getFaces().size()==source.getFaces().size();
        if(sameTopology)for(int i=0;i<target.getFaces().size();i++) {
            if(target.getFaces().get(i)!=source.getFaces().get(i)){sameTopology=false;break;}
        }
        if(!sameTopology) {
            // JavaFX 21's PNT updater retains its dirty-vertex array across a
            // topology rebuild. A growing batch therefore needs a fresh mesh.
            target=new TriangleMesh(source.getVertexFormat());
            target.getPoints().setAll(source.getPoints());
            target.getNormals().setAll(source.getNormals());
            target.getTexCoords().setAll(source.getTexCoords());
            target.getFaces().setAll(source.getFaces());
        } else {
            updateRange(target.getPoints(),source.getPoints(),3);
            updateRange(target.getNormals(),source.getNormals(),3);
            updateRange(target.getTexCoords(),source.getTexCoords(),2);
        }
        return target;
    }

    private static void updateRange(ObservableFloatArray target,ObservableFloatArray source,int stride) {
        int first=0,end=source.size();
        while(first<end && target.get(first)==source.get(first))first++;
        while(end>first && target.get(end-1)==source.get(end-1))end--;
        if(first<end) {
            first=first/stride*stride;end=(end+stride-1)/stride*stride;
            target.set(first,source,first,end-first);
        }
    }
}
