package javaproject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GUIMain extends Application {

  private SoC cpu;
  private Interpreter inter;

  private final List<String> program = new ArrayList<>();
  private final Set<Integer> breakpointsAddrs = new HashSet<>();
  private boolean debugPassed = false;
  private boolean runPaused = false;

  private VBox stateContainer;
  private VBox stackContainer;
  private TextArea ramArea;
  private TextArea commandArea;
  private TextArea lineNumbers;
  private TextArea logArea;
  private Label statusLabel;
  private Label hintLabel;

  private Button btnRun;
  private Button btnStep;
  private Button btnRestart;

  private File currentFile = null;
  private Stage primaryStage;
  private boolean unsavedChanges = false;

  private static final Set<String> KNOWN_OPCODES = new HashSet<>(Arrays.asList(
      "SUB", "SHF", "ADD", "SBC", "CMP", "ADC", "MIN", "MAX", "INV",
      "STR", "LLI", "LHI", "LDR", "MOV", "PSH", "POP", "PSF", "POF",
      "JMN", "OVN", "JMP", "WSP", "JMZ", "HLT", "CALL", "OVP", "RET"));

  @Override
  public void start(Stage primaryStage) {
    cpu = new SoC();
    inter = new Interpreter(cpu);

    Font monoFont = Font.font("Consolas", 13);
    String sectionStyle = "-fx-background-color: #0d1117; -fx-border-color: #30363d; -fx-border-radius: 8; -fx-background-radius: 8;";

    stateContainer = new VBox(10);
    stateContainer.setPadding(new Insets(15));
    stateContainer.setStyle("-fx-background-color: #161b22; -fx-border-radius: 6;");

    stackContainer = new VBox(5);
    stackContainer.setPadding(new Insets(10));
    stackContainer.setStyle("-fx-background-color: #161b22;");

    ScrollPane stackScroll = new ScrollPane(stackContainer);
    stackScroll.setFitToWidth(true);
    stackScroll.setPrefHeight(250);
    stackScroll.setStyle("-fx-background: #161b22; -fx-border-color: #30363d;");

    VBox stateSection = createSection("PROCESSOR STATE", stateContainer);
    VBox stackSection = createSection("STACK EXPLORER", stackScroll);

    VBox leftPanel = new VBox(15, stateSection, stackSection);
    leftPanel.setPrefWidth(420);
    leftPanel.setStyle(sectionStyle);

    ramArea = createConsoleArea("#c9d1d9", "#0d1117");
    ramArea.setPrefWidth(380);
    VBox midPanel = createSection("MEMORY MAP (RAM)", ramArea);
    midPanel.setStyle(sectionStyle);

    commandArea = createConsoleArea("#c9d1d9", "#0d1117");
    logArea = createConsoleArea("#58a6ff", "#010409");
    logArea.setPrefHeight(200);

    lineNumbers = new TextArea("1");
    lineNumbers.setEditable(false);
    lineNumbers.setFocusTraversable(false);
    lineNumbers.setPrefWidth(45);
    lineNumbers.setMinWidth(45);
    lineNumbers.setMaxWidth(45);
    lineNumbers.setFont(Font.font("Consolas", 14));
    lineNumbers.setStyle(
        "-fx-control-inner-background: #0d1117;" +
            "-fx-text-fill: #484f58;" +
            "-fx-border-color: transparent;" +
            "-fx-background-color: transparent;");

    hintLabel = new Label(
        " LLI R0 10\n" +
            " LLI R+ 5\n" +
            " ADD R0 R+\n" +
            " *CMP R0 R+\n" +
            " HLT");
    hintLabel.setFont(monoFont);
    hintLabel.setStyle("-fx-text-fill: #484f58; -fx-padding: 10;");
    hintLabel.setMouseTransparent(true);

    StackPane editorStack = new StackPane(commandArea, hintLabel);
    StackPane.setAlignment(hintLabel, Pos.TOP_LEFT);
    HBox.setHgrow(editorStack, Priority.ALWAYS);

    HBox editorWithLines = new HBox(lineNumbers, editorStack);
    editorWithLines.setStyle("-fx-background-color: #0d1117;");

    VBox editorSection = createSection("SOURCE CODE EDITOR", editorWithLines);
    VBox logSection = createSection("SYSTEM EVENT LOG", logArea);

    VBox rightPanel = new VBox(15, editorSection, logSection);
    VBox.setVgrow(editorSection, Priority.ALWAYS);
    rightPanel.setStyle(sectionStyle);

    HBox mainLayout = new HBox(15, leftPanel, midPanel, rightPanel);
    HBox.setHgrow(rightPanel, Priority.ALWAYS);
    mainLayout.setPadding(new Insets(15));
    mainLayout.setStyle("-fx-background-color: #010409;");

    BorderPane root = new BorderPane();
    root.setTop(createToolBar(primaryStage));
    root.setCenter(mainLayout);

    setupListeners();
    updateUI();

    this.primaryStage = primaryStage;

    Scene scene = new Scene(root, 1500, 800);

    String scrollbarCss = ".scroll-bar {" +
        "    -fx-background-color: #0d1117;" +
        "}" +
        ".scroll-bar .thumb {" +
        "    -fx-background-color: #30363d;" +
        "    -fx-background-radius: 6;" +
        "    -fx-border-color: transparent;" +
        "}" +
        ".scroll-bar .thumb:hover {" +
        "    -fx-background-color: #8b949e;" +
        "}" +
        ".scroll-bar .thumb:pressed {" +
        "    -fx-background-color: #58a6ff;" +
        "}" +
        ".scroll-bar .track {" +
        "    -fx-background-color: #010409;" +
        "    -fx-background-radius: 6;" +
        "    -fx-border-color: #21262d;" +
        "}" +
        ".scroll-bar .increment-button, .scroll-bar .decrement-button {" +
        "    -fx-background-color: transparent;" +
        "    -fx-padding: 0;" +
        "}" +
        ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow {" +
        "    -fx-shape: \"\";" +
        "    -fx-padding: 0;" +
        "}";
    scene.getStylesheets().add("data:text/css," + scrollbarCss.replaceAll(" ", "%20"));

    scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.isControlDown() && event.getCode() == KeyCode.O) {
        openFile();
        event.consume();
      } else if (event.isControlDown() && event.getCode() == KeyCode.S) {
        if (event.isShiftDown())
          saveAs();
        else
          save();
        event.consume();
      }
    });

    primaryStage.setTitle("Ternary SoC Emulator");
    primaryStage.setScene(scene);

    primaryStage.setOnCloseRequest(event -> {
      if (unsavedChanges) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes.");
        alert.setContentText("Do you want to save before closing?");
        alert.initOwner(primaryStage);

        javafx.scene.control.ButtonType btnSaveClose = new javafx.scene.control.ButtonType("Save & Close");
        javafx.scene.control.ButtonType btnDiscard = new javafx.scene.control.ButtonType("Discard");
        javafx.scene.control.ButtonType btnCancel = new javafx.scene.control.ButtonType("Cancel",
            javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnSaveClose, btnDiscard, btnCancel);

        alert.showAndWait().ifPresent(result -> {
          if (result == btnSaveClose)
            save();
          else if (result == btnCancel)
            event.consume();
        });
      }
    });

    primaryStage.show();
  }

  private void resetEmulator() {
    cpu = new SoC();
    inter = new Interpreter(cpu);
    program.clear();
    breakpointsAddrs.clear();
    runPaused = false;
    debugPassed = false;

    logArea.clear();
    logArea.appendText("[SYSTEM] Hardware reset successful. Memory cleared.\n");

    statusLabel.setText("STATUS: SYSTEM RESET");
    statusLabel.setStyle("-fx-text-fill: #8b949e;");

    btnRun.setDisable(true);
    btnStep.setDisable(true);

    updateUI();
  }

  private String toBalancedTernary(int value, int length) {
    if (value == 0)
      return padTernary("0", length);

    StringBuilder sb = new StringBuilder();
    int temp = value;

    while (temp != 0) {
      int rem = temp % 3;
      temp = temp / 3;

      if (rem < 0) {
        rem += 3;
        temp -= 1;
      }
      if (rem == 2) {
        rem = -1;
        temp += 1;
      }

      if (rem == 0)
        sb.append("0");
      else if (rem == 1)
        sb.append("+");
      else
        sb.append("-");
    }

    return padTernary(sb.reverse().toString(), length);
  }

  private String padTernary(String ternaryStr, int requiredLength) {
    if (ternaryStr.length() >= requiredLength)
      return ternaryStr;
    StringBuilder pad = new StringBuilder();
    for (int i = 0; i < requiredLength - ternaryStr.length(); i++)
      pad.append("0");
    return pad.toString() + ternaryStr;
  }

  private HBox createTernaryGraphicBlock(String ternaryString) {
    HBox box = new HBox(3);
    box.setAlignment(Pos.CENTER_LEFT);

    for (char c : ternaryString.toCharArray()) {
      Rectangle rect = new Rectangle(14, 18);
      rect.setArcWidth(4);
      rect.setArcHeight(4);

      if (c == '+')
        rect.setFill(Color.web("#238636"));
      else if (c == '-')
        rect.setFill(Color.web("#da3633"));
      else
        rect.setFill(Color.web("#30363d"));

      rect.setStroke(Color.web("#010409"));
      rect.setStrokeWidth(1);
      box.getChildren().add(rect);
    }
    return box;
  }

  private VBox createRegisterDisplayRow(String regName, int value, int tritLength, String titleColor) {
    Label lblName = new Label(String.format("%-4s : %d", regName, value));
    lblName.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
    lblName.setStyle("-fx-text-fill: " + titleColor + ";");

    HBox graphicBlock = createTernaryGraphicBlock(toBalancedTernary(value, tritLength));

    VBox container = new VBox(4, lblName, graphicBlock);
    container.setPadding(new Insets(0, 0, 5, 0));
    return container;
  }

  private void updateUI() {
    stateContainer.getChildren().clear();

    stateContainer.getChildren().add(createRegisterDisplayRow("PC", inter.getPC(), 12, "#58a6ff"));
    stateContainer.getChildren().add(createRegisterDisplayRow("SP", inter.getSP(), 12, "#58a6ff"));

    Separator sep1 = new Separator();
    sep1.setStyle("-fx-background-color: #30363d; -fx-padding: 5 0 5 0;");
    stateContainer.getChildren().add(sep1);

    stateContainer.getChildren().add(createRegisterDisplayRow("R-", inter.getRegister(-1), 12, "#c9d1d9"));
    stateContainer.getChildren().add(createRegisterDisplayRow("R0", inter.getRegister(0), 12, "#c9d1d9"));
    stateContainer.getChildren().add(createRegisterDisplayRow("R+", inter.getRegister(1), 12, "#c9d1d9"));

    Separator sep2 = new Separator();
    sep2.setStyle("-fx-background-color: #30363d; -fx-padding: 5 0 5 0;");
    stateContainer.getChildren().add(sep2);

    Label flagsLabel = new Label(String.format("FLAGS >> STS: %d  |  OVR: %d  |  HLT: %b",
        inter.getStatus(), inter.getOvr(), inter.getHLT()));
    flagsLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
    flagsLabel.setStyle("-fx-text-fill: #d2a8ff;");
    stateContainer.getChildren().add(flagsLabel);

    stackContainer.getChildren().clear();
    int currentSP = inter.getSP();

    for (int i = 0; i < 8; i++) {
      int targetAddress = currentSP + i;
      if (targetAddress > 265720) {
        break;
      }

      int memValue = 0;
      try {
        memValue = cpu.getRamValue(targetAddress);
      } catch (Exception ignored) {
      }

      Label addrLabel = new Label(String.format("[0x%d]", targetAddress));
      addrLabel.setFont(Font.font("Consolas", 12));
      addrLabel.setStyle("-fx-text-fill: #8b949e;");
      addrLabel.setPrefWidth(90);

      HBox graphicData = createTernaryGraphicBlock(toBalancedTernary(memValue, 6));

      HBox stackRow = new HBox(10, addrLabel, graphicData);
      stackRow.setAlignment(Pos.CENTER_LEFT);
      stackRow.setStyle("-fx-padding: 4; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");

      stackContainer.getChildren().add(stackRow);
    }

    StringBuilder ramBuilder = new StringBuilder();
    ramBuilder.append(" ADDRESS  | DECIMAL  | TERNARY (6 TRITS) \n");
    ramBuilder.append("──────────┼──────────┼───────────────────\n");

    int currentPC = inter.getPC();
    for (int i = -6; i <= 8; i++) {
      int ramAddress = currentPC + i;
      if (ramAddress < -265720 || ramAddress > 265720)
        continue;

      int value = 0;
      try {
        value = cpu.getRamValue(ramAddress);
      } catch (Exception ignored) {
      }

      String ternaryRep = toBalancedTernary(value, 6);
      String pointerStr = (ramAddress == currentPC) ? " << PC" : "";

      if (breakpointsAddrs.contains(ramAddress))
        pointerStr += " [B]";

      ramBuilder.append(String.format(" %8d | %8d | [%s]%s\n", ramAddress, value, ternaryRep, pointerStr));
    }
    ramArea.setText(ramBuilder.toString());
  }

  private void runSyntaxCheck() {
    logArea.clear();
    program.clear();
    breakpointsAddrs.clear();

    logArea.appendText("=== TERNARY SMART ASSEMBLER ===\n");
    String[] codeLines = commandArea.getText().split("\\n");

    int compileErrors = 0;
    int instructionAddress = SoC.START_ADDRESS;

    for (int i = 0; i < codeLines.length; i++) {
      String rawLine = codeLines[i].trim();

      if (rawLine.isEmpty() || rawLine.startsWith("//") || rawLine.startsWith("#"))
        continue;

      boolean isBreakpoint = rawLine.startsWith("*");
      String commandToParse = isBreakpoint ? rawLine.substring(1).trim() : rawLine;
      String originalCommandToSave = commandToParse;

      if (commandToParse.startsWith("[")) {
        int closeIdx = commandToParse.indexOf("]");
        if (closeIdx != -1) {
          try {
            instructionAddress = Integer.parseInt(commandToParse.substring(1, closeIdx).trim());
            commandToParse = commandToParse.substring(closeIdx + 1).trim();

            if (commandToParse.isEmpty()) {
              program.add(originalCommandToSave);
              if (isBreakpoint)
                breakpointsAddrs.add(instructionAddress);
              logArea.appendText(String.format(" ✓ Origin set to RAM address: %d\n", instructionAddress));
              continue;
            }
          } catch (NumberFormatException e) {
            logArea.appendText(String.format("[ERROR L%d] Invalid address format\n", i + 1));
            compileErrors++;
            continue;
          }
        }
      }

      String optimizedCmd = Interpreter.optimize(commandToParse);
      String[] parts = optimizedCmd.split("\\s+");

      if (parts.length < 1)
        continue;

      String opcode = parts[0].toUpperCase();

      if (!KNOWN_OPCODES.contains(opcode)) {
        logArea.appendText(String.format("[ERROR L%d] Unknown command: %s\n", i + 1, opcode));
        compileErrors++;
        continue;
      }

      try {
        Interpreter.validateSyntax(parts);
      } catch (IllegalArgumentException e) {
        logArea.appendText(String.format("[ERROR L%d] %s\n", i + 1, e.getMessage()));
        compileErrors++;
        continue;
      }

      program.add(originalCommandToSave);
      if (isBreakpoint)
        breakpointsAddrs.add(instructionAddress);

      logArea.appendText(String.format(" ✓ [%8d] %s\n", instructionAddress, optimizedCmd));
      instructionAddress += 2;
    }

    if (compileErrors == 0 && !program.isEmpty()) {
      debugPassed = true;
      btnRun.setDisable(false);
      btnStep.setDisable(false);
      statusLabel.setText("STATUS: BUILD SUCCESS");
      statusLabel.setStyle("-fx-text-fill: #2ea043;");
      logArea.appendText("\n[SUCCESS] Ready to flash to RAM.\n");
    } else {
      debugPassed = false;
      btnRun.setDisable(true);
      btnStep.setDisable(true);
      statusLabel.setText("STATUS: COMPILATION ERRORS");
      statusLabel.setStyle("-fx-text-fill: #f85149;");
    }
  }

  private void runMachine(boolean stepMode) {
    if (!debugPassed)
      return;

    if (!runPaused) {
      cpu = new SoC();
      inter = new Interpreter(cpu);
      try {
        inter.assembleAndLoad(program);
        logArea.appendText("\n[SYSTEM] ROM successfully copied to RAM. Clock started...\n");
      } catch (Exception e) {
        logArea.appendText("[FATAL ERROR] Loader failed: " + e.getMessage() + "\n");
        return;
      }
    }

    statusLabel.setText(stepMode ? "STATUS: STEPPING" : "STATUS: RUNNING");

    int maxExecutionLimit = stepMode ? 1 : 1500;
    int stepsExecuted = 0;
    boolean hitBreakpoint = false;

    while (!inter.getHLT() && stepsExecuted < maxExecutionLimit) {

      if (inter.getSP() <= SoC.START_ADDRESS) {
        logArea.appendText("\n[KERNEL PANIC] STACK OVERFLOW! Stack pointer crossed RAM floor.\n");
        break;
      }
      if (inter.getPC() > 265720 || inter.getPC() < -265720) {
        logArea.appendText("\n[KERNEL PANIC] PC OUT OF BOUNDS! Attempted to read invalid memory.\n");
        break;
      }

      if (!stepMode && stepsExecuted > 0 && breakpointsAddrs.contains(inter.getPC())) {
        logArea.appendText(String.format(" ◉ BREAKPOINT HIT (RAM Address: %d)\n", inter.getPC()));
        hitBreakpoint = true;
        runPaused = true;
        break;
      }

      try {
        cpu.step();
        stepsExecuted++;
      } catch (Exception ex) {
        logArea.appendText("\n[HARDWARE FAULT] " + ex.getMessage() + "\n");
        break;
      }
    }

    updateUI();

    if (inter.getHLT()) {
      logArea.appendText("\n[HLT] Processor executed halt instruction safely.\n");
      statusLabel.setText("STATUS: HALTED");
      runPaused = false;
    } else if (hitBreakpoint || stepMode) {
      statusLabel.setText("STATUS: PAUSED");
      runPaused = true;
    } else {
      logArea.appendText("\n[TIMEOUT] Safety limit reached. Cycle interrupted (1500 operations max).\n");
      runPaused = false;
    }

    logArea.setScrollTop(Double.MAX_VALUE);
  }

  private void showHelpModal(Stage ownerStage) {
    Stage modal = new Stage();
    modal.initModality(Modality.WINDOW_MODAL);
    modal.initOwner(ownerStage);
    modal.setTitle("System Manual - New Load/Store Architecture");

    TabPane tabs = new TabPane();
    tabs.setStyle("-fx-background-color: #0d1117;");

    Tab tabIntro = new Tab("Quick Start");
    tabIntro.setClosable(false);
    TextArea txtIntro = createConsoleArea("#2ea043", "#010409");
    txtIntro.setText(
        "=== TERNARY SoC EMULATOR - BEGINNER'S GUIDE ===\n\n" +
            "Welcome to the unique balanced ternary system emulator!\n" +
            "Instead of bits (0 and 1), this architecture operates on trits (-, 0, +).\n\n" +
            "1. VISUAL DICTIONARY (COLORED BLOCKS):\n" +
            "   - Green block  : Positive state (+1)\n" +
            "   - Red block    : Negative state (-1)\n" +
            "   - Gray block   : Neutral state (0)\n\n" +
            "2. KEY HARDWARE REGISTERS:\n" +
            "   - R0 : Main ACCUMULATOR. Default destination for operation results.\n" +
            "   - R-, R+ : Auxiliary registers for storing additional values.\n" +
            "   - PC : Program Counter. Points to the current instruction address in RAM.\n" +
            "   - SP : Stack Pointer. Controls the current top of the stack memory.\n\n" +
            "3. WHY ARE RAM ADDRESSES NEGATIVE?\n" +
            "   In a balanced system, the memory space stretches from -265720 to +265720.\n" +
            "   Your program always starts at the maximum negative address (-265720).\n\n" +
            "4. PANEL WIDTH DIFFERENCE (12 VS 6 BLOCKS):\n" +
            "   Registers are 12 trits wide, whereas a physical RAM cell holds 6 trits.\n" +
            "   Writing a 12-trit value automatically occupies two consecutive RAM cells.\n\n" +
            "5. HOW TO RUN YOUR FIRST PROGRAM:\n" +
            "   Step 1: Go to the 'Examples' tab and copy the provided code.\n" +
            "   Step 2: Close this window and paste the code into the 'SOURCE CODE EDITOR'.\n" +
            "   Step 3: Click the '⚙ SYNTAX CHECK' button on the top bar (must turn green).\n" +
            "   Step 4: Click '▶ RUN FULL' (full execution) or '▶| STEP' (step-by-step).");
    tabIntro.setContent(txtIntro);

    Tab tabArchitecture = new Tab("Architecture");
    tabArchitecture.setClosable(false);
    TextArea txtArch = createConsoleArea("#c9d1d9", "#010409");
    txtArch.setText(
        "=== BALANCED TERNARY (LOAD/STORE ARCH) ===\n\n" +
            "1. MEMORY & REGISTERS:\n" +
            "   A machine word and ALL REGISTERS consist of 12 trits.\n" +
            "   RAM acts as rows of 6 trits. Hardware reads/writes 2 RAM cells\n" +
            "   per cycle to manipulate a 12-trit value.\n\n" +
            "2. HARDWARE REGISTERS:\n" +
            "   [ R- ] , [ R0 ] , [ R+ ] \n\n" +
            "3. INSTRUCTION FORMAT:\n" +
            "   Cycle pulls TWO 6-trit words per Fetch (PC += 2):\n" +
            "   Word 1: Ignored(1) | Opcode(3) | Rd(1) | Rs(1)\n" +
            "   Word 2: Immediate Data (6 trits)");
    tabArchitecture.setContent(txtArch);

    Tab tabInstructions = new Tab("Instructions");
    tabInstructions.setClosable(false);
    TextArea txtInst = createConsoleArea("#58a6ff", "#010409");
    txtInst.setText(
        "=== COMPLETE INSTRUCTION SET ===\n\n" +
            "IMPORTANT ARCHITECTURE INFORMATION:\n" +
            "RAM ORIGIN  : Use '[address]' to set the mapping location in memory.\n" +
            "BREAKPOINTS : Prefix an instruction with '*' to set a breakpoint.\n\n"+

            "1. ARITHMETIC & LOGIC (Sets Status & Overflow flags):\n" +
            "   ADD Rd Rs   : Rd = Rd + Rs\n" +
            "   SUB Rd Rs   : Rd = Rd - Rs\n" +
            "   ADC Rd Rs   : Rd = Rd + Rs + Overflow\n" +
            "   SBC Rd Rs   : Rd = Rd - Rs + Overflow\n" +
            "   MIN Rd Rs   : Rd = min(Rd, Rs)\n" +
            "   MAX Rd Rs   : Rd = max(Rd, Rs)\n" +
            "   CMP Rd Rs   : Status = Rd - Rs (Rd remains unchanged)\n" +
            "   SHF Rd Rs   : Rd = Rd shifted by Rs\n" +
            "   INV Rd      : Rd = -Rd (Tritwise inversion)\n\n" +
            "2. MEMORY & REGISTERS:\n" +
            "   LLI Rd IMM  : Loads IMM into the lower 6 trits of Rd\n" +
            "   LHI Rd IMM  : Loads IMM into the higher 6 trits of Rd\n" +
            "   LDR Rd Rs   : Rd = RAM[Rs] (Loads 12 trits from memory)\n" +
            "   STR Rd Rs   : RAM[Rs] = Rd (Stores 12 trits to memory)\n" +
            "   MOV Rd Rs   : Rd = Rs\n\n" +
            "3. STACK OPERATIONS:\n" +
            "   PSH Rd      : Push Rd to Stack (RAM[--SP] = Rd)\n" +
            "   POP Rd      : Pop from Stack to Rd (Rd = RAM[SP++])\n" +
            "   PSF         : Push Flags (Status & Overflow) to Stack\n" +
            "   POF         : Pop Flags from Stack\n" +
            "   WSP Rd      : Write Stack Pointer (SP = Rd)\n\n" +
            "4. CONTROL FLOW:\n" +
            "   JMP Rd      : Jump to Rd if Status > 0\n" +
            "   JMN Rd      : Jump to Rd if Status < 0\n" +
            "   JMZ Rd      : Jump to Rd if Status == 0\n" +
            "   OVP Rd      : Jump to Rd if Overflow > 0\n" +
            "   OVN Rd      : Jump to Rd if Overflow < 0\n" +
            "   CALL Rd     : Push PC to Stack, then Jump to Rd\n" +
            "   RET         : Return from Call (Pop PC from Stack)\n" +
            "   HLT         : Halt execution");
    tabInstructions.setContent(txtInst);

    Tab tabExamples = new Tab("Examples");
    tabExamples.setClosable(false);
    TextArea txtEx = createConsoleArea("#2ea043", "#010409");
    txtEx.setText(
        "=== CODE EXAMPLES & FEATURES ===\n\n" +
            "1. ABSOLUTE ADDRESSING (Origin Mapping):\n" +
            "   [100]\n" +
            "   LLI R0 15\n" +
            "   ADD R0 R+\n\n" +
            "2. BASIC MATH (15 + 5):\n" +
            "   LLI R0 15\n" +
            "   LLI R+ 5\n" +
            "   ADD R0 R+\n" +
            "   HLT\n\n" +
            "3. MEMORY LOAD/STORE:\n" +
            "   [-250000]\n" +
            "   LLI R- 250\n" +
            "   STR R0 R-\n" +
            "   LDR R+ R-\n" +
            "   HLT\n\n" +
            "4. FIBBONACCI: \n" +
            "   LLI R- 0 \n" +
            "   LLI R0 1\n" +
            "   LLI R+ 8\n" +
            "   PSH R+\n\n" +
            "   [-265712] PSH R0\n" +
            "   ADD R0 R-\n" +
            "   POP R-\n" +
            "   POP R+\n" +
            "   PSH R0\n" +
            "   LLI R0 1\n" +
            "   SUB R+ R0\n" +
            "   POP R0\n\n" +
            "   PSH R+\n" +
            "   LLI R+ -265712\n" +
            "   JMP R+\n\n" +
            "   POP R+\n" +
            "   HLT");
    tabExamples.setContent(txtEx);

    tabs.getTabs().addAll(tabIntro, tabArchitecture, tabInstructions, tabExamples);

    Scene modalScene = new Scene(tabs, 650, 450);
    modal.setScene(modalScene);
    modal.show();
  }

  private VBox createSection(String title, Node content) {
    Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-weight: bold; -fx-font-size: 11px;");

    VBox box = new VBox(8, titleLabel, content);
    box.setPadding(new Insets(10));
    VBox.setVgrow(content, Priority.ALWAYS);
    return box;
  }

  private TextArea createConsoleArea(String textColor, String backgroundColor) {
    TextArea textArea = new TextArea();
    textArea.setFont(Font.font("Consolas", 14));
    textArea.setStyle(
        "-fx-control-inner-background: " + backgroundColor + ";" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-border-color: transparent;" +
            "-fx-highlight-fill: #30363d;" +
            "-fx-highlight-text-fill: #ffffff;");
    return textArea;
  }

  private Button makeActionButton(String text, String colorHex) {
    Button button = new Button(text);
    button.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
    button.setStyle(
        "-fx-background-color: #21262d;" +
            "-fx-text-fill: " + colorHex + ";" +
            "-fx-border-color: #30363d;" +
            "-fx-cursor: hand;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;");

    button.setOnMouseEntered(e -> button.setStyle(
        "-fx-background-color: #30363d;" +
            "-fx-text-fill: " + colorHex + ";" +
            "-fx-border-color: #8b949e;" +
            "-fx-cursor: hand;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"));

    button.setOnMouseExited(e -> button.setStyle(
        "-fx-background-color: #21262d;" +
            "-fx-text-fill: " + colorHex + ";" +
            "-fx-border-color: #30363d;" +
            "-fx-cursor: hand;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"));

    return button;
  }

  private HBox createToolBar(Stage stage) {
    btnRestart = makeActionButton("↺ RESTART", "#f85149");
    btnRestart.setOnAction(e -> resetEmulator());

    Button btnHelp = makeActionButton("? MANUAL", "#d2a8ff");
    btnHelp.setOnAction(e -> showHelpModal(stage));

    Button btnOpen = makeActionButton("📂 OPEN", "#8b949e");
    btnOpen.setOnAction(e -> openFile());

    Button btnSave = makeActionButton("💾 SAVE", "#8b949e");
    btnSave.setOnAction(e -> save());
    btnSave.setMaxHeight(Double.MAX_VALUE);

    Button btnSaveArrow = makeActionButton("▾", "#8b949e");
    btnSaveArrow.setMaxHeight(Double.MAX_VALUE);
    btnSaveArrow.setMinWidth(24);
    btnSaveArrow.setPadding(new Insets(0, 8, 0, 8));

    javafx.scene.control.ContextMenu saveMenu = new javafx.scene.control.ContextMenu();
    javafx.scene.control.MenuItem itemSaveAs = new javafx.scene.control.MenuItem("Save As...");
    itemSaveAs.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px;");
    itemSaveAs.setOnAction(e -> saveAs());
    saveMenu.getItems().add(itemSaveAs);

    btnSaveArrow.setOnAction(e -> saveMenu.show(btnSaveArrow, javafx.geometry.Side.BOTTOM, 0, 0));

    String saveBase = "-fx-background-color: #21262d; -fx-text-fill: #8b949e;-fx-border-color: #30363d; -fx-cursor: hand;";
    String saveHover = "-fx-background-color: #30363d; -fx-text-fill: #8b949e;-fx-border-color: #8b949e; -fx-cursor: hand;";

    btnSave.setStyle(saveBase
        + "-fx-border-radius: 4 0 0 4; -fx-background-radius: 4 0 0 4;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;");
    btnSave.setOnMouseEntered(e -> btnSave.setStyle(saveHover
        + "-fx-border-radius: 4 0 0 4; -fx-background-radius: 4 0 0 4;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;"));
    btnSave.setOnMouseExited(e -> btnSave.setStyle(saveBase
        + "-fx-border-radius: 4 0 0 4; -fx-background-radius: 4 0 0 4;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;"));

    btnSaveArrow.setStyle(saveBase
        + "-fx-border-radius: 0 4 4 0; -fx-background-radius: 0 4 4 0;-fx-border-width: 1 1 1 0;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;");
    btnSaveArrow.setOnMouseEntered(e -> btnSaveArrow.setStyle(saveHover
        + "-fx-border-radius: 0 4 4 0; -fx-background-radius: 0 4 4 0;-fx-border-width: 1 1 1 0;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;"));
    btnSaveArrow.setOnMouseExited(e -> btnSaveArrow.setStyle(saveBase
        + "-fx-border-radius: 0 4 4 0; -fx-background-radius: 0 4 4 0;-fx-border-width: 1 1 1 0;-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: 12px;"));

    HBox splitSaveBtn = new HBox(btnSave, btnSaveArrow);
    splitSaveBtn.setAlignment(Pos.CENTER);
    HBox.setHgrow(btnSave, Priority.NEVER);

    Button btnDebug = makeActionButton("⚙ SYNTAX CHECK", "#58a6ff");
    btnDebug.setOnAction(e -> runSyntaxCheck());

    btnStep = makeActionButton("▶| STEP", "#d29922");
    btnStep.setDisable(true);
    btnStep.setOnAction(e -> runMachine(true));

    btnRun = makeActionButton("▶ RUN FULL", "#2ea043");
    btnRun.setDisable(true);
    btnRun.setOnAction(e -> runMachine(false));

    statusLabel = new Label("STATUS: WAITING FOR CODE");
    statusLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
    statusLabel.setStyle("-fx-text-fill: #8b949e;");

    Region spacerLeft = new Region();
    HBox.setHgrow(spacerLeft, Priority.ALWAYS);

    Region spacerRight = new Region();
    HBox.setHgrow(spacerRight, Priority.ALWAYS);

    HBox toolbar = new HBox(15, btnRestart, btnHelp, btnOpen, splitSaveBtn, spacerLeft, statusLabel, spacerRight,
        btnDebug, btnStep, btnRun);
    toolbar.setAlignment(Pos.CENTER);
    toolbar.setPadding(new Insets(12, 20, 12, 20));
    toolbar.setStyle("-fx-background-color: #010409; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");

    return toolbar;
  }

  private void openFile() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Open Program");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
    if (currentFile != null)
      fc.setInitialDirectory(currentFile.getParentFile());

    File file = fc.showOpenDialog(primaryStage);
    if (file != null) {
      try {
        String content = java.nio.file.Files.readString(file.toPath());
        commandArea.setText(content);
        currentFile = file;
        unsavedChanges = false;
        primaryStage.setTitle("Ternary SoC Emulator — " + file.getName());
        logArea.appendText("[SYSTEM] File loaded: " + file.getName() + "\n");
      } catch (Exception e) {
        logArea.appendText("[ERROR] Could not open file: " + e.getMessage() + "\n");
      }
    }
  }

  private void save() {
    if (currentFile == null)
      saveAs();
    else
      saveToFile(currentFile);
  }

  private void saveAs() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Save Program");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
    if (currentFile != null) {
      fc.setInitialDirectory(currentFile.getParentFile());
      fc.setInitialFileName(currentFile.getName());
    }
    File file = fc.showSaveDialog(primaryStage);
    if (file != null) {
      currentFile = file;
      saveToFile(file);
    }
  }

  private void saveToFile(File file) {
    try {
      java.nio.file.Files.writeString(file.toPath(), commandArea.getText());
      unsavedChanges = false;
      primaryStage.setTitle("Ternary SoC Emulator — " + file.getName());
      logArea.appendText("[SYSTEM] File saved: " + file.getName() + "\n");
    } catch (Exception e) {
      logArea.appendText("[ERROR] Could not save file: " + e.getMessage() + "\n");
    }
  }

  private void setupListeners() {
    commandArea.textProperty().addListener((observable, oldValue, newValue) -> {
      hintLabel.setVisible(newValue == null || newValue.isEmpty());

      if (newValue != null) {
        int lines = newValue.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
          sb.append(i);
          if (i < lines)
            sb.append("\n");
        }
        lineNumbers.setText(sb.toString());
      }

      if (!unsavedChanges) {
        unsavedChanges = true;
        String current = primaryStage.getTitle();
        if (!current.endsWith(" *"))
          primaryStage.setTitle(current + " *");
      }

      if (debugPassed) {
        debugPassed = false;
        btnRun.setDisable(true);
        btnStep.setDisable(true);
        statusLabel.setText("STATUS: CODE MODIFIED");
        statusLabel.setStyle("-fx-text-fill: #d29922;");
      }
      runPaused = false;
    });

    commandArea.scrollTopProperty()
        .addListener((obs, oldVal, newVal) -> lineNumbers.setScrollTop(newVal.doubleValue()));
  }

  public static void main(String[] args) {
    launch(args);
  }
}
