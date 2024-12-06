package StatisticsTest;

import Statistics.NumAndProbability;
import Statistics.ProbabilisticRandomGenImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class ProbabilisticRandomGenImplTest {
    private ProbabilisticRandomGenImpl randomGen;

    @BeforeEach
    public void setUp() {
        List<NumAndProbability> numAndProbabilities = Arrays.asList(
                new NumAndProbability(1, 0.5),
                new NumAndProbability(2, 0.3),
                new NumAndProbability(3, 0.2)
        );
        randomGen = new ProbabilisticRandomGenImpl();
        randomGen.initialize(numAndProbabilities);
    }

    @Test
    public void testNextFromSampleDistribution() {
        Map<Integer, Integer> counts = new HashMap<>();
        int trials = 10000;

        // Run the test multiple times to observe the distribution
        for (int i = 0; i < trials; i++) {
            int result = randomGen.nextFromSample();
            counts.put(result, counts.getOrDefault(result, 0) + 1);
        }

        // Check the distribution is approximately correct
        assertTrue(counts.get(1) >= 4000 && counts.get(1) <= 6000, "Number 1 should be selected approximately 50% of the time.");
        assertTrue(counts.get(2) >= 2500 && counts.get(2) <= 3500, "Number 2 should be selected approximately 30% of the time.");
        assertTrue(counts.get(3) >= 1500 && counts.get(3) <= 2500, "Number 3 should be selected approximately 20% of the time.");
    }

    @Test
    public void testNormalizationOfProbabilities() {
        List<NumAndProbability> numAndProbabilities = Arrays.asList(
                new NumAndProbability(1, 1.0),
                new NumAndProbability(2, 1.0),
                new NumAndProbability(3, 1.0)
        );

        randomGen.initialize(numAndProbabilities);
        assertEquals(1.0 / 3.0, randomGen.nextFromSample(), "The probabilities should be normalized to 1/3 for each number.");
    }

    @Test
    public void testIllegalStateException() {
        List<NumAndProbability> emptyProbabilities = Arrays.asList();
        randomGen.initialize(emptyProbabilities);

        assertThrows(IllegalStateException.class, () -> {
            randomGen.nextFromSample();
        }, "Should throw IllegalStateException when no probabilities are defined.");
    }
}