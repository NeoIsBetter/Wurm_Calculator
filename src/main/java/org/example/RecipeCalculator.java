package org.example;

import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class RecipeCalculator extends JFrame {
    // Fixed values
    private static final int SUGAR_VAL = 47;
    private static final int WATER_VAL = 6;
    private static final int OVEN_VAL  = 40;
    private static final int CYCLE     = 138;

    // Vegetable base values
    private static final Map<String,Integer> VEGETABLES = Map.of(
            "Cucumber", 17,
            "Carrot",    41,
            "Cabbage",   42,
            "Tomato",    43,
            "Corn",      44,
            "Pumpkin",   45,
            "Pea Pod",   46,
            "Potato",    47,
            "Peas",      58,
            "Onion",     91
    );

    // Preparation methods
    private static final List<Prep> PREP_METHODS = List.of(
            new Prep("Fried",   1),
            new Prep("Roasted", 4)
    );

    private static class Prep {
        final String name;
        final int    val;
        Prep(String name, int val) { this.name = name; this.val = val; }
    }

    // One “variant” = one vegetable + one preparation
    private static class Variant {
        final String veg, prep;
        final int    value;
        Variant(String veg, String prep, int value) {
            this.veg   = veg;
            this.prep  = prep;
            this.value = value;
        }
    }

    // All precomputed variants
    private final List<Variant> variants = new ArrayList<>();

    // UI components
    private final JTextField targetField  = new JTextField(5);
    private final JTextField offsetField  = new JTextField("0", 5);
    private final JTextField sizeField    = new JTextField("0", 5);
    private final JButton    calcButton   = new JButton("Calculate Recipe");
    private final JTextArea  resultArea   = new JTextArea(16, 40);

    public RecipeCalculator() {
        super("Recipe Calculator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        buildVariants();
        layoutComponents();
        attachListeners();

        pack();
        setLocationRelativeTo(null);
    }

    private void buildVariants() {
        // Precompute all veg × prep variants
        for (var entry : VEGETABLES.entrySet()) {
            String vegBase = entry.getKey();
            int    baseVal = entry.getValue();
            for (var p : PREP_METHODS) {
                variants.add(new Variant(
                        vegBase, p.name, baseVal + p.val
                ));
            }
        }
    }

    private void layoutComponents() {
        JPanel input = new JPanel();
        input.add(new JLabel("Target (0–137):"));
        input.add(targetField);
        input.add(new JLabel("Offset:"));
        input.add(offsetField);
        input.add(new JLabel("Size:"));
        input.add(sizeField);
        input.add(calcButton);

        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        getContentPane().setLayout(new BorderLayout(5,5));
        getContentPane().add(input, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(resultArea), BorderLayout.CENTER);
    }

    private void attachListeners() {
        calcButton.addActionListener(e -> {
            try {
                int target = Integer.parseInt(targetField.getText().trim());
                int offset = Integer.parseInt(offsetField.getText().trim());
                int size   = Integer.parseInt(sizeField.getText().trim());

                if (target < 0 || target >= CYCLE)
                    throw new NumberFormatException("Target must be between 0 and 137.");
                if (size < 0)
                    throw new NumberFormatException("Size must be ≥ 0.");

                resultArea.setText(computeRecipe(target, offset, size));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Please enter valid integers:\n" + ex.getMessage(),
                        "Input Error", JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private String computeRecipe(int target, int offset, int size) {
        // 1) wrap (target + offset) into 0..137
        int raw     = target + offset;
        int wrapped = raw % CYCLE;
        if (wrapped < 0) wrapped += CYCLE;

        // 2) apply size cycles
        int fullTarget = size * CYCLE + wrapped;

        // 3) ensure at least one variant fits by bumping fullTarget if needed
        int reserved   = SUGAR_VAL + WATER_VAL + OVEN_VAL;
        int minVariant = variants.stream()
                .mapToInt(v -> v.value)
                .min()
                .orElse(Integer.MAX_VALUE);
        while (fullTarget - reserved < minVariant) {
            fullTarget += CYCLE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("▶ Effective target = ")
                .append(size).append("×").append(CYCLE)
                .append(" + ").append(wrapped)
                .append(" + adjustments = ").append(fullTarget)
                .append("\n\nMandatory:\n")
                .append("  • Sugar ×1 (").append(SUGAR_VAL).append(")\n")
                .append("  • Water ×1 (").append(WATER_VAL).append(")\n")
                .append("  • Oven  ×1 (").append(OVEN_VAL).append(")\n\n");

        // 4) distribute evenly across all variants
        int remain = fullTarget - reserved;
        List<Variant> combo = new ArrayList<>();
        int[] counts = new int[variants.size()];

        while (remain > 0) {
            // collect indices of variants that fit
            List<Integer> eligible = new ArrayList<>();
            for (int i = 0; i < variants.size(); i++) {
                if (variants.get(i).value <= remain) {
                    eligible.add(i);
                }
            }

            // if none fit, bump by one cycle and retry
            if (eligible.isEmpty()) {
                fullTarget += CYCLE;
                remain += CYCLE;
                continue;
            }

            // find min usage among eligible
            int minCount = Integer.MAX_VALUE;
            for (int idx : eligible) {
                minCount = Math.min(minCount, counts[idx]);
            }

            // pick the first eligible with that min usage
            int chosenIdx = -1;
            for (int idx : eligible) {
                if (counts[idx] == minCount) {
                    chosenIdx = idx;
                    break;
                }
            }

            Variant chosen = variants.get(chosenIdx);
            combo.add(chosen);
            counts[chosenIdx]++;
            remain -= chosen.value;
        }

        // 5) tally and output
        sb.append("Vegetables (base + prep), evenly distributed:\n");
        Map<String,Map<String,Integer>> tally = new LinkedHashMap<>();
        for (Variant v : combo) {
            tally.computeIfAbsent(v.veg, k -> new LinkedHashMap<>())
                    .merge(v.prep, 1, Integer::sum);
        }
        for (var vegEntry : tally.entrySet()) {
            String veg = vegEntry.getKey();
            for (var prepEntry : vegEntry.getValue().entrySet()) {
                String prep = prepEntry.getKey();
                int count   = prepEntry.getValue();
                int single  = VEGETABLES.get(veg)
                        + PREP_METHODS.stream()
                        .filter(p->p.name.equals(prep))
                        .findFirst().get().val;
                sb.append(String.format(
                        "  • %s ×%d: %s (each %d)\n",
                        veg, count, prep.toLowerCase(), single
                ));
            }
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        // Enable Nimbus look-and-feel for a modern UI:
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // If Nimbus is not available, the default look-and-feel will be used.
        }

        SwingUtilities.invokeLater(() -> new RecipeCalculator().setVisible(true));
    }
}
