package com.sergey.pisarev.util;

import java.nio.file.*;
import java.util.prefs.*;

/** Separate JVM per case, so static stores see the same startup conditions as the EXE. */
public final class PortableStorageRegressionTest {
    public static void main(String[] args) throws Exception {
        String scenario=args[1];
        Path root=Files.createTempDirectory(Path.of(args[0]),"portable-"+scenario+"-").toAbsolutePath();
        Path old=Files.createDirectories(root.resolve("pc-profile/.chekator"));
        Files.writeString(old.resolve("last_program.nc"),"FOREIGN-PC-PROGRAM");
        Files.writeString(old.resolve("settings.properties"),"origin=foreign\n");
        Files.writeString(old.resolve("tools.json"),"FOREIGN-PC-TOOLS");
        Files.writeString(old.resolve("program_edit_history.log"),"FOREIGN-PC-HISTORY");
        Path app=Files.createDirectories(root.resolve("usb/program"));
        Path data=app.resolve("data");
        if(scenario.equals("blocked")) Files.writeString(data,"Not a writable data directory");
        if(scenario.equals("populated")) {
            Files.createDirectories(data);
            Files.writeString(data.resolve("last_program.nc"),"USB-PROGRAM");
            Files.writeString(data.resolve("settings.properties"),"origin=usb\n");
        }
        System.setProperty("user.home",old.getParent().toString());
        System.setProperty("user.dir",root.resolve("another-working-directory").toString());
        System.setProperty("jpackage.app-path",app.resolve("Chekator.exe").toString());
        System.setProperty("chekator.data.dir",old.toString()); // EXE must ignore even a stale override.
        System.setProperty("java.util.prefs.PreferencesFactory",NoRegistry.class.getName());
        check("EXE directory wins",PortableStorage.dataDir().equals(data));
        boolean populated=scenario.equals("populated");
        check("program comes only from USB",UserSettings.getLastProgramText().equals(populated?"USB-PROGRAM":""));
        var prefs=PortablePrefs.open("settings.properties");
        check("settings come only from USB",prefs.get("origin","default").equals(populated?"usb":"default"));
        ToolLibraryStore.load();
        Class.forName(ProgramEditHistoryStore.class.getName());
        check("history never imported",!Files.exists(data.resolve("program_edit_history.log")));
        if(!scenario.equals("blocked")) {
            prefs.put("origin","changed-on-usb"); prefs.flush();
            UserSettings.setLastProgramText("SAVED-ON-USB");
            check("writes stay on USB",Files.readString(data.resolve("last_program.nc")).equals("SAVED-ON-USB"));
        } else {
            check("unwritable media stays local",PortableStorage.mode()==PortableStorage.StorageMode.READ_ONLY);
            check("failure is visible",PortableStorage.describeStorage().contains(I18n.text("storage.004")));
        }
        check("old program untouched",Files.readString(old.resolve("last_program.nc")).equals("FOREIGN-PC-PROGRAM"));
        check("old settings untouched",Files.readString(old.resolve("settings.properties")).equals("origin=foreign\n"));
        System.out.println("PASS portable scenario "+scenario);
    }
    private static void check(String name,boolean ok) {
        if(!ok) throw new AssertionError(name);
        System.out.println("PASS "+name);
    }
    public static final class NoRegistry implements PreferencesFactory {
        public Preferences userRoot(){throw new AssertionError("Windows preferences must not be accessed");}
        public Preferences systemRoot(){throw new AssertionError("Windows preferences must not be accessed");}
    }
}
