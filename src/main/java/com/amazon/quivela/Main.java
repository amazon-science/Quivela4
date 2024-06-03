/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.quivela;

import com.amazon.quivela.parser.parser.*;
import com.amazon.quivela.parser.lexer.*;
import com.amazon.quivela.parser.node.*;
import com.amazon.quivela.checker.*;
import com.amazon.quivela.parser.parser.Parser;
import org.apache.commons.cli.*;

import java.io.*;


public class Main
{
    public static void main(String[] arguments) throws CheckException
    {
        Options options = new Options();

        Option boogiePathOption = Option.builder()
                .option("b").desc("path to Boogie executable").hasArg(true).argName("path").build();
        options.addOption(boogiePathOption);

        try
        {
            CommandLineParser cliParser = new DefaultParser();
            CommandLine cmd = cliParser.parse(options, arguments);

            if (cmd.hasOption(boogiePathOption)) {
                Settings.boogiePath = cmd.getOptionValue(boogiePathOption);
            }

            if (cmd.getArgs().length != 1) {
                printUsageAndExit(options);
            }

            String filename = cmd.getArgs()[0];

            // Create a Parser instance.
            Parser p =
                    new Parser(
                            new Lexer(
                                    new PushbackReader(
                                            new FileReader(filename), 1024)));


            // Parse the input.
            Start tree = p.parse();
            File f = new File(filename);
            Checker.check(f, tree.getPDevelopment());


        }
        catch (ParseException e) {
            // command line argument parsing exception
            printUsageAndExit(options);
        }
        catch(IOException | LexerException | ParserException e)
        {
            throw new CheckException(e);
        }
    }

    private static void printUsageAndExit(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("quivela4 [options] <inputFile>", options);
        System.exit(1);
    }
}
