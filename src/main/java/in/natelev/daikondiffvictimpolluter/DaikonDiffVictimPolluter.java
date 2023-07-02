package in.natelev.daikondiffvictimpolluter;

import daikon.*;
import in.natelev.daikondiffvictimpolluter.RankedInvs.RankedInvByVarMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static in.natelev.daikondiffvictimpolluter.Colors.*;
import static in.natelev.daikondiffvictimpolluter.Output.*;

public class DaikonDiffVictimPolluter {
    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
        }

        if (args.length > 3) {
            boolean lookingForOutput = false;
            for (String arg : Arrays.asList(args).subList(3, args.length)) {
                if (lookingForOutput) {
                    lookingForOutput = false;
                    try {
                        Output.setOutputFile(arg);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                } else if (arg.equals("-o")) {
                    lookingForOutput = true;
                } else if (arg.equals("-h")) {
                    printUsage();
                } else if (arg.equals("--debug")) {
                    debug = true;
                    log(GREEN + "Debug enabled." + RESET);
                } else if (arg.equals("--output-colors-to-file")) {
                    log(GREEN + "Will send colors to file if possible" + RESET);
                    Output.setOutputColorsToFile(true);
                } else {
                    System.err.println(RED + "Unknown option " + arg + RESET);
                    System.exit(1);
                }
            }
        }

        log(MAGENTA + "Loading PptMaps..." + RESET);
        ReducedPptMap polluterVictim = getPptMap(new File(args[0]));
        log(BLUE + "Loaded polluter+victim. Now loading victim..." + RESET);
        ReducedPptMap victim = getPptMap(new File(args[1]));
        log(CYAN + "Loaded victim. Now loading polluter..." + RESET);
        ReducedPptMap polluter = getPptMap(new File(args[2]));
        log(GREEN + "\rLoaded all PptMaps!\n" + RESET);

        if (debug) {
            String debugPrelude = "\n\n\n" + RED + "!!! DEBUG: " + RESET;
            log(debugPrelude + "Polluter+Victim\n" + polluterVictim);
            log(debugPrelude + "Polluter\n" + polluter);
            log(debugPrelude + "Victim\n" + victim);
        }

        ReducedPptMap pvMinusP = allPptsinPVButNotOnlyInP(polluterVictim, polluter,
                victim);

        diff(pvMinusP, victim);

        Output.shutdown();
    }

    private static void printUsage() {
        System.err.println(
                RED + "Usage: DaikonDiffVictimPolluter daikon-pv.inv daikon-victim.inv daikon-polluter.inv (-o <output.dinv>) (--debug) (--output-colors-to-file)"
                        + RESET);
        System.exit(1);
    }

    private static ReducedPptMap getPptMap(File file) {
        try {
            return new ReducedPptMap(FileIO.read_serialized_pptmap(file, true));
        } catch (Throwable e) {
            System.err.println("Error reading serialized pptmap");
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }

    private static ReducedPptMap allPptsinPVButNotOnlyInP(ReducedPptMap polluterVictim, ReducedPptMap polluter,
            ReducedPptMap victim) {
        ReducedPptMap pvMinusP = new ReducedPptMap();
        for (ReducedPpt ppt : polluterVictim.pptIterable()) {
            boolean inPolluter = polluter.containsName(ppt.name);
            boolean inVictim = victim.containsName(ppt.name);

            if (!inPolluter || inVictim) {
                pvMinusP.map.put(ppt.name, ppt);
            }
        }
        return pvMinusP;
    }

    private static void diff(ReducedPptMap pvMinusP, ReducedPptMap victim) {
        StringBuilder diffBuilder = new StringBuilder();

        ArrayList<RankedInvs> rankedOutputInvariants = new ArrayList<>();
        int MAX_RANKED_OUTPUT_INVARIANTS = 6;

        for (ReducedPpt ppt : pvMinusP.pptIterable()) {
            ReducedPpt victimPpt = victim.map.get(ppt.name);

            if (victimPpt == null) {
                continue;
            }

            boolean continueRankingInvariants = rankedOutputInvariants.size() < MAX_RANKED_OUTPUT_INVARIANTS;
            RankedInvByVarMap invariantsByVariable = continueRankingInvariants ? new RankedInvByVarMap()
                    : null;
            List<ReducedInvariant> originalInvariants = ppt.getInvariants();
            List<ReducedInvariant> victimInvariants = victimPpt.getInvariants();

            // remove duplicates
            List<ReducedInvariant> invariants = originalInvariants.stream()
                    .filter((invariant) -> !victimInvariants.contains(invariant)).collect(Collectors.toList());
            victimInvariants.removeIf((invariant) -> originalInvariants.contains(invariant));

            // remove all invariants without a similar invariant in the other run
            invariants.removeIf(
                    (invariant) -> !victimInvariants.stream()
                            .anyMatch((other) -> invariant.hasSameFirstVariableAs(other)));
            victimInvariants.removeIf(
                    (invariant) -> !invariants.stream().anyMatch((other) -> invariant.hasSameFirstVariableAs(other)));

            if (invariants.size() == 0 || victimInvariants.size() == 0)
                continue;

            diffBuilder.append(
                    BLUE +
                            "==========================================================================="
                            + RESET + "\n");
            diffBuilder.append(YELLOW + ppt.name + RESET + "\n");

            for (ReducedInvariant invariant : invariants) {
                if (invariantsByVariable != null && !invariant.getType().contains("NonEqual")
                        && invariant.firstVar() != null)
                    invariantsByVariable.put(invariant.firstVar(), invariant, true, ppt.name);
                diffBuilder.append(
                        RED + "p+v> " + RESET + invariant + " " + RED
                                + "(polluter+victim only)" +
                                RESET + "\n");
            }

            for (ReducedInvariant invariant : victimInvariants) {
                if (invariantsByVariable != null && !invariant.getType().contains("NonEqual")
                        && invariant.firstVar() != null)
                    invariantsByVariable.put(invariant.firstVar(), invariant, false, ppt.name);
                diffBuilder.append(GREEN + "  v> " + RESET + invariant + " " + GREEN
                        + "(victim only)" +
                        RESET + "\n");
            }

            if (continueRankingInvariants) {
                rankedOutputInvariants.addAll(invariantsByVariable.values().stream()
                        .filter((rankedInvs) -> rankedInvs.hasBoth())
                        .sorted((a, b) -> a.totalSize() - b.totalSize())
                        .limit(MAX_RANKED_OUTPUT_INVARIANTS - rankedOutputInvariants.size())
                        .collect(Collectors.toList()));
            }

            diffBuilder.append("\n");
        }

        output(String.join("\n", rankedOutputInvariants.stream()
                .map(rankedInvs -> rankedInvs.toString())
                .collect(Collectors.toList())));

        outputIfFile("\n");
        outputIfFile(diffBuilder.toString());
    }
}
