package com.sergey.pisarev.service;

import com.sergey.pisarev.model.WorkpieceDefinition;

public final class WorkpieceRegressionTest {
    public static void main(String[] args) {
        blank("long symmetric blank", "WORKPIECE(,,,\"CYLINDER\",192,2180,-2180,-2180,212)",
                212, -2180, 2180);
        blank("Siemens manual example", "WORKPIECE(,,,\"CYLINDER\",0,0,-200,-150,100)",
                100, -200, 0);
        blank("incremental end relative to Z0", "WORKPIECE(,,,\"CYLINDER\",0,100,-200,-150,100)",
                100, -100, 100);
        blank("absolute end independent of G91", "G91\nWORKPIECE(,,,\"CYLINDER\",192,100,-200,-150,100)",
                100, -200, 100);
        blank("ZB is not the blank endpoint", "WORKPIECE(,,,\"CYLINDER\",64,100,-200,-900,100)",
                100, -200, 100);
        blank("named workpiece and omitted optional fields", "WORKPIECE(\"SHAFT\",,\"\",\"CYLINDER\",,10,-80,,60)",
                60, -70, 10);
        blank("older Chekator generated line", "WORKPIECE(,,,\"CYLINDER\",231.540,2180,-2180,-2180,0)",
                231.54, -2180, 0);
        var shortBlank = GCodeProgramParser.findWorkpiece("WORKPIECE(,,,\"CYLINDER\",80,200)").orElseThrow();
        check("short legacy form stays unanchored", !shortBlank.isZAnchored());
        var original = new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,212,4360,-2180,2180,2);
        blank("auto-fix emits Siemens coordinates", ProgramDiagnostics.workpieceLine(original),212,-2180,2180);
        var needed = new WorkpieceDefinition(WorkpieceDefinition.Shape.CYLINDER,231.54,2192,-1751,441,0);
        var diagnostic = ProgramDiagnostics.blankMismatch(original,needed);
        blank("auto-fit preserves unmachined ends", diagnostic.applyAutoFix(
                "; header\n"+ProgramDiagnostics.workpieceLine(original)),231.54,-2180,2180);
        check("zero-length standard blank rejected", GCodeProgramParser.findWorkpiece(
                "WORKPIECE(,,,\"CYLINDER\",192,10,10,-20,100)").isEmpty());
    }
    private static void blank(String name,String text,double d,double min,double max) {
        var wp=GCodeProgramParser.findWorkpiece(text).orElseThrow(() -> new AssertionError(name));
        check(name, wp.getDiameterMm()==d && wp.getZMin()==min && wp.getZMax()==max
                && wp.getLengthMm()==max-min && wp.isZAnchored());
    }
    private static void check(String name,boolean ok) {
        if(!ok) throw new AssertionError(name);
        System.out.println("PASS "+name);
    }
}
