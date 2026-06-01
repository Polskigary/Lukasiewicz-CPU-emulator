package javaproject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Interpreter {
  private SoC cpu;

  private static final Set<String> NO_ARG = new HashSet<>(Arrays.asList("HLT", "PSF", "POF", "RET"));
  private static final Set<String> RD_ONLY = new HashSet<>(
      Arrays.asList("INV", "PSH", "POP", "JMN", "OVN", "JMP", "WSP", "CALL", "OVP", "JMZ"));
  private static final Set<String> RD_RS = new HashSet<>(
      Arrays.asList("SUB", "SHF", "ADD", "SBC", "CMP", "ADC", "MIN", "MAX", "LDR", "STR", "MOV"));
  private static final Set<String> RD_IMM = new HashSet<>(Arrays.asList("LLI", "LHI"));

  public Interpreter(SoC cpu) {
    this.cpu = cpu;
  }

  public static void validateSyntax(String[] parts) {
    String opcodeStr = parts[0].toUpperCase();

    if (RD_ONLY.contains(opcodeStr) && parts.length != 2) {
      throw new IllegalArgumentException("Syntax error: " + opcodeStr + " requires exactly 1 argument (Rd).");
    }
    if (RD_RS.contains(opcodeStr) && parts.length != 3) {
      throw new IllegalArgumentException("Syntax error: " + opcodeStr + " requires exactly 2 arguments (Rd, Rs).");
    }
    if (RD_IMM.contains(opcodeStr) && parts.length != 3) {
      throw new IllegalArgumentException("Syntax error: " + opcodeStr + " requires exactly 2 arguments (Rd, IMM).");
    }
    if (NO_ARG.contains(opcodeStr) && parts.length != 1) {
      throw new IllegalArgumentException("Syntax error: " + opcodeStr + " does not take any extra arguments.");
    }
  }

  public void assembleAndLoad(List<String> programLines) {
    int currentRamAddress = SoC.START_ADDRESS;
    cpu.setPC(SoC.START_ADDRESS);

    for (String line : programLines) {
      String optimized = optimize(line);
      if (optimized.isEmpty() || optimized.startsWith("#"))
        continue;

      if (optimized.startsWith("*")) {
        optimized = optimized.substring(1).trim();
      }

      if (optimized.startsWith("[")) {
        int closeBracket = optimized.indexOf("]");
        if (closeBracket != -1) {
          currentRamAddress = Integer.parseInt(optimized.substring(1, closeBracket).trim());
          optimized = optimized.substring(closeBracket + 1).trim();
          if (optimized.isEmpty())
            continue;
        }
      }

      String[] parts = optimized.split("\\s+");
      if (parts.length == 0)
        continue;

      String opcodeStr = parts[0].toUpperCase();
      int rd = 0, rs = 0, imm = 0;

      if (RD_ONLY.contains(opcodeStr)) {
        rd = parseRegister(parts[1]);
      } else if (RD_RS.contains(opcodeStr)) {
        rd = parseRegister(parts[1]);
        rs = parseRegister(parts[2]);
      } else if (RD_IMM.contains(opcodeStr)) {
        rd = parseRegister(parts[1]);
        imm = Integer.parseInt(parts[2]);
      } else if (!NO_ARG.contains(opcodeStr)) {
        throw new IllegalArgumentException("Unknown instruction signature: " + opcodeStr);
      }

      Integer opcodeId = SoC.getOpcodeId(opcodeStr);
      if (opcodeId == null) {
        throw new IllegalArgumentException("Unrecognized instruction: " + opcodeStr);
      }

      int commandWord = (opcodeId * 9) + (rd * 3) + rs;

      cpu.injectRAM(currentRamAddress, commandWord);
      cpu.injectRAM(currentRamAddress + 1, imm);

      currentRamAddress += 2;
    }
  }

  public static String optimize(String line) {
    if (line == null || line.isBlank())
      return "";

    int commentIdx = line.indexOf("//");
    if (commentIdx != -1) {
      line = line.substring(0, commentIdx);
    }

    return line.trim().replaceAll("\\s+", " ");
  }

  public int parseRegister(String reg) {
    switch (reg.toUpperCase()) {
      case "R-":
        return -1;
      case "R0":
        return 0;
      case "R+":
        return 1;
      default:
        throw new IllegalArgumentException("Invalid register: " + reg);
    }
  }

  public int getPC() {
    return cpu.getPC();
  }

  public int getSP() {
    return cpu.getSP();
  }

  public int getRegister(int index) {
    return cpu.getReg(index);
  }

  public int getStatus() {
    return cpu.getStatus();
  }

  public int getOvr() {
    return cpu.getOvr();
  }

  public boolean getHLT() {
    return cpu.HLT;
  }
}
