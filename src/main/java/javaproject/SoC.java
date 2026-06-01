package javaproject;

import java.util.HashMap;
import java.util.Map;

public class SoC {
  public static final int MAX_VALUE = 265720;
  public static final int START_ADDRESS = -MAX_VALUE;

  private int _programcounter = START_ADDRESS;
  private int _stackpointer = MAX_VALUE;
  private int[] _registers = new int[3];

  private int _ramSize = (2 * MAX_VALUE) + 1;
  private int[] _ram = new int[_ramSize];

  private int _status = 0;
  private int _overflow = 0;
  public boolean HLT = false;

  private static final Map<Integer, String> OPCODE_MAP = new HashMap<>();
  private static final Map<String, Integer> STRING_TO_OPCODE = new HashMap<>();

  static {
    String[] ops = {
        "SUB", "SHF", "ADD", "SBC", "CMP", "ADC", "MIN", "INV", "MAX",
        "LDR", "LLI", "LHI", "STR", "MOV", "PSH", "POP", "PSF", "POF",
        "JMN", "OVN", "JMP", "WSP", "JMZ", "HLT", "CALL", "OVP", "RET"
    };
    int id = -13;
    for (String op : ops) {
      OPCODE_MAP.put(id, op);
      STRING_TO_OPCODE.put(op, id);
      id++;
    }
  }

  public static Integer getOpcodeId(String opcode) {
    return STRING_TO_OPCODE.get(opcode);
  }

  private int getAbsoluteIndex(int address) {
    int index = (address + MAX_VALUE) % _ramSize;
    if (index < 0)
      index += _ramSize;
    return index;
  }

  private int readRam(int address) {
    return _ram[getAbsoluteIndex(address)];
  }

  private void writeRam(int address, int value) {
    _ram[getAbsoluteIndex(address)] = value;
  }

  private int readRam12(int address) {
    int upper = readRam(address);
    int lower = readRam(address + 1);
    return upper * 729 + lower;
  }

  private void writeRam12(int address, int value) {
    int lower = value % 729;
    if (lower > 364)
      lower -= 729;
    else if (lower < -364)
      lower += 729;
    int upper = (value - lower) / 729;

    writeRam(address, upper);
    writeRam(address + 1, lower);
  }

  public void injectRAM(int address, int value) {
    writeRam(address, value);
  }

  public int getRamValue(int address) {
    return readRam(address);
  }

  public int getReg(int index) {
    return _registers[index + 1];
  }

  public void setReg(int index, int value) {
    _registers[index + 1] = truncate12(value);
  }

  public int getSP() {
    return _stackpointer;
  }

  public int getPC() {
    return _programcounter;
  }

  public void setPC(int value) {
    _programcounter = value;
  }

  public int getStatus() {
    return _status;
  }

  public int getOvr() {
    return _overflow;
  }

  public int truncate12(long value) {
    long mod = (value + 265720) % 531441;
    if (mod < 0)
      mod += 531441;
    return (int) (mod - 265720);
  }

  private void updateFlags(long res) {
    if (res > MAX_VALUE)
      _overflow = 1;
    else if (res < -MAX_VALUE)
      _overflow = -1;
    else
      _overflow = 0;

    int truncated = truncate12(res);
    if (truncated > 0)
      _status = 1;
    else if (truncated < 0)
      _status = -1;
    else
      _status = 0;
  }

  public void step() {
    if (HLT)
      return;

    int cmdWord = readRam(_programcounter);
    int imm = readRam(_programcounter + 1);
    _programcounter += 2;

    int shifted = cmdWord + 364;
    int rs = (shifted % 3) - 1;
    shifted /= 3;
    int rd = (shifted % 3) - 1;
    shifted /= 3;
    int opcodeId = (shifted % 27) - 13;

    String opcode = OPCODE_MAP.get(opcodeId);
    if (opcode == null)
      throw new RuntimeException("CPU Panic! Unrecognized opcode id: " + opcodeId);

    executeDecoded(opcode, rd, rs, imm);
  }

  private void executeDecoded(String opcode, int rd, int rs, int imm) {
    switch (opcode) {
      case "SUB":
        SUB(rd, rs);
        break;
      case "SHF":
        SHF(rd, rs);
        break;
      case "ADD":
        ADD(rd, rs);
        break;
      case "SBC":
        SBC(rd, rs);
        break;
      case "CMP":
        CMP(rd, rs);
        break;
      case "ADC":
        ADC(rd, rs);
        break;
      case "MIN":
        MIN(rd, rs);
        break;
      case "MAX":
        MAX(rd, rs);
        break;
      case "INV":
        INV(rd);
        break;
      case "LDR":
        LDR(rd, rs);
        break;
      case "LLI":
        LLI(rd, imm);
        break;
      case "LHI":
        LHI(rd, imm);
        break;
      case "STR":
        STR(rd, rs);
        break;
      case "MOV":
        MOV(rd, rs);
        break;
      case "PSH":
        PSH(rd);
        break;
      case "POP":
        POP(rd);
        break;
      case "PSF":
        PSF();
        break;
      case "POF":
        POF();
        break;
      case "JMN":
        JMN(rd);
        break;
      case "OVN":
        OVN(rd);
        break;
      case "JMP":
        JMP(rd);
        break;
      case "WSP":
        WSP(rd);
        break;
      case "JMZ":
        JMZ(rd);
        break;
      case "HLT":
        HLT();
        break;
      case "CALL":
        CALL(rd);
        break;
      case "OVP":
        OVP(rd);
        break;
      case "RET":
        RET();
        break;
      default:
        throw new IllegalArgumentException("Unknown instruction: " + opcode);
    }
  }

  private void ADD(int rd, int rs) {
    long res = (long) getReg(rd) + (long) getReg(rs);
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void SUB(int rd, int rs) {
    long res = (long) getReg(rd) - (long) getReg(rs);
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void ADC(int rd, int rs) {
    long res = (long) getReg(rd) + (long) getReg(rs) + _overflow;
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void SBC(int rd, int rs) {
    long res = (long) getReg(rd) - (long) getReg(rs) + _overflow;
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void CMP(int rd, int rs) {
    long diff = (long) getReg(rd) - (long) getReg(rs);
    updateFlags(diff);
  }

  private void MIN(int rd, int rs) {
    long res = Math.min(getReg(rd), getReg(rs));
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void MAX(int rd, int rs) {
    long res = Math.max(getReg(rd), getReg(rs));
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void INV(int rd) {
    long res = -getReg(rd);
    updateFlags(res);
    setReg(rd, truncate12(res));
  }

  private void SHF(int rd, int rs) {
    int shift = getReg(rs);
    long val = getReg(rd);
    if (shift > 0) {
      if (shift >= 12)
        val = 0;
      else
        val *= (long) Math.pow(3, shift);
    } else if (shift < 0) {
      if (shift <= -12)
        val = 0;
      else
        val /= (long) Math.pow(3, -shift);
    }
    updateFlags(val);
    setReg(rd, truncate12(val));
  }

  private void MOV(int rd, int rs) {
    setReg(rd, getReg(rs));
  }

  private void LDR(int rd, int rs) {
    setReg(rd, readRam12(getReg(rs)));
  }

  private void STR(int rd, int rs) {
    writeRam12(getReg(rs), getReg(rd));
  }

  private void LLI(int rd, int imm) {
    int val = getReg(rd);
    int lower = val % 729;
    if (lower > 364)
      lower -= 729;
    else if (lower < -364)
      lower += 729;
    int upper = (val - lower) / 729;
    setReg(rd, upper * 729 + imm);
  }

  private void LHI(int rd, int imm) {
    int val = getReg(rd);
    int lower = val % 729;
    if (lower > 364)
      lower -= 729;
    else if (lower < -364)
      lower += 729;
    setReg(rd, imm * 729 + lower);
  }

  private void PSH(int rd) {
    _stackpointer -= 2;
    writeRam12(_stackpointer, getReg(rd));
  }

  private void POP(int rd) {
    setReg(rd, readRam12(_stackpointer));
    _stackpointer += 2;
  }

  private void PSF() {
    _stackpointer -= 2;
    int rf = (_status * 3) + _overflow;
    writeRam12(_stackpointer, rf);
  }

  private void POF() {
    int rf = readRam12(_stackpointer);
    _stackpointer += 2;
    int lower = rf % 3;
    if (lower > 1)
      lower -= 3;
    else if (lower < -1)
      lower += 3;
    _overflow = lower;
    int upper = (rf - lower) / 3;
    int stat = upper % 3;
    if (stat > 1)
      stat -= 3;
    else if (stat < -1)
      stat += 3;
    _status = stat;
  }

  private void JMN(int rd) {
    if (_status == -1)
      _programcounter = getReg(rd);
  }

  private void JMP(int rd) {
    if (_status == 1)
      _programcounter = getReg(rd);
  }

  private void JMZ(int rd) {
    if (_status == 0)
      _programcounter = getReg(rd);
  }

  private void OVN(int rd) {
    if (_overflow == -1)
      _programcounter = getReg(rd);
  }

  private void OVP(int rd) {
    if (_overflow == 1)
      _programcounter = getReg(rd);
  }

  private void CALL(int rd) {
    _stackpointer -= 2;
    writeRam12(_stackpointer, _programcounter);
    _programcounter = getReg(rd);
  }

  private void RET() {
    _programcounter = readRam12(_stackpointer);
    _stackpointer += 2;
  }

  private void WSP(int rd) {
    _stackpointer = getReg(rd);
  }

  private void HLT() {
    HLT = true;
  }
}
