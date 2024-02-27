package com.example;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a simple assembler with two passes to process SIC/XE assembly language programs.
 * It generates a Symbol Table, Location Table, and Object Code for each input file.
 */
public class Main {
    // Data structures to store symbol table, location table, and operation codes
    private Map<String, Integer> symTable = new HashMap<>();
    private Map<String, Integer> locTable = new HashMap<>();
    private Map<String, Integer> optab = new HashMap<>();

    // Program counters and lengths
    private int locCounter = 0;
    private int programLength = 0;

    // Flags for processing literals
    private boolean inLiteral = false;
    private int literalLength = 0;

    /**
     * Main method to execute the assembler for a set of input files.
     *
     * @param args Command line arguments (not used in this implementation).
     */
    public static void main(String[] args) {
        // Create an instance of the assembler
        Main assembler = new Main();

        // Input files to process
        String[] inputFiles = {"basic.txt", "functions.txt", "literals.txt", "control_section.txt", "macros.txt", "prog_blocks.txt"};

        // Process each input file
        for (String inputFileName : inputFiles) {
            try {
                // Execute pass 1 and pass 2
                assembler.pass1(inputFileName);
                assembler.pass2(inputFileName, "output.obj");

                // Display tables after processing each input file
                assembler.displayTables(inputFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Pass 1 of the assembler: Build Symbol and Location Tables.
     *
     * @param inputFileName Input file containing SIC/XE assembly code.
     * @throws IOException If an I/O error occurs while reading the input file.
     */
    private void pass1(String inputFileName) throws IOException {
        // Open the input file for reading
        BufferedReader reader = new BufferedReader(new FileReader(inputFileName));
        String line;

        // Flags to track control sections and program blocks
        boolean inControlSection = false;
        String currentControlSection = "";
        boolean inProgramBlock = false;
        String currentProgramBlock = "";

        // Process each line in the input file
        while ((line = reader.readLine()) != null) {
            // Split the line into tokens
            String[] tokens = line.split("\\s+");

            // Skip empty lines
            if (tokens.length == 0) {
                continue;
            }

            // Extract label and opcode
            String label = tokens[0];
            String opcode = (tokens.length > 1) ? tokens[1] : "";

            // Process labels and symbols
            if (!label.isEmpty()) {
                // Prepend control section or program block name to the label if applicable
                if (inControlSection) {
                    label = currentControlSection + "." + label;
                } else if (inProgramBlock) {
                    label = currentProgramBlock + "." + label;
                }
                // Build the symbol table
                symTable.put(label, locCounter);
            }

            // Determine program length and update location counter based on opcodes
            if (opcode.equals("END")) {
                programLength = locCounter;
                inControlSection = false;
            }

            // Update location counter based on opcodes
            if (opcode.equals("CSECT")) {
                locCounter = 0; // Start of a new control section
                inControlSection = true;
                currentControlSection = label;
            } else if (opcode.equals("USE")) {
                // Start of a program block
                inProgramBlock = true;
                currentProgramBlock = (tokens.length > 2) ? tokens[2] : "";
            } else if (opcode.equals("ENDUSE")) {
                // End of a program block
                inProgramBlock = false;
                currentProgramBlock = "";
            } else if (opcode.equals("RESW") || opcode.equals("RESB")) {
                int operandValue = (tokens.length > 2) ? Integer.parseInt(tokens[2]) : 0;
                locCounter += (opcode.equals("RESW")) ? 3 * operandValue : operandValue;
            } else if (opcode.equals("BYTE")) {
                // Handle BYTE directive
                inLiteral = true;
                String literalValue = (tokens.length > 2) ? tokens[2] : "";
                literalLength = (literalValue.startsWith("X'")) ? (literalValue.length() - 3) / 2 : (literalValue.length() - 3);
                locCounter += literalLength;
            } else if (opcode.equals("MEND")) {
                // End of macro definition (no action in pass 1)
            } else if (inProgramBlock) {
                // Process program block instructions (no action in pass 1)
            } else if (opcode.equals("START")) {
                // Set starting address for the program
                locCounter = (tokens.length > 2) ? Integer.parseInt(tokens[2], 16) : 0;
            } else {
                // Update location counter based on standard instructions
                locCounter += (opcode.startsWith("+")) ? 4 : 3; // Assuming fixed format instructions
            }

            // Update the Location Table
            locTable.put(opcode, locCounter);
        }

        // Close the input file
        reader.close();
    }

    /**
     * Pass 2 of the assembler: Generate Object Code.
     *
     * @param inputFileName  Input file containing SIC/XE assembly code.
     * @param outputFileName Output file to write the generated object code.
     * @throws IOException If an I/O error occurs while reading the input file or writing the output file.
     */
    private void pass2(String inputFileName, String outputFileName) throws IOException {
        // Define the OPTAB entries for SIC/XE instructions
        optab.put("LDX", 0x04);
        optab.put("LDA", 0x00);
        optab.put("ADD", 0x18);
        optab.put("STA", 0x0C);
        optab.put("RSUB", 0x4C);
        optab.put("TIX", 0x2C);
        optab.put("JLT", 0x38);
        optab.put("LDS", 0x6C);
        optab.put("LDT", 0x74);
        optab.put("ADDR", 0x90);
        optab.put("COMPR", 0xA0);
        optab.put("JSUB", 0x48);
        optab.put("CLEAR", 0xB4);
        optab.put("TIXR", 0xB8);
        optab.put("JEQ", 0x30);
        optab.put("J", 0x3C);

        // Open the input file for reading and the output file for writing
        BufferedReader reader = new BufferedReader(new FileReader(inputFileName));
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName));
        String line;

        // Flags to track control sections and program blocks
        boolean inControlSection = false;
        String currentControlSection = "";
        boolean inProgramBlock = false;
        String currentProgramBlock = "";

        // Process each line to generate object code
        while ((line = reader.readLine()) != null) {
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) continue;

            String label = tokens[0];
            String opcode = tokens[1];

            // Generate Object Code for instructions
            if (optab.containsKey(opcode)) {
                Integer operandValue = symTable.get(tokens[2]); // Assuming operand is a symbol

                if (operandValue != null) {
                    // Check if the instruction is format 4 (indicated by a preceding '+')
                    boolean isFormat4 = opcode.startsWith("+");

                    // Remove the '+' if present for opcode lookup in OPTAB
                    String lookupOpcode = isFormat4 ? opcode.substring(1) : opcode;

                    // Get the opcode value from OPTAB
                    int opcodeValue = optab.get(lookupOpcode);

                    // Calculate displacement (disp) for instructions without '+'
                    int disp = 0;
                    if (!isFormat4) {
                        disp = operandValue - locCounter;
                    }

                    // Set the format flag (e bit)
                    int formatFlag = isFormat4 ? 1 : 0;

                    // Combine opcode, nixbpe bits, and displacement into the object code
                    int objectCode = (opcodeValue << 24) | (formatFlag << 23) | (disp & 0x7FFFFF);

                    // Write the generated object code to the output file
                    writer.write(String.format("%08X", objectCode) + "\n");
                } else {
                    // Handle case where operand value is not found in the symbol table
      
                }
            }
        }

        // Close the input and output files
        reader.close();
        writer.close();
    }

    /**
     * Display Symbol and Location Tables along with the Object Program for each input file.
     *
     * @param inputFileName Input file for which tables and object program are displayed.
     */
    private void displayTables(String inputFileName) {
        // Display tables for the input file
        System.out.println("Tables for Input File: " + inputFileName);
        System.out.println("==============================================");

        // Display Symbol Table
        System.out.println("Symbol Table:");
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-15s %-15s %-25s %-15s%n", "LINE", "LOC", "SOURCE STATEMENT", "OBJECT CODE");

        int line = 1;
        for (Map.Entry<String, Integer> entry : symTable.entrySet()) {
            System.out.printf("%-15d %-15d %-25s %-15s%n", line++, entry.getValue(), entry.getKey(), "");
        }

        // Display Location Table
        System.out.println("\nLocation Table:");
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-15s %-15s %-25s %-15s%n", "LINE", "LOC", "SOURCE STATEMENT", "OBJECT CODE");

        line = 1;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFileName));
            String sourceLine;

            while ((sourceLine = reader.readLine()) != null) {
                String[] tokens = sourceLine.split("\\s+");
                if (tokens.length < 2) continue;

                String opcode = tokens[1];

                // Display Object Code for instructions
                if (optab.containsKey(opcode)) {
                    Integer operandValue = symTable.get(tokens[2]); // Assuming operand is a symbol

                    if (operandValue != null) {
                        int objectCode = optab.get(opcode) << 16 | operandValue.intValue();
                        System.out.printf("%-15d %-15d %-25s %-15s%n", line++, locTable.get(opcode), sourceLine, String.format("%06X", objectCode));
                    }
                }
            }

            // Close the reader for the input file
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Display program length
        System.out.println("\nProgram Length: " + programLength);
        System.out.println("==============================================\n");
    }
}
