/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javafx.fxml.FXML
 *  javafx.print.Printer
 *  javafx.scene.control.Alert
 *  javafx.scene.control.Alert$AlertType
 *  javafx.scene.control.Button
 *  javafx.scene.control.CheckBox
 *  javafx.scene.control.ComboBox
 *  javafx.scene.control.Label
 *  javafx.scene.control.RadioButton
 *  javafx.scene.control.Spinner
 *  javafx.scene.control.SpinnerValueFactory
 *  javafx.scene.control.SpinnerValueFactory$DoubleSpinnerValueFactory
 *  javafx.scene.control.TextField
 *  javafx.scene.control.ToggleGroup
 *  javafx.scene.image.WritableImage
 *  javafx.stage.Stage
 *  javafx.util.StringConverter
 */
package com.sergey.pisarev.controller;

import com.sergey.pisarev.service.PrintService;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.print.Printer;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class PrintDialogController {
    private static final Logger LOGGER = Logger.getLogger(PrintDialogController.class.getName());
    @FXML
    private ComboBox<PrintService.PaperSize> paperSizeComboBox;
    @FXML
    private ComboBox<PrintService.PrintQuality> qualityComboBox;
    @FXML
    private ComboBox<String> printerComboBox;
    @FXML
    private RadioButton portraitRadioButton;
    @FXML
    private RadioButton landscapeRadioButton;
    @FXML
    private ToggleGroup orientationGroup;
    @FXML
    private Spinner<Double> marginSpinner;
    @FXML
    private CheckBox fitToPageCheckBox;
    @FXML
    private CheckBox showPrintDialogCheckBox;
    @FXML
    private TextField jobNameTextField;
    @FXML
    private Label canvasDimensionsLabel;
    @FXML
    private Label pageDimensionsLabel;
    @FXML
    private Label scaleFactorLabel;
    @FXML
    private Button printButton;
    private Stage dialogStage;
    private WritableImage imageToPrint;
    private double canvasWidth;
    private double canvasHeight;
    private boolean printCancelled = false;

    @FXML
    public void initialize() {
        this.setupControls();
        this.setupEventHandlers();
        this.updatePreview();
    }

    private void setupControls() {
        this.paperSizeComboBox.getItems().addAll(PrintService.PaperSize.values());
        this.paperSizeComboBox.setValue(PrintService.PaperSize.A4);
        this.paperSizeComboBox.setConverter(new StringConverter<PrintService.PaperSize>(){

            public String toString(PrintService.PaperSize paperSize) {
                if (paperSize == null) {
                    return "";
                }
                return paperSize.name() + " (" + paperSize.getWidthMm() + " x " + paperSize.getHeightMm() + " mm)";
            }

            public PrintService.PaperSize fromString(String string) {
                return null;
            }
        });
        this.qualityComboBox.getItems().addAll(PrintService.PrintQuality.values());
        this.qualityComboBox.setValue(PrintService.PrintQuality.NORMAL);
        this.qualityComboBox.setConverter(new StringConverter<PrintService.PrintQuality>(){

            public String toString(PrintService.PrintQuality printQuality) {
                if (printQuality == null) {
                    return "";
                }
                return printQuality.name() + " (" + printQuality.getDpi() + " DPI)";
            }

            public PrintService.PrintQuality fromString(String string) {
                return null;
            }
        });
        this.updatePrinterList();
        this.portraitRadioButton.setToggleGroup(this.orientationGroup);
        this.landscapeRadioButton.setToggleGroup(this.orientationGroup);
        this.portraitRadioButton.setSelected(true);
        SpinnerValueFactory.DoubleSpinnerValueFactory doubleSpinnerValueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 50.0, 10.0, 1.0);
        this.marginSpinner.setValueFactory((SpinnerValueFactory)doubleSpinnerValueFactory);
        this.marginSpinner.setEditable(true);
        this.fitToPageCheckBox.setSelected(true);
        this.showPrintDialogCheckBox.setSelected(true);
        this.jobNameTextField.setText("CNC Model Print");
    }

    private void setupEventHandlers() {
        this.paperSizeComboBox.setOnAction(actionEvent -> this.updatePreview());
        this.qualityComboBox.setOnAction(actionEvent -> this.updatePreview());
        this.orientationGroup.selectedToggleProperty().addListener((observableValue, toggle, toggle2) -> this.updatePreview());
        this.marginSpinner.valueProperty().addListener((observableValue, d, d2) -> this.updatePreview());
        this.fitToPageCheckBox.setOnAction(actionEvent -> this.updatePreview());
    }

    private void updatePrinterList() {
        this.printerComboBox.getItems().clear();
        if (PrintService.isPrintingAvailable()) {
            for (Printer printer2 : PrintService.getAvailablePrinters()) {
                this.printerComboBox.getItems().add(printer2.getName());
            }
            Printer printer3 = PrintService.getDefaultPrinter();
            if (printer3 != null) {
                this.printerComboBox.setValue(printer3.getName());
            } else if (!this.printerComboBox.getItems().isEmpty()) {
                this.printerComboBox.setValue(this.printerComboBox.getItems().get(0));
            }
        } else {
            this.printerComboBox.getItems().add("No printers available");
            this.printerComboBox.setValue("No printers available");
        }
    }

    private void updatePreview() {
        if (this.canvasWidth <= 0.0 || this.canvasHeight <= 0.0) {
            return;
        }
        PrintService.PaperSize paperSize = this.paperSizeComboBox.getValue();
        boolean bl = this.landscapeRadioButton.isSelected();
        double d = (Double)this.marginSpinner.getValue();
        double d2 = bl ? paperSize.getHeightMm() : paperSize.getWidthMm();
        double d3 = bl ? paperSize.getWidthMm() : paperSize.getHeightMm();
        double d4 = d2 * 96.0 / 25.4;
        double d5 = d3 * 96.0 / 25.4;
        double d6 = d * 96.0 / 25.4;
        double d7 = (d4 -= 2.0 * d6) / this.canvasWidth;
        double d8 = (d5 -= 2.0 * d6) / this.canvasHeight;
        double d9 = Math.min(d7, d8);
        if (this.fitToPageCheckBox.isSelected()) {
            d9 = Math.min(d9, 1.0);
        }
        this.canvasDimensionsLabel.setText(String.format("%.0f x %.0f pixels", this.canvasWidth, this.canvasHeight));
        this.pageDimensionsLabel.setText(String.format("%s: %.0f x %.0f mm", paperSize.name(), d2, d3));
        this.scaleFactorLabel.setText(String.format("%.2f", d9));
    }

    @FXML
    private void handlePrint() {
        if (!PrintService.isPrintingAvailable()) {
            this.showError("Print Error", "No printers available.");
            return;
        }
        PrintService.PrintOptions printOptions = new PrintService.PrintOptions();
        printOptions.setQuality(this.qualityComboBox.getValue());
        printOptions.setPaperSize(this.paperSizeComboBox.getValue());
        printOptions.setLandscape(this.landscapeRadioButton.isSelected());
        printOptions.setFitToPage(this.fitToPageCheckBox.isSelected());
        printOptions.setMarginMm((Double)this.marginSpinner.getValue());
        printOptions.setShowPrintDialog(this.showPrintDialogCheckBox.isSelected());
        printOptions.setJobName(this.jobNameTextField.getText());
        this.printButton.setDisable(true);
        this.printButton.setText("Printing...");
        CompletableFuture<Boolean> completableFuture = this.imageToPrint != null ? PrintService.printImage(this.imageToPrint, printOptions) : CompletableFuture.completedFuture(false);
        completableFuture.thenAccept(bl -> {
            if (bl.booleanValue()) {
                this.showInfo("Print Complete", "Canvas printed successfully.");
                this.dialogStage.close();
            } else {
                this.showError("Print Error", "Failed to print canvas.");
                this.printButton.setDisable(false);
                this.printButton.setText("Print");
            }
        }).exceptionally(throwable -> {
            LOGGER.log(Level.SEVERE, "Error during printing", (Throwable)throwable);
            this.showError("Print Error", "An error occurred during printing: " + throwable.getMessage());
            this.printButton.setDisable(false);
            this.printButton.setText("Print");
            return null;
        });
    }

    @FXML
    private void handleCancel() {
        this.printCancelled = true;
        this.dialogStage.close();
    }

    public void setImageToPrint(WritableImage writableImage) {
        this.imageToPrint = writableImage;
        if (writableImage != null) {
            this.canvasWidth = writableImage.getWidth();
            this.canvasHeight = writableImage.getHeight();
            this.updatePreview();
        }
    }

    public void setCanvasDimensions(double d, double d2) {
        this.canvasWidth = d;
        this.canvasHeight = d2;
        this.updatePreview();
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public boolean isPrintCancelled() {
        return this.printCancelled;
    }

    private void showInfo(String string, String string2) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(string);
        alert.setHeaderText(null);
        alert.setContentText(string2);
        alert.showAndWait();
    }

    private void showError(String string, String string2) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(string);
        alert.setHeaderText(null);
        alert.setContentText(string2);
        alert.showAndWait();
    }
}

