/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javafx.print.PageLayout
 *  javafx.print.PageOrientation
 *  javafx.print.Paper
 *  javafx.print.PrintResolution
 *  javafx.print.Printer
 *  javafx.print.PrinterJob
 *  javafx.scene.Node
 *  javafx.scene.image.Image
 *  javafx.scene.image.ImageView
 *  javafx.scene.image.WritableImage
 *  javafx.scene.transform.Scale
 *  javafx.scene.transform.Translate
 */
package com.sergey.pisarev.service;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrintResolution;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

public class PrintService {
    private static final Logger LOGGER = Logger.getLogger(PrintService.class.getName());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    public static CompletableFuture<Boolean> printNode(Node node) {
        return PrintService.printNode(node, new PrintOptions());
    }

    public static CompletableFuture<Boolean> printNode(Node node, PrintOptions printOptions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return PrintService.performPrint(node, printOptions);
            }
            catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Error during printing", exception);
                return false;
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<Boolean> printImage(WritableImage writableImage, PrintOptions printOptions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return PrintService.performImagePrint(writableImage, printOptions);
            }
            catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Error during image printing", exception);
                return false;
            }
        }, EXECUTOR);
    }

    public static Printer[] getAvailablePrinters() {
        return (Printer[])Printer.getAllPrinters().toArray((Object[])new Printer[0]);
    }

    public static Printer getDefaultPrinter() {
        return Printer.getDefaultPrinter();
    }

    public static boolean isPrintingAvailable() {
        return Printer.getAllPrinters().size() > 0;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static boolean performPrint(Node node, PrintOptions printOptions) {
        boolean bl;
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            LOGGER.warning("No printer available");
            return false;
        }
        try {
            PrintService.configurePrintJob(printerJob, printOptions);
            if (printOptions.isShowPrintDialog() && !printerJob.showPrintDialog(node.getScene().getWindow())) {
                LOGGER.info("Print dialog cancelled by user");
                boolean bl2 = false;
                return bl2;
            }
            Node node2 = PrintService.prepareNodeForPrinting(node, printOptions);
            bl = printerJob.printPage(node2);
            if (bl) {
                LOGGER.info("Print job completed successfully");
                printerJob.endJob();
                boolean bl3 = true;
                return bl3;
            }
            LOGGER.warning("Print job failed");
            boolean bl4 = false;
            return bl4;
        }
        catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Error during printing operation", exception);
            bl = false;
            return bl;
        }
        finally {
            if (printerJob != null) {
                printerJob.endJob();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static boolean performImagePrint(WritableImage writableImage, PrintOptions printOptions) {
        boolean bl;
        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            LOGGER.warning("No printer available");
            return false;
        }
        try {
            PrintService.configurePrintJob(printerJob, printOptions);
            if (printOptions.isShowPrintDialog() && !printerJob.showPrintDialog(null)) {
                LOGGER.info("Print dialog cancelled by user");
                boolean bl2 = false;
                return bl2;
            }
            ImageView imageView = new ImageView((Image)writableImage);
            bl = printerJob.printPage((Node)imageView);
            if (bl) {
                LOGGER.info("Image print job completed successfully");
                printerJob.endJob();
                boolean bl3 = true;
                return bl3;
            }
            LOGGER.warning("Image print job failed");
            boolean bl4 = false;
            return bl4;
        }
        catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Error during image printing operation", exception);
            bl = false;
            return bl;
        }
        finally {
            if (printerJob != null) {
                printerJob.endJob();
            }
        }
    }

    private static void configurePrintJob(PrinterJob printerJob, PrintOptions printOptions) {
        Printer printer = printerJob.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(PrintService.getPaper(printOptions.getPaperSize()), printOptions.isLandscape() ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT, printOptions.getMarginMm(), printOptions.getMarginMm(), printOptions.getMarginMm(), printOptions.getMarginMm());
        printerJob.getJobSettings().setPageLayout(pageLayout);
        printerJob.getJobSettings().setJobName(printOptions.getJobName());
        Set<PrintResolution> set = printer.getPrinterAttributes().getSupportedPrintResolutions();
        if (set != null && !set.isEmpty()) {
            int n = printOptions.getQuality().getDpi();
            PrintResolution printResolution = (PrintResolution)set.iterator().next();
            int n2 = Math.abs(printResolution.getCrossFeedResolution() - n);
            for (PrintResolution printResolution2 : set) {
                int n3 = Math.abs(printResolution2.getCrossFeedResolution() - n);
                if (n3 >= n2) continue;
                printResolution = printResolution2;
                n2 = n3;
            }
            printerJob.getJobSettings().setPrintResolution(printResolution);
        }
    }

    private static Node prepareNodeForPrinting(Node node, PrintOptions printOptions) {
        if (printOptions.isFitToPage()) {
            return PrintService.createScaledNode(node, printOptions);
        }
        return node;
    }

    private static Node createScaledNode(Node node, PrintOptions printOptions) {
        Node node2 = node.lookup(node.getStyle());
        if (node2 == null) {
            node2 = node;
        }
        double d = node.getBoundsInParent().getWidth();
        double d2 = node.getBoundsInParent().getHeight();
        double d3 = printOptions.getPaperSize().getWidthMm();
        double d4 = printOptions.getPaperSize().getHeightMm();
        d3 /= 0.3528;
        d4 /= 0.3528;
        double d5 = printOptions.getMarginMm() / 0.3528;
        double d6 = (d3 -= 2.0 * d5) / d;
        double d7 = (d4 -= 2.0 * d5) / d2;
        double d8 = Math.min(d6, d7);
        Scale scale = new Scale(d8, d8);
        node2.getTransforms().add(scale);
        double d9 = (d3 - d * d8) / 2.0;
        double d10 = (d4 - d2 * d8) / 2.0;
        Translate translate = new Translate(d9, d10);
        node2.getTransforms().add(translate);
        return node2;
    }

    private static Paper getPaper(PaperSize paperSize) {
        switch (paperSize.ordinal()) {
            case 0: {
                return Paper.A4;
            }
            case 1: {
                return Paper.A3;
            }
            case 2: {
                return Paper.A2;
            }
            case 3: {
                return Paper.NA_LETTER;
            }
            case 4: {
                return Paper.NA_LETTER;
            }
            case 5: {
                return Paper.A4;
            }
        }
        return Paper.A4;
    }

    public static void shutdown() {
        if (EXECUTOR != null && !EXECUTOR.isShutdown()) {
            EXECUTOR.shutdown();
        }
    }

    public static class PrintOptions {
        private PrintQuality quality = PrintQuality.NORMAL;
        private PaperSize paperSize = PaperSize.A4;
        private boolean landscape = false;
        private boolean fitToPage = true;
        private double marginMm = 10.0;
        private boolean showPrintDialog = true;
        private String jobName = "CNC Model Print";

        public PrintQuality getQuality() {
            return this.quality;
        }

        public void setQuality(PrintQuality printQuality) {
            this.quality = printQuality;
        }

        public PaperSize getPaperSize() {
            return this.paperSize;
        }

        public void setPaperSize(PaperSize paperSize) {
            this.paperSize = paperSize;
        }

        public boolean isLandscape() {
            return this.landscape;
        }

        public void setLandscape(boolean bl) {
            this.landscape = bl;
        }

        public boolean isFitToPage() {
            return this.fitToPage;
        }

        public void setFitToPage(boolean bl) {
            this.fitToPage = bl;
        }

        public double getMarginMm() {
            return this.marginMm;
        }

        public void setMarginMm(double d) {
            this.marginMm = d;
        }

        public boolean isShowPrintDialog() {
            return this.showPrintDialog;
        }

        public void setShowPrintDialog(boolean bl) {
            this.showPrintDialog = bl;
        }

        public String getJobName() {
            return this.jobName;
        }

        public void setJobName(String string) {
            this.jobName = string;
        }
    }

    public static enum PaperSize {
        A4(210.0, 297.0),
        A3(297.0, 420.0),
        A2(420.0, 594.0),
        LETTER(216.0, 279.0),
        LEGAL(216.0, 356.0),
        CUSTOM(0.0, 0.0);

        private final double widthMm;
        private final double heightMm;

        private PaperSize(double d, double d2) {
            this.widthMm = d;
            this.heightMm = d2;
        }

        public double getWidthMm() {
            return this.widthMm;
        }

        public double getHeightMm() {
            return this.heightMm;
        }
    }

    public static enum PrintQuality {
        DRAFT(72),
        NORMAL(150),
        HIGH(300),
        PHOTO(600);

        private final int dpi;

        private PrintQuality(int n2) {
            this.dpi = n2;
        }

        public int getDpi() {
            return this.dpi;
        }
    }
}


